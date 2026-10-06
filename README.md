# Auto Reel (Fabric, Minecraft 26.1.x)

Client-side auto fishing. Press a key and it will:

1. cast your rod,
2. wait while the fish swim up to the bobber (the particle phase) and do nothing,
3. reel in the moment the bobber reports a real bite,
4. wait a moment and cast again.

It pauses while any menu or chat is open, stops by itself if you leave the world, and only acts while a
fishing rod is in your main hand.

**Needs:** Minecraft 26.1.x, Fabric Loader 0.18+, [Fabric API](https://modrinth.com/mod/fabric-api) (the 26.1.2 build), Java 25 (Prism sets this up for you).

**Use:** join a world, hold a fishing rod, press **R**. The action bar shows `Auto Reel: ON` / `OFF`.
Change the key under Options > Controls > Key Binds > Auto Reel.

> Many multiplayer servers (Hypixel SkyBlock included, as far as I know) treat auto-fishing as a macro and
> ban for it. Check your server's rules before using this on one.

---

## Getting the .jar without installing anything (GitHub builds it for you)

1. Make a free account at github.com, then click **+ > New repository**. Name it anything, set it to **Public**, click **Create repository**.
2. On the new repo page click **uploading an existing file**.
3. Unzip this project. Open the unzipped `autoreel` folder, press Ctrl+A, and drag **everything inside** into the GitHub page
   (make sure the `.github` folder, `src`, `gradle`, `build.gradle` etc. all show up in the list). Click **Commit changes**.
4. Open the **Actions** tab. A run called "build" starts by itself; wait about 3-5 minutes for the green check.
5. Go back to the repo's main page. On the right side click **Releases > Auto Reel (latest build)** and download `autoreel-1.0.0.jar`.
   (Backup: Actions > the run > Artifacts > autoreel-jar, which is a zip containing the jar.)
6. Put the jar in your instance's `mods` folder (Prism: select the instance > Mods > Add / Open Folder) together with Fabric API.

If the run shows a red X, click it, open the failed step, and send the error text to Claude.

## Building on your own computer instead

Install a JDK 25, open a terminal in this folder and run `./gradlew build` (Windows: `gradlew build`).
The jar is created in `build/libs/`.

## Tweaking

All timings live at the top of `src/client/java/dev/autoreel/AutoFishController.java` (reaction delay after a bite,
delay before recasting, and so on). Values are in ticks (20 ticks = 1 second). If you edit a file on GitHub,
the build runs again and the Release is replaced with the new jar.

## How the bite is detected

Fish bite when the splash happens: the bobber is flagged as "biting" and gets pulled underwater for 1-2 seconds.
Auto Reel watches for both signals (the flag, and the bobber dropping suddenly) and ignores the first second after
each cast so the landing splash is never mistaken for a bite.
