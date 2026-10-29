# Ojas Family — Production App Design

Date: 2026-10-29
Status: Draft, awaiting approval

## 1. Intent

Ojas Family is the companion app for Swami Anand Swaroop's Ojas Healing community
(YouTube: https://www.youtube.com/@OjasHealing — 164k subscribers, 306 videos, mostly Hindi).
Members practise his teachings daily (protection, meditation, yoga), learn his
understanding of spiritual topics, share their daily seva, and can talk to Swamiji
one-to-one as paid members. Moksha Points reward daily presence and participation and
unlock deeper features over time.

The current codebase is a UI prototype: every screen reads from an in-memory
`OjasRepository` filled with invented content. This project turns it into a real
product: real users, real content from the Ojas Healing channel, real persistence,
real payments, no mock data anywhere.

## 2. Goals

- Real sign-in (Google, Phone OTP, Email/Password) and a persisted user profile.
- Every screen backed by Firebase; no hardcoded content, users, dates or replies.
- Spiritual activities play the real Ojas Healing videos inside the app.
- Moksha Points and levels computed on the server so they cannot be faked.
- Admin (Swamiji's team) manages content from inside the app: schedule Thoughts,
  attach YouTube videos to sections, moderate posts/comments, verify goals.
- UI in Hindi, Gujarati and English, chosen by the user.
- One consistent type scale and spacing system across the app (see §8).
- Paid membership and one-to-one chat through Google Play Billing.
- Ready for Play Store release: signed release build, Data Safety, policy compliance.

## 3. Non-goals (this project)

- iOS app and web admin portal.
- Hosting our own video library: video stays on YouTube.
- AI-generated answers. Swamiji's chat replies come from Swamiji or an assigned senior
  member, never from the app (the current canned `generateSwamijiResponse` is removed).

## 4. Anti-goals (would be failure even if it "works")

- Any screen showing invented people, posts, quotes or answers.
- Points or paid access that a user can grant themselves from the client.
- Shipping with open Firestore rules.
- Personal call / chat content readable by anyone other than the two participants and admins.

## 5. Constraints

- Android, Kotlin, Jetpack Compose, Material 3 (existing stack). minSdk 24, targetSdk 36.
- Firebase project: `ojas-family-app` (created 2026-10-29, account yashkumar03111997@gmail.com),
  Blaze plan. Firestore and Functions in `asia-south1` (Mumbai).
- YouTube Terms: embedded player only, playback must stop when the app is in the
  background. Meditation audio that must keep playing with the screen off is hosted
  in Firebase Storage instead (see §7.4).
- Google Play policy: digital subscriptions and in-app digital purchases must use Play Billing.
- `applicationId` is currently `com.aistudio.ojasfamily.spflqx` (AI Studio leftover). It cannot
  change after the first Play release, so it is changed now — see §11 Q1.

## 6. Architecture

- **App layers:** `ui` (Compose screens + ViewModels) → `domain` (use cases, level/permission rules
  shared with UI) → `data` (repositories over Firebase). One repository per area
  (Auth, Profile, Thought, Community, Activities, Content, Chat, Billing, Notifications).
- **DI:** Hilt. **Navigation:** Navigation Compose with type-safe routes; bottom bar keeps the 5 tabs
  (Community, Spiritual, Home/Thought, Ask, Profile), Home is the start destination.
- **State:** ViewModels expose `StateFlow<UiState>` with explicit Loading / Empty / Error / Content.
  Every list has a designed empty state and error retry. Firestore offline cache on.
- **Backend:** Firebase Auth, Cloud Firestore, Cloud Storage, Cloud Functions (TypeScript, Node 22),
  Cloud Messaging, App Check (Play Integrity), Crashlytics, Analytics, Remote Config.
- **Repo layout:** `app/` (Android), `firebase/` (`firebase.json`, `firestore.rules`,
  `storage.rules`, `firestore.indexes.json`, `functions/`). CI builds the APK and runs
  Functions unit tests + Firestore rules tests against the emulator.

## 7. Feature design

### 7.1 Auth, onboarding, profile
- First launch: language picker → sign-in (Google / Phone OTP / Email) → profile setup.
- Profile: photo (Storage), name, date of birth (only age shown), gender, country → state → city.
- Location data: bundled countries + states JSON (offline, incremental search);
  India cities/villages from a bundled dataset; other countries free-text city.
- Settings: theme (persisted in DataStore), hide profile, hide goals, language, notification toggles,
  morning/night practice reminder times, sign out, delete account (Play requirement).

### 7.2 Moksha Points and levels (server-authoritative)
- Clients never write `mokshaPoints`, `userLevel`, `memberLevel` or `paidUntil`; rules forbid it.
- Cloud Functions award points on Firestore triggers:
  daily open (callable, once per IST calendar day), like (only on content published today IST,
  once per user per item, never re-awarded after unlike), comment, question asked.
- A ledger `users/{uid}/pointEvents/{id}` records every award (idempotent by event key)
  and drives the Paytm-style coin animation on the client.
- Level recomputed by Function after every award and on a daily schedule.
- **All thresholds live in a Firestore `config/levels` document**, editable by admin, so
  rules like "Level 4 = 365 days + 1,00,000 points" can be tuned without an app update.
- Anti-farming: daily cap per action type (in config, default 50 likes / 20 comments / 5 questions).

### 7.3 Content from the Ojas Healing channel
- `content/{id}`: `{ section, youtubeVideoId, title{hi,gu,en}, description, durationSec,
  minUserLevel, minMemberLevel, order, published }`.
- Sections: blue-light meditation, quick protection, morning practice, night practice,
  yoga & pranayama, other, understanding.
- Admin adds a video by pasting a YouTube URL; title/thumbnail fetched via YouTube oEmbed.
- Initial seed (admin confirms): Protection Vidhi playlist → Quick Protection; All about Meditation →
  Meditation; Patanjali Yoga Sutras + Daily Yoga → Yoga; Spiritual Encyclopedia (30 parts),
  Body Chakras & Kundalini, Chakras Shorts → Understanding; Shiva Sutras, Vigyan Bhairav Tantra,
  Self Healing Course → Other. QA by Anand Swaroop → linked from Community Q&A.
- Player: `android-youtube-player` (embedded IFrame player, ToS-compliant).

### 7.4 Spiritual practices
- Morning / night practice: audio guided sessions in Storage, played with Media3 + foreground
  media notification so they work with the screen off; optional companion YouTube video.
- Completing a session logs `users/{uid}/practiceLog/{date}` (drives 21-day goals).
- Reminders: exact local alarms at the user's chosen times (WorkManager fallback), plus FCM.

### 7.5 Thought of the Day (Home)
- `thoughts/{yyyy-MM-dd}` written by admin, can be scheduled a month or more ahead.
- Client shows today plus the previous 6 days only (rules also deny older reads for non-admins).
- Likes `thoughts/{date}/likes/{uid}`, comments `thoughts/{date}/comments/{id}`; counters
  maintained by Functions. Images/links in comments allowed from the level set in config and
  go to `pending` until admin approves.

### 7.6 Community
- Q&A: `questions/{id}`, answers subcollection; Swamiji / senior-member answers are badged.
- Daily Activities (paid members): `dailyPosts/{id}`; posting needs Member Level 3.
- Announcements: admin-only write.
- Mentions (`@name`) resolved to uid and notified.
- Report post / block user (Play UGC policy requirement) + admin moderation queue.

### 7.7 Ask
- Understanding: content items of section `understanding` plus admin-written articles.
- Personal chat: `chats/{chatId}/messages` between member and Swamiji/assigned senior member;
  access gated by an active chat entitlement (weekly one-time or monthly subscription).
- Personal call: deferred to a later phase (§10, phase 5); provider decision in §11.

### 7.8 Goals
- `goals/{id}` defined by admin (cook, wear assigned clothes, household work, yoga, 21-day morning,
  21-day morning+night, plant a tree, energy-healing change). App suggests one goal at a time based
  on level and history.
- Photo-proof goals are submitted with an image → admin verifies → Function awards points and marks achieved.
  21-day goals complete automatically from `practiceLog`.

### 7.9 Payments
- Play Billing subscriptions: `membership_monthly` (₹100/month), `chat_monthly`, `call_monthly`.
  One-time products: `chat_week`, `call_week`.
- Purchase token sent to a callable Function, verified with the Google Play Developer API,
  entitlement written to `users/{uid}/entitlements`. RTDN (Pub/Sub) keeps renewals/cancellations in sync.
- Member level derived from continuous paid months.

### 7.10 Notifications
- FCM per-user token; Functions send for: announcements, thought of the day, new community post,
  interactions on my posts, mentions, daily-activity posts/replies/mentions.
- Each category respects the user's toggle stored in `users/{uid}.notificationPrefs`; master switch.

## 8. Visual system

- **Fonts:** Noto Sans (Latin), Noto Sans Devanagari (Hindi), Noto Sans Gujarati — bundled, so all
  three scripts share metrics and weight. Two weights only: Regular (400) and SemiBold (600).
- **Type scale (all text goes through `MaterialTheme.typography`, no raw `fontSize`):**
  Display 32/40 (points, big numbers), Title 20/28, Body 16/24, Label 14/20, Caption 12/16.
  Devanagari/Gujarati get +2sp line height for matras.
- **Spacing:** 4/8-pt grid tokens (`Spacing.xs=4, s=8, m=12, l=16, xl=24, xxl=32`); 16dp screen
  gutter; 24dp card padding; touch targets ≥ 48dp.
- **Color:** existing saffron/gold/indigo palette kept as brand; 60/30/10 — neutral surfaces,
  text, saffron accent only for primary actions and points. Contrast ≥ 4.5:1 checked for both themes.
- **Motion:** coin-to-counter on point awards, full burst for milestones (already built).
- Components: one `OjasCard`, `SectionHeader`, `EmptyState`, `ErrorState`, `VideoTile`,
  `LevelLockBadge` used by every screen so alignment is consistent.

## 9. Decisions on contradictions in the original spec (my calls — please check)

| Spec conflict | Decision |
|---|---|
| Understanding: Level 1 "can read" vs 4.1 "Level 2" | Level 2 (as 4.1). Configurable. |
| Morning/Night practice "level 3" vs permissions "Level 4 or Member 1" | Free Level 4 or Member Level 1 (permission section wins). |
| Yoga (2.5) / Other (2.6) — no level given | Open to all for public YouTube videos; guided Storage sessions follow daily-practice rule. |
| Chat/Call "user level 2" vs "Member level 2" | Any user at Level 2+ can buy a weekly pack; monthly plans need Member Level 2 (chat) / 3 (anytime call), per spec. |
| Like points on community posts | Only on items published today (IST), once per item. |
| Paid user stops paying | Falls back to their free User Level; paid month count pauses (not reset) — resumes on renewal. |

## 10. Delivery phases

Each phase ends with a signed APK from CI and a working build on a device.

1. **Foundation:** applicationId rename, Hilt, navigation, type scale + fonts + spacing tokens,
   hi/gu/en strings, Firebase wiring, Auth + onboarding + profile, Firestore/Storage rules,
   Functions skeleton with points ledger and levels, remove `OjasRepository` mock data.
2. **Content:** content collection, admin "add YouTube video", YouTube player, Spiritual tab,
   Understanding, seed from the channel playlists.
3. **Social:** Thought of the Day (admin scheduling), Community Q&A, announcements, likes/comments
   with server points, moderation, report/block, goals.
4. **Notifications & practices:** FCM categories, reminders, morning/night audio player, practice log.
5. **Payments:** Play Billing, server verification, membership levels, Daily Activities, personal chat.
6. **Calls:** one-to-one calls (provider per §11).
7. **Release:** release signing in CI, Crashlytics, Data Safety form, privacy policy, store listing.

## 11. Open questions (owner: you)

1. **Package name:** proposed `com.ojashealing.family`. OK?
2. **Play Console:** is there a Google Play developer account for Ojas Healing? Needed for Billing (phase 5).
3. **Admins:** which Google accounts are admins / Swamiji / senior members?
4. **Call provider (phase 6):** Agora (paid per minute, easiest) vs self-hosted WebRTC (cheaper, more work).
5. **Morning/night audio:** will Swamiji's team provide audio files, or should these sessions be YouTube-only?

## 12. Left to the builder

Exact screen layouts within the visual system; Firestore index design; Function code structure;
test strategy per phase (unit for rules/levels, emulator tests for rules and Functions,
Compose UI tests for critical flows).
