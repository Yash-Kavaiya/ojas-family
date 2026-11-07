// Ojas Family admin website. Every action goes through the same Firestore rules and Cloud Functions as
// the app, so only accounts with the admin role (custom claim) can change anything.
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  addDoc, arrayRemove, arrayUnion, collection, collectionGroup, deleteDoc, doc, endAt, getDoc, getDocs, getFirestore,
  limit, orderBy, query, serverTimestamp, setDoc, startAt, Timestamp, updateDoc, where,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js";
import { getDownloadURL, getStorage, ref, uploadBytes } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-storage.js";

const app = initializeApp({
  apiKey: "AIzaSyCcegKesVprKs9g2Rs0b060TGuRfOwz-k4",
  authDomain: "ojas-family-app.firebaseapp.com",
  projectId: "ojas-family-app",
  storageBucket: "ojas-family-app.firebasestorage.app",
  messagingSenderId: "207605785922",
  appId: "1:207605785922:web:fa49a503d893ef782d94db",
});
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);
const fns = getFunctions(app, "asia-south1");
const call = (name, data) => httpsCallable(fns, name)(data).then((r) => r.data);

const SECTIONS = {
  blue_light: "Blue Healing Light", quick_protection: "Quick Protection", morning: "Morning practice",
  night: "Night practice", yoga: "Yoga & Pranayama", other: "Other", understanding: "Understanding",
};
const ROLES = { member: "Member", senior_member: "Senior member", swamiji: "Swamiji", admin: "Admin" };

// ---------- helpers ----------
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
// Member-written text is shown as text, never as HTML
const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const view = () => $("#view");
let toastTimer;
function toast(msg, bad = false) {
  const t = $("#toast");
  t.textContent = msg;
  t.style.background = bad ? "var(--bad)" : "";
  t.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add("hidden"), 3500);
}
async function run(btn, fn, okMsg) {
  if (btn) btn.disabled = true;
  try {
    const r = await fn();
    if (okMsg) toast(typeof okMsg === "function" ? okMsg(r) : okMsg);
    return r;
  } catch (e) {
    console.error(e);
    toast(e?.message || "Something went wrong", true);
  } finally {
    if (btn) btn.disabled = false;
  }
}
const istToday = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const addDays = (day, n) => new Date(new Date(`${day}T12:00:00+05:30`).getTime() + n * 86400000).toISOString().slice(0, 10);
const istMidnight = (day) => Timestamp.fromDate(new Date(`${day}T00:00:00+05:30`));
const fmt = (ts) => (ts?.toDate ? ts.toDate().toLocaleString() : "");
const thumb = (id) => `https://i.ytimg.com/vi/${id}/mqdefault.jpg`;

// ---------- sign in ----------
$("#btn-signin").onclick = () => signInWithPopup(auth, new GoogleAuthProvider()).catch((e) => ($("#signin-msg").textContent = e.message));
$("#btn-signout").onclick = () => signOut(auth);

onAuthStateChanged(auth, async (user) => {
  $("#loading").classList.add("hidden");
  if (!user) {
    $("#app").classList.add("hidden");
    $("#signin").classList.remove("hidden");
    return;
  }
  // Emails on the admin list are promoted on first sign-in; fetch a fresh token with the new role
  try {
    const r = await call("syncRole", {});
    if (r?.roleChanged) await user.getIdToken(true);
  } catch (e) { console.warn(e); }
  const claims = (await user.getIdTokenResult()).claims;
  if (claims.role !== "admin") {
    $("#signin-msg").textContent = `${user.email} is not an admin. Ask an admin to add this email on the Admins page.`;
    $("#app").classList.add("hidden");
    $("#signin").classList.remove("hidden");
    await signOut(auth);
    return;
  }
  $("#me-name").textContent = user.email;
  $("#signin").classList.add("hidden");
  $("#app").classList.remove("hidden");
  route();
});

window.addEventListener("hashchange", route);
const PAGES = { dashboard, members, thoughts, content, moderation, announcements, admins, settings };
function route() {
  if (!auth.currentUser) return;
  const [page, arg] = (location.hash.slice(1) || "dashboard").split("/");
  $$("#nav a").forEach((a) => a.classList.toggle("active", a.getAttribute("href") === `#${page}`));
  view().innerHTML = `<div class="spinner"></div>`;
  (PAGES[page] || dashboard)(arg).catch((e) => {
    console.error(e);
    view().innerHTML = `<p class="error">${esc(e.message)}</p>`;
  });
}

// ---------- dashboard ----------
async function dashboard() {
  const s = await call("adminStats", {});
  const u = s.users;
  const stat = (n, l, warn = false) => `<div class="card stat ${warn && n > 0 ? "warn" : ""}"><div class="n">${n}</div><div class="l">${l}</div></div>`;
  const bars = (labels, values) => {
    const max = Math.max(1, ...values);
    return labels.map((l, i) => `<div class="bar"><span>${l}</span><div class="track"><div class="fill" style="width:${(100 * values[i]) / max}%"></div></div><span>${values[i]}</span></div>`).join("");
  };
  const m = s.moderation;
  const waiting = m.comments + m.answers + m.replies + m.questions + m.dailyPosts;
  view().innerHTML = `
    <h1>Dashboard</h1><p class="muted">Today (IST): ${esc(s.today)}</p>
    <div class="grid">
      ${stat(u.total, "Members")}${stat(u.activeToday, "Opened the app today")}${stat(u.newToday, "Joined today")}
      ${stat(s.paid.membership, "Paid memberships")}${stat(s.paid.chat, "Chat packs active")}${stat(s.paid.call, "Call packs active")}
      ${stat(s.activity.questionsToday, "Questions today")}${stat(s.activity.dailyToday, "Daily posts today")}
      ${stat(s.activity.chatsWaiting, "Chats waiting for a reply", true)}
      ${stat(waiting, "Posts waiting for approval", true)}${stat(m.goalPhotos, "Goal photos to review", true)}${stat(m.reports, "Reports", true)}
    </div>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(320px,1fr));margin-top:16px">
      <div class="card"><h3>Free levels</h3>${bars(["Level 1", "Level 2", "Level 3", "Level 4"], u.byLevel)}</div>
      <div class="card"><h3>Member levels</h3>${bars(["Member 1", "Member 2", "Member 3"], u.byMemberLevel)}
        <p class="muted">Staff: ${u.admins} admins, ${u.swamiji} Swamiji, ${u.seniors} senior members</p></div>
      <div class="card"><h3>Thought of the Day</h3>
        <p>${s.thoughts.scheduledAhead} scheduled from today. ${s.thoughts.missingNext30.length ? `<b>${s.thoughts.missingNext30.length}</b> of the next 30 days have none:` : "The next 30 days are all scheduled."}</p>
        <div class="row">${s.thoughts.missingNext30.slice(0, 14).map((d) => `<a class="chip" href="#thoughts/${d}">${d}</a>`).join("")}</div>
        <p><a href="#thoughts">Schedule or bulk import →</a></p></div>
      <div class="card"><h3>Library</h3><p>${s.content.total} videos. <a href="#content">Manage videos →</a></p>
        <h3>Moderation</h3><p>${waiting} posts, ${m.goalPhotos} goal photos, ${m.reports} reports. <a href="#moderation">Open →</a></p></div>
    </div>`;
}

// ---------- members & gifts ----------
let memberRows = [];
async function members() {
  view().innerHTML = `
    <h1>Members &amp; gifts</h1>
    <p class="muted">Select members, then give Moksha Points, membership, chat or calls (for payments taken outside the app, or as a gift).</p>
    <div class="row"><input id="m-search" placeholder="Search by name (as written)" style="flex:1;min-width:200px"><button id="m-go">Search</button>
      <select id="m-filter"><option value="">Everyone</option><option value="paid">Paid members</option><option value="free">Free</option><option value="staff">Staff</option></select></div>
    <div class="card stack" style="margin:12px 0">
      <div class="row"><b id="m-count">0 selected</b><button class="link" id="m-all">Select all shown</button><button class="link" id="m-none">Clear</button></div>
      <div class="row">
        <label>Moksha Points<input id="g-points" type="number" min="1" max="100000" value="50" style="width:110px"></label>
        <label>Note (optional)<input id="g-note" placeholder="e.g. Diwali gift" style="width:200px"></label>
        <button class="primary" id="g-give-points">Give points</button>
      </div>
      <div class="row">
        <label>Access<select id="g-access"><option value="membership">Membership</option><option value="chat">Chat</option><option value="call">Calls</option></select></label>
        <label>Days<input id="g-days" type="number" min="1" max="366" value="30" style="width:90px"></label>
        <label>Calls (empty = unlimited)<input id="g-credits" type="number" min="1" max="99" style="width:110px"></label>
        <button class="primary" id="g-give-access">Give access</button><button class="danger" id="g-revoke">End access now</button>
      </div>
      <div class="row"><label>Role (one member)<select id="g-role">${Object.entries(ROLES).map(([k, v]) => `<option value="${k}">${v}</option>`).join("")}</select></label><button id="g-set-role">Set role</button></div>
    </div>
    <div id="m-table"></div>`;
  const load = async () => {
    const q = $("#m-search").value.trim();
    const base = collection(db, "users");
    const snap = await getDocs(q ? query(base, orderBy("displayName"), startAt(q), endAt(q + ""), limit(300)) : query(base, orderBy("createdAt", "desc"), limit(500)));
    memberRows = snap.docs.map((d) => ({ uid: d.id, ...d.data() }));
    draw();
  };
  const filtered = () => {
    const f = $("#m-filter").value;
    return memberRows.filter((m) => !f || (f === "paid" && (m.memberLevel ?? 0) > 0) || (f === "free" && !(m.memberLevel > 0)) || (f === "staff" && (m.role ?? "member") !== "member"));
  };
  const selected = () => $$("#m-table input[type=checkbox]:checked").map((c) => c.value);
  const updateCount = () => ($("#m-count").textContent = `${selected().length} selected`);
  const draw = () => {
    const rows = filtered();
    $("#m-table").innerHTML = `<table><thead><tr><th></th><th>Member</th><th>Points</th><th>Level</th><th>Member level</th><th>Role</th><th>Last open</th></tr></thead><tbody>${
      rows.map((m) => `<tr><td><input type="checkbox" value="${esc(m.uid)}"></td>
        <td><div class="row">${m.photoUrl ? `<img class="avatar" src="${esc(m.photoUrl)}" alt="">` : ""}<div>${esc(m.displayName || "(no name)")}<br><span class="muted" style="font-size:12px">${esc([m.city, m.country].filter(Boolean).join(", "))}</span></div></div></td>
        <td>${m.mokshaPoints ?? 0}</td><td>${m.userLevel ?? 1}</td><td>${m.memberLevel ?? 0}</td><td>${esc(ROLES[m.role] || "Member")}</td><td>${esc(m.lastDailyOpen || "")}</td></tr>`).join("")
    }</tbody></table><p class="muted">${rows.length} shown</p>`;
    $$("#m-table input[type=checkbox]").forEach((c) => (c.onchange = updateCount));
    updateCount();
  };
  $("#m-go").onclick = load;
  $("#m-search").onkeydown = (e) => e.key === "Enter" && load();
  $("#m-filter").onchange = draw;
  $("#m-all").onclick = () => { $$("#m-table input[type=checkbox]").forEach((c) => (c.checked = true)); updateCount(); };
  $("#m-none").onclick = () => { $$("#m-table input[type=checkbox]").forEach((c) => (c.checked = false)); updateCount(); };
  const need = (n) => { const s = selected(); if (s.length < n) { toast("Select a member first", true); return null; } return s; };
  $("#g-give-points").onclick = (e) => {
    const uids = need(1); if (!uids) return;
    const amount = Number($("#g-points").value);
    if (!confirm(`Give ${amount} Moksha Points to ${uids.length} member(s)?`)) return;
    run(e.target, () => call("adminGivePoints", { uids, amount, note: $("#g-note").value }), (r) => `Points given to ${r.given} member(s)`).then(load);
  };
  const grant = (e, revoke) => {
    const uids = need(1); if (!uids) return;
    const entitlement = $("#g-access").value;
    const days = Number($("#g-days").value);
    const credits = $("#g-credits").value ? Number($("#g-credits").value) : undefined;
    const what = revoke ? `End ${entitlement} now for` : `Give ${days} days of ${entitlement} to`;
    if (!confirm(`${what} ${uids.length} member(s)?`)) return;
    run(e.target, async () => {
      for (const uid of uids) await call("adminGrant", { uid, entitlement, days: revoke ? 1 : days, credits, revoke });
      return uids.length;
    }, (n) => `Done for ${n} member(s)`).then(load);
  };
  $("#g-give-access").onclick = (e) => grant(e, false);
  $("#g-revoke").onclick = (e) => grant(e, true);
  $("#g-set-role").onclick = (e) => {
    const uids = need(1); if (!uids) return;
    if (uids.length !== 1) return toast("Select exactly one member to change a role", true);
    run(e.target, () => call("setRole", { uid: uids[0], role: $("#g-role").value }), "Role saved. They see it after reopening the app.").then(load);
  };
  await load();
}

// ---------- Thought of the Day ----------
async function thoughts(prefill) {
  const today = istToday();
  const snap = await getDocs(query(collection(db, "thoughts"), where("publishAt", ">=", istMidnight(addDays(today, -7))), orderBy("publishAt")));
  const byDay = Object.fromEntries(snap.docs.map((d) => [d.id, d.data()]));
  const days = Array.from({ length: 38 }, (_, i) => addDays(today, i - 7));
  view().innerHTML = `
    <h1>Thought of the Day</h1>
    <p class="muted">Each thought appears at 00:00 IST on its day; members can open the last 7 days. A push goes out at 06:00 IST.</p>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(340px,1fr))">
      <div class="card stack">
        <h3>Write or edit</h3>
        <label>Date<input id="t-date" type="date" value="${esc(prefill || addDays(today, 1))}"></label>
        <label>Title<input id="t-title" maxlength="150"></label>
        <label>Thought<textarea id="t-text" maxlength="5000"></textarea></label>
        <label>Picture (optional)<input id="t-image" type="file" accept="image/*"></label>
        <div id="t-current"></div>
        <button class="primary" id="t-save">Save</button>
      </div>
      <div class="card stack">
        <h3>Bulk import (schedule a month at once)</h3>
        <p class="muted">One per line: <code>YYYY-MM-DD | Title | Thought</code>. Write <code>\\n</code> for a new line inside the thought. Existing days are replaced.</p>
        <textarea id="t-bulk" placeholder="2026-12-01 | Inner peace | Peace is not found outside..."></textarea>
        <button class="primary" id="t-import">Import</button>
      </div>
    </div>
    <h2>Calendar</h2>
    <table><thead><tr><th>Day</th><th>Title</th><th>Picture</th><th></th></tr></thead><tbody>${
      days.map((d) => { const t = byDay[d]; return `<tr><td>${d}${d === today ? ' <span class="chip on">today</span>' : ""}</td>
        <td>${t ? esc(t.title) : '<span class="muted">—</span>'}</td><td>${t?.imageUrl ? "✓" : ""}</td>
        <td><button class="link" data-edit="${d}">${t ? "Edit" : "Write"}</button>${t ? ` · <button class="link danger" data-del="${d}">Delete</button>` : ""}</td></tr>`; }).join("")
    }</tbody></table>`;
  const fill = (d) => {
    const t = byDay[d] || {};
    $("#t-date").value = d; $("#t-title").value = t.title || ""; $("#t-text").value = t.text || "";
    $("#t-current").innerHTML = t.imageUrl ? `<img class="thumb" src="${esc(t.imageUrl)}" alt="">` : "";
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  fill($("#t-date").value);
  $("#t-date").onchange = () => fill($("#t-date").value);
  $$("[data-edit]").forEach((b) => (b.onclick = () => fill(b.dataset.edit)));
  $$("[data-del]").forEach((b) => (b.onclick = () => confirm(`Delete the thought for ${b.dataset.del}?`) &&
    run(b, () => deleteDoc(doc(db, "thoughts", b.dataset.del)), "Deleted").then(() => thoughts())));
  $("#t-save").onclick = (e) => run(e.target, async () => {
    const day = $("#t-date").value, title = $("#t-title").value.trim(), text = $("#t-text").value.trim();
    if (!day || !title || !text) throw new Error("Date, title and thought are required");
    const fields = { title, text, publishAt: istMidnight(day) };
    const file = $("#t-image").files[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) throw new Error("Picture must be under 5 MB");
      const r = ref(storage, `thoughts/${day}.jpg`);
      await uploadBytes(r, file, { contentType: file.type || "image/jpeg" });
      fields.imageUrl = await getDownloadURL(r);
    }
    await setDoc(doc(db, "thoughts", day), fields, { merge: true });
  }, "Saved").then(() => thoughts($("#t-date")?.value));
  $("#t-import").onclick = (e) => run(e.target, async () => {
    const lines = $("#t-bulk").value.split("\n").map((l) => l.trim()).filter(Boolean);
    let n = 0;
    for (const line of lines) {
      const [day, title, ...rest] = line.split("|").map((x) => x.trim());
      const text = rest.join(" | ").replace(/\\n/g, "\n");
      if (!/^\d{4}-\d{2}-\d{2}$/.test(day) || !title || !text) throw new Error(`Line not understood: ${line.slice(0, 60)}`);
      await setDoc(doc(db, "thoughts", day), { title: title.slice(0, 150), text: text.slice(0, 5000), publishAt: istMidnight(day) }, { merge: true });
      n++;
    }
    return n;
  }, (n) => `${n} thoughts scheduled`).then(() => thoughts());
}

// ---------- videos ----------
async function content() {
  const snap = await getDocs(collection(db, "content"));
  const items = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const sectionOpts = (sel) => Object.entries(SECTIONS).map(([k, v]) => `<option value="${k}" ${k === sel ? "selected" : ""}>${v}</option>`).join("");
  view().innerHTML = `
    <h1>Videos</h1>
    <p class="muted">Videos from youtube.com/@OjasHealing. Morning and Night open at Level 4 or with membership; Understanding at Level 2.</p>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(340px,1fr))">
      <div class="card stack"><h3>Add one video</h3>
        <label>YouTube link<input id="v-url" placeholder="https://youtu.be/..."></label>
        <label>Section<select id="v-section">${sectionOpts("blue_light")}</select></label>
        <button class="primary" id="v-add">Add</button></div>
      <div class="card stack"><h3>Import a playlist (newest 15)</h3>
        <label>Playlist id or link<input id="p-id" placeholder="PL..."></label>
        <label>Section<select id="p-section">${sectionOpts("other")}</select></label>
        <button class="primary" id="p-import">Import</button></div>
    </div>
    ${Object.entries(SECTIONS).map(([sec, name]) => {
      const list = items.filter((i) => i.section === sec).sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
      return `<h2>${name} <span class="muted">(${list.length})</span></h2>
      <table><thead><tr><th></th><th>Title</th><th>Section</th><th>Order</th><th>Min level</th><th>Shown</th><th></th></tr></thead><tbody>${
        list.map((i) => `<tr data-id="${esc(i.id)}"><td><img class="thumb" src="${thumb(esc(i.videoId))}" alt=""></td>
          <td><input class="v-title" value="${esc(i.title)}" maxlength="200" style="width:100%"></td>
          <td><select class="v-sec">${sectionOpts(i.section)}</select></td>
          <td><input class="v-order" type="number" value="${Number(i.order ?? 0)}" style="width:80px"></td>
          <td><input class="v-min" type="number" min="1" max="4" value="${Number(i.minUserLevel ?? 1)}" style="width:60px"></td>
          <td><input class="v-pub" type="checkbox" ${i.published !== false ? "checked" : ""}></td>
          <td><button class="v-save">Save</button> <button class="link danger v-del">Delete</button></td></tr>`).join("")
      }</tbody></table>`;
    }).join("")}`;
  $("#v-add").onclick = (e) => run(e.target, () => call("addVideo", { url: $("#v-url").value, section: $("#v-section").value }), "Added").then(content);
  $("#p-import").onclick = (e) => {
    const raw = $("#p-id").value.trim();
    const playlistId = (raw.match(/list=([A-Za-z0-9_-]+)/) || [null, raw])[1];
    run(e.target, () => call("importPlaylist", { playlistId, section: $("#p-section").value }), (r) => `${r.created} added, ${r.skipped} already there`).then(content);
  };
  $$("tr[data-id]").forEach((tr) => {
    const id = tr.dataset.id;
    $(".v-save", tr).onclick = (e) => run(e.target, () => updateDoc(doc(db, "content", id), {
      title: $(".v-title", tr).value.trim(), section: $(".v-sec", tr).value, order: Number($(".v-order", tr).value) || 0,
      minUserLevel: Math.min(4, Math.max(1, Number($(".v-min", tr).value) || 1)), published: $(".v-pub", tr).checked,
    }), "Saved");
    $(".v-del", tr).onclick = (e) => confirm("Delete this video from the app?") && run(e.target, () => deleteDoc(doc(db, "content", id)), "Deleted").then(content);
  });
}

// ---------- moderation ----------
async function moderation() {
  const pend = (q) => getDocs(q).then((s) => s.docs);
  const [c, a, r, q, d, goals, reports, goalDefs] = await Promise.all([
    pend(query(collectionGroup(db, "comments"), where("status", "==", "pending"))),
    pend(query(collectionGroup(db, "answers"), where("status", "==", "pending"))),
    pend(query(collectionGroup(db, "replies"), where("status", "==", "pending"))),
    pend(query(collection(db, "questions"), where("status", "==", "pending"))),
    pend(query(collection(db, "dailyPosts"), where("status", "==", "pending"))),
    pend(query(collectionGroup(db, "goalProgress"), where("status", "==", "submitted"))),
    pend(query(collection(db, "reports"), orderBy("createdAt", "desc"))),
    getDocs(collection(db, "goals")).then((s) => Object.fromEntries(s.docs.map((x) => [x.id, x.data()]))),
  ]);
  const posts = [...c, ...a, ...r, ...q, ...d];
  const names = {};
  await Promise.all(goals.map(async (g) => {
    const uid = g.ref.parent.parent.id;
    names[uid] = (await getDoc(doc(db, "users", uid))).data()?.displayName || uid;
  }));
  view().innerHTML = `
    <h1>Moderation</h1>
    <h2>Posts waiting for approval (${posts.length})</h2>
    <p class="muted">Posts with a link or a picture wait here. Approving shows them to everyone and awards the author's point.</p>
    <div class="stack">${posts.map((p) => { const x = p.data(); return `<div class="card pending" data-path="${esc(p.ref.path)}">
      <b>${esc(x.authorName)}</b> <span class="muted">${esc(p.ref.path.split("/")[0])} · ${fmt(x.createdAt)}</span>
      <p>${esc(x.title ? `${x.title}\n${x.body || ""}` : x.text)}</p>${x.imageUrl ? `<img src="${esc(x.imageUrl)}" alt="">` : ""}
      <div class="row"><button class="primary p-ok">Approve</button><button class="danger p-del">Remove</button></div></div>`; }).join("") || '<p class="muted">Nothing waiting.</p>'}</div>
    <h2>Goal photos (${goals.length})</h2>
    <div class="stack">${goals.map((g) => { const x = g.data(); const uid = g.ref.parent.parent.id; return `<div class="card pending" data-path="${esc(g.ref.path)}">
      <b>${esc(names[uid])}</b> · ${esc(goalDefs[g.id]?.title?.en || g.id)}<p>${esc(x.note || "")}</p>${x.photoUrl ? `<img src="${esc(x.photoUrl)}" alt="">` : ""}
      <div class="row"><button class="primary g-ok">Approve</button><button class="danger g-no">Reject</button></div></div>`; }).join("") || '<p class="muted">No goal photos to review.</p>'}</div>
    <h2>Reports (${reports.length})</h2>
    <div class="stack">${reports.map((rp) => { const x = rp.data(); return `<div class="card" data-id="${esc(rp.id)}" data-target="${esc(x.targetPath)}">
      <span class="muted">${fmt(x.createdAt)} · ${esc(x.targetPath)}</span><p>${esc(x.reason)}</p>
      <div class="row"><button class="danger r-remove">Remove the post</button><button class="r-dismiss">Dismiss</button></div></div>`; }).join("") || '<p class="muted">No reports.</p>'}</div>`;
  $$("[data-path]").forEach((el) => {
    const path = el.dataset.path;
    const ok = $(".p-ok", el), del = $(".p-del", el), gok = $(".g-ok", el), gno = $(".g-no", el);
    if (ok) ok.onclick = (e) => run(e.target, () => updateDoc(doc(db, path), { status: "visible" }), "Approved").then(() => el.remove());
    if (del) del.onclick = (e) => run(e.target, () => deleteDoc(doc(db, path)), "Removed").then(() => el.remove());
    if (gok) gok.onclick = (e) => run(e.target, () => updateDoc(doc(db, path), { status: "approved", reviewedAt: serverTimestamp() }), "Approved: points added").then(() => el.remove());
    if (gno) gno.onclick = (e) => run(e.target, () => updateDoc(doc(db, path), { status: "rejected", reviewedAt: serverTimestamp() }), "Rejected").then(() => el.remove());
  });
  $$("[data-target]").forEach((el) => {
    $(".r-remove", el).onclick = (e) => confirm("Delete the reported post?") && run(e.target, async () => {
      await deleteDoc(doc(db, el.dataset.target)).catch(() => {});
      await deleteDoc(doc(db, "reports", el.dataset.id));
    }, "Removed").then(() => el.remove());
    $(".r-dismiss", el).onclick = (e) => run(e.target, () => deleteDoc(doc(db, "reports", el.dataset.id)), "Dismissed").then(() => el.remove());
  });
}

// ---------- announcements ----------
async function announcements() {
  const snap = await getDocs(query(collection(db, "announcements"), orderBy("createdAt", "desc"), limit(50)));
  view().innerHTML = `
    <h1>Announcements</h1>
    <div class="card stack"><label>Title<input id="a-title" maxlength="150"></label><label>Message<textarea id="a-body" maxlength="2000"></textarea></label>
      <p class="muted">Members with announcement notifications on get a push.</p><button class="primary" id="a-post">Post</button></div>
    <h2>Posted</h2><div class="stack">${snap.docs.map((d) => { const x = d.data(); return `<div class="card" data-id="${d.id}"><b>${esc(x.title)}</b>
      <span class="muted">${fmt(x.createdAt)}</span><p>${esc(x.body)}</p><button class="link danger a-del">Delete</button></div>`; }).join("") || '<p class="muted">None yet.</p>'}</div>`;
  $("#a-post").onclick = (e) => {
    const title = $("#a-title").value.trim(), body = $("#a-body").value.trim();
    if (!title) return toast("Title is required", true);
    run(e.target, () => addDoc(collection(db, "announcements"), { title, body, createdAt: serverTimestamp() }), "Posted").then(announcements);
  };
  $$(".a-del").forEach((b) => (b.onclick = () => confirm("Delete this announcement?") &&
    run(b, () => deleteDoc(doc(db, "announcements", b.closest("[data-id]").dataset.id)), "Deleted").then(announcements)));
}

// ---------- admins ----------
async function admins() {
  const list = (await getDoc(doc(db, "config", "admins"))).data()?.emails || [];
  const current = await getDocs(query(collection(db, "users"), where("role", "==", "admin")));
  view().innerHTML = `
    <h1>Admins</h1>
    <p class="muted">People on this list become admins when they sign in to the app or this website with that Google account (or a verified email).</p>
    <div class="card stack">
      <div class="row"><input id="ad-email" type="email" placeholder="name@gmail.com" style="flex:1;min-width:220px"><button class="primary" id="ad-add">Add</button></div>
      <table><tbody>${list.map((e) => `<tr><td>${esc(e)}</td><td><button class="link danger" data-email="${esc(e)}">Remove from list</button></td></tr>`).join("") || '<tr><td class="muted">Empty</td></tr>'}</tbody></table>
      <div class="row"><button id="ad-apply">Make listed accounts admin now</button><span class="muted">for people who already have an account</span></div>
    </div>
    <h2>Accounts with the admin role (${current.size})</h2>
    <table><tbody>${current.docs.map((d) => `<tr><td>${esc(d.data().displayName || d.id)}</td><td class="muted">${esc(d.id)}</td></tr>`).join("")}</tbody></table>
    <p class="muted">To take admin away from someone, remove them from the list and set their role to Member on the Members page.</p>`;
  $("#ad-add").onclick = (e) => {
    const email = $("#ad-email").value.trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return toast("Enter an email address", true);
    run(e.target, () => setDoc(doc(db, "config", "admins"), { emails: arrayUnion(email) }, { merge: true }), "Added").then(admins);
  };
  $$("[data-email]").forEach((b) => (b.onclick = () => run(b, () => updateDoc(doc(db, "config", "admins"), { emails: arrayRemove(b.dataset.email) }), "Removed").then(admins)));
  $("#ad-apply").onclick = (e) => run(e.target, () => call("adminApplyAdminList", {}),
    (r) => `Admin now: ${r.promoted.join(", ") || "nobody new"}. Not signed up yet: ${r.notSignedUpYet.join(", ") || "none"}`).then(admins);
}

// ---------- settings ----------
async function settings() {
  const lv = (await getDoc(doc(db, "config", "levels"))).data() || {};
  const turn = (await getDoc(doc(db, "config", "turn"))).data() || {};
  const staff = (await getDoc(doc(db, "config", "staff"))).data() || {};
  const n = (path, dflt) => path.split(".").reduce((o, k) => o?.[k], lv) ?? dflt;
  const lvl = (k, d, p) => `<tr><td>${k.toUpperCase()}</td><td><input type="number" id="${k}-days" value="${n(`${k}.days`, d)}" style="width:100px"></td><td><input type="number" id="${k}-points" value="${n(`${k}.points`, p)}" style="width:120px"></td></tr>`;
  view().innerHTML = `
    <h1>Settings</h1>
    <div class="card stack"><h3>Free user levels</h3><p class="muted">A level needs both: days of using the app and Moksha Points. Changes apply at each member's next point.</p>
      <table><thead><tr><th>Level</th><th>Days</th><th>Points</th></tr></thead><tbody>${lvl("l2", 7, 7)}${lvl("l3", 90, 200)}${lvl("l4", 365, 10000)}</tbody></table>
      <h3>Daily point limits</h3><div class="row">
        <label>Likes<input type="number" id="cap-like" value="${n("dailyCaps.like", 50)}" style="width:90px"></label>
        <label>Comments and answers<input type="number" id="cap-comment" value="${n("dailyCaps.comment", 20)}" style="width:90px"></label>
        <label>Questions<input type="number" id="cap-question" value="${n("dailyCaps.question", 5)}" style="width:90px"></label></div>
      <button class="primary" id="s-levels">Save levels</button></div>
    <div class="card stack" style="margin-top:12px"><h3>Calls</h3>
      <p>Members call: <b>${esc(staff.swamijiUid || "nobody yet: set someone's role to Swamiji on the Members page")}</b></p>
      <p class="muted">If calls ring but never connect on some networks, add a TURN relay here (no app update needed).</p>
      <label>TURN URLs (comma separated)<input id="turn-urls" value="${esc((turn.urls || []).join(", "))}" placeholder="turn:turn.example.com:3478"></label>
      <div class="row"><label>Username<input id="turn-user" value="${esc(turn.username || "")}"></label><label>Password<input id="turn-pass" value="${esc(turn.credential || "")}"></label></div>
      <button class="primary" id="s-turn">Save calls</button></div>`;
  const num = (id) => Math.max(0, Math.floor(Number($(id).value) || 0));
  $("#s-levels").onclick = (e) => run(e.target, () => setDoc(doc(db, "config", "levels"), {
    l2: { days: num("#l2-days"), points: num("#l2-points") },
    l3: { days: num("#l3-days"), points: num("#l3-points") },
    l4: { days: num("#l4-days"), points: num("#l4-points") },
    dailyCaps: { like: num("#cap-like"), comment: num("#cap-comment"), question: num("#cap-question") },
  }, { merge: true }), "Levels saved");
  $("#s-turn").onclick = (e) => run(e.target, () => setDoc(doc(db, "config", "turn"), {
    urls: $("#turn-urls").value.split(",").map((s) => s.trim()).filter(Boolean), username: $("#turn-user").value.trim(), credential: $("#turn-pass").value.trim(),
  }), "Saved");
}
