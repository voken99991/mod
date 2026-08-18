package com.chaoslabs.chaosmod;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChaosVoteManager {
    private static boolean votingActive = false;
    private static final Map<UUID, Integer> playerVotes = new HashMap<>();

    public static void registerListeners() {
        // Intercept player chat messages during voting phases
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (!votingActive) return;

            String content = message.getContent().getString().trim();

            // Detect if the player sent a single key: "1", "2", or "3"
            if (content.equals("1") || content.equals("2") || content.equals("3")) {
                int option = Integer.parseInt(content);
                recordVote(sender, option);
            }
        });
    }

    public static void recordVote(ServerPlayerEntity player, int option) {
        playerVotes.put(player.getUuid(), option);
        player.sendMessage(Text.literal("§a[Chaos Vote] You selected Option " + option + "!"), true);
    }

    public static boolean isVotingActive() {
        return votingActive;
    }

    public static void setVotingActive(boolean active) {
        votingActive = active;
        if (active) {
            playerVotes.clear();
        }
    }
}
