// Ojas Family admin website. Every action goes through the same Firestore rules and Cloud Functions as
// the app, so only accounts with the admin role (custom claim) can change anything.
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  addDoc, arrayRemove, arrayUnion, collection, collectionGroup, deleteDoc, deleteField, doc, endAt, getDoc, getDocs, getFirestore,
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
// A report carries the path its sender chose. "Remove the post" may only ever delete a member's post,
// never settings, videos or announcements (same pattern as firestore.rules and the app)
const REPORTABLE = /^(questions\/[^\/]+(\/answers\/[^\/]+)?|thoughts\/[^\/]+\/comments\/[^\/]+|announcements\/[^\/]+\/comments\/[^\/]+|dailyPosts\/[^\/]+(\/replies\/[^\/]+)?)$/;

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
const PAGES = { dashboard, members, points, ai, practice, thoughts, content, books, moderation, announcements, calls, admins, settings };
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
const GA_URL = "https://console.firebase.google.com/project/ojas-family-app/analytics";
const PRACTICE_KEYS = ["blue_light", "quick_protection", "morning", "night"];
const PRACTICE_COLORS = { blue_light: "#2f7cf6", quick_protection: "#8e5cf0", morning: "#f2a33a", night: "#3d4bb0" };
const COMMUNITY = [
  ["questions", "Questions", "#c8661b"], ["answers", "Answers", "#e39a52"], ["posts", "Activity posts", "#2e7d32"],
  ["replies", "Replies", "#66a86a"], ["comments", "Thought comments", "#3d4bb0"], ["likes", "Likes", "#9aa8ff"],
];
const LANG_NAMES = { en: "English", hi: "Hindi", gu: "Gujarati" };
let dashRange = "30";

const sum = (a) => a.reduce((x, y) => x + y, 0);
const pct = (a, b) => (b > 0 ? Math.round((100 * a) / b) : 0);
const shortDay = (d) => new Date(`${d}T12:00:00+05:30`).toLocaleDateString(undefined, { day: "numeric", month: "short" });

/** A day with every number present, so older stored days and today's live numbers read the same way. */
function fullDay(d) {
  const z = (o, keys) => Object.fromEntries(keys.map((k) => [k, Number(o?.[k] ?? 0)]));
  return {
    day: d.day,
    people: z(d.people, ["active", "new", "total"]),
    practice: { people: Number(d.practice?.people ?? 0), sections: Object.fromEntries(PRACTICE_KEYS.map((k) => [k, z(d.practice?.sections?.[k], ["started", "completed"])])) },
    ai: z(d.ai, ["questions", "up", "down"]),
    community: z(d.community, ["questions", "answers", "posts", "replies", "comments", "likes", "announcements"]),
    calls: z(d.calls, ["total", "connected", "minutes", "requests"]),
    points: z(d.points, ["transfers", "transferred"]),
    snapshot: d.snapshot,
  };
}

/** Line chart as inline SVG (the site loads no chart library). Each point has a tooltip. */
function lineChart(days, series, height = 200) {
  const W = 640, H = height, L = 36, B = 24, T = 10, R = 10;
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  const nice = Math.ceil(max / 4) * 4 || 4;
  const x = (i) => L + (days.length < 2 ? (W - L - R) / 2 : (i * (W - L - R)) / (days.length - 1));
  const y = (v) => T + (H - T - B) * (1 - v / nice);
  const grid = [0, 1, 2, 3, 4].map((k) => {
    const v = (nice * k) / 4;
    return `<line x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}" class="gl"/><text x="${L - 6}" y="${y(v) + 4}" class="ax" text-anchor="end">${Math.round(v)}</text>`;
  }).join("");
  const step = Math.max(1, Math.ceil(days.length / 8));
  const xl = days.map((d, i) => (showLabel(i, days.length, step) ? `<text x="${x(i)}" y="${H - 6}" class="ax" text-anchor="middle">${esc(shortDay(d))}</text>` : "")).join("");
  const lines = series.map((s) => {
    const pts = s.values.map((v, i) => `${x(i)},${y(v)}`).join(" ");
    const dots = s.values.map((v, i) => `<circle cx="${x(i)}" cy="${y(v)}" r="3" fill="${s.color}"><title>${esc(shortDay(days[i]))}: ${v} ${esc(s.label)}</title></circle>`).join("");
    return `<polyline points="${pts}" fill="none" stroke="${s.color}" stroke-width="2.2" stroke-linejoin="round"/>${dots}`;
  }).join("");
  return `<svg viewBox="0 0 ${W} ${H}" class="chart" role="img">${grid}${xl}${lines}</svg>${legend(series)}`;
}

/** Stacked bars per day as inline SVG. */
function stackedBars(days, series, height = 200) {
  const W = 640, H = height, L = 36, B = 24, T = 10, R = 10;
  const totals = days.map((_, i) => sum(series.map((s) => s.values[i])));
  const nice = Math.ceil(Math.max(1, ...totals) / 4) * 4 || 4;
  const slot = (W - L - R) / Math.max(1, days.length);
  const bw = Math.max(2, Math.min(28, slot * 0.7));
  const y = (v) => T + (H - T - B) * (1 - v / nice);
  const grid = [0, 1, 2, 3, 4].map((k) => {
    const v = (nice * k) / 4;
    return `<line x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}" class="gl"/><text x="${L - 6}" y="${y(v) + 4}" class="ax" text-anchor="end">${Math.round(v)}</text>`;
  }).join("");
  const step = Math.max(1, Math.ceil(days.length / 8));
  const bars = days.map((d, i) => {
    let acc = 0;
    const cx = L + slot * i + slot / 2;
    const parts = series.map((s) => {
      const v = s.values[i];
      if (!v) return "";
      const top = y(acc + v), h = y(acc) - top;
      acc += v;
      return `<rect x="${cx - bw / 2}" y="${top}" width="${bw}" height="${h}" fill="${s.color}" rx="2"><title>${esc(shortDay(d))}: ${v} ${esc(s.label)}</title></rect>`;
    }).join("");
    const label = showLabel(i, days.length, step) ? `<text x="${cx}" y="${H - 6}" class="ax" text-anchor="middle">${esc(shortDay(d))}</text>` : "";
    return parts + label;
  }).join("");
  return `<svg viewBox="0 0 ${W} ${H}" class="chart" role="img">${grid}${bars}</svg>${legend(series)}`;
}

/** Every [step]th date, and the last one, never two crowded together at the end. */
const showLabel = (i, count, step) => i === count - 1 || (i % step === 0 && count - 1 - i >= step * 0.6);

const legend = (series) => `<div class="legend">${series.map((s) => `<span><i style="background:${s.color}"></i>${esc(s.label)}</span>`).join("")}</div>`;

const hbars = (pairs, color = "var(--accent)") => {
  const max = Math.max(1, ...pairs.map((p) => p[1]));
  return pairs.map(([l, v]) => `<div class="bar"><span title="${esc(l)}">${esc(l)}</span><div class="track"><div class="fill" style="width:${(100 * v) / max}%;background:${color}"></div></div><span>${v}</span></div>`).join("") || `<p class="muted">No data yet.</p>`;
};

async function dashboard() {
  const today = istToday();
  // History (up to two 90-day periods, for the comparison), today's live numbers and the existing to-do counts
  // Each part loads on its own, so one failing part shows a message instead of an empty page
  const failed = [];
  const soft = (what, p, fallback) => p.catch((e) => { console.error(what, e); failed.push(`${what}: ${e.message}`); return fallback; });
  const [hist, live, s] = await Promise.all([
    soft("History", getDocs(query(collection(db, "stats"), where("day", ">=", addDays(today, -180)), orderBy("day"))).then((q) => q.docs.map((d) => fullDay(d.data()))), []),
    soft("Today's numbers", call("adminDayStats", {}).then(fullDay), fullDay({ day: today })),
    soft("To-do counts", call("adminStats", {}), null),
  ]);
  const all = [...hist.filter((d) => d.day < today), live];
  const snapshot = live.snapshot || [...hist].reverse().find((d) => d.snapshot)?.snapshot;
  view().innerHTML = `
    <div class="row" style="justify-content:space-between"><div><h1>Dashboard</h1><p class="muted">How the app is used. Days are India time; today (${esc(today)}) is counted live.</p></div>
      <div class="row">
        <div class="seg" id="d-range">${[["1", "Today"], ["7", "7 days"], ["30", "30 days"], ["90", "90 days"]].map(([v, l]) => `<button data-r="${v}" class="${dashRange === v ? "on" : ""}">${l}</button>`).join("")}</div>
        <button id="d-csv">Download CSV</button><a class="btn" href="${GA_URL}" target="_blank" rel="noopener">Open Google Analytics ↗</a>
      </div></div>
    ${failed.length ? `<div class="card notice" style="margin:12px 0"><b>Some numbers could not be loaded.</b>${failed.map((f) => `<div class="muted">${esc(f)}</div>`).join("")}</div>` : ""}
    ${hist.length < 2 ? `<div class="card notice row" style="justify-content:space-between;margin:12px 0"><span>History starts tonight. Fill the last 90 days from what members already did (AI questions are only known from now on).</span><button class="primary" id="d-fill">Fill history</button></div>` : ""}
    <div id="d-body"></div>
    <h2>Needs attention</h2><div id="d-todo"></div>`;
  $$("#d-range button").forEach((b) => (b.onclick = () => { dashRange = b.dataset.r; $$("#d-range button").forEach((x) => x.classList.toggle("on", x === b)); drawDash(all, snapshot); }));
  $("#d-csv").onclick = () => downloadCsv(dashRange === "1" ? all.slice(-1) : all.slice(0, -1).slice(-Number(dashRange)));
  const fill = $("#d-fill");
  if (fill) fill.onclick = () => run(fill, () => call("adminBackfillStats", { days: 90 }), (r) => `Filled ${r.filled.length} days`).then((r) => r && route());
  drawDash(all, snapshot);
  if (s) drawTodo(s);
}

function drawDash(all, snapshot) {
  const n = Number(dashRange);
  // Longer ranges use whole days only (up to yesterday), so today's unfinished numbers don't drag the lines down
  const wholeDays = n === 1 ? all : all.slice(0, -1);
  const cur = wholeDays.slice(-n);
  // Compared only when the earlier period is fully known
  const prevAll = wholeDays.slice(-2 * n, -n);
  const prev = prevAll.length === n ? prevAll : [];
  const days = cur.map((d) => d.day);
  const tot = (list, f) => sum(list.map(f));
  const card = (label, value, before, hint = "") => {
    const delta = prev.length && before != null ? value - before : null;
    const cls = delta == null || delta === 0 ? "" : delta > 0 ? "up" : "down";
    const dtxt = delta == null ? "" : `<span class="delta ${cls}">${delta > 0 ? "▲" : delta < 0 ? "▼" : "•"} ${Math.abs(delta)}${before > 0 ? ` (${delta >= 0 ? "+" : "−"}${Math.abs(pct(delta, before))}%)` : ""}</span>`;
    return `<div class="card stat"><div class="n">${value}</div><div class="l">${label}</div>${dtxt}${hint ? `<div class="muted" style="font-size:12px">${hint}</div>` : ""}</div>`;
  };
  const avg = (list, f) => (list.length ? Math.round(tot(list, f) / list.length) : 0);
  const done = (d) => sum(PRACTICE_KEYS.map((k) => d.practice.sections[k].completed));
  const posts = (d) => sum(COMMUNITY.map(([k]) => (k === "likes" ? 0 : d.community[k])));
  const label = n === 1 ? "vs yesterday" : `vs previous ${n} days`;
  const busiest = cur.reduce((a, d) => (d.people.active > (a?.people.active ?? -1) ? d : a), null);

  const sections = PRACTICE_KEYS.map((k) => {
    const st = tot(cur, (d) => d.practice.sections[k].started), cp = tot(cur, (d) => d.practice.sections[k].completed);
    return { k, st, cp };
  });
  const allDone = sum(sections.map((x) => x.cp));
  const favourite = [...sections].sort((a, b) => b.cp - a.cp)[0];
  const up = tot(cur, (d) => d.ai.up), down = tot(cur, (d) => d.ai.down);

  $("#d-body").innerHTML = `
    <p class="muted">${n === 1 ? "Today so far" : `The ${n} days up to yesterday`}${prev.length ? ` · compared ${label}` : ""}</p>
    <div class="grid">
      ${card(n === 1 ? "Opened the app" : "Active people a day (avg)", avg(cur, (d) => d.people.active), avg(prev, (d) => d.people.active))}
      ${card("New members", tot(cur, (d) => d.people.new), tot(prev, (d) => d.people.new))}
      ${card("Practices completed", tot(cur, done), tot(prev, done))}
      ${card("People who practised (a day, avg)", avg(cur, (d) => d.practice.people), avg(prev, (d) => d.practice.people))}
      ${card("Ask Swamiji questions", tot(cur, (d) => d.ai.questions), tot(prev, (d) => d.ai.questions))}
      ${card("Posts, answers & comments", tot(cur, posts), tot(prev, posts))}
      ${card("Members in total", cur.at(-1)?.people.total ?? 0, prev.at(-1)?.people.total ?? 0)}
      ${card("Paid memberships", snapshot?.paid?.membership ?? 0, null, "now")}
    </div>
    ${n > 1 ? `
    <div class="charts">
      <div class="card"><h3>People using the app</h3>${lineChart(days, [
        { label: "Opened the app", color: "#c8661b", values: cur.map((d) => d.people.active) },
        { label: "Practised", color: "#2f7cf6", values: cur.map((d) => d.practice.people) },
        { label: "New members", color: "#2e7d32", values: cur.map((d) => d.people.new) },
      ])}${busiest ? `<p class="muted">Busiest day: ${esc(shortDay(busiest.day))} with ${busiest.people.active} people.</p>` : ""}</div>
      <div class="card"><h3>Practices completed a day</h3>${stackedBars(days, PRACTICE_KEYS.map((k) => ({ label: SECTIONS[k], color: PRACTICE_COLORS[k], values: cur.map((d) => d.practice.sections[k].completed) })))}</div>
      <div class="card"><h3>Ask Swamiji</h3>${lineChart(days, [
        { label: "Questions", color: "#8e5cf0", values: cur.map((d) => d.ai.questions) },
        { label: "Helpful 👍", color: "#2e7d32", values: cur.map((d) => d.ai.up) },
        { label: "Not helpful 👎", color: "#c62828", values: cur.map((d) => d.ai.down) },
      ], 170)}</div>
      <div class="card"><h3>Community activity a day</h3>${stackedBars(days, COMMUNITY.map(([k, l, c]) => ({ label: l, color: c, values: cur.map((d) => d.community[k]) })), 170)}</div>
    </div>` : ""}
    <div class="charts">
      <div class="card"><h3>Practices</h3>
        <table><thead><tr><th>Practice</th><th>Started</th><th>Completed</th><th>Finish rate</th><th>Share</th></tr></thead><tbody>
        ${sections.map((x) => `<tr><td><i class="dot" style="background:${PRACTICE_COLORS[x.k]}"></i>${esc(SECTIONS[x.k])}</td><td>${x.st}</td><td>${x.cp}</td><td>${pct(x.cp, x.st)}%</td><td>${pct(x.cp, allDone)}%</td></tr>`).join("")}
        </tbody></table>
        <p class="muted">${allDone ? `Most used: <b>${esc(SECTIONS[favourite.k])}</b>. ` : ""}A practice counts as completed after 90% of it has played.</p></div>
      <div class="card"><h3>Ask Swamiji answers</h3>
        <div class="grid" style="grid-template-columns:repeat(3,1fr)">
          <div><div class="big">${tot(cur, (d) => d.ai.questions)}</div><div class="muted">questions</div></div>
          <div><div class="big">${up + down ? `${pct(up, up + down)}%` : "–"}</div><div class="muted">rated helpful</div></div>
          <div><div class="big">${up} / ${down}</div><div class="muted">👍 / 👎</div></div>
        </div>
        <p><a href="#ai">Read the rated answers →</a></p>
        <h3>Calls &amp; Freedom Points</h3>
        <p>${tot(cur, (d) => d.calls.connected)} calls connected of ${tot(cur, (d) => d.calls.total)} (${tot(cur, (d) => d.calls.minutes)} minutes), ${tot(cur, (d) => d.calls.requests)} call bookings.<br>
        ${tot(cur, (d) => d.points.transfers)} point gifts between members (${tot(cur, (d) => d.points.transferred)} points).</p></div>
    </div>
    ${snapshot ? `
    <h2>Members</h2>
    <div class="charts three">
      <div class="card"><h3>Language</h3>${hbars(Object.entries(snapshot.languages || {}).filter(([, v]) => v).map(([k, v]) => [LANG_NAMES[k] || k, v]).sort((a, b) => b[1] - a[1]))}</div>
      <div class="card"><h3>State</h3>${hbars(Object.entries(snapshot.states || {}).sort((a, b) => b[1] - a[1]), "#2e7d32")}</div>
      <div class="card"><h3>Country</h3>${hbars(Object.entries(snapshot.countries || {}).sort((a, b) => b[1] - a[1]), "#c8661b")}</div>
    </div>` : ""}`;
}

function downloadCsv(list) {
  const cols = [
    ["day", (d) => d.day], ["active", (d) => d.people.active], ["new_members", (d) => d.people.new], ["members_total", (d) => d.people.total],
    ["people_practised", (d) => d.practice.people],
    ...PRACTICE_KEYS.flatMap((k) => [[`${k}_started`, (d) => d.practice.sections[k].started], [`${k}_completed`, (d) => d.practice.sections[k].completed]]),
    ["ai_questions", (d) => d.ai.questions], ["ai_helpful", (d) => d.ai.up], ["ai_not_helpful", (d) => d.ai.down],
    ...COMMUNITY.map(([k]) => [k, (d) => d.community[k]]), ["announcements", (d) => d.community.announcements],
    ["calls", (d) => d.calls.total], ["calls_connected", (d) => d.calls.connected], ["call_minutes", (d) => d.calls.minutes], ["call_bookings", (d) => d.calls.requests],
    ["point_gifts", (d) => d.points.transfers], ["points_gifted", (d) => d.points.transferred],
  ];
  const csv = [cols.map((c) => c[0]).join(","), ...list.map((d) => cols.map((c) => c[1](d)).join(","))].join("\n");
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
  a.download = `ojas-usage-${list[0]?.day ?? ""}-to-${list.at(-1)?.day ?? ""}.csv`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

/** The to-do counts and levels that were the whole dashboard before. */
function drawTodo(s) {
  const u = s.users;
  const stat = (n, l, warn = false) => `<div class="card stat ${warn && n > 0 ? "warn" : ""}"><div class="n">${n}</div><div class="l">${l}</div></div>`;
  const bars = (labels, values) => {
    const max = Math.max(1, ...values);
    return labels.map((l, i) => `<div class="bar"><span>${l}</span><div class="track"><div class="fill" style="width:${(100 * values[i]) / max}%"></div></div><span>${values[i]}</span></div>`).join("");
  };
  const m = s.moderation;
  const waiting = m.comments + m.answers + m.replies + m.questions + m.dailyPosts;
  $("#d-todo").innerHTML = `
    <div class="grid">
      ${stat(s.activity.chatsWaiting, "Chats waiting for a reply", true)}
      ${stat(waiting, "Posts waiting for approval", true)}${stat(m.goalPhotos, "Goal photos to review", true)}${stat(m.reports, "Reports", true)}
    </div>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(320px,1fr));margin-top:16px">
      <div class="card"><h3>Free levels</h3>${bars(["Level 1", "Level 2", "Level 3", "Level 4"], u.byLevel)}</div>
      <div class="card"><h3>Member levels</h3>${bars(["Member 1", "Member 2", "Member 3"], u.byMemberLevel)}
        <p class="muted">Staff: ${u.admins} admins, ${u.swamiji} Swamiji, ${u.seniors} senior members</p>
        <p class="muted">Paid now: ${s.paid.membership} memberships, ${s.paid.chat} chat packs, ${s.paid.call} call packs</p></div>
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
    <p class="muted">Select members, then give Freedom Points, membership, chat or calls (for payments taken outside the app, or as a gift).</p>
    <div class="row"><input id="m-search" placeholder="Search by name (as written)" style="flex:1;min-width:200px"><button id="m-go">Search</button>
      <select id="m-filter"><option value="">Everyone</option><option value="paid">Paid members</option><option value="free">Free</option><option value="staff">Staff</option></select></div>
    <div class="card stack" style="margin:12px 0">
      <div class="row"><b id="m-count">0 selected</b><button class="link" id="m-all">Select all shown</button><button class="link" id="m-none">Clear</button></div>
      <div class="row">
        <label>Freedom Points<input id="g-points" type="number" min="1" max="100000" value="50" style="width:110px"></label>
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
    $("#m-table").innerHTML = `<table><thead><tr><th></th><th>Member</th><th>Points</th><th>Earned</th><th>Level</th><th>Member level</th><th>Role</th><th>Last open</th></tr></thead><tbody>${
      rows.map((m) => `<tr><td><input type="checkbox" value="${esc(m.uid)}"></td>
        <td title="${esc(m.uid)}"><div class="row">${m.photoUrl ? `<img class="avatar" src="${esc(m.photoUrl)}" alt="">` : ""}<div>${esc(m.displayName || "(no name)")}<br><span class="muted" style="font-size:12px">${esc([m.city, m.country].filter(Boolean).join(", "))}</span></div></div></td>
        <td>${esc(m.mokshaPoints ?? 0)}</td><td>${esc(m.earnedPoints ?? m.mokshaPoints ?? 0)}</td><td>${esc(m.userLevel ?? 1)}</td><td>${esc(m.memberLevel ?? 0)}</td><td>${esc(ROLES[m.role] || "Member")}</td><td>${esc(m.lastDailyOpen || "")}</td></tr>`).join("")
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
    if (!confirm(`Give ${amount} Freedom Points to ${uids.length} member(s)?`)) return;
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
    const role = $("#g-role").value;
    const who = memberRows.find((m) => m.uid === uids[0])?.displayName || "this member";
    if (!confirm(`Make ${who} ${ROLES[role]}?${role === "admin" ? " Admins can change everything, including other admins." : ""}`)) return;
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
    <div class="split">
      <div class="card stack">
        <h3>Write or edit</h3>
        <label>Date<input id="t-date" type="date" value="${esc(prefill || addDays(today, 1))}"></label>
        <label>Title<input id="t-title" maxlength="150"></label>
        <label>Thought<textarea id="t-text" maxlength="5000"></textarea></label>
        <label>Picture (optional)<input id="t-image" type="file" accept="image/*"></label>
        <div id="t-current"></div>
        <button class="primary" id="t-save">Save</button>
      </div>
      <div class="card cal">
        <h3>Calendar</h3>
        <table><thead><tr><th>Day</th><th>Title</th><th>Picture</th><th></th></tr></thead><tbody>${
          days.map((d) => { const t = byDay[d]; return `<tr data-day="${d}"><td>${d}${d === today ? ' <span class="chip on">today</span>' : ""}</td>
            <td>${t ? esc(t.title) : '<span class="muted">—</span>'}</td><td>${t?.imageUrl ? `<img class="mini" src="${esc(t.imageUrl)}" alt="Picture for ${d}" loading="lazy">` : ""}</td>
            <td><button class="link" data-edit="${d}">${t ? "Edit" : "Write"}</button>${t ? ` · <button class="link danger" data-del="${d}">Delete</button>` : ""}</td></tr>`; }).join("")
        }</tbody></table>
      </div>
    </div>
    <div class="card stack" style="margin-top:12px">
      <h3>Bulk import (schedule a month at once)</h3>
      <p class="muted">One per line: <code>YYYY-MM-DD | Title | Thought</code>. Write <code>\\n</code> for a new line inside the thought. Existing days are replaced.</p>
      <textarea id="t-bulk" placeholder="2026-12-01 | Inner peace | Peace is not found outside..."></textarea>
      <button class="primary" id="t-import">Import</button>
    </div>`;
  const fill = (d) => {
    const t = byDay[d] || {};
    $("#t-date").value = d; $("#t-title").value = t.title || ""; $("#t-text").value = t.text || "";
    $("#t-image").value = "";
    $("#t-current").innerHTML = t.imageUrl ? `<img class="thumb" src="${esc(t.imageUrl)}" alt=""><div class="muted">Saved picture for ${esc(d)}</div>` : "";
    $$(".cal tr[data-day]").forEach((r) => r.classList.toggle("sel", r.dataset.day === d));
    // On a narrow screen the form sits above the calendar
    if (window.innerWidth <= 1000) window.scrollTo({ top: 0, behavior: "smooth" });
  };
  let preview;
  $("#t-image").onchange = () => {
    const f = $("#t-image").files[0];
    if (preview) URL.revokeObjectURL(preview);
    if (!f) return fill($("#t-date").value);
    preview = URL.createObjectURL(f);
    $("#t-current").innerHTML = `<img class="thumb" src="${preview}" alt=""><div class="muted">New picture: ${esc(f.name)} (${Math.round(f.size / 1024)} KB), saved when you press Save</div>`;
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

// ---------- books ----------
const clampLevel = (v) => Math.min(4, Math.max(1, Math.trunc(Number(v)) || 1));
function bookFields(title, author, url, min) {
  const t = title.trim(), u = url.trim();
  if (!t) throw new Error("Give the book a title");
  if (!/^https:\/\/\S+$/.test(u)) throw new Error("The link must start with https://");
  return { title: t.slice(0, 200), author: author.trim().slice(0, 120), url: u.slice(0, 1990), minUserLevel: clampLevel(min) };
}
async function books() {
  const snap = await getDocs(collection(db, "books"));
  const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }))
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0) || String(a.title).localeCompare(String(b.title)));
  view().innerHTML = `
    <h1>Books</h1>
    <p class="muted">PDF books on the Ask tab's Books section. Paste a link anyone can open (for a Google Drive file: Share → "Anyone with the link"). Like Understanding videos, books open at Level 2 or with membership; raise "Min level" for a single book.</p>
    <div class="card stack" style="max-width:560px"><h3>Add a book</h3>
      <label>Title<input id="b-title" maxlength="200"></label>
      <label>Author (optional)<input id="b-author" maxlength="120"></label>
      <label>PDF link<input id="b-url" placeholder="https://drive.google.com/..."></label>
      <label>Min level<input id="b-min" type="number" min="1" max="4" value="1" style="width:80px"></label>
      <button class="primary" id="b-add">Add</button></div>
    <h2>Books <span class="muted">(${list.length})</span></h2>
    <table><thead><tr><th>Title</th><th>Author</th><th>Link</th><th>Order</th><th>Min level</th><th>Shown</th><th></th></tr></thead><tbody>${
      list.map((b) => `<tr data-id="${esc(b.id)}">
        <td><input class="b-title" value="${esc(b.title)}" maxlength="200" style="width:100%"></td>
        <td><input class="b-author" value="${esc(b.author || "")}" maxlength="120"></td>
        <td><input class="b-url" value="${esc(b.url)}" style="width:100%"> <a href="${esc(b.url)}" target="_blank" rel="noopener">Open</a></td>
        <td><input class="b-order" type="number" value="${Number(b.order ?? 0)}" style="width:80px"></td>
        <td><input class="b-min" type="number" min="1" max="4" value="${Number(b.minUserLevel ?? 1)}" style="width:60px"></td>
        <td><input class="b-pub" type="checkbox" ${b.published !== false ? "checked" : ""}></td>
        <td><button class="b-save">Save</button> <button class="link danger b-del">Delete</button></td></tr>`).join("") || '<tr><td colspan="7" class="muted">No books yet.</td></tr>'
    }</tbody></table>`;
  $("#b-add").onclick = (e) => run(e.target, () => addDoc(collection(db, "books"), {
    ...bookFields($("#b-title").value, $("#b-author").value, $("#b-url").value, $("#b-min").value),
    minMemberLevel: 0, order: list.length, published: true,
  }), "Added").then(books);
  $$("tr[data-id]").forEach((tr) => {
    const id = tr.dataset.id;
    $(".b-save", tr).onclick = (e) => run(e.target, () => updateDoc(doc(db, "books", id), {
      ...bookFields($(".b-title", tr).value, $(".b-author", tr).value, $(".b-url", tr).value, $(".b-min", tr).value),
      order: Math.trunc(Number($(".b-order", tr).value) || 0), published: $(".b-pub", tr).checked,
    }), "Saved");
    $(".b-del", tr).onclick = (e) => confirm("Delete this book from the app?") && run(e.target, () => deleteDoc(doc(db, "books", id)), "Deleted").then(books);
  });
}

// ---------- moderation ----------
async function moderation() {
  const pend = (q) => getDocs(q).then((s) => s.docs);
  const [c, a, r, q, d, goals, reports, goalDefs, faces] = await Promise.all([
    pend(query(collectionGroup(db, "comments"), where("status", "==", "pending"))),
    pend(query(collectionGroup(db, "answers"), where("status", "==", "pending"))),
    pend(query(collectionGroup(db, "replies"), where("status", "==", "pending"))),
    pend(query(collection(db, "questions"), where("status", "==", "pending"))),
    pend(query(collection(db, "dailyPosts"), where("status", "==", "pending"))),
    pend(query(collectionGroup(db, "goalProgress"), where("status", "==", "submitted"))),
    pend(query(collection(db, "reports"), orderBy("createdAt", "desc"))),
    getDocs(collection(db, "goals")).then((s) => Object.fromEntries(s.docs.map((x) => [x.id, x.data()]))),
    pend(query(collection(db, "facePhotos"), where("status", "==", "submitted"))),
  ]);
  const posts = [...c, ...a, ...r, ...q, ...d];
  const names = {};
  await Promise.all([...goals.map((g) => g.ref.parent.parent.id), ...faces.map((f) => f.id)].map(async (uid) => {
    names[uid] = (await getDoc(doc(db, "users", uid))).data()?.displayName || uid;
  }));
  view().innerHTML = `
    <h1>Moderation</h1>
    <h2>Posts waiting for approval (${posts.length})</h2>
    <p class="muted">Posts with a link, a picture, audio or video wait here. Approving shows them to everyone and awards the author's point.</p>
    <div class="stack">${posts.map((p) => { const x = p.data(); return `<div class="card pending" data-path="${esc(p.ref.path)}">
      <b>${esc(x.authorName)}</b> <span class="muted">${esc(p.ref.path.split("/")[0])} · ${fmt(x.createdAt)}</span>
      <p>${esc(x.title ? `${x.title}\n${x.body || ""}` : x.text)}</p>${x.imageUrl ? `<img src="${esc(x.imageUrl)}" alt="">` : ""}${mediaHtml(x)}
      <div class="row"><button class="primary p-ok">Approve</button><button class="danger p-del">Remove</button></div></div>`; }).join("") || '<p class="muted">Nothing waiting.</p>'}</div>
    <h2>Goal photos (${goals.length})</h2>
    <div class="stack">${goals.map((g) => { const x = g.data(); const uid = g.ref.parent.parent.id; return `<div class="card pending" data-path="${esc(g.ref.path)}">
      <b>${esc(names[uid])}</b> · ${esc(goalDefs[g.id]?.title?.en || g.id)}<p>${esc(x.note || "")}</p>${x.photoUrl ? `<img src="${esc(x.photoUrl)}" alt="">` : ""}
      <div class="row"><button class="primary g-ok">Approve</button><button class="danger g-no">Reject</button></div></div>`; }).join("") || '<p class="muted">No goal photos to review.</p>'}</div>
    <h2>Face photos for the opening animation (${faces.length})</h2>
    <p class="muted">Member Level 3 members upload a photo of their face. Once approved it shows in that member's own opening animation.</p>
    <div class="stack">${faces.map((f) => { const x = f.data(); return `<div class="card pending" data-path="${esc(f.ref.path)}">
      <b>${esc(names[f.id])}</b>${x.photoUrl ? `<img src="${esc(x.photoUrl)}" alt="">` : ""}
      <div class="row"><button class="primary f-ok">Approve</button><button class="danger f-no">Reject</button></div></div>`; }).join("") || '<p class="muted">No face photos to review.</p>'}</div>
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
    const fok = $(".f-ok", el), fno = $(".f-no", el);
    if (fok) fok.onclick = (e) => run(e.target, () => updateDoc(doc(db, path), { status: "approved", reviewedAt: serverTimestamp() }), "Approved").then(() => el.remove());
    if (fno) fno.onclick = (e) => run(e.target, () => updateDoc(doc(db, path), { status: "rejected", reviewedAt: serverTimestamp() }), "Rejected").then(() => el.remove());
  });
  $$("[data-target]").forEach((el) => {
    $(".r-remove", el).onclick = (e) => confirm("Delete the reported post?") && run(e.target, async () => {
      if (!REPORTABLE.test(el.dataset.target)) throw new Error("This report does not point at a member's post. Dismiss it instead.");
      await deleteDoc(doc(db, el.dataset.target)).catch(() => {});
      await deleteDoc(doc(db, "reports", el.dataset.id));
    }, "Removed").then(() => el.remove());
    $(".r-dismiss", el).onclick = (e) => run(e.target, () => deleteDoc(doc(db, "reports", el.dataset.id)), "Dismissed").then(() => el.remove());
  });
}

// ---------- announcements ----------
/** A picture, audio or video saved on a post or an older announcement, playable here. */
function mediaHtml(x) {
  const url = typeof x.mediaUrl === "string" && x.mediaUrl.startsWith("https://") ? esc(x.mediaUrl) : "";
  if (!url) return "";
  if (x.mediaType === "image") return `<img src="${url}" alt="" style="max-width:100%;max-height:320px;border-radius:12px">`;
  if (x.mediaType === "audio") return `<audio controls preload="none" src="${url}" style="width:100%"></audio>`;
  if (x.mediaType === "video") return `<video controls preload="metadata" src="${url}" style="max-width:100%;max-height:360px;border-radius:12px"></video>`;
  return "";
}

async function announcements() {
  const snap = await getDocs(query(collection(db, "announcements"), orderBy("createdAt", "desc"), limit(50)));
  view().innerHTML = `
    <h1>Announcements</h1>
    <div class="card stack"><label>Title<input id="a-title" maxlength="150"></label><label>Message<textarea id="a-body" maxlength="2000"></textarea></label>
      <p class="muted">For a Zoom meeting or a new course: words only, links work. Members reply in words. Members with announcement notifications on get a push.</p>
      <button class="primary" id="a-post">Post</button></div>
    <h2>Posted</h2><div class="stack">${snap.docs.map((d) => { const x = d.data(); return `<div class="card stack" data-id="${esc(d.id)}"><div><b>${esc(x.title)}</b>
      <span class="muted">${fmt(x.createdAt)}</span></div><p>${esc(x.body)}</p>${mediaHtml(x)}<button class="link danger a-del">Delete</button></div>`; }).join("") || '<p class="muted">None yet.</p>'}</div>`;
  $("#a-post").onclick = (e) => {
    const title = $("#a-title").value.trim(), body = $("#a-body").value.trim();
    if (!title) return toast("Title is required", true);
    run(e.target, () => addDoc(collection(db, "announcements"), { title, body, createdAt: serverTimestamp() }), "Posted").then(announcements);
  };
  $$(".a-del").forEach((b) => (b.onclick = () => confirm("Delete this announcement?") &&
    run(b, () => deleteDoc(doc(db, "announcements", b.closest("[data-id]").dataset.id)), "Deleted").then(announcements)));
}

// ---------- calls: requests, schedule, history, Admin-team call receiver ----------
const IST_FMT = new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
const ist = (ts) => (ts?.toDate ? IST_FMT.format(ts.toDate()) : "");
const secs = (n) => (n > 0 ? `${Math.floor(n / 60)}:${String(n % 60).padStart(2, "0")}` : "");

/** "2026-11-07T16:30" typed in India time -> Date. */
function istInput(v) {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(v || "");
  if (!m) return null;
  return new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]) - 330 * 60 * 1000);
}

async function calls() {
  const [reqSnap, callSnap, staffDoc, adminsSnap] = await Promise.all([
    getDocs(query(collection(db, "callRequests"), orderBy("at", "desc"), limit(100))),
    getDocs(query(collection(db, "calls"), orderBy("createdAt", "desc"), limit(100))),
    getDoc(doc(db, "config", "staff")),
    getDocs(query(collection(db, "users"), where("role", "==", "admin"))),
  ]);
  const staff = staffDoc.data() || {};
  const now = Date.now();
  const reqs = reqSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const open = reqs.filter((r) => ["pending", "proposed", "confirmed"].includes(r.status));
  const upcoming = open.filter((r) => r.status === "confirmed" && r.at?.toMillis() > now - 3600e3).sort((a, b) => a.at.toMillis() - b.at.toMillis());
  const row = (r) => `<tr data-id="${esc(r.id)}"><td>${esc(ist(r.status === "proposed" ? r.proposedAt : r.at))}</td><td>${esc(r.memberName || r.memberUid)}</td>
    <td>${r.channel === "admin" ? "Admin team" : "Swamiji"}</td><td>${r.video ? "Video" : "Audio"}</td><td>${esc(r.note || "")}</td><td><b>${esc(r.status)}</b></td>
    <td class="row">${r.status === "pending" ? `<button class="primary c-ok">Confirm</button><input type="datetime-local" class="c-when" title="India time">
      <button class="c-prop">Propose</button><button class="link danger c-no">Decline</button>` : r.status === "confirmed" ? '<button class="link danger c-cancel">Cancel</button>' : ""}</td></tr>`;
  view().innerHTML = `
    <h1>Calls</h1>
    <div class="card stack"><h3>Admin team call receiver</h3>
      <p class="muted">Members' audio and video calls to the Admin team ring this admin. Admin-team chat goes to every admin.</p>
      <div class="row"><select id="c-support"><option value="">Nobody (Admin-team calls hidden in the app)</option>
        ${adminsSnap.docs.map((d) => `<option value="${esc(d.id)}" ${staff.supportUid === d.id ? "selected" : ""}>${esc(d.get("displayName") || d.id)}</option>`).join("")}</select>
      <button class="primary" id="c-save">Save</button></div></div>
    <div class="card stack" style="margin-top:12px"><h3>Upcoming confirmed calls</h3>
      ${upcoming.length ? `<ul>${upcoming.map((r) => `<li><b>${esc(ist(r.at))}</b> · ${esc(r.memberName || "")} · ${r.channel === "admin" ? "Admin team" : "Swamiji"} · ${r.video ? "Video" : "Audio"}</li>`).join("")}</ul>` : '<p class="muted">None.</p>'}
      <p class="muted">Both sides get a reminder 15 minutes before and when the call starts. Start the call from the app (Ask → Inbox → Call requests).</p></div>
    <h2>Requests</h2>
    <div class="card"><table><thead><tr><th>When (IST)</th><th>Member</th><th>With</th><th>Kind</th><th>Note</th><th>Status</th><th></th></tr></thead>
      <tbody>${reqs.map(row).join("") || '<tr><td colspan="7" class="muted">No requests yet.</td></tr>'}</tbody></table></div>
    <h2>Call history</h2>
    <div class="card"><table><thead><tr><th>When</th><th>Caller</th><th>Called</th><th>Kind</th><th>Result</th><th>Length</th></tr></thead>
      <tbody>${callSnap.docs.map((d) => { const c = d.data(); const result = c.seconds > 0 ? "Talked" : c.status === "declined" ? "Declined" : c.status === "ringing" || c.status === "active" ? "In progress" : "Missed";
        return `<tr><td>${esc(fmt(c.createdAt))}</td><td>${esc(c.callerName || c.callerUid)}</td><td>${esc(c.calleeName || c.calleeUid)}</td><td>${c.video ? "Video" : "Audio"}</td><td>${result}</td><td>${secs(c.seconds || 0)}</td></tr>`; }).join("")
        || '<tr><td colspan="6" class="muted">No calls yet.</td></tr>'}</tbody></table></div>`;

  const me = auth.currentUser;
  const answer = (id, data, msg) => run(null, () => updateDoc(doc(db, "callRequests", id), { staffUid: me.uid, staffName: me.displayName || "Admin", ...data }), msg).then(calls);
  $("#c-save").onclick = (e) => {
    const sel = $("#c-support");
    const name = sel.value ? sel.options[sel.selectedIndex].text : "";
    run(e.target, () => setDoc(doc(db, "config", "staff"), { supportUid: sel.value, supportName: name }, { merge: true }), "Saved");
  };
  $$(".c-ok").forEach((b) => (b.onclick = () => answer(b.closest("tr").dataset.id, { status: "confirmed" }, "Confirmed")));
  $$(".c-no").forEach((b) => (b.onclick = () => confirm("Decline this request?") && answer(b.closest("tr").dataset.id, { status: "declined" }, "Declined")));
  $$(".c-cancel").forEach((b) => (b.onclick = () => confirm("Cancel this confirmed call?") && answer(b.closest("tr").dataset.id, { status: "cancelled" }, "Cancelled")));
  $$(".c-prop").forEach((b) => (b.onclick = () => {
    const when = istInput(b.closest("tr").querySelector(".c-when").value);
    if (!when || when.getTime() < Date.now()) return toast("Pick a future date and time first", true);
    answer(b.closest("tr").dataset.id, { status: "proposed", proposedAt: Timestamp.fromDate(when) }, "New time sent");
  }));
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
  const about = (await getDoc(doc(db, "config", "about"))).data() || {};
  const ta = (id, label) => `<label>${label}<textarea id="ab-${id}" maxlength="8000">${esc(about[id] || "")}</textarea></label>`;
  const n = (path, dflt) => path.split(".").reduce((o, k) => o?.[k], lv) ?? dflt;
  const lvl = (k, d, p) => `<tr><td>${k.toUpperCase()}</td><td><input type="number" id="${k}-days" value="${esc(n(`${k}.days`, d))}" style="width:100px"></td><td><input type="number" id="${k}-points" value="${esc(n(`${k}.points`, p))}" style="width:120px"></td></tr>`;
  view().innerHTML = `
    <h1>Settings</h1>
    <div class="card stack"><h3>Free user levels</h3><p class="muted">A level needs both: days of using the app and Freedom Points. Changes apply at each member's next point.</p>
      <table><thead><tr><th>Level</th><th>Days</th><th>Points</th></tr></thead><tbody>${lvl("l2", 7, 7)}${lvl("l3", 90, 200)}${lvl("l4", 365, 10000)}</tbody></table>
      <h3>Daily point limits</h3><div class="row">
        <label>Likes<input type="number" id="cap-like" value="${esc(n("dailyCaps.like", 50))}" style="width:90px"></label>
        <label>Comments and answers<input type="number" id="cap-comment" value="${esc(n("dailyCaps.comment", 20))}" style="width:90px"></label>
        <label>Questions<input type="number" id="cap-question" value="${esc(n("dailyCaps.question", 5))}" style="width:90px"></label></div>
      <button class="primary" id="s-levels">Save levels</button></div>
    <div class="card stack" style="margin-top:12px"><h3>Calls</h3>
      <p>Members call: <b>${esc(staff.swamijiUid || "nobody yet: set someone's role to Swamiji on the Members page")}</b></p>
      <p class="muted">If calls ring but never connect on some networks, add a TURN relay here (no app update needed).</p>
      <label>TURN URLs (comma separated)<input id="turn-urls" value="${esc((turn.urls || []).join(", "))}" placeholder="turn:turn.example.com:3478"></label>
      <div class="row"><label>Username<input id="turn-user" value="${esc(turn.username || "")}"></label><label>Password<input id="turn-pass" value="${esc(turn.credential || "")}"></label></div>
      <button class="primary" id="s-turn">Save calls</button></div>
    <div class="card stack" style="margin-top:12px"><h3>About box on the Home tab</h3>
      <p class="muted">Leave a box empty to show the app's built-in text. Hindi and Gujarati are optional; English shows when they are empty.</p>
      ${ta("ojasFamily", "About Ojas Family (English)")}${ta("ojasFamily_hi", "About Ojas Family (Hindi)")}${ta("ojasFamily_gu", "About Ojas Family (Gujarati)")}
      ${ta("swamiji", "About Swamiji (English)")}${ta("swamiji_hi", "About Swamiji (Hindi)")}${ta("swamiji_gu", "About Swamiji (Gujarati)")}
      ${ta("help", "App help: how to use the app (English)")}${ta("help_hi", "App help (Hindi)")}${ta("help_gu", "App help (Gujarati)")}
      ${ta("legal", "Legal disclaimer (English)")}${ta("legal_hi", "Legal disclaimer (Hindi)")}${ta("legal_gu", "Legal disclaimer (Gujarati)")}
      <label>Extra links, one per line: Title | https://link<textarea id="ab-links">${esc((about.links || []).map((l) => `${l.title} | ${l.url}`).join("\n"))}</textarea></label>
      <button class="primary" id="s-about">Save About</button></div>`;
  const num = (id) => Math.max(0, Math.floor(Number($(id).value) || 0));
  $("#s-levels").onclick = (e) => run(e.target, () => setDoc(doc(db, "config", "levels"), {
    l2: { days: num("#l2-days"), points: num("#l2-points") },
    l3: { days: num("#l3-days"), points: num("#l3-points") },
    l4: { days: num("#l4-days"), points: num("#l4-points") },
    dailyCaps: { like: num("#cap-like"), comment: num("#cap-comment"), question: num("#cap-question") },
  }, { merge: true }), "Levels saved");
  $("#s-about").onclick = (e) => {
    const links = $("#ab-links").value.split("\n").map((line) => line.split("|").map((x) => x.trim())).filter((p) => p.length >= 2 && p[0] && p[1]);
    const bad = links.find((p) => !p[1].startsWith("https://"));
    if (bad) return toast(`Links must start with https:// (${bad[0]})`, true);
    const data = { links: links.map(([title, url]) => ({ title: title.slice(0, 60), url: url.slice(0, 500) })) };
    for (const k of ["ojasFamily", "ojasFamily_hi", "ojasFamily_gu", "swamiji", "swamiji_hi", "swamiji_gu", "help", "help_hi", "help_gu", "legal", "legal_hi", "legal_gu"]) data[k] = $(`#ab-${k}`).value.trim();
    run(e.target, () => setDoc(doc(db, "config", "about"), data), "About saved");
  };
  $("#s-turn").onclick = (e) => run(e.target, () => setDoc(doc(db, "config", "turn"), {
    urls: $("#turn-urls").value.split(",").map((s) => s.trim()).filter(Boolean), username: $("#turn-user").value.trim(), credential: $("#turn-pass").value.trim(),
  }), "Saved");
}

// ---------- Freedom Points: amounts, gifting rules, one member's points, gifts ----------
const POINT_FIELDS = [
  ["dailyOpen", "Daily app open", 1], ["practice", "Each guided practice finished (once a day each)", 2],
  ["allPracticesBonus", "Bonus: all four practices in one day", 3], ["streakBonus", "Streak bonus", 10],
  ["streakBonusEvery", "Streak bonus every … days in a row", 7], ["like", "Like", 1], ["comment", "Comment", 1],
  ["question", "Question", 1], ["answer", "Answer", 1], ["profileField", "Each profile item filled in (once)", 5],
  ["profileComplete", "Bonus: profile 100% (once)", 25], ["aiDaily", "First Ask Swamiji AI question of the day", 1],
  ["maxTransfer", "Largest single gift between members", 100], ["dailySendCap", "Most a member can gift per day", 100],
  ["minLevelToSend", "Level needed to gift points", 2],
];

async function points() {
  const cfg = (await getDoc(doc(db, "config", "points"))).data() || {};
  const transfers = await getDocs(query(collection(db, "pointTransfers"), orderBy("createdAt", "desc"), limit(50)));
  const adjustments = await getDocs(query(collection(db, "pointAdjustments"), orderBy("createdAt", "desc"), limit(30)));
  const when = (t) => (t?.toDate ? t.toDate().toLocaleString() : "");
  view().innerHTML = `
    <h1>Freedom Points</h1>
    <p class="muted">What each activity earns and the gifting rules. Members see the same amounts in the app under "How it works". Swamiji can change them in the app too.</p>
    <div class="card stack"><h3>Points for each activity</h3>
      <table><tbody>${POINT_FIELDS.map(([k, label, d]) => `<tr><td>${esc(label)}</td><td><input type="number" min="0" max="100000" id="pt-${k}" value="${esc(cfg[k] ?? d)}" style="width:110px"></td></tr>`).join("")}
      <tr><td>Members can gift points to each other</td><td><input type="checkbox" id="pt-transfersEnabled" ${cfg.transfersEnabled === false ? "" : "checked"}></td></tr></tbody></table>
      <button class="primary" id="pt-save">Save amounts</button></div>

    <div class="card stack" style="margin-top:12px"><h3>Change one member's points</h3>
      <p class="muted">On the Members page, hold the pointer on a member's name to see their id, and paste it here. Swamiji and admins can also do this in the app from the member's profile. "Count for Level" also changes the points their Level is based on.</p>
      <div class="row">
        <label>Member id<input id="adj-uid" style="width:260px"></label>
        <label>Change<select id="adj-mode"><option value="add">Add</option><option value="deduct">Remove</option><option value="set">Set to</option></select></label>
        <label>Points<input id="adj-amount" type="number" min="0" style="width:110px"></label>
        <label><input type="checkbox" id="adj-earned"> Count for Level</label>
      </div>
      <div class="row"><label>Reason (the member sees it)<input id="adj-note" style="width:320px"></label>
        <button class="primary" id="adj-go">Apply</button>
        <button id="adj-freeze">Pause gifting</button><button id="adj-unfreeze">Resume gifting</button></div></div>

    <div class="card" style="margin-top:12px"><h3>Latest gifts between members</h3>
      <table><thead><tr><th>When</th><th>From</th><th>To</th><th>Points</th><th>Note</th><th></th></tr></thead><tbody>${
        transfers.docs.map((d) => { const t = d.data(); return `<tr><td>${esc(when(t.createdAt))}</td><td>${esc(t.fromName)}</td><td>${esc(t.toName)}</td><td>${esc(t.amount)}</td><td>${esc(t.note || "")}</td>
          <td>${t.status === "reversed" ? `<span class="muted">Undone</span>` : `<button class="link" data-reverse="${esc(d.id)}">Undo</button>`}</td></tr>`; }).join("") || `<tr><td colspan="6" class="muted">No gifts yet</td></tr>`
      }</tbody></table></div>

    <div class="card" style="margin-top:12px"><h3>Changes made by Swamiji and admins</h3>
      <table><thead><tr><th>When</th><th>Member</th><th>By</th><th>Change</th><th>Reason</th></tr></thead><tbody>${
        adjustments.docs.map((d) => { const a = d.data(); return `<tr><td>${esc(when(a.createdAt))}</td><td>${esc(a.uid)}</td><td>${esc(a.byRole)}</td>
          <td>${esc(a.mode)}${a.amount !== undefined ? ` ${esc(a.amount)} (${esc(a.before)} → ${esc(a.after)})` : ""}</td><td>${esc(a.note || "")}</td></tr>`; }).join("") || `<tr><td colspan="5" class="muted">None yet</td></tr>`
      }</tbody></table></div>`;

  $("#pt-save").onclick = (e) => {
    const data = { transfersEnabled: $("#pt-transfersEnabled").checked };
    for (const [k] of POINT_FIELDS) data[k] = Math.max(0, Math.floor(Number($(`#pt-${k}`).value) || 0));
    run(e.target, () => call("updatePointsConfig", data), "Amounts saved");
  };
  const uid = () => $("#adj-uid").value.trim();
  $("#adj-go").onclick = (e) => {
    if (!uid()) return toast("Enter the member id", true);
    if (!$("#adj-note").value.trim()) return toast("Add a reason", true);
    const body = { uid: uid(), mode: $("#adj-mode").value, amount: Number($("#adj-amount").value), target: $("#adj-earned").checked ? "earned" : "balance", note: $("#adj-note").value };
    if (!confirm(`${body.mode} ${body.amount} points for ${body.uid}?`)) return;
    run(e.target, () => call("staffAdjustPoints", body), (r) => `Done. Balance ${r.balance}, Level ${r.userLevel}`).then(points);
  };
  $("#adj-freeze").onclick = (e) => uid() ? run(e.target, () => call("setTransfersFrozen", { uid: uid(), frozen: true }), "Gifting paused") : toast("Enter the member id", true);
  $("#adj-unfreeze").onclick = (e) => uid() ? run(e.target, () => call("setTransfersFrozen", { uid: uid(), frozen: false }), "Gifting resumed") : toast("Enter the member id", true);
  $$("[data-reverse]").forEach((b) => (b.onclick = () => {
    if (!confirm("Undo this gift? The points go back to the sender (as far as the receiver still has them).")) return;
    run(b, () => call("reverseTransfer", { transferId: b.dataset.reverse }), (r) => `${r.returned} points returned`).then(points);
  }));
}

// ---------- Practice tab: the one guided video or audio for each practice ----------
const PRACTICE_SECTIONS = ["blue_light", "quick_protection", "morning", "night"];

/** The 11-character YouTube id from a link or a bare id; "" when there is none. */
function youTubeId(text) {
  const t = (text || "").trim();
  if (/^[A-Za-z0-9_-]{11}$/.test(t)) return t;
  const m = /(?:youtu\.be\/|[?&]v=|\/embed\/|\/shorts\/|\/live\/)([A-Za-z0-9_-]{11})/.exec(t);
  return m ? m[1] : "";
}

/** "1:02:05", "12:30" or "750" to seconds; 0 when it can't be read. */
function toSeconds(text) {
  const parts = (text || "").trim().split(":").map((x) => Number(x));
  if (!parts.length || parts.some((n) => !Number.isFinite(n) || n < 0)) return 0;
  return Math.floor(parts.reduce((acc, n) => acc * 60 + n, 0));
}

const clock = (sec) => {
  const s = Math.max(0, Math.floor(Number(sec) || 0));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
  return h ? `${h}:${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}` : `${m}:${String(r).padStart(2, "0")}`;
};

async function practice() {
  const cfg = (await getDoc(doc(db, "config", "practiceMedia"))).data() || {};
  const card = (key) => {
    const m = cfg[key] || {};
    const yt = m.kind === "youtube";
    const now = !m.kind ? `<span class="error">Nothing set: this practice is hidden in the app</span>`
      : yt ? `YouTube <a href="https://www.youtube.com/watch?v=${esc(m.videoId)}" target="_blank" rel="noopener">${esc(m.videoId)}</a> · ${esc(clock(m.durationSec))}`
        + (m.fallbackVideoId ? `<br>Fallback <a href="https://www.youtube.com/watch?v=${esc(m.fallbackVideoId)}" target="_blank" rel="noopener">${esc(m.fallbackVideoId)}</a> · ${esc(clock(m.fallbackDurationSec))}` : "")
      : `${esc(m.kind === "audio" ? "Audio" : "Video")} file · ${esc(clock(m.durationSec))}`;
    return `<div class="card stack" data-sec="${key}">
      <h3>${esc(SECTIONS[key])}</h3>
      <p class="muted">${esc(m.title || "")}</p>
      <p>${now}</p>
      ${yt ? `<p class="muted">If the app shows "Open in YouTube", the channel has embedding turned off for this video:
        <a href="https://www.youtube.com/embed/${esc(m.videoId)}" target="_blank" rel="noopener">check it here</a> (it must play, not say "Video unavailable").</p>` : ""}
      <label>Title shown in the app<input class="pm-title" value="${esc(m.title || "")}" maxlength="120"></label>
      <label>Length (h:mm:ss, mm:ss or seconds)<input class="pm-length" value="${esc(m.durationSec ? clock(m.durationSec) : "")}" style="width:140px"></label>
      <details ${yt || !m.kind ? "open" : ""}><summary>Play a YouTube video instead</summary>
        <div class="stack" style="margin-top:8px">
          <label>YouTube link<input class="pm-yt" value="${yt ? esc(`https://youtu.be/${m.videoId}`) : ""}" placeholder="https://youtu.be/..."></label>
          <label>Fallback YouTube link (optional: plays when the main one can't play in the app)<input class="pm-fb" value="${m.fallbackVideoId ? esc(`https://youtu.be/${m.fallbackVideoId}`) : ""}" placeholder="https://youtu.be/..."></label>
          <label>Fallback length<input class="pm-fb-length" value="${esc(m.fallbackDurationSec ? clock(m.fallbackDurationSec) : "")}" style="width:140px"></label>
        </div></details>
      <button class="primary pm-save">Save</button>
    </div>`;
  };
  view().innerHTML = `
    <h1>Practice tab</h1>
    <p class="muted">Each guided practice plays one video or audio. Members earn points when they finish it (90% of the length must really play),
      so the length must match the real video. Hosted files are uploaded with <code>scripts/uploadPracticeMedia.ts</code>; here you can change
      titles and lengths, or switch a practice to a YouTube video (the video's "Allow embedding" must be on in YouTube Studio).</p>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(340px,1fr))">${PRACTICE_SECTIONS.map(card).join("")}</div>`;

  $$(".pm-save").forEach((btn) => (btn.onclick = () => {
    const box = btn.closest("[data-sec]");
    const key = box.dataset.sec;
    const m = cfg[key] || {};
    const val = (cls) => box.querySelector(cls).value;
    const title = val(".pm-title").trim();
    const seconds = toSeconds(val(".pm-length"));
    const ytText = val(".pm-yt").trim();
    const fbText = val(".pm-fb").trim();
    if (!(seconds > 0)) return toast("Enter the length, like 12:30", true);
    const data = { title: title || m.title || SECTIONS[key], durationSec: seconds };
    if (ytText) {
      const id = youTubeId(ytText);
      if (!id) return toast("That YouTube link doesn't look right", true);
      Object.assign(data, { kind: "youtube", videoId: id, url: deleteField() });
      if (fbText) {
        const fb = youTubeId(fbText);
        const fbSeconds = toSeconds(val(".pm-fb-length"));
        if (!fb) return toast("That fallback link doesn't look right", true);
        if (fb === id) return toast("The fallback must be a different video", true);
        if (!(fbSeconds > 0)) return toast("Enter the fallback's length", true);
        Object.assign(data, { fallbackVideoId: fb, fallbackDurationSec: fbSeconds });
      } else {
        Object.assign(data, { fallbackVideoId: deleteField(), fallbackDurationSec: deleteField() });
      }
    } else if (m.kind === "youtube") {
      return toast("Add the YouTube link (a hosted file is uploaded with the script)", true);
    } else if (!m.kind) {
      return toast("Add a YouTube link, or upload a file with the script", true);
    }
    if (!confirm(`Save ${SECTIONS[key]}? Members see the change right away.`)) return;
    run(btn, () => setDoc(doc(db, "config", "practiceMedia"), { [key]: data }, { merge: true }), "Saved").then(practice);
  }));
}

// ---------- Ask Swamiji AI ----------
async function ai() {
  const cfg = (await getDoc(doc(db, "config", "ai"))).data() || {};
  const feedback = await getDocs(query(collection(db, "aiFeedback"), orderBy("createdAt", "desc"), limit(50)));
  const sources = await getDocs(query(collection(db, "knowledgeSources"), limit(500)));
  const done = sources.docs.filter((d) => d.get("status") === "done").length;
  const failed = sources.docs.filter((d) => d.get("status") === "failed");
  view().innerHTML = `
    <h1>Ask Swamiji AI</h1>
    <p class="muted">Answers come from Swamiji's teachings only: notes on ${done} Ojas Healing videos plus the About texts, in English, Hindi and Gujarati.
      A hosted AI model (NVIDIA API) writes each answer on the server; the key is kept as a server secret. Conversations stay on members' phones; only answers they rate come here.</p>
    <div class="card stack"><h3>Settings</h3>
      <div class="row">
        <label><input type="checkbox" id="ai-enabled" ${cfg.enabled === false ? "" : "checked"}> AI is on</label>
        <label>Questions a day (free)<input type="number" id="ai-free" min="0" value="${esc(cfg.dailyFree ?? 5)}" style="width:90px"></label>
        <label>Questions a day (members, staff)<input type="number" id="ai-member" min="0" value="${esc(cfg.dailyMember ?? 5)}" style="width:90px"></label>
        <label>Passages per answer<input type="number" id="ai-topk" min="1" max="8" value="${esc(cfg.topK ?? 5)}" style="width:70px"></label>
        <label>Model<input id="ai-model" value="${esc(cfg.model || "nvidia/nemotron-3-ultra-550b-a55b")}" style="width:300px"></label>
      </div>
      <button class="primary" id="ai-save">Save</button></div>
    ${failed.length ? `<div class="card" style="margin-top:12px"><h3>Videos that could not be read (${failed.length})</h3><ul>${failed.slice(0, 30).map((d) => `<li>${esc(d.get("title"))} <span class="muted">${esc(d.get("error") || "")}</span></li>`).join("")}</ul></div>` : ""}
    <div class="card" style="margin-top:12px"><h3>Members' feedback on answers</h3>
      <table><thead><tr><th></th><th>Question</th><th>Answer</th><th>Comment</th></tr></thead><tbody>${
        feedback.docs.map((d) => { const f = d.data(); return `<tr><td>${f.rating > 0 ? "👍" : "👎"}</td><td>${esc(f.question)}</td><td><details><summary>Show</summary>${esc(f.answer)}</details></td><td>${esc(f.comment || "")}</td></tr>`; }).join("") || `<tr><td colspan="4" class="muted">No feedback yet</td></tr>`
      }</tbody></table></div>`;
  $("#ai-save").onclick = (e) => run(e.target, () => setDoc(doc(db, "config", "ai"), {
    enabled: $("#ai-enabled").checked,
    dailyFree: Math.max(0, Math.floor(Number($("#ai-free").value) || 0)),
    dailyMember: Math.max(0, Math.floor(Number($("#ai-member").value) || 0)),
    topK: Math.min(8, Math.max(1, Math.floor(Number($("#ai-topk").value) || 5))),
    model: $("#ai-model").value.trim() || "nvidia/nemotron-3-ultra-550b-a55b",
  }, { merge: true }), "AI settings saved");
}
