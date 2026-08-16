package com.chaoslabs.chaosmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Heightmap;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class ChaosVoteManager {
    private static final Random RANDOM = new Random();

    // Roughly 30 seconds from one event to the next:
    // 15s waiting + 5s countdown + 10s voting = 30s.
    private static final int WAIT_TICKS = 300;
    private static final int COUNTDOWN_TICKS = 100;
    private static final int VOTE_TICKS = 200;

    private final List<ChaosEvent> events = new ArrayList<>();
    private final Map<UUID, Integer> votes = new HashMap<>();
    private final Set<String> recentEvents = new HashSet<>();

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("CHAOS VOTE"),
            BossBar.Color.PURPLE,
            BossBar.Style.PROGRESS
    );

    private MinecraftServer server;
    private boolean running;
    private boolean countdown;
    private boolean voting;
    private int timer;
    private int lastCountdownSecond = -1;
    private List<ChaosEvent> currentOptions = List.of();

    public ChaosVoteManager() {
        createEvents();
        bossBar.setPercent(1.0f);
        bossBar.setVisible(false);

        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            running = false;
            countdown = false;
            voting = false;
            timer = 0;
            bossBar.setVisible(false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            bossBar.clearPlayers();
            bossBar.setVisible(false);
            server = null;
            running = false;
            countdown = false;
            voting = false;
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString().trim();
            if (!voting || !text.matches("[123]")) {
                return true;
            }
            castChatVote(sender, Integer.parseInt(text));
            return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (!running || server.getPlayerManager().getPlayerList().isEmpty()) {
            return;
        }

        syncBossBarPlayers(server);

        if (voting) {
            timer--;
            updateVoteBossBar();
            if (timer <= 0) {
                finishVote(server);
            }
            return;
        }

        if (countdown) {
            timer--;
            int second = Math.max(1, (timer + 19) / 20);
            if (second != lastCountdownSecond) {
                lastCountdownSecond = second;
                broadcastCountdown(server, second);
                playSound(server, "minecraft:block.note_block.hat", 0.9f, second <= 1 ? 1.4f : 1.0f);
            }
            bossBar.setName(Text.literal("CHAOS VOTE • Starting in " + Math.max(0, (timer + 19) / 20) + "s").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
            bossBar.setPercent(Math.max(0.0f, Math.min(1.0f, timer / (float) COUNTDOWN_TICKS)));
            if (timer <= 0) {
                startVote(server);
            }
            return;
        }

        timer--;
        if (timer <= 0) {
            startCountdown(server);
        } else {
            bossBar.setVisible(false);
        }
    }

    private void startCountdown(MinecraftServer server) {
        currentOptions = pickOptions(3);
        votes.clear();
        countdown = true;
        voting = false;
        timer = COUNTDOWN_TICKS;
        lastCountdownSecond = -1;

        bossBar.setColor(BossBar.Color.PURPLE);
        bossBar.setName(Text.literal("CHAOS VOTE • GET READY").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        bossBar.setPercent(1.0f);
        bossBar.setVisible(true);

        broadcastHeader(server, "CHAOS VOTE", Formatting.LIGHT_PURPLE);
        server.getPlayerManager().broadcast(
                Text.literal("3 choices are coming... get ready to type 1, 2, or 3.").formatted(Formatting.GRAY), false
        );
        playSound(server, "minecraft:block.note_block.pling", 1.0f, 1.0f);
    }

    private void startVote(MinecraftServer server) {
        countdown = false;
        voting = true;
        timer = VOTE_TICKS;
        votes.clear();

        bossBar.setColor(BossBar.Color.YELLOW);
        bossBar.setVisible(true);

        broadcastHeader(server, "VOTE NOW", Formatting.YELLOW);
        server.getPlayerManager().broadcast(
                Text.literal("Type ONLY 1, 2, or 3 in chat. You have 10 seconds.").formatted(Formatting.WHITE, Formatting.BOLD), false
        );

        for (int i = 0; i < currentOptions.size(); i++) {
            int option = i + 1;
            ChaosEvent event = currentOptions.get(i);
            Formatting color = switch (option) {
                case 1 -> Formatting.AQUA;
                case 2 -> Formatting.YELLOW;
                default -> Formatting.RED;
            };
            server.getPlayerManager().broadcast(
                    Text.literal("[" + option + "] " + event.name() + " — " + event.description()).formatted(color), false
            );
        }

        updateVoteBossBar();
        playSound(server, "minecraft:block.note_block.pling", 1.0f, 1.5f);
    }

    private void finishVote(MinecraftServer server) {
        voting = false;
        bossBar.setVisible(false);

        int winningIndex = chooseWinner();
        ChaosEvent winner = currentOptions.get(winningIndex);
        int winnerVotes = countVotes(winningIndex);

        broadcastHeader(server, "CHAOS ACTIVATED", Formatting.RED);
        server.getPlayerManager().broadcast(
                Text.literal(winner.name() + " — " + winner.description() + "  (" + winnerVotes + " vote" + (winnerVotes == 1 ? "" : "s") + ")")
                        .formatted(Formatting.WHITE), false);

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (!players.isEmpty()) {
            winner.action().accept(server, players);
        }

        playSound(server, "minecraft:entity.player.levelup", 1.0f, 1.0f);

        recentEvents.add(winner.name());
        if (recentEvents.size() > 8) {
            recentEvents.remove(recentEvents.iterator().next());
        }
        timer = WAIT_TICKS;
        countdown = false;
    }

    private void syncBossBarPlayers(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            bossBar.addPlayer(player);
        }
    }

    private void updateVoteBossBar() {
        int a = countVotes(0);
        int b = countVotes(1);
        int c = countVotes(2);
        int seconds = Math.max(0, (timer + 19) / 20);
        bossBar.setName(Text.literal("VOTE • " + seconds + "s   |   ① " + a + "   ② " + b + "   ③ " + c)
                .formatted(Formatting.YELLOW, Formatting.BOLD));
        bossBar.setPercent(Math.max(0.0f, Math.min(1.0f, timer / (float) VOTE_TICKS)));
    }

    private void broadcastCountdown(MinecraftServer server, int second) {
        Formatting color = second <= 1 ? Formatting.RED : Formatting.LIGHT_PURPLE;
        server.getPlayerManager().broadcast(Text.literal("" + second).formatted(color, Formatting.BOLD), true);
    }

    private void playSound(MinecraftServer server, String soundId, float volume, float pitch) {
        execute(server, "playsound " + soundId + " master @a ~ ~ ~ " + volume + " " + pitch);
    }

    private int chooseWinner() {
        int best = 0;
        int bestVotes = countVotes(0);
        for (int i = 1; i < currentOptions.size(); i++) {
            int count = countVotes(i);
            if (count > bestVotes) {
                best = i;
                bestVotes = count;
            } else if (count == bestVotes && RANDOM.nextBoolean()) {
                best = i;
            }
        }
        return best;
    }

    private int countVotes(int index) {
        int count = 0;
        for (Integer vote : votes.values()) {
            if (vote == index) count++;
        }
        return count;
    }

    private List<ChaosEvent> pickOptions(int count) {
        List<ChaosEvent> pool = new ArrayList<>();
        for (ChaosEvent event : events) {
            if (!recentEvents.contains(event.name())) {
                for (int i = 0; i < Math.max(1, event.weight()); i++) pool.add(event);
            }
        }
        Collections.shuffle(pool, RANDOM);

        List<ChaosEvent> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ChaosEvent candidate : pool) {
            if (names.add(candidate.name())) {
                result.add(candidate);
                if (result.size() == count) return result;
            }
        }

        for (ChaosEvent candidate : events) {
            if (names.add(candidate.name())) {
                result.add(candidate);
                if (result.size() == count) break;
            }
        }
        return result;
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                   net.minecraft.command.CommandRegistryAccess registryAccess,
                                   CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("chaos")
                .then(CommandManager.literal("start").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                    if (running) {
                        ctx.getSource().sendError(Text.literal("Chaos Vote is already running."));
                        return 0;
                    }
                    running = true;
                    countdown = false;
                    voting = false;
                    timer = 1;
                    ctx.getSource().sendFeedback(() -> Text.literal("Chaos Vote started. First round begins shortly.").formatted(Formatting.LIGHT_PURPLE), true);
                    return 1;
                }))
                .then(CommandManager.literal("stop").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                    running = false;
                    countdown = false;
                    voting = false;
                    timer = 0;
                    bossBar.setVisible(false);
                    ctx.getSource().sendFeedback(() -> Text.literal("Chaos Vote stopped.").formatted(Formatting.GRAY), true);
                    return 1;
                }))
                .then(CommandManager.literal("skip").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                    if (!voting) {
                        ctx.getSource().sendError(Text.literal("There is no active vote."));
                        return 0;
                    }
                    timer = 0;
                    ctx.getSource().sendFeedback(() -> Text.literal("Vote skipped; revealing the winner...").formatted(Formatting.YELLOW), true);
                    return 1;
                }))
                .then(CommandManager.literal("status").executes(ctx -> {
                    String state = !running ? "stopped" : voting ? "voting" : countdown ? "countdown" : "waiting";
                    int seconds = Math.max(0, (timer + 19) / 20);
                    ctx.getSource().sendFeedback(() -> Text.literal("Chaos status: " + state + " | " + seconds + "s remaining").formatted(Formatting.GRAY), false);
                    return 1;
                }))
                .then(CommandManager.literal("vote")
                        .then(CommandManager.argument("option", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> castVote(ctx, IntegerArgumentType.getInteger(ctx, "option"))))));
    }

    private int castVote(CommandContext<ServerCommandSource> ctx, int oneBasedOption) {
        if (!voting || currentOptions.size() < oneBasedOption) {
            ctx.getSource().sendError(Text.literal("There is no active Chaos Vote."));
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Only players can vote."));
            return 0;
        }
        return castVote(player, oneBasedOption);
    }

    private void castChatVote(ServerPlayerEntity player, int oneBasedOption) {
        if (!voting || currentOptions.size() < oneBasedOption) return;
        castVote(player, oneBasedOption);
    }

    private int castVote(ServerPlayerEntity player, int oneBasedOption) {
        votes.put(player.getUuid(), oneBasedOption - 1);
        updateVoteBossBar();
        player.sendMessage(Text.literal("Vote: " + oneBasedOption + " — " + currentOptions.get(oneBasedOption - 1).name()).formatted(Formatting.GREEN), true);
        playSound(player.getServer(), "minecraft:block.note_block.pling", 0.65f, 1.0f + (oneBasedOption - 1) * 0.2f);
        return 1;
    }

    private void broadcastHeader(MinecraftServer server, String title, Formatting color) {
        server.getPlayerManager().broadcast(Text.literal("\n" + title).formatted(color, Formatting.BOLD), false);
    }

    private ServerPlayerEntity randomPlayer(List<ServerPlayerEntity> players) {
        return players.get(RANDOM.nextInt(players.size()));
    }

    private void give(ServerPlayerEntity player, Item item, int amount) {
        player.giveItemStack(new ItemStack(item, amount));
    }

    private void status(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int ticks, int amplifier) {
        player.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier));
    }

    private void teleportRandomly(ServerPlayerEntity player, double radius) {
        ServerWorld world = player.getServerWorld();
        double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * radius;
        double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * radius;
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        double y = Math.max(world.getBottomY() + 1, top + 1);
        player.teleport(world, x, y, z, Set.of(), player.getYaw(), player.getPitch());
    }

    private void spawnNear(ServerPlayerEntity player, EntityType<?> type, int count) {
        ServerWorld world = player.getServerWorld();
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = 2.5 + RANDOM.nextDouble() * 4.0;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            type.spawn(world, new BlockPos(x, y, z), SpawnReason.COMMAND);
        }
    }

    private void createEvents() {
        add("Jackpot", "Everyone gets 3 diamonds.", 2, (s, p) -> p.forEach(player -> give(player, Items.DIAMOND, 3)));
        add("Emerald Rain", "Everyone gets 8 emeralds.", 2, (s, p) -> p.forEach(player -> give(player, Items.EMERALD, 8)));
        add("Golden Hour", "Everyone gets 2 golden apples.", 2, (s, p) -> p.forEach(player -> give(player, Items.GOLDEN_APPLE, 2)));
        add("Speed Demon", "Everyone gets Speed II for 30 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.SPEED, 600, 1)));
        add("Mega Strength", "Everyone gets Strength II for 15 seconds.", 2, (s, p) -> p.forEach(player -> status(player, StatusEffects.STRENGTH, 300, 1)));
        add("Feather Feet", "Everyone gets Slow Falling for 30 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.SLOW_FALLING, 600, 0)));
        add("Instant Snack", "Everyone is completely fed.", 3, (s, p) -> p.forEach(player -> player.getHungerManager().setFoodLevel(20)));
        add("XP Shower", "Everyone gets 15 XP levels.", 2, (s, p) -> p.forEach(player -> player.addExperienceLevels(15)));
        add("Random Diamond", "One player receives a diamond block.", 2, (s, p) -> give(randomPlayer(p), Items.DIAMOND_BLOCK, 1));
        add("Chosen One", "One player gets a netherite ingot.", 1, (s, p) -> give(randomPlayer(p), Items.NETHERITE_INGOT, 1));
        add("Free Horse", "A horse appears beside a random player.", 3, (s, p) -> spawnNear(randomPlayer(p), EntityType.HORSE, 1));
        add("Cow Party", "A herd of cows appears around one player.", 4, (s, p) -> spawnNear(randomPlayer(p), EntityType.COW, 12));
        add("Chicken Attack", "A ridiculous flock of chickens appears.", 4, (s, p) -> spawnNear(randomPlayer(p), EntityType.CHICKEN, 20));
        add("Bee Movie", "One player gets surrounded by bees.", 3, (s, p) -> spawnNear(randomPlayer(p), EntityType.BEE, 10));
        add("Cat Lottery", "Cats appear around everyone.", 3, (s, p) -> p.forEach(player -> spawnNear(player, EntityType.CAT, 3)));
        add("Teleport Roulette", "Everyone is teleported somewhere nearby.", 4, (s, p) -> p.forEach(player -> teleportRandomly(player, 150)));
        add("One-Way Ticket", "One random player is teleported far away.", 3, (s, p) -> teleportRandomly(randomPlayer(p), 500));
        add("Swap!", "Two random players swap positions.", 3, (s, p) -> {
            if (p.size() < 2) return;
            ServerPlayerEntity a = p.get(RANDOM.nextInt(p.size()));
            ServerPlayerEntity b = p.get(RANDOM.nextInt(p.size()));
            int guard = 0;
            while (a == b && guard++ < 10) b = p.get(RANDOM.nextInt(p.size()));
            if (a == b) return;
            Vec3d apos = a.getPos();
            Vec3d bpos = b.getPos();
            a.teleport(a.getServerWorld(), bpos.x, bpos.y, bpos.z, Set.of(), b.getYaw(), b.getPitch());
            b.teleport(b.getServerWorld(), apos.x, apos.y, apos.z, Set.of(), a.getYaw(), a.getPitch());
        });
        add("Low Gravity", "Everyone gets Jump Boost III and Slow Falling.", 3, (s, p) -> p.forEach(player -> {
            status(player, StatusEffects.JUMP_BOOST, 400, 2);
            status(player, StatusEffects.SLOW_FALLING, 400, 0);
        }));
        add("Levitation", "One player floats for 8 seconds.", 3, (s, p) -> status(randomPlayer(p), StatusEffects.LEVITATION, 160, 1));
        add("Haste", "Everyone gets Haste III for 20 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.HASTE, 400, 2)));
        add("Regeneration", "Everyone gets Regeneration II for 15 seconds.", 2, (s, p) -> p.forEach(player -> status(player, StatusEffects.REGENERATION, 300, 1)));
        add("Resistance", "Everyone gets Resistance II for 15 seconds.", 1, (s, p) -> p.forEach(player -> status(player, StatusEffects.RESISTANCE, 300, 1)));
        add("Big Bonk", "One random player takes 6 hearts of damage.", 4, (s, p) -> damageRandom(p, 12));
        add("Tiny Bonk", "Everyone takes 1 heart of damage.", 4, (s, p) -> p.forEach(player -> damage(player, 2)));
        add("Half Hearts", "Everyone is reduced to half health.", 2, (s, p) -> p.forEach(player -> player.setHealth(Math.max(1f, player.getHealth() / 2f))));
        add("Oops, Thunder", "Lightning strikes near a random player.", 4, (s, p) -> strikeNear(randomPlayer(p), 2));
        add("Triple Thunder", "Three lightning bolts appear near one player.", 2, (s, p) -> strikeNear(randomPlayer(p), 3));
        add("Creeper Delivery", "Three creepers appear around a random player.", 2, (s, p) -> spawnNear(randomPlayer(p), EntityType.CREEPER, 3));
        add("Spider Party", "A swarm of spiders appears around one player.", 3, (s, p) -> spawnNear(randomPlayer(p), EntityType.SPIDER, 8));
        add("Zombie Problem", "A group of zombies appears around one player.", 3, (s, p) -> spawnNear(randomPlayer(p), EntityType.ZOMBIE, 10));
        add("Poisoned", "One player gets Poison II for 12 seconds.", 3, (s, p) -> status(randomPlayer(p), StatusEffects.POISON, 240, 1));
        add("Blindness", "Everyone is blind for 8 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.BLINDNESS, 160, 0)));
        add("Slowness", "Everyone gets Slowness IV for 12 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.SLOWNESS, 240, 3)));
        add("Mining Fatigue", "Everyone gets Mining Fatigue IV for 15 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.MINING_FATIGUE, 300, 3)));
        add("Hunger", "Everyone gets Hunger III for 20 seconds.", 3, (s, p) -> p.forEach(player -> status(player, StatusEffects.HUNGER, 400, 2)));
        add("Rotten Food", "One player gets Rotten Flesh and Hunger.", 3, (s, p) -> {
            ServerPlayerEntity player = randomPlayer(p);
            give(player, Items.ROTTEN_FLESH, 16);
            status(player, StatusEffects.HUNGER, 300, 2);
        });
        add("Inventory Roulette", "A random player gets 6 random junk items.", 4, (s, p) -> giveRandomJunk(randomPlayer(p)));
        add("Drop One", "Everyone drops one random inventory stack.", 3, (s, p) -> p.forEach(this::dropRandomStack));
        add("Nightmare Weather", "The weather turns into a thunderstorm.", 3, (s, p) -> execute(s, "weather thunder 999999"));
        add("Sun Party", "The storm is cleared and the sun returns.", 2, (s, p) -> execute(s, "weather clear 999999"));
        add("Midnight", "It suddenly becomes midnight.", 3, (s, p) -> execute(s, "time set midnight"));
        add("Noon", "It suddenly becomes noon.", 3, (s, p) -> execute(s, "time set noon"));
        add("Tiny Boom", "A small explosion happens near one random player.", 2, (s, p) -> explodeNear(randomPlayer(p)));
        add("Party Popper", "Fireworks launch around everyone.", 3, (s, p) -> execute(s, "execute as @a at @s run summon firework_rocket ~ ~2 ~"));
        add("Mob Roulette", "A random mob appears near one player.", 4, (s, p) -> {
            EntityType<?>[] mobs = {EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.SLIME, EntityType.WITCH, EntityType.ENDERMAN, EntityType.GOAT, EntityType.FROG};
            spawnNear(randomPlayer(p), mobs[RANDOM.nextInt(mobs.length)], 2);
        });
        add("Chicken Rain", "Everyone gets chickens around them.", 3, (s, p) -> p.forEach(player -> spawnNear(player, EntityType.CHICKEN, 4)));
        add("Lucky Pocket", "Everyone gets a random useful item.", 4, (s, p) -> {
            Item[] useful = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.BREAD, Items.ARROW, Items.TORCH};
            p.forEach(player -> give(player, useful[RANDOM.nextInt(useful.length)], 1 + RANDOM.nextInt(4)));
        });
        add("Curse of Chaos", "One player gets 4 random bad effects at once.", 2, (s, p) -> {
            ServerPlayerEntity player = randomPlayer(p);
            status(player, StatusEffects.BLINDNESS, 200, 0);
            status(player, StatusEffects.SLOWNESS, 200, 2);
            status(player, StatusEffects.HUNGER, 300, 2);
            status(player, StatusEffects.WEAKNESS, 300, 1);
        });
        add("Apple Bomb", "Everyone gets an apple, but one gets a creeper too.", 2, (s, p) -> {
            p.forEach(player -> give(player, Items.APPLE, 1));
            spawnNear(randomPlayer(p), EntityType.CREEPER, 1);
        });
        add("Fish Out of Water", "A bunch of fish appear around everyone.", 3, (s, p) -> p.forEach(player -> spawnNear(player, EntityType.COD, 6)));
        add("Rich for a Second", "Everyone gets 16 gold nuggets.", 3, (s, p) -> p.forEach(player -> give(player, Items.GOLD_NUGGET, 16)));
    }

    private void add(String name, String description, int weight, BiConsumer<MinecraftServer, List<ServerPlayerEntity>> action) {
        events.add(new ChaosEvent(name, description, weight, action));
    }

    private void damageRandom(List<ServerPlayerEntity> players, float amount) {
        damage(randomPlayer(players), amount);
    }

    private void damage(ServerPlayerEntity player, float amount) {
        if (player.isCreative() || player.isSpectator()) return;
        player.damage(player.getServerWorld().getDamageSources().generic(), amount);
    }

    private void strikeNear(ServerPlayerEntity player, int count) {
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (RANDOM.nextDouble() * 8 - 4);
            double z = player.getZ() + (RANDOM.nextDouble() * 8 - 4);
            int y = player.getServerWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int) x, (int) z);
            execute(player.getServer(), "summon minecraft:lightning_bolt " + x + " " + y + " " + z);
        }
    }

    private void explodeNear(ServerPlayerEntity player) {
        execute(player.getServer(), "summon minecraft:tnt " + (player.getX() + 2) + " " + player.getY() + " " + player.getZ() + " {fuse:40}");
    }

    private void execute(MinecraftServer server, String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
    }

    private void giveRandomJunk(ServerPlayerEntity player) {
        Item[] junk = {Items.ROTTEN_FLESH, Items.DIRT, Items.COBBLESTONE, Items.KELP, Items.STRING, Items.STICK, Items.POISONOUS_POTATO, Items.GRAVEL, Items.FEATHER, Items.SPIDER_EYE};
        for (int i = 0; i < 6; i++) give(player, junk[RANDOM.nextInt(junk.length)], 1 + RANDOM.nextInt(12));
    }

    private void dropRandomStack(ServerPlayerEntity player) {
        if (player.getInventory().isEmpty()) return;
        int attempts = 0;
        while (attempts++ < 20) {
            int slot = RANDOM.nextInt(player.getInventory().size());
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty()) {
                player.dropItem(stack.copy(), true, false);
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                return;
            }
        }
    }
}
