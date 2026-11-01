package io.github.zxymiku.playguard.fabric;

import io.github.zxymiku.playguard.PlayGuard;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class PlayGuardFabric implements DedicatedServerModInitializer {
    private PlayGuard guard;
    @Override public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> guard = new PlayGuard(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (guard != null) guard.tick(); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (guard != null) guard.join(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (guard != null) guard.leave(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (guard != null) guard.stop(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            PlayGuard.registerCommands(dispatcher, () -> guard));
    }
}
