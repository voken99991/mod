# Chaos Vote — Minecraft 1.21.11

Fabric server-side chaos event mod.

## IMPORTANT GitHub setup

Delete every existing file inside `.github/workflows/` in your repository first.
Then copy this project so `.github/workflows/BUILD-CHAOS-VOTE-1-21-11.yml` is at the repository root.

Go to **Actions → BUILD CHAOS VOTE 1.21.11 → Run workflow**.

The workflow downloads and verifies **Gradle 9.2.0 itself**. Do not use an older Gradle workflow.

The build artifact is `chaos-vote-1.21.11-jar`.

## Server

Upload the resulting `chaos-vote-1.0.0.jar` into the server's `mods` folder, alongside Fabric API for Minecraft 1.21.11.

## Gameplay

- About every 30 seconds a new round starts.
- 5-second countdown.
- 10-second vote.
- Players vote by typing only `1`, `2`, or `3` in chat.
- Boss bar shows countdown and live vote totals.
- Countdown/vote/winner sound effects are included.
- `/chaos start`, `/chaos stop`, `/chaos skip`, `/chaos status` are available to operators.
