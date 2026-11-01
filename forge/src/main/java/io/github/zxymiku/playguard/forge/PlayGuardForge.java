package io.github.zxymiku.playguard.forge;

import io.github.zxymiku.playguard.PlayGuard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;

@Mod("playguard")
public final class PlayGuardForge {
    private PlayGuard guard;
    public PlayGuardForge() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::joined);
        MinecraftForge.EVENT_BUS.addListener(this::left);
        MinecraftForge.EVENT_BUS.addListener(this::stopping);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }
    private void started(ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer()) guard = new PlayGuard(event.getServer());
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && guard != null) guard.tick();
    }
    private void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (guard != null && event.getEntity() instanceof ServerPlayer player) guard.join(player);
    }
    private void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (guard != null && event.getEntity() instanceof ServerPlayer player) guard.leave(player);
    }
    private void stopping(ServerStoppingEvent event) { if (guard != null) { guard.stop(); guard = null; } }
    private void commands(RegisterCommandsEvent event) { PlayGuard.registerCommands(event.getDispatcher(), () -> guard); }
}
