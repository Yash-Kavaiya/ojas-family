# Phase 3a — Thought of the Day and Community Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real Thought of the Day (Home) and Community (Q&A + announcements) with likes, comments, answers and server-awarded Moksha Points, plus report/moderation.

**Architecture:** Firestore collections written by clients under level-checking rules; Cloud Functions Firestore triggers (2nd gen) keep counters and award points through the existing `awardPoints` ledger, so points stay server-only. Admins schedule thoughts and post announcements from inside the app; comments containing links wait for admin approval.

**Tech Stack:** Phase 1–2 stack; `firebase-functions/v2/firestore` triggers.

**Spec:** `docs/superpowers/specs/2026-10-29-ojas-family-production-design.md` (§7.2, §7.5, §7.6, §9)

## Global Constraints

- "Today" is the IST day (`istDay`). A thought's doc id is its IST date `yyyy-MM-dd` with field `publishAt` (timestamp, 00:00 IST). Non-admins read a thought only when `publishAt <= now` and `publishAt > now − 7 days` (rules); the app shows today + previous 6.
- Points (all `milestone=false`, small coin animation): like on a thought/question published **today IST** +1 (`like:thought:<id>` / `like:question:<id>`, never re-awarded after unlike); comment on a thought +1 (`comment:<thoughtId>:<commentId>`); question asked +1 (`question:<id>`); answer +1 (`answer:<qid>:<aid>`). Daily caps from `config/levels.dailyCaps` (like 50, comment 20, question 5) counted per IST day; over-cap actions still post but earn nothing.
- Who may act (rules use `users/{uid}` levels; staff bypass): like — `userLevel >= 2 || memberLevel >= 1`; comment/answer/ask — `userLevel >= 3 || memberLevel >= 2`; post thought/announcement — admin only.
- Text limits: comment/answer 1000 chars, question title 150, body 2000. Text containing `http://`, `https://` or `www.` is stored with `status: "pending"` and only the author and admins see it until approved; otherwise `status: "visible"`.
- Reports: any signed-in user creates `reports/{id}` `{targetPath, reason, reporterUid}`; admins read/delete. Blocking: `users/{uid}/blocked/{otherUid}` owner-only; the app hides blocked authors' content.
- Author snapshot on every post: `authorUid, authorName, authorPhoto, authorRole` copied from the profile at write time (rules check `authorUid == request.auth.uid`; `authorRole` must equal the caller's claim role or `member`).
- All strings en/hi/gu; typography + `Spacing` only.

## Review Focus

1. **Like, unlike, like again on today's thought** — exactly one point. Test in Task 2 (`relike earns nothing`).
2. **Liking yesterday's thought** — like counts, no point. Test in Task 2.
3. **51st like of the IST day** — no point, like still recorded. Test in Task 2 (`daily cap`).
4. **L2 user tries to comment by writing directly to Firestore** — rejected by rules. Test in Task 1.
5. **Comment with a link** — hidden from others until approved. Test in Task 1 (rules: pending comment unreadable by others) + Task 2 (status set by client is verified: `visible` with a link is rejected).

---

### Task 1: Rules for thoughts, questions, announcements, reports, blocks

**Files:** Modify `firebase/firestore.rules`; Test `firebase/rules-test/rules.test.ts`

- [ ] **Step 1: Failing tests** — `member reads today's thought`; `member cannot read a thought older than 7 days`; `member cannot read a scheduled future thought`; `admin reads future thought`; `L1 cannot like`; `L2 can like own uid only`; `L2 cannot comment`; `L3 can comment visible text`; `comment containing a link must be pending`; `pending comment unreadable by other members, readable by author and admin`; `L3 can ask a question`; `answer by member with authorRole swamiji is rejected`; `non-admin cannot post announcement`; `anyone signed in can report; only admin reads reports`; `blocked list is owner-only`.
- [ ] **Step 2:** emulator → FAIL. **Step 3:** implement with helper functions `canLike()`, `canComment()`, `hasLink(text)` (regex `.*(https?://|www\\.).*`). **Step 4:** → PASS. **Step 5:** commit, deploy rules.

### Task 2: Functions — counters and points for likes, comments, questions, answers

**Files:** Create `firebase/functions/src/social.ts`, `test/social.test.ts`; Modify `src/points.ts` (daily cap), `src/index.ts`

**Interfaces:**
- Produces: `awardCapped(db, uid, action: "like"|"comment"|"question", award: Award, now: Date): Promise<boolean>` (reads/increments `users/{uid}/counters/{istDay}` in the same transaction); handlers `onThoughtLike(db, thoughtId, uid, now)`, `onThoughtUnlike(db, thoughtId)`, `onThoughtComment(db, thoughtId, commentId, data, now)`, `onQuestion(db, qid, data, now)`, `onQuestionLike/Unlike`, `onAnswer(db, qid, aid, data, now)`; v2 triggers wiring them.

- [ ] **Step 1: Failing tests** — `like on today's thought: likeCount 1 and +1 point`; `relike earns nothing` (create, delete, create → points 1, likeCount 1); `like on yesterday's thought: no point`; `daily cap` (cap 2 in config: 3 likes → 2 points); `comment increments commentCount and awards +1`; `pending comment awards nothing until approved` (approval = status change to visible awards then); `question awards +1 and answer awards +1 to answerer`.
- [ ] **Step 2:** emulator → FAIL. **Step 3:** implement. **Step 4:** → PASS. **Step 5:** commit, deploy.

### Task 3: Data layer for social

**Files:** Create `data/social/{SocialModels,ThoughtRepository,CommunityRepository,ModerationRepository}.kt`; Modify `di/AppContainer.kt`
- Test: `app/src/test/java/com/ojashealing/family/data/social/SocialRulesTest.kt`

**Interfaces:**
- Produces: `data class Author(uid, name, photo, role: Role)`; `Thought(id, publishAt: Long, title, text, likeCount, commentCount, likedByMe)`; `Comment(id, author, text, createdAt, status)`; `Question(id, author, title, body, createdAt, likeCount, answerCount, likedByMe)`; `Answer(id, author, text, createdAt, status)`; `Announcement(id, title, body, createdAt)`; `fun containsLink(text: String): Boolean`; `fun visibleThoughtIds(todayIst: LocalDateLike): List<String>` (today and 6 previous, newest first); repositories with `observe…`, `toggleLike`, `addComment`, `ask`, `answer`, `postThought(date, title, text)` (admin), `postAnnouncement`, `report(path, reason)`, `block(uid)`, `observePending()`, `approve(path)`, `remove(path)`.
- [ ] **Step 1: Failing tests** — `containsLink` true for `https://x`, `www.x.com`, false for `hello`; `visibleThoughtIds(2026-03-01)` → `["2026-03-01","2026-02-28",…,"2026-02-23"]` (7 ids, crosses month).
- [ ] **Step 2:** → FAIL. **Step 3:** implement over Firestore; the client sets `status` by `containsLink`. **Step 4:** → PASS. **Step 5:** commit.

### Task 4: Home — Thought of the Day

**Files:** Create `ui/thought/{ThoughtViewModel,HomeScreen,ThoughtDetailScreen,ComposeThoughtScreen}.kt`; Modify `ui/main/MainShell.kt`, strings
- Test: `ThoughtViewModelTest` — `selectsTodayByDefault`, `likeHiddenWhenViewerCannotLike`, `commentBoxHiddenBelowLevel3`.
- [ ] Steps: failing tests → implement (day chips for the 7 days, card with title/text, like button with count, comment list + composer, lock hints for levels; admins get "Schedule thought" with a date picker up to 60 days ahead) → pass → commit.

### Task 5: Community — Q&A and announcements

**Files:** Create `ui/community/{CommunityViewModel,CommunityScreen,QuestionDetailScreen,AskQuestionScreen}.kt`; Modify `ui/main/MainShell.kt`, strings
- Test: `CommunityViewModelTest` — `blockedAuthorsHidden`, `askHiddenBelowLevel3`, `staffAnswersFirst`.
- [ ] Steps: failing tests → implement (tabs Q&A / Announcements; question list newest first; detail with answers, Swamiji/senior badges, answer composer; report + block in an overflow menu; admins post announcements) → pass → commit.

### Task 6: Moderation queue

**Files:** Create `ui/admin/{ModerationScreen,ModerationViewModel}.kt`; Modify `ui/screens/ProfileTab.kt`
- Test: `ModerationViewModelTest` — `approveCallsRepository`, `listsPendingAndReports`.
- [ ] Steps: failing tests → implement (pending comments/answers with approve/remove; reports with open target/remove/dismiss) → pass → commit.

### Task 7: Ship

- [ ] Deploy rules + functions; full suites green; merge to `main`, push, CI green.
