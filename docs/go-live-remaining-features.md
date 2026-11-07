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
