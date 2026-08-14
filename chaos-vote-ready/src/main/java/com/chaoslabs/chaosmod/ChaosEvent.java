package com.chaoslabs.chaosmod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.function.BiConsumer;

public record ChaosEvent(String name, String description, int weight, BiConsumer<MinecraftServer, List<ServerPlayerEntity>> action) {
}
