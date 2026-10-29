# Phase 1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mock prototype's foundation with a real one: Firebase-backed sign-in, onboarding and profile, server-authoritative Moksha Points (daily open) and levels, the hi/gu/en UI, and the app-wide type/spacing system — with every mock removed.

**Architecture:** Android app in layers `ui` → `domain` → `data`, wired by a hand-written `AppContainer` (see Global Constraints for why not Hilt). Firestore holds users/config; Cloud Functions (TypeScript) are the only writer of points and levels. Screens observe `StateFlow<UiState>` from ViewModels.

**Tech Stack:** Kotlin 2.2.10, AGP 9.1.1, Compose BOM 2024.09.00, Material 3, Navigation Compose 2.8.9, Firebase BoM 34.17.0 (Auth, Firestore, Storage, Functions, Messaging, Crashlytics, App Check Play Integrity), Credential Manager 1.5.0 + googleid 1.1.1, DataStore 1.1.7, AppCompat 1.7.x (per-app locales), Coil 2.7.0. Backend: Firebase CLI, Cloud Functions v2 on Node 22 + TypeScript, `firebase-functions-test`, `@firebase/rules-unit-testing`, Jest.

**Spec:** `docs/superpowers/specs/2026-10-29-ojas-family-production-design.md` (§5, §6, §7.1, §7.2, §8, §9; phase 1 of §10)

## Global Constraints

- applicationId and namespace: `com.ojashealing.family`. Firebase project: `ojas-family-app`. Region for Firestore and Functions: `asia-south1`.
- DI: manual `AppContainer` instead of the spec's Hilt — Hilt's Gradle plugin is a known risk on AGP 9 built-in Kotlin; revisit when Hilt documents AGP 9 support. (Builder call, recorded here; update spec §6 in Task 1.)
- No raw `fontSize =` / `fontWeight =` in `ui/screens` or `ui/components`; all text uses `MaterialTheme.typography`. No raw `.dp` spacing literals for padding/spacers in new code; use `Spacing` tokens.
- Type scale (sp size/line height): display 32/40, title 20/28, body 16/24, label 14/20, caption 12/16; Devanagari/Gujarati locales add +2sp line height. Weights: Regular 400 and SemiBold 600 only.
- Fonts bundled in `res/font`: Noto Sans, Noto Sans Devanagari, Noto Sans Gujarati (Regular + SemiBold each), from github.com/notofonts (OFL-1.1; add `licenses/OFL.txt`).
- Spacing tokens: `xs=4, s=8, m=12, l=16, xl=24, xxl=32` (dp). Screen gutter 16dp, card padding 24dp, touch targets ≥ 48dp.
- Every user-visible string in `values/strings.xml` with translations in `values-hi` and `values-gu`. Languages: `en`, `hi`, `gu`.
- Clients never write `mokshaPoints`, `userLevel`, `memberLevel`, `paidMonths`, `role`, `lastDailyOpen`, or anything under `pointEvents`; Firestore rules enforce it.
- "Today" for points is the IST calendar day (`Asia/Kolkata`), computed on the server.
- Level thresholds live in Firestore `config/levels`; defaults from spec: L2 = 7 days + 7 pts, L3 = 90 days + 500 pts, L4 = 365 days + 100000 pts.
- Remove all mock content: `OjasRepository`, `initial*()` seed lists, `generateSwamijiResponse`, the level simulator dialog in `OjasTopBar`.

## Review Focus

1. **Daily-open called twice on the same IST day / across midnight IST** — exactly one point per IST day; a call at 23:59 and 00:01 IST yields two. Test in Task 6 (`claimDailyOpen` idempotency + IST boundary).
2. **Client tries to raise its own points/level** — rules reject any write touching protected fields, including on profile create. Test in Task 5 (`rules: user cannot write mokshaPoints on create or update`).
3. **User signs in with a second provider for the same email / deletes account mid-onboarding** — profile doc is created once by `onUserCreate`, onboarding resumes where it stopped; deleting the auth user deletes `users/{uid}` and subcollections. Tests in Task 6 (`onUserCreate is idempotent`, `deleteAccount removes user data`).
4. **Offline at launch** — app shows cached profile, daily-open retried when back online, no crash. Test in Task 8 (`HomeViewModel retries daily open after network error`).
5. **Hindi/Gujarati text overflowing one-line labels (tabs, buttons)** — labels wrap or ellipsize, never clip. Test in Task 3 (Robolectric screenshot-free check: every bottom-nav and tab label uses `maxLines = 1` + `TextOverflow.Ellipsis`, asserted by Compose UI test in `hi` and `gu`).

---

## File Structure

```
firebase/
  firebase.json, .firebaserc, firestore.rules, firestore.indexes.json, storage.rules
  functions/ (package.json, tsconfig.json, jest.config.js)
    src/index.ts                exports
    src/time.ts                 istDay()
    src/levels.ts               computeUserLevel(), LevelConfig
    src/points.ts               awardPoints() ledger writer
    src/auth.ts                 onUserCreate, deleteAccount
    src/daily.ts                claimDailyOpen
    test/*.test.ts              unit + emulator tests
  rules-test/ (package.json, rules.test.ts)
app/src/main/
  res/font/*.ttf, res/values{,-hi,-gu}/strings.xml, res/xml/locales_config.xml
  java/com/ojashealing/family/
    OjasApp.kt                  Application, AppContainer
    MainActivity.kt
    di/AppContainer.kt
    ui/theme/{Color,Type,Spacing,Theme}.kt
    ui/components/{OjasCard,SectionHeader,StateViews,LevelLockBadge,OjasTopBar,PaytmCoinAnimation}.kt
    ui/navigation/{Routes,OjasNavHost}.kt
    ui/onboarding/{LanguageScreen,SignInScreen,PhoneOtpScreen,ProfileSetupScreen,OnboardingViewModel}.kt
    ui/settings/{SettingsScreen,SettingsViewModel}.kt
    ui/screens/... (existing 5 tabs, rewired to empty states in Task 8)
    domain/{Permissions,UserLevel,Age}.kt
    data/auth/AuthRepository.kt
    data/user/{UserRepository,UserDto}.kt
    data/points/PointsRepository.kt
    data/settings/SettingsStore.kt
    data/location/{LocationRepository}.kt + assets/location/{countries.json,states.json,in_cities.json}
```

---

### Task 1: Package rename, Firebase project wiring, CI keystore

**Files:**
- Modify: `app/build.gradle.kts` (namespace, applicationId, Firebase deps), `gradle/libs.versions.toml`, move `app/src/{main,test,androidTest}/java/com/example/**` → `com/ojashealing/family/**`
- Create: `app/google-services.json`, `firebase/firebase.json`, `firebase/.firebaserc`
- Modify: `.github/workflows/build-apk.yml`, spec §6 (DI line)

**Interfaces:**
- Produces: package `com.ojashealing.family`; Firebase Android app registered in `ojas-family-app`; CI signs debug builds with a stable keystore from secret `DEBUG_KEYSTORE_B64`.

- [ ] **Step 1:** Register Android app `com.ojashealing.family` in `ojas-family-app` (Firebase MCP `firebase_create_app`), download config to `app/google-services.json`.
- [ ] **Step 2:** Generate one debug keystore (alias `androiddebugkey`, passwords `android`), print SHA-1 and SHA-256, register both on the Firebase Android app (`firebase_create_android_sha`). Store it base64-encoded as GitHub secret `DEBUG_KEYSTORE_B64`; CI decodes it to `debug.keystore` instead of generating a new one.
- [ ] **Step 3:** Move sources to the new package (git mv), update `package`/`import` lines, `namespace`/`applicationId`, manifest `.MainActivity`.
- [ ] **Step 4:** Add deps: firebase-auth, firestore, storage, functions, messaging, crashlytics (+ plugin), appcheck-playintegrity, credentials, credentials-play-services, googleid, datastore-preferences, navigation-compose, appcompat, coil-compose. Remove firebase-ai, appcheck-recaptcha, room, retrofit, moshi, okhttp (unused).
- [ ] **Step 5:** `firebase_init` in `firebase/`: Firestore `(default)` in `asia-south1`, Storage, Auth providers emailPassword + googleSignIn (brand "Ojas Family", support email yashkumar03111997@gmail.com). Phone provider enabled in console (needs Blaze — confirm billing is on first).
- [ ] **Step 6:** Verify: CI `gradle assembleDebug testDebugUnitTest` green; `aapt dump badging` on the APK shows `package: name='com.ojashealing.family'`.
- [ ] **Step 7:** Commit `chore: rename package to com.ojashealing.family and wire Firebase`.

### Task 2: Visual system — fonts, type scale, spacing, shared components

**Files:**
- Create: `res/font/noto_sans_{regular,semibold}.ttf`, `noto_sans_devanagari_{regular,semibold}.ttf`, `noto_sans_gujarati_{regular,semibold}.ttf`, `licenses/OFL.txt`, `ui/theme/Spacing.kt`, `ui/components/{OjasCard,SectionHeader,StateViews,LevelLockBadge}.kt`
- Modify: `ui/theme/Type.kt`, `ui/theme/Theme.kt`
- Test: `app/src/test/.../ui/theme/TypographyTest.kt`, `app/src/test/.../ui/NoRawTextStyleTest.kt`

**Interfaces:**
- Produces: `fun ojasTypography(script: Script): Typography`; `enum class Script { Latin, Devanagari, Gujarati }`; `fun scriptFor(locale: Locale): Script`; `object Spacing { val xs; s; m; l; xl; xxl: Dp }`; composables `OjasCard(modifier, onClick: (() -> Unit)? = null, content)`, `SectionHeader(title: String, action: (@Composable () -> Unit)? = null)`, `LoadingState()`, `EmptyState(icon: ImageVector, title: String, body: String, action: Pair<String, () -> Unit>? = null)`, `ErrorState(message: String, onRetry: () -> Unit)`, `LevelLockBadge(requirement: String)`.
- Mapping to M3 roles: displayLarge→display, titleLarge/titleMedium→title, bodyLarge/bodyMedium→body, labelLarge/labelMedium→label, bodySmall/labelSmall→caption.

- [ ] **Step 1: Failing tests**
  - `TypographyTest.latinScaleMatchesSpec`: `ojasTypography(Script.Latin).titleLarge.fontSize == 20.sp && lineHeight == 28.sp`; `bodyLarge` 16/24; `labelLarge` 14/20; `bodySmall` 12/16; `displayLarge` 32/40.
  - `TypographyTest.indicScriptsAddTwoSpLineHeight`: `ojasTypography(Script.Devanagari).bodyLarge.lineHeight == 26.sp`, same for Gujarati.
  - `TypographyTest.onlyTwoWeights`: every style's `fontWeight` ∈ {`FontWeight.Normal`, `FontWeight.SemiBold`}.
  - `TypographyTest.scriptForLocale`: `hi`→Devanagari, `gu`→Gujarati, `en`→Latin.
  - `NoRawTextStyleTest.screensUseThemeTypography`: reads every `.kt` under `app/src/main/java/com/ojashealing/family/ui/{screens,components,onboarding,settings}` and asserts no line matches `fontSize\s*=` or `fontWeight\s*=` (allow-list: `PaytmCoinAnimation.kt` coin glyph only).
- [ ] **Step 2:** Run `gradle testDebugUnitTest --tests '*Typography*'` → FAIL (functions undefined).
- [ ] **Step 3:** Download fonts; implement `ojasTypography` with a `FontFamily` per script (Noto Sans Latin always first so mixed text renders), `Spacing`, the shared components. `OjasFamilyTheme(darkTheme, locale)` selects the typography.
- [ ] **Step 4:** Run typography tests → PASS. `NoRawTextStyleTest` stays failing until Task 8 migrates screens — mark it `@Ignore("enabled in Task 8")` now.
- [ ] **Step 5:** Commit `feat(ui): Noto type scale, spacing tokens, shared state components`.

### Task 3: Three languages and per-app language switching

**Files:**
- Create: `res/values-hi/strings.xml`, `res/values-gu/strings.xml`, `res/xml/locales_config.xml`, `data/settings/SettingsStore.kt`
- Modify: `res/values/strings.xml`, `AndroidManifest.xml` (`android:localeConfig`, `AppLocalesMetadataHolderService` autoStoreLocales), `MainActivity` → `AppCompatActivity`
- Test: `app/src/test/.../i18n/StringResourcesTest.kt`, `app/src/test/.../ui/LabelOverflowTest.kt`

**Interfaces:**
- Produces: `class SettingsStore(context)` with `val themeMode: Flow<ThemeMode>`, `suspend fun setThemeMode(ThemeMode)`, `enum class ThemeMode { System, Light, Dark }`; `fun setAppLanguage(tag: String)` (wraps `AppCompatDelegate.setApplicationLocales`), `fun currentAppLanguage(): String`.
- String keys used by later tasks: `tab_community, tab_spiritual, tab_home, tab_ask, tab_profile, onboarding_choose_language, signin_title, signin_google, signin_phone, signin_email, profile_setup_title, field_name, field_dob, field_gender, field_country, field_state, field_city, action_continue, action_retry, action_save, empty_*` (one per tab), `error_generic, error_offline, settings_*`.

- [ ] **Step 1: Failing tests**
  - `StringResourcesTest.everyKeyTranslated`: parse the three `strings.xml` files; key sets of `values-hi` and `values-gu` equal `values`.
  - `StringResourcesTest.placeholdersMatch`: for each key, the `%1$s`/`%1$d` placeholders are identical across languages.
  - `LabelOverflowTest` (Robolectric, `@Config(qualifiers = "hi")` and `"gu"`): render the bottom nav; every label node has text and the `Text` uses `maxLines = 1` (assert via `SemanticsProperties.Text` + width ≤ parent).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Write strings (Hindi and Gujarati translations of all keys; Devanagari numerals not used — keep 0-9). Implement `SettingsStore` on DataStore Preferences.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(i18n): Hindi, Gujarati, English with per-app language`.

### Task 4: Domain rules — levels, permissions, age

**Files:**
- Create: `domain/UserLevel.kt`, `domain/Permissions.kt`, `domain/Age.kt`
- Delete: `data/model/User.kt` (replaced), `data/model/*` mock-only types
- Test: `app/src/test/.../domain/PermissionsTest.kt`, `AgeTest.kt` (move existing `UserProfileTest` age cases here)

**Interfaces:**
- Produces:
  - `enum class UserLevel(val rank: Int) { L1(1), L2(2), L3(3), L4(4) }`, `enum class MemberLevel(val rank: Int) { None(0), M1(1), M2(2), M3(3) }`, `enum class Role { Member, SeniorMember, Swamiji, Admin }`
  - `data class Viewer(val userLevel: UserLevel, val memberLevel: MemberLevel, val role: Role)`
  - `object Permissions { fun canLike(v: Viewer): Boolean; canComment; canAskQuestion; canReadUnderstanding; canDoDailyPractices; canPostDailyActivity; canViewOtherProfiles; canBuyWeeklyChat; canBuyMonthlyChat; canBuyAnytimeCalls; isStaff(v) }`
  - `fun ageOn(dob: LocalDateLike, today: LocalDateLike): Int` where `data class LocalDateLike(val year: Int, val month: Int, val day: Int)` (no java.time; minSdk 24).

- [ ] **Step 1: Failing tests** (`PermissionsTest`), each a one-line assertion:
  - `L1/None`: canLike false, canReadUnderstanding false.
  - `L2/None`: canLike true, canComment false, canReadUnderstanding true, canBuyWeeklyChat true.
  - `L3/None`: canComment true, canAskQuestion true, canDoDailyPractices false.
  - `L4/None`: canDoDailyPractices true.
  - `L2/M1`: canDoDailyPractices true; `L1/M1`: canLike true (member starts at User Level 2 rights).
  - `L2/M2`: canComment true, canBuyMonthlyChat true; `L2/M1`: canBuyMonthlyChat false.
  - `L2/M3`: canPostDailyActivity true, canViewOtherProfiles true, canBuyAnytimeCalls true.
  - `Role.Admin` with `L1/None`: every permission true.
  - `AgeTest`: (1990-06-15, 2026-06-14)→35, (1990-06-15, 2026-06-15)→36, (2000-02-29, 2026-02-28)→25.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement per spec §9 table. **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(domain): level, member and role permissions`.

### Task 5: Firestore and Storage security rules

**Files:**
- Create: `firebase/firestore.rules`, `firebase/storage.rules`, `firebase/firestore.indexes.json`, `firebase/rules-test/{package.json,rules.test.ts}`
- Modify: `.github/workflows/build-apk.yml` (job `backend`: `npm ci && firebase emulators:exec --only firestore,storage "npm test"`)

**Interfaces:**
- Produces data model (phase 1):
  - `users/{uid}`: `displayName, photoUrl, dob ("yyyy-MM-dd"), gender, country, state, city, language, profileComplete: bool, hideProfile: bool, hideGoals: bool, notificationPrefs: map<string,bool>, fcmTokens: list` (user-writable) + `mokshaPoints:int, userLevel:int, memberLevel:int, role:string, createdAt, lastDailyOpen ("yyyy-MM-dd"), activeDays:int` (server-only).
  - `users/{uid}/pointEvents/{eventKey}`: `amount, reason, milestone: bool, createdAt` (server-only, owner-readable).
  - `config/levels`: `{ l2:{days,points}, l3:{...}, l4:{...}, dailyCaps:{like,comment,question} }` (readable by signed-in users, admin-writable).

- [ ] **Step 1: Failing tests** (`rules.test.ts`, `@firebase/rules-unit-testing`):
  - `owner can read and update own displayName`
  - `user cannot write mokshaPoints on create or update` (also `userLevel`, `role`, `memberLevel`, `lastDailyOpen`)
  - `user cannot read another user's doc when target hideProfile is true`; `M3 member can read when hideProfile is false`; `L2 user cannot read other users at all`
  - `user cannot write pointEvents`; `owner can read own pointEvents`
  - `signed-in user can read config/levels; non-admin cannot write it`
  - `unauthenticated user can read nothing`
  - Storage: `owner can upload users/{uid}/avatar.jpg under 5 MB image/*`; `cannot upload to another uid`; `cannot upload 6 MB`.
- [ ] **Step 2:** Run emulator tests → FAIL. **Step 3:** Write rules (staff role read from `request.auth.token.role`, set as a custom claim by Functions). **Step 4:** → PASS.
- [ ] **Step 5:** Commit `feat(backend): Firestore and Storage security rules with tests`.

### Task 6: Cloud Functions — user lifecycle, daily open, levels

**Files:**
- Create: `firebase/functions/{package.json,tsconfig.json,jest.config.js}`, `src/{index,time,levels,points,auth,daily}.ts`, `test/{time,levels,points,daily,auth}.test.ts`

**Interfaces:**
- Produces:
  - `istDay(at: Date): string` → `"yyyy-MM-dd"` in Asia/Kolkata.
  - `computeUserLevel(input: { activeDays: number; points: number }, cfg: LevelConfig): 1|2|3|4`
  - `awardPoints(db, uid, eventKey: string, amount: number, reason: string, milestone: boolean): Promise<boolean>` — transaction; returns false if `pointEvents/{eventKey}` exists; increments `mokshaPoints`; recomputes `userLevel`.
  - `onUserCreate` (auth `beforeUserCreated` is Identity Platform; use v1 `auth.user().onCreate`): creates `users/{uid}` with defaults `mokshaPoints 0, userLevel 1, memberLevel 0, role "member", activeDays 0, profileComplete false, language "en"` if absent.
  - callable `claimDailyOpen() → { awarded: boolean, points: number }`: event key `daily:{istDay}`, +1 point, `activeDays += 1`, `lastDailyOpen = istDay`, milestone true.
  - callable `deleteAccount()`: recursive delete `users/{uid}`, Storage `users/{uid}/`, then the auth user.
  - callable `setRole({ uid, role })`: admin-only; sets custom claim + `users/{uid}.role`.
  - Seed script `npm run seed:config` writes default `config/levels`.

- [ ] **Step 1: Failing tests**
  - `time.test`: `istDay(new Date("2026-10-29T18:29:00Z")) === "2026-10-29"`; `istDay(new Date("2026-10-29T18:31:00Z")) === "2026-10-30"`.
  - `levels.test`: defaults → `{6,1000}`→1, `{7,7}`→2, `{90,499}`→2, `{90,500}`→3, `{365,99999}`→3, `{365,100000}`→4.
  - `daily.test` (emulator): two calls same IST day → points +1, second returns `awarded:false`; call at 18:29Z and 18:31Z → +2, `activeDays` 2.
  - `daily.test`: user at 6 activeDays / 6 points claims → `userLevel` becomes 2.
  - `auth.test`: `onUserCreate` twice for same uid leaves one doc and does not reset existing `mokshaPoints`.
  - `auth.test`: `deleteAccount` removes `users/{uid}` and its `pointEvents`.
  - `auth.test`: non-admin `setRole` → `permission-denied`.
- [ ] **Step 2:** Run `npm test` (emulator) → FAIL. **Step 3:** Implement; region `asia-south1` on every function; App Check enforced on callables. **Step 4:** → PASS.
- [ ] **Step 5:** Deploy rules + functions (`firebase deploy --only firestore,storage,functions`), run `seed:config`. Verify in console that `config/levels` exists.
- [ ] **Step 6:** Commit `feat(backend): user lifecycle, daily open points and levels`.

### Task 7: Data layer and onboarding (language → sign in → profile)

**Files:**
- Create: `di/AppContainer.kt`, `OjasApp.kt`, `data/auth/AuthRepository.kt`, `data/user/{UserRepository,UserDto}.kt`, `data/points/PointsRepository.kt`, `data/location/LocationRepository.kt`, `assets/location/*.json`, `ui/onboarding/*`, `ui/navigation/{Routes,OjasNavHost}.kt`
- Test: `app/src/test/.../ui/onboarding/OnboardingViewModelTest.kt`, `data/location/LocationRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 3 `SettingsStore`, Task 4 domain types, Task 6 callables.
- Produces:
  - `interface AuthRepository { val currentUid: StateFlow<String?>; suspend fun signInWithGoogle(activity: Activity): Result<Unit>; suspend fun startPhoneSignIn(activity: Activity, e164: String): Result<PhoneVerification>; suspend fun confirmPhoneCode(v: PhoneVerification, code: String): Result<Unit>; suspend fun signInWithEmail(email: String, password: String, create: Boolean): Result<Unit>; suspend fun signOut() }`
  - `interface UserRepository { fun observe(uid: String): Flow<UserProfile?>; suspend fun saveProfile(uid: String, edit: ProfileEdit): Result<Unit>; suspend fun uploadAvatar(uid: String, uri: Uri): Result<String> }`
  - `data class UserProfile(uid, displayName, photoUrl, dob, gender, country, state, city, language, profileComplete, hideProfile, hideGoals, notificationPrefs: Map<String,Boolean>, mokshaPoints: Int, viewer: Viewer, activeDays: Int)`
  - `data class ProfileEdit(displayName, dob, gender, country, state, city, language)` — never contains server-only fields.
  - `interface PointsRepository { suspend fun claimDailyOpen(): Result<Boolean>; fun observeNewEvents(uid: String): Flow<PointEvent> }` — `PointEvent(amount, reason, milestone)` feeds the existing coin overlay (`RewardEvent`).
  - `class LocationRepository(assets) { fun countries(query: String): List<Country>; fun states(countryCode: String, query: String): List<Region>; fun cities(countryCode: String, stateCode: String, query: String): List<String> }` — India cities from `in_cities.json`; others return empty (UI falls back to free text).
  - Routes: `Language`, `SignIn`, `PhoneOtp`, `ProfileSetup`, `Main`. Start = `Language` if no language chosen, else `SignIn` if signed out, else `ProfileSetup` if `!profileComplete`, else `Main`.
- Location data source: dr5hn/countries-states-cities-database (ODbL-1.0; attribution in Settings → About).

- [ ] **Step 1: Failing tests**
  - `LocationRepositoryTest.countrySearchIsPrefixAndCaseInsensitive`: `countries("ind")` first item code `IN`.
  - `LocationRepositoryTest.statesOfIndiaIncludeGujarat`; `citiesOfGujaratContainAhmedabad`; `citiesForNonIndiaAreEmpty`.
  - `OnboardingViewModelTest` (fakes for repos): `startDestination` = Language / SignIn / ProfileSetup / Main for the four states above.
  - `OnboardingViewModelTest.saveDisabledUntilRequiredFieldsSet`: name, dob, gender, country required; city optional.
  - `OnboardingViewModelTest.dobInFutureRejected` and `under13Rejected` (Play families policy: app is 13+).
- [ ] **Step 2:** → FAIL. **Step 3:** Implement repositories over Firebase SDKs, Google sign-in with Credential Manager (`GetSignInWithGoogleOption`, web client ID from `google-services.json` `default_web_client_id`), phone with `PhoneAuthProvider.verifyPhoneNumber`, onboarding screens using Task 2 components and Task 3 strings. **Step 4:** → PASS.
- [ ] **Step 5:** Manual check on device/emulator: each sign-in method creates `users/{uid}`; profile saved; relaunch lands on Main.
- [ ] **Step 6:** Commit `feat: onboarding with Google, phone and email sign-in`.

### Task 8: Remove mocks, rewire the shell, migrate screens to the visual system

**Files:**
- Delete: `data/repository/OjasRepository.kt`, `data/model/{AskTopics,Community,Goal,SpiritualActivity,ThoughtOfDay,LocationData}.kt`, `MokshaLikePointsTest.kt`, `ExampleRobolectricTest.kt`, `UserProfileTest.kt` (moved in Task 4)
- Modify: `MainActivity.kt`, `ui/components/{OjasTopBar,PaytmCoinAnimation}.kt`, all `ui/screens/**`
- Test: `app/src/test/.../ui/main/HomeViewModelTest.kt`; un-ignore `NoRawTextStyleTest`

**Interfaces:**
- Consumes: Tasks 2, 3, 4, 7.
- Produces: `MainViewModel(userRepo, pointsRepo)` exposing `profile: StateFlow<UserProfile?>`, `reward: StateFlow<RewardEvent?>`; calls `claimDailyOpen()` on each app foreground (retry with backoff on network error).

- [ ] **Step 1: Failing tests**
  - `HomeViewModelTest.claimsDailyOpenOnStart`; `retriesDailyOpenAfterNetworkError` (fake fails once, succeeds second; reward emitted once).
  - `HomeViewModelTest.rewardEmittedFromPointEvents` (milestone flag propagated).
  - `NoRawTextStyleTest` enabled.
- [ ] **Step 2:** → FAIL. **Step 3:** Top bar shows real `mokshaPoints` and level; level simulator removed. Each of the five tabs renders its real header plus `EmptyState` with the tab's `empty_*` string (content arrives in phases 2–3). Profile tab shows the real profile (age from `ageOn`), edit profile reuses `ProfileSetupScreen`. Replace every raw `fontSize`/`fontWeight`/spacing literal with typography/`Spacing`. **Step 4:** → PASS; `grep -r "OjasRepository\|initialThoughts\|generateSwamijiResponse" app/src` returns nothing.
- [ ] **Step 5:** Commit `refactor: remove mock data, wire shell to Firebase, apply type scale`.

### Task 9: Settings, notification preferences, account deletion

**Files:**
- Create: `ui/settings/{SettingsScreen,SettingsViewModel}.kt`
- Test: `app/src/test/.../ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsStore`, `UserRepository`, `AuthRepository`, callable `deleteAccount`.
- Produces: notification pref keys (stored in `users/{uid}.notificationPrefs`): `all, announcements, thoughtOfDay, communityPosts, communityInteractions, communityMentions, dailyPosts, dailyReplies, dailyMentions`; reminder times `morningReminder`, `nightReminder` ("HH:mm").

- [ ] **Step 1: Failing tests**
  - `masterSwitchOffDisablesAllCategoriesInUi` (category toggles disabled, stored values untouched).
  - `themeChangePersists` (SettingsStore fake).
  - `languageChangeCallsSetAppLanguage`.
  - `deleteAccountSignsOutAfterSuccess`; `deleteAccountShowsErrorAndStaysSignedInOnFailure`.
- [ ] **Step 2:** → FAIL. **Step 3:** Implement; delete requires a confirm dialog with typed confirmation. **Step 4:** → PASS.
- [ ] **Step 5:** Commit `feat: settings, notification preferences, account deletion`.

### Task 10: Phase exit check

- [ ] **Step 1:** CI green: `assembleDebug`, `testDebugUnitTest`, backend job (rules + functions tests).
- [ ] **Step 2:** On a device with the CI APK, in each language: install → pick language → sign in (Google, phone, email) → profile → Main shows +1 coin burst once; relaunch same day → no second point; Settings → delete account removes the user in console.
- [ ] **Step 3:** Update spec status to "Phase 1 shipped", open PR to `main`.
