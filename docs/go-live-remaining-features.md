# Go-live steps: practices, Daily Activities, payments, chat, calls, notifications

Everything below is built and tested on the `feat/remaining-features` branch. These steps change the
live Firebase project or need accounts only the owner has, so they are done by hand.

## 1. Deploy the server (Firebase project `ojas-family-app`)

```sh
cd firebase
firebase deploy --only firestore:rules,firestore:indexes,storage,functions --project ojas-family-app
```

The first functions deploy turns on Cloud Scheduler (daily Thought push at 06:00 IST, daily paid-day count
at 00:05 IST) and creates the Pub/Sub topic `play-rtdn`. Indexes can take a few minutes to build; the
Daily Activities feed and the senior-member chat inbox wait for them.

## 2. New level thresholds and the video library

```sh
cd firebase/functions
gcloud auth application-default login
npm run seed:config              # Level 3 = 90 days + 200 points, Level 4 = 365 days + 10,000 points
npx ts-node scripts/seedContent.ts   # 110 Ojas Healing videos into the 7 sections; skips ones already there
```

Admins can still add, move or unpublish videos in the app (Profile → Manage videos).

## 3. Roles

In the app, an admin opens **Profile → Members and access**, finds Swamiji's account and sets the role
to **Swamiji**. Members' calls and chats go to that account (it is saved as `config/staff.swamijiUid`).
Senior members get the **Senior member** role; Swamiji assigns chats to them from the chat screen.

## 4. Payments (Google Play)

Until Play Billing is live, the app says online payment is not available yet, and an admin can switch
access on by hand: **Members and access → Give access** (membership, chat or calls, for N days).

To take payments in the app:

1. Play Console → the app → **Monetize → Products**:
   - Subscriptions: `membership_monthly` (₹100/month), `chat_monthly`, `call_monthly`
   - One-time products (consumable): `chat_week`, `call_week`
2. **Setup → API access**: link the Google Cloud project `ojas-family-app`, then in **Users and
   permissions** invite the Functions service account (`ojas-family-app@appspot.gserviceaccount.com`)
   with *View financial data* and *Manage orders and subscriptions*.
3. **Monetization setup → Real-time developer notifications**: topic
   `projects/ojas-family-app/topics/play-rtdn`.
4. Upload a signed build to an internal testing track; prices appear in the app from then on.

Member Level comes from paid days: Level 1 from the first day, Level 2 after 90 paid days, Level 3 after
180. Days stop counting while unpaid and continue on renewal.

## 5. Calls

Calls are peer-to-peer audio (WebRTC) using Google's public STUN server. On some mobile networks a
direct connection is impossible; if members report calls that never connect, add a TURN relay
(e.g. a paid service) as a Firestore document `config/turn`:

```json
{ "urls": ["turn:turn.example.com:3478"], "username": "…", "credential": "…" }
```

No app update is needed.

## 6. Check on a phone

- Free user: likes locked until Level 2, Daily tab shows "for members", morning/night videos locked.
- Give yourself membership (step 4, admin grant): Daily tab opens, morning/night show
  "I did today's practice", photo button appears in comments.
- Two phones: member with a call pack taps **Ask → Call now**; Swamiji's phone rings (also when closed).
- Settings → notifications: switch off "Thought of the Day" and check no 06:00 push arrives next day.

## 7. UI, practice, Moksha Points and Ask Swamiji AI upgrade (branch `feat/wow-ui-ai-points`)

Built and tested locally. Each step below changes the live project, so it needs the owner's go-ahead.

1. **Deploy** rules, indexes and functions (step 1 above).
2. **Ask Swamiji AI model: NVIDIA API** (owner decision 2026-11-13; no Gemini, no Vertex AI and no
   model on the phone). The `askSwamiji` function finds the matching passages with keyword search
   and calls NVIDIA's OpenAI-compatible API (`nvidia/nemotron-3-ultra-550b-a55b`, changeable on the
   admin site). The key is a server secret; the app never sees it, and it must never be committed:
   ```sh
   cd firebase && firebase functions:secrets:set NVIDIA_API_KEY --project ojas-family-app
   firebase deploy --only functions,storage,hosting --project ojas-family-app
   ```
   `askSwamiji` needs this secret to deploy. Every member gets 5 questions a day (admin site → Ask
   Swamiji AI). Questions with no matching teaching, or with crisis words, get a fixed reply and
   don't reach the model.
3. **Knowledge base for Ask Swamiji AI**: about 2,400 passages in Firestore `knowledge/`, published
   as `ai/knowledge.json` in Storage (only the server reads it). The notes on the Ojas Healing
   videos, in English, Hindi and Gujarati, were written once in November 2026 and are kept; the
   script no longer calls any AI service. To change facts, edit
   `firebase/functions/knowledge/ojas-basics.md` (or add another `.md` there, one `## ` heading per
   passage) and publish:
   ```sh
   cd firebase/functions
   gcloud auth application-default login
   GOOGLE_CLOUD_QUOTA_PROJECT=ojas-family-app npx ts-node scripts/buildKnowledge.ts
   ```
   Admin site → **Ask Swamiji AI** shows members' 👍/👎 on answers, the daily question allowance, the
   model name and an on/off switch.
4. **Turn on embedding on the Ojas Healing YouTube videos first.** Checked 2026-11-13: YouTube answers
   "embedding disabled" for every library video tested, including Vidhi 1 (KPRvRrJgmVk), so they
   can't play inside the app (the Spiritual library and Quick Protection show "Open in YouTube"
   instead, and no practice points can be earned for them). In YouTube Studio → Content → select all
   → Edit → **Allow embedding** → Update.
   **Quick Protection = Protection Vidhi 1 from YouTube** (no upload). Put the video's length in seconds
   (the server checks the member really listened to 90% of it):
   ```sh
   npx ts-node scripts/uploadPracticeMedia.ts ../../videos/streaming --youtube quick_protection=KPRvRrJgmVk:<seconds>
   ```
   The other three practices keep their uploaded files. The Practice tab now shows only the one
   guided meditation per section; the other videos stay in the Spiritual library.
5. **Moksha Points**: amounts and gifting rules are in `config/points` (defaults apply until saved).
   Admin site → **Moksha Points**, or in the app (Swamiji/admin) → Moksha Points → How it works →
   Edit amounts. Defaults: daily open 1, each practice 2, all four in a day +3, every 7-day streak +10,
   like/comment/question/answer 1, each profile item 5 and 100% +25, first AI question of the day 1;
   gifts from Level 2, up to 100 per gift and 100 a day. Points received from members never raise a
   Level (`earnedPoints`); existing accounts start with earned = their current balance automatically.

Check on a phone: Practice tab streak card and the immersive player (wave moves with the voice, blue
light pours down, Show video works); Moksha Points → How it works opens on the first visit; send
points between two test accounts and see the sender's Level stay the same; as Swamiji, open a
member's profile → Manage points; fill the profile to 100% and see the bonus; Ask Swamiji AI answers
with videos under the answer.
