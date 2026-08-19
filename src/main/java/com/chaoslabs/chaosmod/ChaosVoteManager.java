package com.chaoslabs.chaosmod;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ChaosVoteManager {
    private static final int INTERVAL_TICKS = 45 * 20; // 45 seconds (900 ticks)
    private static final Random RANDOM = new Random();

    private final List<ChaosEvent> events = new ArrayList<>();
    private final ServerBossBar bossBar = new ServerBossBar(
        Text.literal("NEXT CHAOS EVENT").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
        BossBar.Color.PURPLE,
        BossBar.Style.PROGRESS
    );

    private MinecraftServer server;
    private boolean running = true;
    private int timer = INTERVAL_TICKS;

    public ChaosVoteManager() {
        createEvents();

        // Register in-game control commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("chaos")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("start").executes(ctx -> {
                    running = true;
                    timer = INTERVAL_TICKS;
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[Chaos] Started! Events trigger every 45s."), true);
                    return 1;
                }))
                .then(CommandManager.literal("stop").executes(ctx -> {
                    running = false;
                    bossBar.clearPlayers();
                    bossBar.setVisible(false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§c[Chaos] Stopped."), true);
                    return 1;
                }))
                .then(CommandManager.literal("toggle").executes(ctx -> {
                    running = !running;
                    if (!running) {
                        bossBar.clearPlayers();
                        bossBar.setVisible(false);
                    } else {
                        timer = INTERVAL_TICKS;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("§e[Chaos] Toggled to " + (running ? "ON" : "OFF")), true);
                    return 1;
                }))
            );
        });

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            running = true;
            timer = INTERVAL_TICKS;
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            running = false;
            bossBar.clearPlayers();
        });

        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (!running || server.getPlayerManager().getPlayerList().isEmpty()) {
            bossBar.setVisible(false);
            return;
        }

        // Sync bossbar with current players
        bossBar.setVisible(true);
        bossBar.clearPlayers();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            bossBar.addPlayer(player);
        }

        timer--;

        // Display timer countdown
        float percent = Math.max(0f, Math.min(1f, timer / (float) INTERVAL_TICKS));
        bossBar.setPercent(percent);
        int secondsLeft = Math.max(1, (timer + 19) / 20);
        bossBar.setName(Text.literal("NEXT CHAOS EVENT IN " + secondsLeft + "s").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));

        if (timer <= 0) {
            timer = INTERVAL_TICKS;
            triggerRandomEvent(server);
        }
    }

    private void triggerRandomEvent(MinecraftServer server) {
        if (events.isEmpty()) return;

        // Randomly select one event
        ChaosEvent event = events.get(RANDOM.nextInt(events.size()));
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());

        if (!players.isEmpty()) {
            event.action().accept(server, players);
        }

        // Broadcast to server chat
        server.getPlayerManager().broadcast(
            Text.literal("§6[CHAOS] §eTriggered: §f" + event.name() + " §7(" + event.description() + ")"),
            false
        );
    }

    private void createEvents() {
        // Place all your events.add(new ChaosEvent(...)) calls here
    }
}
