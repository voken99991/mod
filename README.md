# Chaos Vote

Minecraft 1.21.11 Fabric server-side chaos game. Every 30 seconds a round completes: 15s waiting, 5s countdown, 10s voting, then the winning event fires.

During voting, players can simply type `1`, `2`, or `3` in chat. The message is consumed so it does not clutter chat. `/chaos vote 1|2|3` and admin controls remain available.

Build requirements: Java 21 and Gradle 9.2.0. The included GitHub Actions workflow downloads and invokes Gradle 9.2.0 directly.

Admin commands: `/chaos start`, `/chaos stop`, `/chaos skip`, `/chaos status`.
