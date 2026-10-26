# Donut Pay Tracker (Fabric, Minecraft 26.2, client-side)

Lists incoming `<name> paid you $<amount>` chat messages in a HUD. Open chat (T / Enter)
and click WIN / LOSE / SKIP to mark the current payment with a check, an X, or a dash.
"Undo last" reverts the most recent mark. Scroll the mouse wheel over the panel for older entries.

It only reads chat and draws on your own screen. It never sends commands or payments.

## Build
1. Install JDK 25 (26.x needs Java 25).
2. In this folder run `./gradlew build` (`gradlew.bat build` on Windows). The Gradle wrapper is included.
3. The jar is in `build/libs/` (use the one without `-sources`).
4. Put it in your `mods` folder with Fabric Loader 0.20.1+ and Fabric API 0.161.0+26.2.

Versions are set in `gradle.properties` (copied from the official Fabric example mod for 26.2).

## Try it without the server
`/donutpay test 25.1M` adds a fake payment. `/donutpay clear` empties the list.

## Tweaks
- Panel position: `originX` / `originY` in `HudRenderer.java`
- What a win pays (the "x = ..." line): `WIN_MULTIPLIER` in `PaymentTracker.java` (default 2.0)
- Rows shown at once: `VISIBLE_ROWS` in `PaymentTracker.java`
- Chat format it matches: the `PAID_YOU` regex in `PaymentTracker.java`
