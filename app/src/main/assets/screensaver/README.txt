Bundled screensaver / wallpaper photos go here.

Drop any number of .jpg / .jpeg / .png / .webp files into this folder. They are
picked up automatically as:
  - the full-screen slideshow wallpaper on the launcher home screen, and
  - the images shown by the built-in screensaver (DreamService).

Notes:
  - Image files in this folder are gitignored (see .gitignore) so personal photos
    are never committed. This README keeps the folder present in the repo.
  - Photos with EXIF GPS + DateTimeOriginal will show their location and date in the
    top-right overlay on the home screen (reverse-geocoded on-device).
  - Alternatively, serve photos remotely without rebuilding: set MANIFEST_URL in
    app/src/main/java/com/nihar/tvlauncher/screensaver/ScreensaverConfig.kt to a URL
    returning JSON (an array of image URLs, or {"images":[...]}). Remote images take
    precedence over these bundled ones.
