# Ojas Family: full product spec

Date: 2026-11-15
Status: describes the app as built on branch `feat/freedom-practice`. It replaces
`2026-10-29-ojas-family-production-design.md` as the current reference; that file stays as the
record of the original design and its decisions.

## 1. What the app is

Ojas Family is the Android companion app of Swami Anand Swaroop's Ojas Healing community
(https://www.youtube.com/@OjasHealing). Members read a daily thought, do guided practices, take up
goals, share in a community, and reach Swamiji and his team by chat and call. Freedom Points and
levels reward steady practice and open more of the app over time.

Languages: English, Hindi and Gujarati, chosen by the member and changeable in Settings.

## 2. Platform and architecture

| Area | Choice |
|---|---|
| App | Android, Kotlin, Jetpack Compose, Material 3. minSdk 24, targetSdk 36. Package `com.ojashealing.family`. |
| Layers | `ui` (screens and ViewModels) → `domain` (levels, permissions) → `data` (one repository per area). Hand-written `AppContainer` for dependencies. |
| Backend | Firebase project `ojas-family-app`: Auth, Firestore, Storage, Cloud Functions (TypeScript) in `asia-south1`, Cloud Messaging, App Check. |
| Video | YouTube embedded player for channel videos; guided practice audio/video in Storage. |
| Calls | WebRTC, peer to peer, Firestore signalling, optional TURN relay from `config/turn`. |
| AI | Ask Swamiji AI answers through the NVIDIA API from the server; no model on the phone. |
| Payments | Google Play Billing, verified on the server. |
| Admin | In-app admin screens, plus the admin website in `firebase/admin-web`. |

Rule for the whole system: the phone never decides points, levels, membership or approvals. Those
are written only by Cloud Functions or by an admin, and Firestore rules refuse anything else.

## 3. People, roles and levels

**Roles:** member, senior member, Swamiji, admin. Senior members, Swamiji and admins are "staff".

**User Level (free), from active days and earned Freedom Points** (`config/levels`, editable):

| Level | Needs | Opens |
|---|---|---|
| 1 | new member | Thought of the Day, guided meditations, public videos |
| 2 | 7 days and 7 points | likes, Understanding, weekly chat and call packs, sending points |
| 3 | 90 days and 200 points | comments, questions and answers, hiding the profile |
| 4 | 365 days and 10,000 points | morning and night practices |

**Member Level (paid), from days of paid membership:**

| Level | Needs | Opens |
|---|---|---|
| 1 | active membership | Level 2 rights at once, morning and night practices, Activity (view, like, reply), photo activities in Freedom Practice, pictures in posts, chat |
| 2 | 90 paid days | Level 3 rights, audio calls, monthly chat |
| 3 | 180 paid days | posting in Activity, video calls, other members' profiles, anytime calls, face photo in the opening animation |

When membership lapses the member falls back to their free User Level; paid days pause and resume
on renewal.

## 4. Screens

The app has five tabs. Home is the start tab.

### 4.1 Opening animation
A blue healing light crosses the screen top to bottom: 5 seconds on first install (the lotus logo
and name rest afterwards), 3 seconds on every later open. A tap skips it.

A Member Level 3 member (or staff) can upload a photo of their face from Profile. After an admin
approves it, that member's own opening animation shows the photo large under the logo and the
light passes over it. Everyone else sees the plain animation. A new upload waits for approval
again, and until then the plain animation returns.

### 4.2 Onboarding
Language → sign-in (Google, phone OTP or email and password) → profile setup (name, date of birth,
gender, country, state, city or village, photo).

### 4.3 Home
- Greeting, practice nudge with the current streak, and the Thought of the Day (today and the six
  days before) with likes and comments.
- "Know more" box: About Ojas Family, About Swamiji, **App help**, **Legal**, the YouTube channel
  and any links admins add. App help explains how to use every part of the app; Legal is the
  disclaimer. All four texts come from `config/about` when an admin has written them (per
  language), otherwise from the wording built into the app.

### 4.4 Practice
Two sections at the top of the tab.

**Daily Practice (दैनिक साधना).** The streak card, four guided practices (Blue Healing Light,
Quick Protection, Morning, Night) and the channel videos by section. Morning and night need Level 4
or membership. Finishing a practice earns points once a day for each; the streak counts days in a
row with at least one practice.

**Freedom Practice (मुक्ति साधना).** Every goal, in two parts. Each goal has a level: Easy, Medium
or Hard.

1. *Sadhana goals.* The member chooses a goal from the list and taps "I did it today" each day.
   - Each goal needs a set number of days in a row (for example 7, 21 or 40). A missed day starts
     the count again from 1.
   - On the last day the goal is achieved and its points are added.
   - Only Easy goals show at first. The next level opens after two goals of the level before are
     achieved (or all of them when that level has fewer than two).
   - The two 21-day practice goals (morning; morning and night) sit in this list and complete on
     their own from the practice log.
   - Open to every member.
2. *Activities.* The member does the activity and shares a photo with an optional note.
   - The photo is posted at once in Community › Activity, labelled with the goal's name.
   - Points are added only when an admin approves the photo. If the admin rejects it, the post is
     taken down and the member can send another.
   - A note with a link waits for an admin before the post shows.
   - For paid members (Member Level 1 and above) and staff. Free members see the activities locked
     with a link to membership.

Goals are defined in `goals/{id}` (seeded from `firebase/functions/scripts/goals.json`): `kind`
(`check`, `photo`, `practice_morning_21`, `practice_both_21`), `level`, `days`, `reward`, `order`,
`active`, and title and description in three languages.

### 4.5 Community
Three sections, in this order; the tab opens on Activity.

- **Activity:** photos from members (their own posts from Member Level 3, and goal photos from
  Freedom Practice). Paid members view, like and reply. Free members see that it is for members.
- **Announcements:** from admins, with picture, audio or video; members like and comment.
- **Q&A:** questions and answers; staff answers are badged.

Everywhere: mentions, report a post, block a member, and an admin moderation queue. Posts with a
picture or a link wait for approval.

### 4.6 Ask
- **Ask Swamiji AI:** answers only from Swamiji's recorded teachings, labelled as an AI guide, with
  a daily allowance of questions.
- **Understanding:** teaching videos, from Level 2.
- **Chat** with Swamiji's team and with admins, including pictures, PDFs and voice notes.
- **Calls:** audio and video, scheduled calls with approval and reminders, call history.

### 4.7 Profile
- Identity card, profile completion with the points each missing item earns, Freedom Points, level
  and membership.
- **Freedom Practice card**, filled in on its own: the sadhana goals being done now with "day N of
  D", photos waiting for review, and everything achieved (sadhana goals with their number of days).
  A button opens Freedom Practice.
- Face photo card (Member Level 3).
- Settings: language, theme, reminders, notification switches, hide profile, hide goals, sign out,
  delete account.
- Other members' profiles (viewable from Member Level 3) show achieved goals and the sadhana in
  progress with its days, unless the owner hides their goals or profile.

## 5. Freedom Points

Formerly "Moksha Points". Hindi: मुक्ति पॉइंट; Gujarati: મુક્તિ પોઇન્ટ. The data field is still
`mokshaPoints`.

Default amounts (`config/points`, editable by admins and Swamiji):

| Action | Points |
|---|---|
| Opening the app, once a day | 1 |
| Finishing a guided practice, each once a day | 2 |
| All four practices in one day | +3 |
| Every 7 days in a row with a practice | +10 |
| Like, comment, question, answer | 1 each, with daily limits (50 likes, 20 comments, 5 questions) |
| Each profile item filled in | 5; +25 at 100% |
| First Ask Swamiji AI question of the day | 1 |
| Achieving a goal | the goal's reward (20 to 100 in the seed) |

Every award is recorded in `users/{uid}/pointEvents` under a key that makes it once-only, and drives
the coin animation. Members from Level 2 can gift points to another member (limits per gift and per
day); gifted points can be spent or given again but do not raise the receiver's level. Swamiji and
admins can give, correct and reverse points and pause gifting for an account.

## 6. Membership and purchases

Google Play products: `membership_monthly`, `chat_monthly`, `call_monthly` (subscriptions) and
`chat_week`, `call_week` (one-time; the call pack carries 4 calls). The purchase token goes to the
`verifyPurchase` function, is checked with the Google Play Developer API and becomes an entitlement
in `users/{uid}/entitlements`. Play notifications keep renewals and cancellations in step. Admins
can grant membership, chat or calls for payments taken outside the app.

## 7. Notifications

Data-only pushes; the app writes the visible notification in the member's language.

- Broadcast topics: announcements, Thought of the Day, community posts, Activity posts.
- Personal: replies and likes on my posts, mentions, Activity replies and mentions, chat, calls.
- Each category has a switch in Profile › Settings, with a master switch. Chat and calls obey only
  the master switch.
- Local reminders for the morning and night practice, at times the member sets.

## 8. Admin

**In the app (admins):** content (videos per section), moderation (posts, goal photos, face photos,
reports), members, scheduling the Thought of the Day, announcements.

**Admin website:** dashboard, members and gifts, Freedom Points settings, Ask Swamiji AI, Practice
tab (guided practices), Thought of the Day, videos, moderation (including goal photos and face
photos), announcements, calls, admins, and Settings (levels, daily limits, TURN relay, and the
About, App help and Legal texts).

Goals themselves are edited in `goals.json` and written with `scripts/seedGoals.ts`; there is no
goal editor yet (see §12).

## 9. Data model (Firestore)

| Path | Holds | Written by |
|---|---|---|
| `users/{uid}` | profile; server fields `mokshaPoints`, `earnedPoints`, `userLevel`, `memberLevel`, `role`, `achievedGoalIds`, `goalDays` | member (profile fields only); server |
| `users/{uid}/pointEvents` | points ledger | server |
| `users/{uid}/practiceLog/{day}` | practices done that IST day | server |
| `users/{uid}/goalProgress/{goalId}` | `status` (`submitted`, `approved`, `rejected`, `active`), `photoUrl`, `note`, `streak`, `lastDay` | member (photo submission, paid only); admin (review); server (daily ticks) |
| `users/{uid}/entitlements` | membership, chat, call | server |
| `publicProfiles/{uid}` | what others may see, including `achievedGoals` and `goalDays` | server |
| `goals/{id}` | goal definitions | admin |
| `facePhotos/{uid}` | `photoUrl`, `status` | member (Member Level 3, as `submitted`); admin (review) |
| `thoughts/{day}` | Thought of the Day, likes, comments | admin; members |
| `questions`, `announcements`, `dailyPosts` | community posts with likes, answers, comments, replies | members by level; goal photos by the server |
| `content/{id}` | videos by section | admin |
| `chats`, `adminChats`, `calls`, `callRequests` | one-to-one conversations and calls | the two sides and staff |
| `reports`, `config/*` | reports; levels, points, about, staff, turn | members; admins |

Storage: `users/{uid}/…` (profile, goal and face photos; owner writes, signed-in members read),
`thoughts/`, `announcements/`, `practice/` (admins write), `chats/`, `adminChats/` (the member and
staff), `ai/` (server only).

## 10. Server functions (the ones behind this release)

- `tickGoal` (callable): ticks today for a `check` goal. Checks the goal is active and its level
  is open, counts days in a row by IST day, writes progress and `users/{uid}.goalDays`, and marks
  the goal `approved` when the days are reached.
- `goalProgressWritten` (trigger): posts a submitted goal photo to `dailyPosts/goal_{uid}_{goalId}`,
  removes that post when the photo is rejected, and on approval adds the goal to
  `achievedGoalIds` and awards the reward once.
- `userWritten` (trigger): copies the public part of the profile, including `goalDays`, to
  `publicProfiles`.
- `logPractice`, `startPractice`, `completePractice`: practice log, practice points and the 21-day
  goals.

All other functions (points, levels, community counters and pushes, purchases, chat, calls, AI,
admin) are unchanged in this release.

## 11. Quality bar

- No screen shows invented content. Every list has loading, empty and error states.
- Points, levels, membership and approvals cannot be written from the phone; rules tests cover it.
- Checked on this branch: app compiles, app unit tests pass, security-rules tests and
  Cloud Functions tests pass on the emulator.
- Not yet checked: the new screens on a real phone, and a release (R8) build.

## 12. Decisions made for this release, and what is open

| Topic | Decision | Who |
|---|---|---|
| Points for photo activities | When an admin approves | Owner, 2026-11-15 |
| Who may share photo activities | All paid members | Owner, 2026-11-15 |
| Who writes sadhana goals | Admin list; the member chooses | Owner, 2026-11-15 |
| When a sadhana goal is achieved | A set number of days in a row | Owner, 2026-11-15 |
| Community opens on | Activity | Owner, 2026-11-15 |
| Opening of the next sadhana level | After two goals of the level before | Builder's choice; owner to confirm |
| Level for the face photo | Member Level 3 | Builder's choice; owner to confirm |
| Sadhana goal list | Six sample goals in `goals.json` | Owner will send the real list |
| Legal text | A general disclaimer written by the builder | Owner or their lawyer to review before release |

Open items, not built:
- A goal editor for admins (today goals change through `goals.json` and the seed script).
- Sadhana goals written by the member in their own words.
- Repeating a goal after it is achieved.

## 13. Release steps for this version

1. Deploy Firestore rules and Cloud Functions (new: `tickGoal`).
2. From `firebase/functions`: `npx ts-node scripts/seedGoals.ts` (adds levels to the existing goals
   and the six sadhana goals).
3. Rebuild the Ask Swamiji AI knowledge (`scripts/buildKnowledge.ts`), so the AI knows Freedom
   Points and Freedom Practice.
4. Deploy the admin website.
5. Check on a phone: Practice tab sections, ticking a goal, a goal photo appearing in Activity,
   App help and Legal, and the opening animation with and without a face photo.
6. Build and publish the new app version.
