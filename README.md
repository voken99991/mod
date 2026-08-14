# Chaos Vote

A Fabric server-side party mod for Minecraft Java Edition 1.21.1.

## What it does

Every 30 seconds, the server opens a short vote with three random events. Players click a button in chat or type `/chaos vote 1`, `/chaos vote 2`, or `/chaos vote 3`. After 10 seconds, the most popular event happens to a random player or the whole server.

There are good, bad and completely stupid events, including:

- random teleports
- random animal/entity spawns
- gifts and loot
- potion buffs and debuffs
- lightning
- creepers
- inventory chaos
- swapping positions
- sudden weather/time changes
- tiny explosions
- XP changes
- food/health changes
- mob swarms
- and more

## Commands

- `/chaos start` - start the 30-second cycle (operator only)
- `/chaos stop` - stop the cycle (operator only)
- `/chaos vote <1|2|3>` - vote in the current round
- `/chaos status` - show the current round and timer
- `/chaos skip` - immediately finish the current vote (operator only)

## Server setup

This mod is designed to run server-side. Players do not need to install the mod to join a dedicated server running it.

### Build

Open the project in IntelliJ IDEA with the Minecraft Development plugin or VS Code with Java support. Run the Gradle `build` task. The finished JAR will be in `build/libs/`.

You need Java 21 and a Fabric 1.21.1 server with Fabric API installed.
