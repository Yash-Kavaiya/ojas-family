# Screen Help Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A top-bar help icon explains the current screen, and each screen's help opens by itself once after install.

**Architecture:** `ui/help/ScreenHelp.kt` holds the topics, the tab-to-topic mapping, the phone-only "seen" store and the bottom sheet. `MainShell` picks the current topic and opens it. Text lives in the three `strings.xml` files, and a test checks its numbers against `PlanAccess.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, SharedPreferences.

**Spec:** `docs/superpowers/specs/2026-11-21-screen-help-design.md`

## Global Constraints

- Never run `tools/gen_strings.py`. Edit the three `strings.xml` files by hand.
- Seen state uses SharedPreferences file `screen_help`, which the backup rules do not include.
- Access wording says "7 दिन बाद", never "active".
- No git commits unless the owner asks.

## Review Focus

- Translated access text with a number that differs from the plan table (Task 1 test, all three languages).
- A screen's help reopening by itself after it was seen (Task 1 test).
- Help opening over a sub-screen, or before the profile loads (Task 1 test).
- Every inner tab reaching its own topic (Task 1 test).
- A long topic on a small phone must scroll inside the sheet (manual check).

---

### Task 1: Topics, first-time rule and text

**Files:**
- Create: `app/src/main/java/com/ojashealing/family/ui/help/ScreenHelp.kt`
- Create: `app/src/test/java/com/ojashealing/family/ui/help/ScreenHelpTest.kt`
- Modify: the three `strings.xml`

**Interfaces:**
- Produces: `enum class HelpTopic`, `data class HelpRow(label: Int, text: Int)`
- Produces: `helpTopicFor(tab: MainTab, communityTab: Int, practicePart: Int, askPart: Int): HelpTopic`
- Produces: `shouldAutoOpenHelp(topic: HelpTopic, seen: Set<String>, onTabs: Boolean, profileLoaded: Boolean, sheetOpen: Boolean): Boolean`
- Produces: `class HelpSeen(context)` with `seen: Set<String>` and `mark(topic)`

- [ ] **Step 1:** Write tests for the mapping, the auto-open rule and each access string's numbers in all languages.
- [ ] **Step 2:** Run `testDebugUnitTest --tests '*ScreenHelpTest'`. Expected: FAIL, unresolved.
- [ ] **Step 3:** Implement the file and add the strings.
- [ ] **Step 4:** Run the same tests. Expected: PASS.

### Task 2: Icon, sheet and first-time opening

**Files:**
- Modify: `ui/components/OjasTopBar.kt`, `ui/main/MainShell.kt`, `ui/help/ScreenHelp.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces: `ScreenHelpSheet(topic: HelpTopic, onClose: () -> Unit)`, and `onOpenHelp: (() -> Unit)?` on `OjasTopBar`.

- [ ] **Step 1:** Add the scrolling sheet with a Got it button, and the help icon beside the bell.
- [ ] **Step 2:** In `MainShell`, open the sheet from the icon, and auto-open by `shouldAutoOpenHelp`, marking the topic seen when it opens.
- [ ] **Step 3:** Run `testDebugUnitTest assembleDebug`. Expected: BUILD SUCCESSFUL.
