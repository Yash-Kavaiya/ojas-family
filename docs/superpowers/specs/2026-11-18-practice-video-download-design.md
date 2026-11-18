# Practice videos: download once, play from the phone

Date: 2026-11-18 · Size: small change

## Why
The four guided practice videos (Blue Light, Quick Protection, Morning, Night) are streamed from
Firebase Storage on every play, so download cost grows with every play. They are the same videos
every day. Downloading each one once per phone and playing it from the phone makes the cost about
zero and playback smooth on any network.

## Goals
- Each phone downloads the four videos once and plays them only from the phone. No streaming.
- Videos re-encoded to 480p first, so the download is smaller (~413 MB today → ~250 MB).
- When an admin replaces a video, phones download the new one and delete the old one.
- Points and the 90%-watched check keep working the same way.

## Non-goals
- No change to YouTube practices (kind `youtube`); they keep streaming in the YouTube player.
- No downloads for other content (library videos, Activity, Q&A).
- No new hosting service; files stay in Firebase Storage.

## Approaches
- **A. Automatic background download on Wi-Fi (picked by owner).** Videos are ready before a
  member opens Practice. Costs ~250 MB of phone space without asking.
- B. A Download button on each practice. Member chooses, but must remember, and the first use waits.
- C. A + B. Most complete, a little more to build.

## Design
- **Video files (one-time, on the owner's PC):** re-encode Blue Light, Morning and Night to 480p
  H.264 + AAC with ffmpeg (`-movflags +faststart`). Quick Protection is already 360p and stays as is.
  Upload them with the existing `firebase/functions/scripts/uploadPracticeMedia.ts`, which gives each
  upload a new token, so the URL changes.
- **Download (app):** a WorkManager job, limited to unmetered network (Wi-Fi) with enough storage,
  runs at app start and whenever `config/practiceMedia` changes. For each `video`/`audio` practice
  it downloads the file to app-private storage (`filesDir/practice/`), named from a hash of the URL.
  It writes to a temp file and renames it when complete, so a half-download is never played. Files
  whose URL is no longer in the config are deleted. Retries with backoff if interrupted.
- **Playing:** the practice player uses the local file. Until it is downloaded, the practice card
  and session show "Downloading on Wi-Fi…" with progress, and a "Download now with mobile data"
  button that runs the same job without the Wi-Fi limit (my call: without it, a member with no
  Wi-Fi could never practice). Never streams.
- **New dependency:** `androidx.work:work-runtime-ktx`.

## Left to the builder
- Exact wording of the downloading/progress text (in all three strings.xml, edited by hand).
- ffmpeg quality settings (CRF ~26–28) as long as the result looks clean at 480p.
