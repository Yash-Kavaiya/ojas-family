# Practice Video Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The four guided practice videos download once per phone (on Wi-Fi, or mobile data on request) and play only from the phone, at 480p.

**Architecture:** A `PracticeDownloads` class owns `filesDir/practice/`, decides what to download or delete from `config/practiceMedia`, and runs one unique WorkManager job (`practice-downloads`) whose worker downloads with OkHttp. The signed-in shell feeds config changes into it. The practice session and the practice cards read its state; the session player only ever gets a local `file://` URI.

**Tech Stack:** Kotlin, Jetpack Compose, Media3 ExoPlayer, WorkManager (`androidx.work:work-runtime-ktx` 2.10.0), OkHttp 4.10.0 (already a dependency), ffmpeg 8, Firebase Admin script.

**Spec:** `docs/superpowers/specs/2026-11-18-practice-video-download-design.md`

## Global Constraints

- Only practices whose `MediaType` is `Video` or `Audio` are downloaded; `YouTube` practices are untouched and keep streaming.
- Never stream a `Video`/`Audio` practice: the session player gets only a local file URI.
- Default download runs only on an unmetered network (`NetworkType.UNMETERED`) with `setRequiresStorageNotLow(true)`; "Download now with mobile data" re-enqueues with `NetworkType.CONNECTED`.
- Files live in `context.filesDir/practice/`, named `sha256(url).take(32) + "." + ext` (`mp4`, `m4a` or `mp3` from the URL path; default `mp4`).
- Download to `<name>.part`, rename to `<name>` only after the full body is written.
- New user-facing strings go into all three of `values/`, `values-hi/`, `values-gu/strings.xml`, edited by hand (never run `tools/gen_strings.py`).
- Re-encode: 854x480 (keep aspect, `-2` width), H.264 `-crf 27 -preset slow`, AAC 96k, `-movflags +faststart`. Quick Protection is not re-encoded.
- Build/test with the JDK and Gradle paths in the local build notes (`JAVA_HOME=/c/Users/yashk/.jdks/jdk-21.0.13+9`, Gradle 9.3.1 from `~/.gradle/wrapper/dists`), output redirected to a file.

## Review Focus

1. **Config not loaded yet / read fails** (signed out, offline, rules error): `observeMedia()` emits an empty map; that must never delete downloaded files. Test in Task 1.
2. **Admin replaces a video:** new URL → new file name → old file deleted and new one downloaded; a session already open on the old file keeps playing it until closed. Test in Task 1 (plan) .
3. **Download interrupted midway** (Wi-Fi drops, app killed): only a `.part` file exists, it is never reported as ready, and the retry starts it again. Test in Task 1 (`.part` is not "ready").
4. **Phone low on storage:** the job waits (storage-not-low constraint) instead of failing half-way; the session keeps showing "Downloading on Wi-Fi…" with the mobile-data button.
5. **Opening a practice before the file exists:** the session shows the download state and starts playing by itself as soon as the file appears, without reopening. Test in Task 3.

---

### Task 1: Download planning and storage (pure logic + worker)

**Files:**
- Create: `app/src/main/java/com/ojashealing/family/data/practice/PracticeDownloads.kt`
- Modify: `gradle/libs.versions.toml` (add `work = "2.10.0"` and `androidx-work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }`), `app/build.gradle.kts` (`implementation(libs.androidx.work.runtime)`)
- Modify: `app/src/main/java/com/ojashealing/family/di/AppContainer.kt` (add `val practiceDownloads = PracticeDownloads(context)`)
- Test: `app/src/test/java/com/ojashealing/family/data/practice/PracticeDownloadsTest.kt`

**Interfaces:**
- Produces:
  - `fun practiceFileName(url: String): String`
  - `data class SyncPlan(val download: List<PracticeMedia>, val delete: List<String>)`
  - `fun planSync(media: Map<ContentSection, PracticeMedia>, existing: List<String>): SyncPlan` — `existing` are file names in the folder (including `.part`); ignores YouTube; empty `media` → `SyncPlan(emptyList(), emptyList())`; deletes every existing name (and its `.part`) not belonging to a current URL.
  - `sealed interface DownloadState { data object Ready; data class Downloading(val percent: Int, val waitingForWifi: Boolean); data object NotNeeded }`
  - `class PracticeDownloads(context: Context)` with
    - `fun localFile(media: PracticeMedia): File?` — the finished file, or null
    - `fun sync(media: Map<ContentSection, PracticeMedia>)` — applies `planSync` deletions, enqueues the job if anything is missing (`ExistingWorkPolicy.KEEP`, Wi-Fi only)
    - `fun downloadNowOnMobileData()` — re-enqueues with `ExistingWorkPolicy.REPLACE`, `NetworkType.CONNECTED`
    - `fun state(media: PracticeMedia): Flow<DownloadState>` — `NotNeeded` for YouTube; `Ready` when `localFile` is non-null; otherwise `Downloading` from the unique work's progress (`percent` for this file; `waitingForWifi = true` while the work is ENQUEUED). Re-checks the file each time work info changes.
  - `class PracticeDownloadWorker(context, params) : CoroutineWorker` — input `urls: Array<String>`; for each URL without a finished file, downloads with OkHttp to `.part`, reports `setProgress(workDataOf("url" to url, "percent" to n))`, then renames. Any IO error → `Result.retry()` (exponential backoff, 30 s).

- [ ] **Step 1: Write failing tests** (plain JUnit, no Android)

```kotlin
class PracticeDownloadsTest {
    private fun video(s: ContentSection, url: String) = PracticeMedia(s, url, MediaType.Video, 600, "")
    private val a = "https://x/o/practice%2Fblue_light.mp4?alt=media&token=1"
    private val b = "https://x/o/practice%2Fblue_light.mp4?alt=media&token=2"

    @Test fun fileNameIsStableAndKeepsExtension() {
        assertEquals(practiceFileName(a), practiceFileName(a))
        assertNotEquals(practiceFileName(a), practiceFileName(b))
        assertTrue(practiceFileName(a).endsWith(".mp4"))
        assertTrue(practiceFileName("https://x/o/night.m4a?alt=media").endsWith(".m4a"))
    }
    @Test fun emptyConfigDeletesNothing() {
        assertEquals(SyncPlan(emptyList(), emptyList()), planSync(emptyMap(), listOf(practiceFileName(a))))
    }
    @Test fun replacedVideoDeletesOldAndDownloadsNew() {
        val plan = planSync(mapOf(ContentSection.BlueLight to video(ContentSection.BlueLight, b)), listOf(practiceFileName(a)))
        assertEquals(listOf(practiceFileName(a)), plan.delete)
        assertEquals(listOf(b), plan.download.map { it.url })
    }
    @Test fun partFileIsNotReadyAndIsRedownloaded() {
        val plan = planSync(mapOf(ContentSection.BlueLight to video(ContentSection.BlueLight, a)), listOf(practiceFileName(a) + ".part"))
        assertEquals(listOf(a), plan.download.map { it.url })
        assertTrue(plan.delete.isEmpty())
    }
    @Test fun youTubeIsIgnored() {
        val yt = PracticeMedia(ContentSection.QuickProtection, "KPRvRrJgmVk", MediaType.YouTube, 600, "")
        assertEquals(SyncPlan(emptyList(), emptyList()), planSync(mapOf(ContentSection.QuickProtection to yt), emptyList()))
    }
}
```

- [ ] **Step 2: Run, expect compile failure** — `gradle :app:testDebugUnitTest --tests '*PracticeDownloadsTest*'` → FAIL (unresolved `practiceFileName`).
- [ ] **Step 3: Add the WorkManager dependency, implement `PracticeDownloads.kt` and the worker** (OkHttpClient with a 30 s read timeout; worker gets the folder from `applicationContext.filesDir`). Register in `AppContainer`.
- [ ] **Step 4: Run tests** — same command → PASS.
- [ ] **Step 5: Commit** — `feat(practice): download guided practice media to the phone`

### Task 2: Start syncing when signed in

**Files:**
- Modify: `app/src/main/java/com/ojashealing/family/ui/main/MainShell.kt` (signed-in shell, near the `GuidedPracticeViewModel` at line ~249)

**Interfaces:**
- Consumes: `container.practiceMedia.observeMedia()`, `container.practiceDownloads.sync(...)`

- [ ] **Step 1: Add** `LaunchedEffect(Unit) { container.practiceMedia.observeMedia().collect { container.practiceDownloads.sync(it) } }` in the composable that only exists while signed in.
- [ ] **Step 2: Build** — `gradle :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `feat(practice): keep downloaded practices in step with the admin's choice`

### Task 3: Session plays only the local file

**Files:**
- Modify: `app/src/main/java/com/ojashealing/family/ui/content/PracticeSession.kt` (`SessionUi`, `PracticeSessionViewModel` init at lines 247-262, `PracticeSessionScreen` loading branch at lines 456-465)
- Modify: `app/src/main/java/com/ojashealing/family/ui/main/MainShell.kt:858` (pass `container.practiceDownloads`)
- Modify: three `strings.xml`
- Test: `app/src/test/java/com/ojashealing/family/ui/content/PracticeSessionDownloadTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `PracticeDownloads.state(media)`, `localFile(media)`, `downloadNowOnMobileData()`
- Produces: `SessionUi.download: DownloadState?` (null until media is known); `PracticeSessionViewModel(context, section, repo, downloads: PracticeDownloads, progress, uid)`; `fun useMobileData()`

- [ ] **Step 1: Write failing test** — make `PracticeDownloads`'s file folder injectable (`PracticeDownloads(context, dir: File = File(context.filesDir, "practice"))`) and add an `open fun state` or constructor seam so the test can drive state. Test `startsPlayingWhenFileAppears`: with media for Blue Light and state flow emitting `Downloading(40, true)` then `Ready` (and the file created), assert `ui.download` goes `Downloading` → `Ready` and `player.currentMediaItem?.localConfiguration?.uri?.scheme == "file"`. Test `youTubeNotAffected`: YouTube media → `ui.download == DownloadState.NotNeeded` and `player.currentMediaItem == null`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** In init, after media is known: collect `downloads.state(media)` into `ui.download`; on first `Ready`, `player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), pos)` and `prepare()` (once). Remove the old `fromUri(media.url)`. In the screen, when `media` is a file type and `ui.download is Downloading`, show in place of the video: progress indicator (determinate when `percent > 0`), text `session_downloading_wifi` (`Downloading on Wi-Fi… %1$d%%`) and an `OutlinedButton` with `session_download_mobile` (`Download now with mobile data`) calling `vm.useMobileData()`. Hindi/Gujarati translations written by hand.
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Commit** — `feat(practice): play guided practices from the downloaded file`

### Task 4: Practice cards show "Downloading…"

**Files:**
- Modify: `app/src/main/java/com/ojashealing/family/ui/content/GuidedPractice.kt` (`GuidedCard`, `buildGuided`, the trailing-label `when` at ~line 239), `MainShell.kt:249` (pass downloads)
- Modify: three `strings.xml` (`guided_downloading` = `Downloading…`)
- Test: extend the existing `buildGuided` tests in `app/src/test/java/com/ojashealing/family/domain/UpgradeFeaturesTest.kt`

**Interfaces:**
- Produces: `GuidedCard.downloading: Boolean`; `buildGuided(..., ready: (PracticeMedia) -> Boolean = { true })`

- [ ] **Step 1: Failing test** — `buildGuided` with `ready = { false }` for a Video practice gives `downloading == true`; for a YouTube practice `downloading == false`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; in `GuidedPracticeViewModel` combine in a flow of `downloads.localFile(..) != null` per media, refreshed by the downloads work info. Label order in the `when`: locked, done, downloading, resume/earn.
- [ ] **Step 4: Run** `gradle :app:testDebugUnitTest` → all PASS.
- [ ] **Step 5: Commit** — `feat(practice): show which practices are still downloading`

### Task 5: 480p videos uploaded

**Files:**
- Local only (git-ignored): `videos/480p/blue_light.mp4`, `videos/480p/morning.mp4`, `videos/480p/night.mp4`

- [ ] **Step 1: Encode** each of `videos/streaming/{blue_light,morning,night}.mp4`:
  `ffmpeg -i <in> -vf scale=-2:480 -c:v libx264 -crf 27 -preset slow -c:a aac -b:a 96k -movflags +faststart videos/480p/<name>.mp4`
- [ ] **Step 2: Check** with ffprobe: height 480, duration within 1 s of the source (1758, 1952, 515 s); total size of the three well under the current 329 MB. Spot-check one frame visually.
- [ ] **Step 3: Upload** — `cd firebase/functions && npx ts-node scripts/uploadPracticeMedia.ts ../../videos/480p` (only the three present files change; Quick Protection keeps its current file). Needs Application Default Credentials; if missing, ask the owner to run `gcloud auth application-default login`.
- [ ] **Step 4: Verify** `config/practiceMedia` now has new tokens for the three and the same URL for `quick_protection`.

### Task 6: Full check and APK

- [ ] **Step 1:** `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → tests pass, lint has only the known `local.properties` error.
- [ ] **Step 2:** Install the APK on the emulator: open Practice on Wi-Fi, watch the cards go from "Downloading…" to normal, then turn on airplane mode and play Blue Light from start to finish to confirm it plays offline and points are given (points need network at the end; turn it back on before the last 10%).
- [ ] **Step 3:** Commit any fixes, push `main`.
