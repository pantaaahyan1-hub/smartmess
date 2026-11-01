"use strict";
let state,
  admin,
  view = "overview",
  stream = null,
  scanTimer = null,
  busy = false,
  charts = [],
  selectedDay = null,
  adminDay = null;
const $ = (s) => document.querySelector(s),
  esc = (s) =>
    String(s ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    ),
  money = (n) =>
    "₹" + Number(n || 0).toLocaleString("en-IN", { maximumFractionDigits: 2 }),
  icons = { BREAKFAST: "🍳", LUNCH: "🍛", SNACKS: "🥪", DINNER: "🍲" };
async function api(path, method = "GET", data) {
  const headers = { "Content-Type": "application/json" };
  headers[$("meta[name=_csrf_header]").content] = $("meta[name=_csrf]").content;
  const r = await fetch("/api" + path, {
    method,
    headers,
    body: data === undefined ? undefined : JSON.stringify(data),
  });
  if (r.redirected) {
    location.href = "/login";
    throw Error("Session expired");
  }
  const text = await r.text();
  let json;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw Error(
      r.status === 403
        ? "You do not have permission for this action."
        : "Unexpected server response",
    );
  }
  if (!r.ok) throw Error(json.message || "Request failed (" + r.status + ")");
  return json;
}
function toast(message, error = false) {
  const t = $("#toast");
  t.textContent = message;
  t.style.background = error ? "#ac4541" : "#1b6b4b";
  t.style.display = "block";
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.style.display = "none"), 5500);
}
async function action(fn) {
  try {
    await fn();
  } catch (e) {
    toast(e.message, true);
  }
}
function heading(title, sub, extra = "") {
  return `<div class="page-heading"><div><h1>${title}</h1><p>${sub}</p></div>${extra || `<div class="date-chip">◷ ${new Date(state.today + "T12:00:00").toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", year: "numeric" })}</div>`}</div>`;
}
function stat(label, value, note, accent = false) {
  return `<div class="stat ${accent ? "accent" : ""}"><div class="stat-label">${label}<span>↗</span></div><strong>${value}</strong><small>${note}</small></div>`;
}
function panel(title, sub, body, extra = "") {
  return `<section class="panel"><div class="panel-head"><div><h3>${title}</h3>${sub ? `<p>${sub}</p>` : ""}</div>${extra}</div>${body}</section>`;
}
function table(headers, rows) {
  return `<div class="table-wrap"><table><thead><tr>${headers.map((x) => `<th>${x}</th>`).join("")}</tr></thead><tbody>${rows.length ? rows.join("") : `<tr><td colspan="${headers.length}" class="empty">No records yet. Your activity will appear here.</td></tr>`}</tbody></table></div>`;
}
function row(cells) {
  return "<tr>" + cells.map((c) => "<td>" + c + "</td>").join("") + "</tr>";
}
function chart(id, type, labels, datasets) {
  if (!window.Chart) return;
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: datasets.length > 1,
        position: "bottom",
        labels: { boxWidth: 8, usePointStyle: true, font: { size: 10 } },
      },
    },
    scales:
      type === "doughnut"
        ? {}
        : {
            x: { grid: { display: false }, ticks: { font: { size: 10 } } },
            y: {
              beginAtZero: true,
              grid: { color: "#edf1e9" },
              ticks: { font: { size: 10 } },
            },
          },
  };
  charts.push(
    new Chart($("#" + id), { type, data: { labels, datasets }, options }),
  );
}
function dates(n) {
  return Array.from({ length: n }, (_, i) => {
    let d = new Date(state.today + "T12:00:00");
    d.setDate(d.getDate() - (n - 1 - i));
    return d.toISOString().slice(0, 10);
  });
}
async function refresh() {
  state = await api("/me");
  const order = ["BREAKFAST", "LUNCH", "SNACKS", "DINNER"];
  state.meals.sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id));
  if (state.user.role === "ADMIN") {
    admin = await api("/admin/data" + (adminDay ? "?day=" + adminDay : ""));
    admin.meals.sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id));
    admin.forecasts.sort(
      (a, b) => order.indexOf(a.mealId) - order.indexOf(b.mealId),
    );
  }
}
function navigation() {
  const role = state.user.role;
  const items =
    role === "STUDENT"
      ? [
          ["overview", "◫", "Overview"],
          ["meals", "♧", "My meals"],
          ["wallet", "▣", "Wallet & spending"],
          ["history", "◷", "Attendance"],
          ["face", "◎", "Face enrollment"],
        ]
      : role === "ADMIN"
        ? [
            ["overview", "◫", "Overview"],
            ["students", "♙", "Students & wallets"],
            ["manage", "☷", "Meals & schedules"],
            ["forecast", "✳", "Food intelligence"],
            ["records", "◷", "Bookings & reports"],
            ["scanner", "◎", "Entrance scanner"],
          ]
        : [["scanner", "◎", "Entrance scanner"]];
  if (!items.some((i) => i[0] === view)) view = items[0][0];
  $("#nav").innerHTML = items
    .map(
      ([v, i, t]) =>
        `<button data-view="${v}" class="${view === v ? "active" : ""}"><span>${i}</span>${t}</button>`,
    )
    .join("");
  $("#nav")
    .querySelectorAll("button")
    .forEach((b) => (b.onclick = () => action(() => navigate(b.dataset.view))));
  $("#username").textContent = state.user.name;
  $("#avatar").textContent = state.user.name.charAt(0);
  $("#breadcrumb").textContent = items.find((i) => i[0] === view)[2];
}
async function navigate(next) {
  stopCamera();
  if (next !== "forecast") adminDay = null;
  view = next;
  await refresh();
  render();
}
function render() {
  charts.forEach((c) => c.destroy());
  charts = [];
  navigation();
  $("#content").classList.remove("fade");
  const role = state.user.role;
  if (role === "STUDENT") {
    if (view === "overview" || view === "meals") studentMeals();
    if (view === "wallet") wallet();
    if (view === "history") history();
    if (view === "face") camera(false);
  } else {
    if (view === "overview") adminOverview();
    if (view === "students") students();
    if (view === "manage") manage();
    if (view === "forecast") forecast();
    if (view === "records") {
      records();
      bookingControls();
    }
    if (view === "scanner") camera(true);
  }
  void $("#content").offsetWidth;
  $("#content").classList.add("fade");
}
function bookingControls() {
  $("#content").insertAdjacentHTML(
    "beforeend",
    panel(
      "Manage a booking",
      "Changes follow the same selection deadline as student bookings.",
      `<form id="admin-booking" class="inline-form"><label>Student<select name="studentId">${admin.students
        .filter((s) => s.role === "STUDENT")
        .map((s) => `<option value="${s.id}">${esc(s.name)}</option>`)
        .join(
          "",
        )}</select></label><label>Date<input type="date" name="day" min="${state.today}" value="${state.today}" required></label><label>Meal<select name="meal">${admin.meals.map((m) => `<option>${m.id}</option>`).join("")}</select></label><label>Choice<select name="yes"><option value="true">YES</option><option value="false">NO</option></select></label><button class="primary">Save booking</button></form>`,
    ),
  );
  $("#admin-booking").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      const b = Object.fromEntries(new FormData(e.target));
      b.studentId = Number(b.studentId);
      b.yes = b.yes === "true";
      await api("/admin/booking", "POST", b);
      await refresh();
      render();
      toast("Booking updated; no wallet charge");
    });
  };
}
function mealCards() {
  const day = selectedDay || state.today;
  return `<div class="meals-grid">${state.meals
    .map((m) => {
      let b = state.bookings.find((b) => b.day === day && b.mealId === m.id);
      const cutoff = new Date(day + "T" + m.starts + "+05:30");
      cutoff.setTime(cutoff.getTime() - 3 * 3600000);
      const locked = new Date(state.serverTime + "+05:30") >= cutoff;
      const cutoffLabel = cutoff.toLocaleString("en-IN", { timeZone: "Asia/Kolkata", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", hour12: false });
      return `<article class="meal-card"><div class="meal-art"><span>${icons[m.id]}</span><span class="pill ${locked ? "gray" : ""}">${locked ? "Selections closed" : "Open for selection"}</span></div><div class="meal-body"><div class="meal-title"><span>${esc(m.title)}</span><span>${money(m.price)}</span></div><p>${esc(m.menu)}</p><div class="meal-time">◷ ${m.starts.slice(0, 5)} – ${m.ends.slice(0, 5)} IST</div><div class="choice"><button data-meal="${m.id}" data-yes="true" class="${b?.attending ? "selected" : ""}" ${locked ? "disabled" : ""}>YES</button><button data-meal="${m.id}" data-yes="false" class="no ${b && !b.attending ? "selected" : ""}" ${locked ? "disabled" : ""}>NO</button></div><span class="deadline">${b ? "Saved: " + (b.attending ? "YES" : "NO") : "Pending — automatic YES at deadline"} · Deadline ${cutoffLabel} IST</span></div></article>`;
    })
    .join("")}</div>`;
}
function bindMeals() {
  $("#meal-day").onchange = (e) => {
    selectedDay = e.target.value;
    render();
  };
  document.querySelectorAll("[data-meal]").forEach(
    (b) =>
      (b.onclick = () =>
        action(async () => {
          b.disabled = true;
          await api("/selection", "POST", {
            meal: b.dataset.meal,
            day: selectedDay || state.today,
            yes: b.dataset.yes === "true",
          });
          await refresh();
          render();
          toast("Meal choice saved. You are charged only when you attend.");
        })),
  );
}
function studentMeals() {
  const p = state.user,
    today = state.attendance.filter((a) => a.day === state.today),
    spent = today.reduce((s, a) => s + Number(a.amount), 0),
    chosen = state.bookings.filter(
      (b) => b.day === state.today && b.attending,
    ).length;
  $("#content").innerHTML =
    heading(
      view === "overview"
        ? `Student dashboard · ${esc(p.name)}`
        : "Meal selections",
      "Menus, prices and selections.",
    ) +
    (view === "overview"
      ? `<div class="stats">${stat("Available balance", money(p.wallet), "Prepaid internal wallet", true)}${stat("Today’s spending", money(spent), "of " + money(p.dailyBudget) + " daily budget")}${stat("Meals planned", chosen + " / 4", "YES selections for today")}${stat("Meals enjoyed", today.length, "Verified by attendance")}</div>`
      : "") +
    panel(
      "Daily meals",
      "Choose YES or NO until three hours before service. Unselected meals become YES at the deadline; only attendance is charged.",
      mealCards(),
      `<input type="date" aria-label="Meal date" id="meal-day" value="${selectedDay || state.today}" min="${state.today}" max="${new Date(new Date(state.today + "T12:00:00").getTime() + 7 * 86400000).toISOString().slice(0, 10)}" style="width:160px;margin:0">`,
    ) +
    `<div class="notice">ⓘ Choosing YES reserves your intent. Your wallet is charged only after a successful face scan at the mess entrance. Missed meals cost ₹0.</div>` +
    (view === "overview"
      ? `<div class="two-col">${panel("Your spending, at a glance", "Last seven days · actual attendance charges", '<div class="chart-wrap"><canvas id="spend-chart"></canvas></div>')}${panel(
          "Recent activity",
          "Your latest wallet updates",
          state.transactions
            .slice(0, 4)
            .map(
              (t) =>
                `<div class="activity"><span class="activity-icon">${t.amount > 0 ? "↓" : "↗"}</span><div><b>${esc(t.description)}</b><small>${esc(t.createdAt.replace("T", " ").slice(0, 16))}</small></div><strong>${money(t.amount)}</strong></div>`,
            )
            .join("") || '<p class="empty">No activity yet.</p>',
        )}</div>`
      : "");
  bindMeals();
  if (view === "overview") {
    const days = dates(7);
    chart(
      "spend-chart",
      "bar",
      days.map((d) => d.slice(5)),
      [
        {
          label: "Meal spending",
          data: days.map((d) =>
            state.attendance
              .filter((a) => a.day === d)
              .reduce((s, a) => s + Number(a.amount), 0),
          ),
          backgroundColor: "#8cae70",
          borderRadius: 5,
        },
      ],
    );
  }
}
function wallet() {
  $("#content").innerHTML =
    heading(
      "Wallet and budget",
      "A simulated prepaid wallet for campus dining.",
    ) +
    `<div class="stats">${stat("Wallet balance", money(state.user.wallet), "Contact admin to add demo credits", true)}${stat("Daily budget", money(state.user.dailyBudget), "Your personal spending limit")}${stat("Today’s remaining budget", money(Math.max(0, state.user.dailyBudget - state.attendance.filter((a) => a.day === state.today).reduce((s, a) => s + Number(a.amount), 0))), "Based on actual attendance")}${stat("Biometric enrollment", state.faceSamples ? "Active" : "Not enrolled", state.faceSamples + " encrypted samples")}</div>` +
    panel(
      "Set a daily budget",
      "Charges exceeding your remaining budget are declined.",
      `<form id="budget-form" class="inline-form"><label>Daily spending limit (₹)<input name="amount" type="number" step="0.01" min="0" max="10000" value="${state.user.dailyBudget}" required></label><button class="primary">Save budget</button></form>`,
    ) +
    panel(
      "Wallet transactions",
      "DEMO HISTORY entries illustrate historical behavior; opening balances were assigned separately.",
      table(
        ["DATE & TIME", "DESCRIPTION", "AMOUNT"],
        state.transactions.map((t) =>
          row([
            esc(t.createdAt.replace("T", " ").slice(0, 16)),
            esc(t.description),
            `<b>${money(t.amount)}</b>`,
          ]),
        ),
      ),
    );
  $("#budget-form").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      await api("/budget", "POST", { amount: Number(e.target.amount.value) });
      await refresh();
      render();
      toast("Daily budget updated");
    });
  };
}
function history() {
  $("#content").innerHTML =
    heading(
      "Every meal, accounted for.",
      "Attendance is recorded only after a successful entrance verification.",
    ) +
    panel(
      "Your attendance",
      "Historical demo entries are seeded examples; new entries come from the scanner.",
      table(
        ["DATE", "MEAL", "CHARGED", "CHECK-IN"],
        state.attendance.map((a) =>
          row([
            a.day,
            esc(a.mealId),
            money(a.amount),
            a.scannedAt.slice(11, 16) + " IST",
          ]),
        ),
      ),
    );
}
function adminStats() {
  const day = admin.day,
    as = admin.attendance.filter((a) => a.day === day),
    ws = admin.waste.filter((w) => w.day === day),
    waste = ws.reduce((s, w) => s + w.prepared - w.served, 0);
  return `<div class="stats">${stat(
    "Expected guests",
    admin.forecasts.reduce((s, f) => s + f.expected, 0),
    "Across four meal sessions",
    true,
  )}${stat("Meal revenue", money(as.reduce((s, a) => s + Number(a.amount), 0)), "Verified attendance charges")}${stat("Meals served", as.length, "Unique meal check-ins")}${stat("Portions wasted", waste, "From submitted preparation records")}</div>`;
}
function adminOverview() {
  const ds = dates(7);
  $("#content").innerHTML =
    heading(
      "Admin dashboard",
      "Attendance, revenue and preparation.",
    ) +
    adminStats() +
    `<div class="two-col">${panel("Dining trends", "Attendance and waste · last 7 days", '<div class="chart-wrap"><canvas id="trend"></canvas></div>')}${panel("Today’s preparation plan", "Forecasts adapt to YES bookings and historical attendance", admin.forecasts.map((f) => `<div class="activity"><span class="activity-icon">${icons[f.mealId]}</span><div><b>${esc(f.mealId)}</b><small>${f.booked} booked · ${Math.round(f.showRate * 100)}% expected show-up</small></div><strong>${f.recommended} portions</strong></div>`).join(""))}</div>` +
    panel(
      "Live attendance",
      "Refreshes every 30 seconds when this dashboard is open.",
      table(
        ["STUDENT", "MEAL", "TIME", "AMOUNT"],
        admin.attendance
          .filter((a) => a.day === admin.day)
          .sort((a, b) => b.scannedAt.localeCompare(a.scannedAt))
          .slice(0, 8)
          .map((a) =>
            row([
              esc(admin.students.find((s) => s.id === a.studentId)?.name),
              a.mealId,
              a.scannedAt.slice(11, 16),
              money(a.amount),
            ]),
          ),
      ),
    );
  chart(
    "trend",
    "line",
    ds.map((d) => d.slice(5)),
    [
      {
        label: "Served",
        data: ds.map((d) => admin.attendance.filter((a) => a.day === d).length),
        borderColor: "#2e7954",
        backgroundColor: "#e4eddd",
        fill: true,
        tension: 0.35,
      },
      {
        label: "Wasted",
        data: ds.map((d) =>
          admin.waste
            .filter((w) => w.day === d)
            .reduce((s, w) => s + w.prepared - w.served, 0),
        ),
        borderColor: "#c6a56b",
        tension: 0.35,
      },
    ],
  );
}
function students() {
  const users = admin.students.filter((s) => s.role === "STUDENT");
  $("#content").innerHTML =
    heading(
      "Students and wallets",
      "Manage student access, prepaid credits and biometric deletion.",
    ) +
    panel(
      "Add internal wallet credits",
      "Demo credits only. No payment gateway or real bank transfer.",
      `<form id="credit-form" class="inline-form"><label>Student<select name="studentId">${users.map((s) => `<option value="${s.id}">${esc(s.name)} · ${esc(s.email)}</option>`).join("")}</select></label><label>Amount (₹)<input name="amount" type="number" min="0.01" max="10000" step="0.01" required></label><button class="primary">Add credits</button></form>`,
    ) +
    panel(
      "Student directory",
      users.length + " registered students",
      table(
        ["STUDENT", "EMAIL", "BALANCE", "BUDGET", "STATUS", "ACTIONS"],
        users.map((s) =>
          row([
            esc(s.name),
            esc(s.email),
            money(s.wallet),
            money(s.dailyBudget),
            `<span class="pill ${s.active ? "" : "red"}">${s.active ? "Active" : "Inactive"}</span>`,
            `<button data-active="${s.id}" data-value="${!s.active}">${s.active ? "Deactivate" : "Activate"}</button> <button class="danger" data-delete="${s.id}">Delete face</button>`,
          ]),
        ),
      ),
    );
  $("#credit-form").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      await api("/admin/topup", "POST", {
        studentId: Number(e.target.studentId.value),
        amount: Number(e.target.amount.value),
      });
      await refresh();
      render();
      toast("Wallet credited");
    });
  };
  document.querySelectorAll("[data-active]").forEach(
    (b) =>
      (b.onclick = () =>
        action(async () => {
          await api("/admin/student", "POST", {
            id: Number(b.dataset.active),
            active: b.dataset.value === "true",
          });
          await refresh();
          render();
        })),
  );
  document.querySelectorAll("[data-delete]").forEach(
    (b) =>
      (b.onclick = () =>
        action(async () => {
          if (
            confirm("Permanently delete this student’s encrypted face samples?")
          ) {
            await api("/admin/face/" + b.dataset.delete, "DELETE");
            toast("Biometrics deleted");
          }
        })),
  );
}
function manage() {
  $("#content").innerHTML =
    heading(
      "A menu worth looking forward to.",
      "Set prices and service times. Selection closes three hours before service.",
    ) +
    admin.meals
      .map((m) =>
        panel(
          icons[m.id] + " " + esc(m.title),
          "Same-day service · times in IST",
          `<form class="meal-form" data-id="${m.id}"><div class="form-grid"><label>Title<input name="title" value="${esc(m.title)}" required maxlength="80"></label><label>Price (₹)<input name="price" type="number" min="0" max="1000" step="0.01" value="${m.price}" required></label><label>Menu<input name="menu" value="${esc(m.menu)}" required maxlength="500"></label><label>Deadline (automatic)<input name="deadline" readonly type="time" value="${m.deadline.slice(0, 5)}" required></label><label>Service begins<input name="starts" type="time" value="${m.starts.slice(0, 5)}" required></label><label>Service ends<input name="ends" type="time" value="${m.ends.slice(0, 5)}" required></label></div><button class="primary">Save ${esc(m.title)}</button></form>`,
        ),
      )
      .join("");
  document.querySelectorAll(".meal-form").forEach(
    (f) =>
      (f.onsubmit = (e) => {
        e.preventDefault();
        action(async () => {
          const input = Object.fromEntries(new FormData(f));
          input.id = f.dataset.id;
          input.price = Number(input.price);
          await api("/admin/meal", "POST", input);
          await refresh();
          render();
          toast("Meal settings updated");
        });
      }),
  );
}
function forecast() {
  const wastes = admin.waste,
    prepared = wastes.reduce((s, w) => s + w.prepared, 0),
    wasted = wastes.reduce((s, w) => s + w.prepared - w.served, 0);
  let errors = [];
  for (const f of admin.forecasts) {
    const actual = admin.attendance.filter(
      (a) => a.day === f.day && a.mealId === f.mealId,
    ).length;
    if (f.day < state.today) errors.push(Math.abs(actual - f.expected));
  }
  const savings = wastes.reduce((s, w) => {
    const price = admin.meals.find((m) => m.id === w.mealId)?.price || 0;
    return (
      s +
      Math.max(
        0,
        admin.students.filter((s) => s.role === "STUDENT").length - w.prepared,
      ) *
        price *
        0.45
    );
  }, 0);
  $("#content").innerHTML =
    heading(
      "Prepare with a little foresight.",
      "Preparation forecasts and waste records.",
      `<input id="forecast-day" aria-label="Forecast date" type="date" value="${admin.day}" style="width:160px">`,
    ) +
    `<div class="stats">${stat("Historical waste rate", prepared ? ((100 * wasted) / prepared).toFixed(1) + "%" : "—", "Wasted ÷ prepared portions", true)}${stat("Portions wasted", wasted, "Across submitted records")}${stat("Estimated savings", money(savings), "vs. preparing for every demo student")}${stat("Forecast MAE", errors.length ? (errors.reduce((s, x) => s + x, 0) / errors.length).toFixed(1) : "—", "Mean absolute error · selected past date")}</div>` +
    panel(
      "Preparation recommendations",
      "Changing the forecast date recalculates using only earlier history.",
      table(
        ["MEAL", "YES BOOKINGS", "SHOW-UP RATE", "EXPECTED", "PREPARE"],
        admin.forecasts.map((f) =>
          row([
            f.mealId,
            f.booked,
            (f.showRate * 100).toFixed(1) + "%",
            f.expected,
            `<b>${f.recommended} portions</b>`,
          ]),
        ),
      ),
    ) +
    `<div class="notice">${esc(admin.forecasts[0]?.explanation)} Savings use an explicit assumption: food ingredient cost is 45% of the current meal price. These are estimates, not realized financial savings. Forecast accuracy is meaningful only after a session has finished.</div>` +
    panel(
      "Record kitchen output",
      "Served portions are derived from verified attendance. One attendance = one portion.",
      `<form id="waste-form" class="inline-form"><label>Date<input name="day" type="date" value="${state.today}" max="${state.today}" required></label><label>Meal<select name="mealId">${admin.meals.map((m) => `<option>${m.id}</option>`).join("")}</select></label><label>Portions prepared<input name="prepared" type="number" min="0" max="100000" required></label><button class="primary">Save waste record</button></form>`,
    ) +
    panel(
      "Prepared, served, wasted",
      "Latest 28 records · synthetic demo history preloaded",
      table(
        ["DATE", "MEAL", "PREPARED", "SERVED", "WASTED", "WASTE %"],
        wastes
          .slice()
          .sort((a, b) => b.day.localeCompare(a.day))
          .slice(0, 28)
          .map((w) =>
            row([
              w.day,
              w.mealId,
              w.prepared,
              w.served,
              w.prepared - w.served,
              w.prepared
                ? (((w.prepared - w.served) * 100) / w.prepared).toFixed(1) +
                  "%"
                : "0%",
            ]),
          ),
      ),
    );
  $("#forecast-day").onchange = (e) =>
    action(async () => {
      adminDay = e.target.value;
      await refresh();
      render();
    });
  $("#waste-form").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      const w = Object.fromEntries(new FormData(e.target));
      w.prepared = Number(w.prepared);
      await api("/admin/waste", "POST", w);
      await refresh();
      render();
      toast("Kitchen output recorded");
    });
  };
}
function records() {
  $("#content").innerHTML =
    heading(
      "The full dining picture.",
      "Student bookings, verified check-ins and downloadable attendance.",
      `<a class="primary" style="padding:12px;border-radius:8px" href="/api/admin/report">↓ Export CSV</a>`,
    ) +
    panel(
      "Attendance report",
      "Most recent 150 check-ins",
      table(
        ["DATE", "STUDENT", "MEAL", "AMOUNT"],
        admin.attendance
          .slice()
          .sort((a, b) => b.scannedAt.localeCompare(a.scannedAt))
          .slice(0, 150)
          .map((a) =>
            row([
              a.day,
              esc(admin.students.find((p) => p.id === a.studentId)?.name),
              a.mealId,
              money(a.amount),
            ]),
          ),
      ),
    ) +
    panel(
      "Today’s meal bookings",
      "YES bookings do not create a charge.",
      table(
        ["STUDENT", "MEAL", "SELECTION"],
        admin.bookings
          .filter((b) => b.day === state.today)
          .map((b) =>
            row([
              esc(admin.students.find((p) => p.id === b.studentId)?.name),
              b.mealId,
              `<span class="pill ${b.attending ? "" : "red"}">${b.attending ? "YES" : "NO"}</span>`,
            ]),
          ),
      ),
    );
}
function camera(scanner) {
  $("#content").innerHTML =
    heading(
      scanner
        ? "Entrance scanner"
        : "Face enrollment",
      scanner
        ? "Live camera verification with automatic meal eligibility and billing."
        : "Enroll your face securely to use the mess entrance scanner.",
    ) +
    `<div class="camera-grid"><section class="panel"><div class="panel-head"><h3>${scanner ? "Entrance scanner" : "Face enrollment"}</h3><span id="camera-status" class="pill gray">Camera off</span></div><div class="video-box"><video id="video" autoplay muted playsinline></video><div class="video-overlay"></div><div class="video-label" id="camera-prompt">Start your camera and allow browser permission.</div></div><canvas id="capture" hidden></canvas><div class="camera-actions"><button id="start-camera" class="primary">◎ Start Camera</button><button id="stop-camera">Stop Camera</button><button id="capture-face" disabled>${scanner ? "Start automatic scanning" : "Capture & enroll"}</button></div><div id="scan-result" aria-live="polite"></div></section><section><div class="panel"><h3>${scanner ? "Scanner instructions" : "Enrollment instructions"}</h3><div class="steps"><div class="step"><div><b>Camera position</b><p>Keep one face in view, with good lighting and no mask. </p></div></div><div class="step"><div><b>Move gently left and right</b><p>Each scan captures six frames across six seconds. Face movement and distinct-frame checks reject simple static captures.</p></div></div><div class="step"><div><b>${scanner ? "Only eligible meals are charged" : "Encrypted, consent-based enrollment"}</b><p>${scanner ? "The current session, YES booking, wallet, daily budget and existing attendance are checked in one database transaction." : "Samples use AES-256-GCM encryption. Your face is never included in demo data. You can delete your enrollment anytime."}</p></div></div></div>${scanner ? '<div class="notice">Repeated scans never charge the same student for the same meal twice.</div>' : `<label style="font-size:12px"><input id="consent" type="checkbox">I consent to storing encrypted facial samples for campus meal verification.</label><p class="muted">${state.faceSamples} encrypted samples currently enrolled.</p><button id="delete-face" class="danger">Delete my biometric data</button>`}</div><div class="notice">Movement checks offer basic replay resistance; this webcam project does not provide certified liveness detection. Photos moved in front of the camera or video replays may bypass it. Localhost camera access works in Chrome or Edge.</div></section></div>`;
  $("#start-camera").onclick = () => action(startCamera);
  $("#stop-camera").onclick = stopCamera;
  $("#capture-face").onclick = () =>
    action(async () => {
      if (scanner) {
        $("#capture-face").disabled = true;
        await scanLoop();
      } else await captureEnrollment();
    });
  if (!scanner)
    $("#delete-face").onclick = () =>
      action(async () => {
        if (confirm("Delete your face samples permanently?")) {
          await api("/face", "DELETE");
          await refresh();
          render();
          toast("Your biometric data was deleted");
        }
      });
}
async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia)
    throw Error(
      "Camera API unavailable. Open http://localhost:8080 in Chrome or Edge.",
    );
  stopCamera();
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 640 },
        height: { ideal: 480 },
        facingMode: "user",
      },
      audio: false,
    });
    $("#video").srcObject = stream;
    await $("#video").play();
    $("#camera-status").textContent = "● Camera live";
    $("#camera-status").className = "pill";
    $("#camera-prompt").textContent =
      "Camera ready. Keep your face in the guide.";
    $("#capture-face").disabled = false;
    stream.getVideoTracks()[0].onended = stopCamera;
  } catch (e) {
    stopCamera();
    throw Error(
      e.name === "NotAllowedError"
        ? "Camera permission denied. Allow camera access in your browser and Windows Settings → Privacy & security → Camera."
        : e.name === "NotFoundError"
          ? "No webcam found. Connect or enable your laptop camera."
          : e.name === "NotReadableError"
            ? "Camera is busy. Close Teams, Zoom or other camera apps."
            : e.message,
    );
  }
}
function stopCamera() {
  clearTimeout(scanTimer);
  scanTimer = null;
  if (stream) {
    stream.getTracks().forEach((t) => t.stop());
    stream = null;
  }
  const video = $("#video");
  if (video) video.srcObject = null;
  if ($("#capture-face")) $("#capture-face").disabled = true;
  if ($("#camera-status")) {
    $("#camera-status").textContent = "Camera off";
    $("#camera-status").className = "pill gray";
  }
  if ($("#camera-prompt"))
    $("#camera-prompt").textContent =
      "Camera stopped. Start again to continue.";
}
async function collect() {
  if (!stream) throw Error("Start your camera first");
  const challenge = await api("/face/challenge", "POST");
  const images = [];
  for (let i = 0; i < 6; i++) {
    if (!stream || !$("#video")) throw Error("Capture cancelled");
    $("#camera-prompt").textContent =
      `Move your head slowly left ↔ right · capturing ${i + 1}/6`;
    await new Promise((resolve) => setTimeout(resolve, 1000));
    if (!stream || !$("#video")) throw Error("Capture cancelled");
    const v = $("#video"),
      c = $("#capture");
    if (!v.videoWidth) throw Error("Camera has no video frames");
    c.width = Math.min(v.videoWidth, 960);
    if (!v.videoWidth || !v.videoHeight) throw Error("Camera is not ready. Wait for live video and retry.");
    c.height = Math.round((v.videoHeight / v.videoWidth) * c.width);
    c.getContext("2d").drawImage(v, 0, 0, c.width, c.height);
    images.push(c.toDataURL("image/jpeg", 0.8));
  }
  $("#camera-prompt").textContent =
    "OpenCV is detecting and verifying your face…";
  return { images, nonce: challenge.nonce };
}
async function captureEnrollment() {
  if (busy) return;
  if (!$("#consent").checked)
    throw Error("Please read and accept biometric consent first.");
  busy = true;
  $("#capture-face").disabled = true;
  try {
    const capture = await collect();
    capture.consent = true;
    await api("/face/enroll", "POST", capture);
    $("#scan-result").innerHTML =
      '<div class="scanner-result"><h2>Enrollment complete ✓</h2>Your encrypted face profile is ready for the entrance scanner.</div>';
    await refresh();
    toast("Face profile enrolled successfully");
  } finally {
    busy = false;
    if (stream && $("#capture-face")) $("#capture-face").disabled = false;
  }
}
async function scanLoop() {
  if (!stream || view !== "scanner") return;
  if (busy) {
    scanTimer = setTimeout(scanLoop, 1000);
    return;
  }
  busy = true;
  try {
    const capture = await collect();
    const r = await api("/scanner/recognize", "POST", capture);
    if ($("#scan-result"))
      $("#scan-result").innerHTML =
        `<div class="scanner-result ${r.granted ? "" : "denied"}"><h2>${esc(r.message)}</h2><p>${r.granted ? esc(r.name) + " · " + esc(r.meal) + "<br>Charged " + money(r.charged) + " · Wallet " + money(r.wallet) : esc(r.reason)}</p><small>LBPH distance ${Number(r.distance).toFixed(1)} / threshold ${r.threshold} · Lower is a closer match</small></div>`;
  } catch (e) {
    if ($("#scan-result"))
      $("#scan-result").innerHTML =
        `<div class="scanner-result denied"><b>No admission recorded</b><p>${esc(e.message)}</p></div>`;
  } finally {
    busy = false;
    if (stream && view === "scanner") {
      $("#camera-prompt").textContent =
        "Next scan in 5 seconds. One person at a time.";
      scanTimer = setTimeout(scanLoop, 5000);
    }
  }
}
window.addEventListener("beforeunload", stopCamera);
document.addEventListener("visibilitychange", () => {
  if (document.hidden) stopCamera();
});
setInterval(() => {
  if (state && (view === "overview" || (state.user.role === "STUDENT" && view === "meals")))
    action(async () => {
      await refresh();
      render();
    });
}, 30000);
action(async () => {
  await refresh();
  render();
});
