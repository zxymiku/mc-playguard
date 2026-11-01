package io.github.zxymiku.playguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** All callbacks and mutations run on the Minecraft server thread. */
public final class PlayGuard {
    private static final Logger LOG = LoggerFactory.getLogger("PlayGuard");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SOURCE = "[playguard:temporary-rest]";
    private static final String REASON = "[PlayGuard] 游玩时间已满，请休息后再回来。";
    private final MinecraftServer server;
    private final Path configFile = Path.of("config", "playguard.json");
    private final Path stateFile;
    private Config config;
    private State state;
    private final Map<UUID, Long> lastSeen = new HashMap<>();
    private long lastTick = System.nanoTime();
    private long lastSave = lastTick;

    public static final class Config {
        public int playMinutes = 180;
        public int restMinutes = 60;
        public List<String> whitelist = new ArrayList<>();
        void validate() {
            if (playMinutes < 1 || playMinutes > 525600 || restMinutes < 1 || restMinutes > 525600
                    || whitelist == null || whitelist.stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("时间必须在 1~525600 分钟之间，whitelist 必须为字符串数组");
        }
    }
    public static final class PlayerState {
        public String name;
        public PlayClock clock = new PlayClock();
        public long bannedUntil;
    }
    public static final class State {
        public Map<UUID, PlayerState> players = new HashMap<>();
    }

    public PlayGuard(MinecraftServer server) {
        this.server = server;
        stateFile = server.getWorldPath(LevelResource.ROOT).resolve("playguard-state.json");
        try {
            config = read(configFile, Config.class, new Config());
            config.validate();
            if (!Files.exists(configFile)) write(configFile, config);
            state = read(stateFile, State.class, new State());
            if (state.players == null) throw new IOException("Missing players map");
            for (var entry : state.players.entrySet()) {
                PlayerState p = entry.getValue();
                if (p == null || p.clock == null || p.clock.playedMillis < 0 || p.bannedUntil < 0)
                    throw new IOException("Invalid player state: " + entry.getKey());
                if (p.bannedUntil != 0) { p.bannedUntil = 0; p.clock = new PlayClock(); }
            }
            // Source+reason identify our bans independently of the state file (including a crash).
            for (UserBanListEntry ban : new ArrayList<>(server.getPlayerList().getBans().getEntries())) {
                if (owned(ban)) server.getPlayerList().getBans().remove(ban.getUser());
            }
            save();
            LOG.info("PlayGuard active: {} minutes play / {} minutes rest", config.playMinutes, config.restMinutes);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("PlayGuard 配置或存档读取失败，请修复文件后重启；原文件不会被覆盖", e);
        }
    }

    public void join(ServerPlayer player) {
        UUID id = player.getUUID();
        PlayerState p = state.players.computeIfAbsent(id, ignored -> new PlayerState());
        p.name = player.getGameProfile().getName();
        if (exempt(id, p.name)) { clear(id, p); return; }
        if (p.bannedUntil > System.currentTimeMillis()) {
            player.connection.disconnect(Component.literal(REASON));
            return;
        }
        lastSeen.put(id, System.nanoTime());
        player.sendSystemMessage(Component.literal("[防沉迷] 本轮已在线 " + p.clock.playedMillis / 60000
                + " 分钟，累计达到 " + config.playMinutes + " 分钟后需休息 " + config.restMinutes + " 分钟。"));
    }

    public void leave(ServerPlayer player) {
        account(player, System.nanoTime());
        lastSeen.remove(player.getUUID());
        save();
    }

    public void tick() {
        long now = System.nanoTime();
        if (now - lastTick < 1_000_000_000L) return;
        lastTick = now;
        long wallNow = System.currentTimeMillis();
        for (var entry : state.players.entrySet()) {
            PlayerState p = entry.getValue();
            if (p.bannedUntil != 0 && (wallNow >= p.bannedUntil || exempt(entry.getKey(), p.name))) {
                clear(entry.getKey(), p);
                save();
            }
        }
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) account(player, now);
        if (now - lastSave >= 30_000_000_000L) { save(); lastSave = now; }
    }

    private void account(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        PlayerState p = state.players.get(id);
        if (p == null) return;
        Long previous = lastSeen.get(id);
        if (exempt(id, p.name)) { clear(id, p); return; }
        if (previous == null || p.bannedUntil != 0) return;
        lastSeen.put(id, now);
        long limit = config.playMinutes * 60_000L;
        for (int minutes : p.clock.advance((now - previous) / 1_000_000L, limit)) {
            player.sendSystemMessage(Component.literal("[防沉迷] 还有 " + minutes
                    + " 分钟将达到游玩时限，届时封禁 " + config.restMinutes + " 分钟，请及时保存进度。"));
        }
        if (p.clock.exhausted(limit)) {
            p.bannedUntil = System.currentTimeMillis() + config.restMinutes * 60_000L;
            lastSeen.remove(id);
            var bans = server.getPlayerList().getBans();
            var existing = bans.get(player.getGameProfile());
            if (existing == null || owned(existing)) {
                bans.add(new UserBanListEntry(player.getGameProfile(), new Date(), SOURCE,
                        new Date(p.bannedUntil), REASON));
            }
            save();
            player.connection.disconnect(Component.literal(REASON + " 休息 " + config.restMinutes + " 分钟后自动解封。"));
        }
    }

    private static boolean owned(UserBanListEntry ban) {
        return ban != null && SOURCE.equals(ban.getSource()) && REASON.equals(ban.getReason());
    }
    private boolean exempt(UUID id, String name) {
        return config.whitelist.stream().anyMatch(s -> s.equalsIgnoreCase(id.toString())
                || (name != null && s.equalsIgnoreCase(name)));
    }
    private void clear(UUID id, PlayerState p) {
        GameProfile profile = new GameProfile(id, p.name == null ? "unknown" : p.name);
        var bans = server.getPlayerList().getBans();
        if (owned(bans.get(profile))) bans.remove(profile);
        p.bannedUntil = 0;
        p.clock = new PlayClock();
        lastSeen.remove(id);
        if (!exempt(id, p.name) && server.getPlayerList().getPlayer(id) != null)
            lastSeen.put(id, System.nanoTime());
    }
    public void stop() {
        long now = System.nanoTime();
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) account(p, now);
        save(); lastSeen.clear();
    }
    private void save() {
        try { write(stateFile, state); }
        catch (IOException e) { LOG.error("无法保存防沉迷计时数据: {}", stateFile, e); }
    }
    private static <T> T read(Path path, Class<T> type, T fallback) throws IOException {
        if (!Files.exists(path)) return fallback;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T result = JSON.fromJson(reader, type);
            if (result == null) throw new IOException("Empty JSON: " + path);
            return result;
        }
    }
    private static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, JSON.toJson(value), StandardCharsets.UTF_8);
        try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<PlayGuard> instance) {
        var root = Commands.literal("playguard").requires(s -> s.hasPermission(3));
        root.then(Commands.literal("reload").executes(c -> {
            PlayGuard g = instance.get();
            if (g == null) return 0;
            try {
                Config replacement = read(g.configFile, Config.class, new Config());
                replacement.validate();
                g.config = replacement;
                for (var e : g.state.players.entrySet())
                    if (g.exempt(e.getKey(), e.getValue().name)) g.clear(e.getKey(), e.getValue());
                for (ServerPlayer p : new ArrayList<>(g.server.getPlayerList().getPlayers()))
                    g.lastSeen.putIfAbsent(p.getUUID(), System.nanoTime());
                g.save();
                c.getSource().sendSuccess(() -> Component.literal("防沉迷配置已重载。"), true);
                return 1;
            } catch (IOException | RuntimeException e) {
                c.getSource().sendFailure(Component.literal("重载失败，保留旧配置：" + e.getMessage()));
                return 0;
            }
        }));
        var whitelist = Commands.literal("whitelist");
        whitelist.then(Commands.literal("list").executes(c -> {
            PlayGuard g = instance.get(); if (g == null) return 0;
            c.getSource().sendSuccess(() -> Component.literal("免封白名单：" + String.join(", ", g.config.whitelist)), false);
            return 1;
        }));
        for (String action : List.of("add", "remove")) {
            whitelist.then(Commands.literal(action).then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
                PlayGuard g = instance.get(); if (g == null) return 0;
                String target = StringArgumentType.getString(c, "player");
                if (!target.matches("[A-Za-z0-9_]{1,16}")) {
                    try { UUID.fromString(target); }
                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal("请输入玩家名或 UUID。")); return 0; }
                }
                List<String> before = new ArrayList<>(g.config.whitelist);
                g.config.whitelist.removeIf(s -> s.equalsIgnoreCase(target));
                if (action.equals("add")) g.config.whitelist.add(target);
                try { write(g.configFile, g.config); }
                catch (IOException e) {
                    g.config.whitelist = before;
                    c.getSource().sendFailure(Component.literal("白名单保存失败：" + e.getMessage())); return 0;
                }
                for (var e : g.state.players.entrySet()) {
                    if (g.exempt(e.getKey(), e.getValue().name)) g.clear(e.getKey(), e.getValue());
                }
                for (ServerPlayer p : g.server.getPlayerList().getPlayers())
                    g.lastSeen.putIfAbsent(p.getUUID(), System.nanoTime());
                g.save();
                c.getSource().sendSuccess(() -> Component.literal("白名单已更新：" + target), true);
                return 1;
            })));
        }
        root.then(whitelist);
        dispatcher.register(root);
    }
}
