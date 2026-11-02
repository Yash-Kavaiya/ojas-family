# Phase 2 — Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill the Practice tab and the Understanding part of Ask with real Ojas Healing YouTube videos, playable in the app and managed by admins from inside the app.

**Architecture:** Firestore `content/{id}` documents hold one YouTube video each with its section and unlock level. Admins add a single video by URL or import a whole channel playlist through admin-only callables that read YouTube's public oEmbed/RSS endpoints server-side. The app lists content per section, locks items the viewer has not unlocked, and plays videos in an embedded YouTube player that stops when the app is backgrounded (YouTube ToS).

**Tech Stack:** existing Phase 1 stack + `com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0`; Functions use Node 22 `fetch` and `fast-xml-parser`.

**Spec:** `docs/superpowers/specs/2026-10-29-ojas-family-production-design.md` (§7.3, §7.4, §7.7, §9)

## Global Constraints

- Content doc id is `yt_<videoId>`; re-importing a playlist never duplicates or overwrites admin edits (only creates missing docs).
- Sections (Firestore id → UI): `blue_light`, `quick_protection`, `morning`, `night`, `yoga`, `other`, `understanding`.
- Default unlocks per spec §9: `blue_light`, `quick_protection`, `yoga`, `other` open to all; `morning`, `night` need `Permissions.canDoDailyPractices`; `understanding` needs `Permissions.canReadUnderstanding`. A doc's `minUserLevel` / `minMemberLevel` can only raise these, never lower them.
- Only staff role `admin` writes `content/**`; every signed-in user reads.
- Thumbnails: `https://i.ytimg.com/vi/<id>/hqdefault.jpg`. No YouTube Data API key.
- Admin account for launch: `yash.kavaiya3@gmail.com` (custom claim `role=admin`).
- All UI text through `strings.xml` in en/hi/gu; typography and `Spacing` only (NoRawTextStyleTest stays green).
- Morning/night practices are YouTube-only in this phase (audio player is phase 4).

## Review Focus

1. **Pasted URL variants** (`youtu.be/ID`, `watch?v=ID&t=30`, `shorts/ID`, `embed/ID`, plain 11-char id, garbage) — valid ones resolve to the same id, garbage is rejected with a message. Test in Task 1 (`youtubeIdFrom`).
2. **Playlist re-import after an admin edited a title or unlock level** — edits survive. Test in Task 3 (`import keeps existing docs untouched`).
3. **Locked item tapped** — no player opens; the user sees what unlocks it. Test in Task 1 (`accessFor` returns the requirement) + Task 5 UI.
4. **App backgrounded while a video plays** — playback pauses (ToS). Covered by the library's lifecycle observer; asserted in Task 5 by registering the player with the screen lifecycle.
5. **Non-admin calls an admin callable** — `permission-denied`, nothing written. Test in Task 3.

---

### Task 1: Content model, URL parsing and access rules (Android, pure Kotlin)

**Files:**
- Create: `app/src/main/java/com/ojashealing/family/domain/Content.kt`
- Test: `app/src/test/java/com/ojashealing/family/domain/ContentTest.kt`

**Interfaces:**
- Produces:
  - `enum class ContentSection(val id: String) { BlueLight("blue_light"), QuickProtection("quick_protection"), Morning("morning"), Night("night"), Yoga("yoga"), Other("other"), Understanding("understanding"); companion fun fromId(id: String?): ContentSection? }`
  - `data class ContentItem(val id: String, val section: ContentSection, val videoId: String, val title: String, val minUserLevel: Int = 1, val minMemberLevel: Int = 0, val order: Long = 0, val published: Boolean = true)`
  - `fun youtubeIdFrom(input: String): String?`
  - `sealed interface Access { data object Open; data class Locked(val needsUserLevel: Int?, val needsMemberLevel: Int?) }`
  - `fun accessFor(item: ContentItem, viewer: Viewer): Access`

- [ ] **Step 1: Failing tests** — `youtubeIdFrom`: `"https://youtu.be/Pg54NjjSvzY"`, `"https://www.youtube.com/watch?v=Pg54NjjSvzY&t=30s"`, `"https://youtube.com/shorts/Pg54NjjSvzY"`, `"https://www.youtube.com/embed/Pg54NjjSvzY"`, `"Pg54NjjSvzY"` → `"Pg54NjjSvzY"`; `"hello"`, `"https://example.com/watch?v=Pg54NjjSvzY"` → `null`. `accessFor`: L1 viewer + `QuickProtection` → Open; L1 + `Morning` → Locked(needsUserLevel=4, needsMemberLevel=1); L2/M1 + `Morning` → Open; L1 + `Understanding` → Locked(2, 1); L2 + item `Yoga` with `minUserLevel=3` → Locked(3, null); Admin role + anything → Open.
- [ ] **Step 2:** run `gradle testDebugUnitTest --tests com.ojashealing.family.domain.ContentTest` → FAIL (unresolved).
- [ ] **Step 3:** implement; host check is `(www\.|m\.)?youtube\.com|youtu\.be` only.
- [ ] **Step 4:** → PASS. **Step 5:** commit `feat(domain): content sections, YouTube id parsing, unlock rules`.

### Task 2: Firestore rules for content

**Files:** Modify `firebase/firestore.rules`; Test `firebase/rules-test/rules.test.ts`

- [ ] **Step 1: Failing tests** — `signed-in user can read content`; `unauthenticated cannot read content`; `non-admin cannot write content`; `admin can create content with valid fields`; `admin cannot create content with unknown section`.
- [ ] **Step 2:** emulator run → FAIL. **Step 3:** add `match /content/{id}` with `validContent()` (section in list, videoId 11 chars, title string ≤ 200, min levels ints). **Step 4:** → PASS. **Step 5:** commit `feat(backend): content rules`; deploy `firestore:rules`.

### Task 3: Admin callables — add video, import playlist

**Files:** Create `firebase/functions/src/content.ts`, `firebase/functions/test/content.test.ts`, `firebase/functions/test/fixtures/playlist.xml`; Modify `src/index.ts`, `package.json` (+ `fast-xml-parser`)

**Interfaces:**
- Produces: `parsePlaylistFeed(xml: string): { videoId: string; title: string; published: string }[]`; `importEntries(db, section, entries): Promise<{ created: number; skipped: number }>`; `addVideoFor(db, callerToken, { url, section, title? }): Promise<{ id: string }>`; callables `addVideo`, `importPlaylist({ playlistId, section })`.

- [ ] **Step 1: Failing tests** — `parsePlaylistFeed` on fixture → 3 entries with ids/titles in feed order; `import creates yt_<id> docs with section and order by feed position`; `import keeps existing docs untouched` (pre-set title "Edited" survives, `skipped` counts it); `non-admin addVideo is permission-denied`; `addVideo rejects a non-YouTube url` (`invalid-argument`).
- [ ] **Step 2:** emulator run → FAIL. **Step 3:** implement; `addVideo` resolves the title from `https://www.youtube.com/oembed?url=...&format=json` when not given (fetch injectable for tests). **Step 4:** → PASS. **Step 5:** commit; deploy functions.

### Task 4: Content repository and player dependency

**Files:** Create `data/content/ContentRepository.kt`; Modify `di/AppContainer.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Interfaces:**
- Produces: `interface ContentRepository { fun observe(): Flow<List<ContentItem>>; suspend fun addVideo(url: String, section: ContentSection): Result<Unit>; suspend fun importPlaylist(playlistId: String, section: ContentSection): Result<Int>; suspend fun setPublished(id: String, published: Boolean): Result<Unit>; suspend fun delete(id: String): Result<Unit> }` — `observe` returns published items ordered by `section, order` (admins see unpublished too).

- [ ] **Step 1:** implement over Firestore + callables; DTO mapping skips docs with unknown section. Verify: `gradle assembleDebug` succeeds.
- [ ] **Step 2:** commit `feat(data): content repository`.

### Task 5: Practice tab, Understanding and the video player

**Files:** Create `ui/content/{ContentViewModel,VideoTile,PracticeTab,UnderstandingSection,VideoPlayerScreen}.kt`; Modify `ui/main/MainShell.kt`, `ui/screens/TabScreens.kt`, strings (en/hi/gu)
- Test: `app/src/test/java/com/ojashealing/family/ui/content/ContentViewModelTest.kt`

**Interfaces:**
- Produces: `data class SectionUi(val section: ContentSection, val items: List<Pair<ContentItem, Access>>)`; `ContentViewModel(content: Flow<List<ContentItem>>, viewer: Flow<Viewer?>)` with `practice: StateFlow<List<SectionUi>>` (sections in spec order 2.1→2.6, empty sections omitted) and `understanding: StateFlow<List<Pair<ContentItem, Access>>>`. Route `VideoRoute(videoId: String, title: String)`.

- [ ] **Step 1: Failing tests** — `practiceGroupsBySectionInSpecOrder`; `emptySectionsAreHidden`; `understandingExcludedFromPractice`; `lockedItemsCarryRequirement` (L1 viewer sees Morning item as Locked).
- [ ] **Step 2:** → FAIL. **Step 3:** implement; Practice tab = `LazyColumn` of section headers + horizontally scrolling `VideoTile`s (16:9 thumbnail, title max 2 lines, lock badge); locked tap shows a dialog naming the requirement; player screen uses `YouTubePlayerView` added to the screen lifecycle (`lifecycleOwner.lifecycle.addObserver`). Ask tab shows the Understanding list (locked state for L1). **Step 4:** → PASS (incl. NoRawTextStyleTest, StringResourcesTest). **Step 5:** commit.

### Task 6: Admin content manager

**Files:** Create `ui/admin/{AdminContentScreen,AdminContentViewModel,ChannelPlaylists}.kt`; Modify `ui/screens/ProfileTab.kt` (Admin entry for staff admins only), `ui/main/MainShell.kt`
- Test: `app/src/test/java/com/ojashealing/family/ui/admin/AdminContentViewModelTest.kt`

**Interfaces:**
- Consumes: `ContentRepository`. Produces `ChannelPlaylists`: list of `(playlistId, titleRes, section)` for the Ojas Healing playlists — Protection Vidhi → quick_protection; All about Meditation → blue_light; Patanjali Yoga Sutras → yoga; Spiritual Encyclopedia, Body Chakras & Kundalini, Chakras Shorts → understanding; Shiva Sutras, Vigyan Bhairav Tantra, Self Healing Course → other.

- [ ] **Step 1: Failing tests** — `addVideoRejectsInvalidUrlWithoutCallingServer`; `importReportsCreatedCount`; `adminEntryHiddenForNonAdmins` (pure function `showAdminEntry(viewer)`).
- [ ] **Step 2:** → FAIL. **Step 3:** implement screen: add-by-URL form (section picker), one-tap import per channel playlist, list with publish switch and delete (confirm). **Step 4:** → PASS. **Step 5:** commit.

### Task 7: Ship

- [ ] **Step 1:** deploy rules + functions; set `role=admin` claim for `yash.kavaiya3@gmail.com` once that account has signed in to the app.
- [ ] **Step 2:** full local suites green; push `main`; CI green; report APK link.
