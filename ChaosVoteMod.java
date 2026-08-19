package com.chaoslabs.chaosmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class ChaosVoteMod implements ModInitializer {
    
    @Override
    public void onInitialize() {
        // 1. Register the server tick event to run our 45-second timer
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ChaosVoteManager.tick(server);
        });

        // 2. Register basic commands to control the mod (/chaos start and /chaos stop)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("chaos")
                .requires(source -> source.hasPermissionLevel(2)) // Requires OP/Admin permissions
                .then(CommandManager.literal("start")
                    .executes(context -> {
                        ChaosVoteManager.setChaosActive(true);
                        context.getSource().sendFeedback(() -> Text.literal("§a[Chaos] Random events started! A new event will trigger every 45 seconds."), true);
                        return 1;
                    })
                )
                .then(CommandManager.literal("stop")
                    .executes(context -> {
                        ChaosVoteManager.setChaosActive(false);
                        context.getSource().sendFeedback(() -> Text.literal("§c[Chaos] Random events stopped."), true);
                        return 1;
                    })
                )
            );
        });
    }
}
