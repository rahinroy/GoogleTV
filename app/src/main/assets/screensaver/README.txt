Optional OFFLINE FALLBACK photos. Normally empty.

Wallpaper / screensaver photos now live in a separate repo and are fetched at
runtime, so adding a photo never means rebuilding the APK:

    https://github.com/rahinroy/photos

Drop images there (in its photos/ folder) and a GitHub Action rewrites
screensaver.json; the TV picks them up on its own. ScreensaverConfig.MANIFEST_URL
points at that file.

This folder is the LAST resort in ImageManifestRepository's precedence chain:
    remote manifest -> last-cached manifest (offline) -> these bundled assets

So you only need files here if you want something to show on a device that has
never once reached the network. Drop .jpg / .jpeg / .png / .webp files in and
they are picked up automatically; every file here is gitignored (see .gitignore)
so personal photos are never committed, and this README keeps the folder present.

Note that bundled photos are read for EXIF on-device (GPS -> place, DateTimeOriginal
-> date) for the top-right overlay, whereas remote photos get that same overlay from
the lat/lon/taken fields the manifest carries.
