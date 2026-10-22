# Oldspell

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

## Signing key (one-time setup)

Builds are signed with your private upload key, which is never stored in this repo.
Add it once as two repository secrets: on GitHub open **Settings → Secrets and variables →
Actions → New repository secret** and create:

| Name | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the full contents of `RELEASE_KEYSTORE_BASE64.txt` |
| `RELEASE_KEYSTORE_PASSWORD` | the contents of `RELEASE_KEYSTORE_PASSWORD.txt` |

Keep your upload keystore file (`talewind-upload.jks`) and its password backed up somewhere safe (a password manager is
ideal). If you lose them, Google Play can reset the upload key, but it takes a support request.

## Getting the app

Every push to `main` builds the app on GitHub (Actions tab, about 5 minutes) and publishes it
on the **Releases** page:

- `Oldspell.apk` installs directly on your phone (allow "Install unknown apps" when asked).
- `Oldspell.aab` is the file you upload to Google Play.

To build locally instead, open the folder in Android Studio and press Run.

## Publishing on Google Play

1. Create a developer account at play.google.com/console (one-time US$25 fee).
2. Create an app named Oldspell and upload `Oldspell.aab` from the latest release.
   Keep **Play App Signing** on; Google then holds the final signing key and your
   your upload keystore file (`talewind-upload.jks`) acts as the upload key.
3. For the privacy policy URL, use the link to `PRIVACY.md` in this repo.
4. Data safety form: the app collects and shares no data.
5. Each new upload needs a higher version code; the build number handles that automatically.

Before publishing, search the USPTO trademark database and Google Play for "Oldspell" to make
sure the name is free. Don't use other companies' names (e.g. Audible) in the listing.

## First run

1. Tap **Add audiobook folder** and choose the folder that holds your books
   (e.g. `Audiobooks` in internal storage or on an SD card). You can add more than one folder
   in Settings.
2. The library fills in as files are read. Tap any book to start listening.

Requires Android 8.0 or newer.

## Project layout

```
app/src/main/java/com/oldspell/app/
  data/       Book model, JSON library store, folder scanner, MP4 chapter reader
  playback/   Media3 playback service, controller connection, sleep timer, shake detector
  ui/         Compose screens: Home, Library, Player, Settings, mini player
```

Built with Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 / ExoPlayer.
