# Phase 3b — Goals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin-defined goals that members complete with a photo, verified by an admin, awarding Moksha Points and showing on the profile.

**Architecture:** `goals/{id}` (admin-written) and `users/{uid}/goalProgress/{goalId}` (member submits, admin approves). A Firestore trigger awards the goal's reward once on approval through `awardPoints` (milestone burst). 21-day practice goals complete automatically in Phase 4 from the practice log; this phase ships photo goals.

**Spec:** `docs/superpowers/specs/2026-10-29-ojas-family-production-design.md` (§7.8)

## Global Constraints

- Goal: `{ title, description, kind: "photo" | "practice_morning_21" | "practice_both_21", reward: int, order: int, active: bool }`.
- Progress: `{ status: "submitted" | "approved" | "rejected", photoUrl, note, submittedAt, reviewedAt? }`. Members may only write `status: "submitted"`; only admins set `approved`/`rejected`.
- Reward event key `goal:<goalId>`, `milestone: true`; awarded once even if approval toggles.
- Suggested goal = first active goal by `order` whose progress is not `approved` and not `submitted`.
- Seed (spec): cook and post, wear assigned clothes, household work, daily yoga, plant a tree, energy-healing change (photo, 25 pts each); morning practice 21 days (50); morning + night 21 days (100).

## Review Focus

1. **Member writes `approved` on own progress** — rejected (Task 1).
2. **Admin approves twice / un-approves and re-approves** — reward once (Task 2).
3. **Rejected goal resubmitted** — allowed, goes back to submitted (Task 1).
4. **Goal hidden by `hideGoals`** — own profile still shows; others never read progress (rules: owner/admin only) (Task 1).
5. **Photo upload fails** — submission not created, user sees error (Task 4).

### Task 1: Rules — goals and goal progress
- [ ] Failing tests: `members read goals; only admin writes`; `member submits own progress as submitted`; `member cannot set approved`; `member can resubmit after rejection`; `other members cannot read progress`; `admin approves`; `admin lists submitted progress via collection group`. → implement → pass → commit, deploy.

### Task 2: Function — award on approval
- [ ] Failing tests: `approval awards goal reward as milestone`; `second approval awards nothing`; `rejection awards nothing`. → `onGoalProgressWritten(db, uid, goalId, before, after)` + trigger → pass → commit, deploy. Seed the 8 goals.

### Task 3: Data + suggestion logic
- [ ] Failing tests (`GoalsTest`): `suggestsFirstOpenGoalByOrder`; `skipsApprovedAndSubmitted`; `achievedListedNewestFirst`. → `GoalRepository` (observeGoals, observeProgress(uid), submit(uid, goalId, photoUri, note), observeSubmissions() admin, review(uid, goalId, approve)) + `suggestGoal(goals, progress)`, `achieved(goals, progress)` → pass → commit.

### Task 4: UI — goal card, submit screen, profile goals, admin review
- [ ] Failing test: `GoalSubmitViewModelTest.uploadFailureKeepsUserOnScreenWithError`. → Profile: suggested goal card + achieved list (respects own view; `hideGoals` only affects others); submit screen (photo picker, note); Moderation screen gets a "Goal photos" section with approve/reject. → pass → commit.

### Task 5: Ship
- [ ] Full suites green; merge to `main`, push, CI green.
