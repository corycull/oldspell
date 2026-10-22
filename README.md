# Earmark

An audiobook player for Android that plays the audiobook files already on your phone, with a
dark, cover-art-first design and the controls you'd expect from a dedicated audiobook app.

## Features

- **Library from your own files.** Pick a folder once; every `.m4b` becomes a book (with its
  built-in chapters), every folder of `mp3 / m4a / ogg / flac / opus` files becomes a book with
  one chapter per file, and `CD 1 / CD 2 / Disc 3` subfolders are merged into one book.
  Cover art comes from the file tags or a `cover.jpg` / `folder.jpg`.
- **Home** with a big *Continue listening* card plus shelves: Continue listening, Recently added,
  Up next and Finished.
- **Library** with All / Not started / In progress / Finished filters, search, and sorting by
  recent, title, author, date added or length. Each row shows progress and time left.
- **Full-screen player**: large cover, chapter-relative scrubber with elapsed / remaining in the
  chapter and total time left (adjusted for speed), skip back / forward, previous / next chapter.
- **Narration speed** 0.5x – 3.5x in 0.05 steps with presets.
- **Sleep timer**: 5–90 minutes or end of chapter, fades out over the last 10 seconds, and
  shaking the phone restarts it.
- **Chapters and bookmarks** sheet; bookmarks can carry notes.
- **Background playback** with lock-screen / notification controls. Headphone, Bluetooth, car
  and notification "previous / next" buttons skip back / forward instead of changing files.
  Pauses when headphones are unplugged.
- Position saved every 5 seconds and on every pause or seek, so you always resume where you were.
- Mark as finished / unfinished, reset progress, remove from library (and restore later).

## Getting the APK onto your phone

You need to build it once; there are two ways.

**Option A: GitHub (no computer software needed)**
1. Create a new repository on github.com and upload the contents of this folder
   (keep the `.github` folder; it holds the build script).
2. Open the repo's **Actions** tab. The "Build APK" workflow runs automatically
   (or tap *Run workflow*). It takes about 5 minutes.
3. Open the repo's **Releases** page on your phone and download `Earmark.apk`.
4. Open the downloaded file and allow "Install unknown apps" for your browser when asked.

**Option B: Android Studio**
1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Plug in your phone with USB debugging on and press Run, or use
   *Build → Build APK(s)* and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.

## First run

1. Tap **Add audiobook folder** and choose the folder that holds your books
   (e.g. `Audiobooks` in internal storage or on an SD card). You can add more than one folder
   in Settings.
2. The library fills in as files are read. Tap any book to start listening.

Requires Android 8.0 or newer.

## Project layout

```
app/src/main/java/com/earmark/app/
  data/       Book model, JSON library store, folder scanner, MP4 chapter reader
  playback/   Media3 playback service, controller connection, sleep timer, shake detector
  ui/         Compose screens: Home, Library, Player, Settings, mini player
```

Built with Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 / ExoPlayer.
