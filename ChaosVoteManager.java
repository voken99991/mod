package com.chaoslabs.chaosmod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class ChaosVoteManager {
    private static final int ROUND_INTERVAL_TICKS = 45 * 20; // 45 seconds at 20 ticks-per-second
    private static int tickCounter = 0;
    private static boolean chaosActive = false;
    private static final Random RANDOM = new Random();

    // Call this every server tick
    public static void tick(MinecraftServer server) {
        if (!chaosActive) return;

        tickCounter++;

        // Every 45 seconds, trigger a random event
        if (tickCounter >= ROUND_INTERVAL_TICKS) {
            triggerRandomEvent(server);
            tickCounter = 0; // Reset the timer
        }
    }

    private static void triggerRandomEvent(MinecraftServer server) {
        // Fetch your registered events (adjust this method call to match your ChaosEvent.java setup)
        List<ChaosEvent> events = ChaosEvent.getRegisteredEvents();
        
        if (events == null || events.isEmpty()) {
            server.getPlayerManager().broadcast(Text.literal("§c[Chaos] Error: No events are registered!"), false);
            return;
        }

        // Pick a random event
        ChaosEvent selectedEvent = events.get(RANDOM.nextInt(events.size()));
        
        // Execute the chosen event
        selectedEvent.execute(server);

        // Broadcast the triggered event to chat
        server.getPlayerManager().broadcast(
            Text.literal("§6[Chaos] §eTriggered event: §f" + selectedEvent.getName()), 
            false
        );
    }

    public static boolean isChaosActive() {
        return chaosActive;
    }

    public static void setChaosActive(boolean active) {
        chaosActive = active;
        if (active) {
            tickCounter = 0; // Reset timer when started so it takes a full 45s for the first event
        }
    }
}
