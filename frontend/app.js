import { presentation, escapeHtml as esc, statusLabel, formatTime } from "./catalog.js";

const $ = id => document.getElementById(id);
const state = {
  events: [], orders: [], pending: new Map(), posting: new Set(),
  category: "all", user: "user001", orderError: false, ordersLoaded: false,
};
let eventSequence = 0;
let orderSequence = 0;
let userEpoch = 0;
let pollTimer;
let pollAttempts = 0;
let toastTimer;

function storageGet(storageName, key) {
  try { return window[storageName].getItem(key); } catch { return null; }
}
function storageSet(storageName, key, value) {
  try { window[storageName].setItem(key, value); } catch { /* 隱私模式也能使用。 */ }
}

function restorePending() {
  state.pending.clear();
  try {
    const saved = JSON.parse(storageGet("sessionStorage", "ticket.pending." + state.user) || "[]");
    if (Array.isArray(saved)) saved.forEach(item => {
      if (/^[1-9]\d*$/.test(String(item.eventId)) && Number.isFinite(item.at) && Date.now() - item.at < 86400000) {
        state.pending.set(String(item.eventId), item);
      }
    });
  } catch { /* 損壞的瀏覽器暫存不影響後端訂單。 */ }
}
function persistPending() {
  storageSet("sessionStorage", "ticket.pending." + state.user, JSON.stringify([...state.pending.values()]));
}

async function request(path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(path, { ...options, signal: controller.signal, cache: "no-store", headers: { Accept: "application/json" } });
    const result = await response.json();
    if (!response.ok) throw new Error("服務暫時無法回應（HTTP " + response.status + "），請稍後重新整理。");
    return result;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("連線逾時，請稍後重新整理。");
    if (error instanceof SyntaxError || error instanceof TypeError) throw new Error("目前連不上搶票服務，請稍後再試。");
    throw error;
  } finally { clearTimeout(timer); }
}

function notify(message, tone = "success") {
  clearTimeout(toastTimer);
  $("toast").dataset.tone = tone;
  $("toastText").textContent = message;
  $("toastIcon").textContent = tone === "success" ? "✓" : tone === "pending" ? "…" : "!";
  $("toast").hidden = false;
  toastTimer = setTimeout(() => { $("toast").hidden = true; }, 14000);
}

function renderEvents() {
  const visible = state.events.filter(event => state.category === "all" || presentation(event.name).category === state.category);
  $("eventCount").textContent = visible.length + " 場活動";
  $("eventGrid").innerHTML = visible.length ? visible.map(event => {
    const meta = presentation(event.name);
    const id = String(event.id);
    const owned = state.orders.some(order => String(order.eventId) === id && order.status === "SUCCESS");
    const pending = state.pending.has(id);
    const busy = state.posting.has(id);
    const stock = Math.max(0, Number(event.stock) || 0);
    const total = Math.max(0, Number(event.totalStock) || 0);
    const disabled = busy || pending || owned || stock === 0;
    const label = busy ? "送出中…" : owned ? "已取得票券" : pending ? "訂單處理中" : stock === 0 ? "已售完" : "立即搶票";
    return `<article class="event-card">
      <div class="card-cover"><img src="/images/${meta.image}.webp" alt="${esc(meta.title)}活動封面" width="960" height="640"><span class="category-badge">${esc(meta.label)}</span></div>
      <div class="card-body"><h3 class="event-title">${esc(meta.title)}</h3>${meta.subtitle ? `<p class="event-original">${esc(meta.subtitle)}</p>` : ""}<p class="event-description">${esc(meta.description)}</p>
      <div class="card-bottom"><div><span class="stock-label">剩餘票數</span><span class="stock-number">${stock}<span class="stock-total">/ ${total} 張</span></span></div>
      <button class="buy-button${owned ? " owned" : ""}" data-event-id="${esc(id)}" aria-label="${esc(meta.title + "：" + label)}" ${disabled ? "disabled" : ""}>${label}<span class="arrow" aria-hidden="true">${owned ? "✓" : disabled ? "" : "↗"}</span></button></div></div>
    </article>`;
  }).join("") : '<p class="grid-message">這個類別目前沒有活動，試試其他分類吧。</p>';
}

function renderOrders() {
  $("orderCount").textContent = state.orders.length;
  $("orderDot").hidden = state.orders.length === 0 && state.pending.size === 0;
  $("ordersHint").textContent = state.orderError ? "連線中斷，以下為上次讀取結果" : state.pending.size ? "已送出申請，正在確認訂單" : "訂單依成立時間排列";
  const pending = [...state.pending.values()].sort((a, b) => b.at - a.at).map(item => {
    const meta = presentation(item.name);
    return `<article class="order-item"><img class="order-thumb" src="/images/${meta.image}.webp" alt="" width="52" height="61"><div class="order-content"><h3 class="order-title">${esc(meta.title)}</h3><div class="order-meta"><span class="order-id">尚未取得訂單編號</span><span class="status-badge pending">${item.uncertain ? "結果待確認" : "處理中"}</span></div><p class="order-time">等待後端確認，請勿重複送出</p></div></article>`;
  }).join("");
  const orders = state.orders.map(order => {
    const meta = presentation(order.eventName);
    const badge = order.status === "SUCCESS" ? " success" : ["QUEUED", "PENDING"].includes(order.status) ? " pending" : " other";
    return `<article class="order-item"><img class="order-thumb" src="/images/${meta.image}.webp" alt="" width="52" height="61"><div class="order-content"><h3 class="order-title">${esc(meta.title)}</h3><div class="order-meta"><span class="order-id">訂單 #${esc(order.id)}</span><span class="status-badge${badge}">${statusLabel(order.status)}</span></div><p class="order-time">${esc(formatTime(order.createdAt))}</p></div></article>`;
  }).join("");
  $("ordersList").innerHTML = pending + orders || `<p class="panel-empty">${state.orderError ? "暫時讀不到訂單。<br>請按上方「重新整理」再試一次。" : state.ordersLoaded ? "目前沒有訂單。<br>搶票成功後，票券會顯示在這裡。" : "正在讀取你的訂單…"}</p>`;
}

async function loadEvents() {
  const sequence = ++eventSequence;
  $("refreshEvents").disabled = true;
  $("eventGrid").setAttribute("aria-busy", "true");
  try {
    const events = await request("/api/events");
    if (!Array.isArray(events)) throw new Error("活動資料格式不正確，請稍後再試。");
    if (sequence !== eventSequence) return;
    state.events = events;
    $("eventError").hidden = true;
    renderEvents();
  } catch (error) {
    if (sequence !== eventSequence) return;
    $("eventError").textContent = error.message + (state.events.length ? " 以下保留上次的活動資料。" : "");
    $("eventError").hidden = false;
    if (!state.events.length) {
      $("eventCount").textContent = "尚未連線";
      $("eventGrid").innerHTML = '<p class="grid-message">活動暫時載入不了，請按右上方重新整理。</p>';
    }
  } finally {
    if (sequence === eventSequence) {
      $("refreshEvents").disabled = false;
      $("eventGrid").setAttribute("aria-busy", "false");
    }
  }
}

async function loadOrders({ announceError = false } = {}) {
  const sequence = ++orderSequence;
  const epoch = userEpoch;
  const user = state.user;
  $("refreshOrders").disabled = true;
  try {
    const orders = await request("/api/orders?userId=" + encodeURIComponent(user));
    if (!Array.isArray(orders)) throw new Error("訂單資料格式不正確，請稍後再試。");
    if (sequence !== orderSequence || epoch !== userEpoch) return false;
    state.orders = orders;
    state.orderError = false;
    state.ordersLoaded = true;
    const resolved = [];
    for (const [id, pending] of state.pending) {
      if (state.posting.has(id)) continue;
      const order = orders.find(item => String(item.eventId) === id);
      if (order) { resolved.push({ order, pending }); state.pending.delete(id); }
    }
    persistPending();
    renderOrders();
    renderEvents();
    const successes = resolved.filter(item => item.order.status === "SUCCESS");
    if (successes.length) {
      notify(successes.length === 1 ? "搶票成功！" + presentation(successes[0].pending.name).title + " · 訂單已成立" : "搶票成功！" + successes.length + " 筆訂單已成立");
      void loadEvents();
    } else if (resolved.length) {
      notify("訂單已更新，請在「我的訂單」查看實際狀態。", "pending");
    }
    if (!state.pending.size) stopPolling();
    return true;
  } catch (error) {
    if (sequence !== orderSequence || epoch !== userEpoch) return false;
    state.orderError = true;
    renderOrders();
    if (announceError) notify(error.message, "error");
    return false;
  } finally {
    if (sequence === orderSequence && epoch === userEpoch) $("refreshOrders").disabled = false;
  }
}

function stopPolling() { clearTimeout(pollTimer); pollTimer = undefined; }
function startPolling(restart = false) {
  if (restart) { stopPolling(); pollAttempts = 0; }
  if (!state.pending.size || pollTimer) return;
  const epoch = userEpoch;
  pollTimer = setTimeout(async () => {
    pollTimer = undefined;
    if (epoch !== userEpoch) return;
    await loadOrders();
    if (epoch !== userEpoch || !state.pending.size) return;
    pollAttempts++;
    if (pollAttempts < 30) startPolling();
    else notify("訂單仍待確認，可在「我的訂單」重新整理查看。請勿重複搶票。", "pending");
  }, 1500);
}

async function buy(id) {
  const event = state.events.find(item => String(item.id) === id);
  if (!event || state.posting.has(id) || state.pending.has(id)) return;
  if (state.orders.some(order => String(order.eventId) === id && order.status === "SUCCESS")) return;
  state.posting.add(id);
  $("userId").disabled = true;
  $("userForm").querySelector("button").disabled = true;
  renderEvents();
  const pending = { eventId: id, name: event.name, at: Date.now(), uncertain: false };
  try {
    const result = await request("/api/events/" + encodeURIComponent(id) + "/buy?userId=" + encodeURIComponent(state.user), { method: "POST" });
    if (result.success === true) {
      state.pending.set(id, pending);
      persistPending();
      // QUEUED 只是取得資格。只有 GET /orders 看見 SUCCESS 才顯示成功。
      notify(result.status === "QUEUED" ? "已取得搶票資格，正在建立訂單…" : "已送出，正在確認你的訂單…", "pending");
    } else if (result.success === false) {
      notify(result.message || "尚未取得票券，請重新整理後確認。", "error");
    } else {
      // 無法判讀的回應與逾時一樣，不自行重送 POST。
      pending.uncertain = true;
      state.pending.set(id, pending);
      persistPending();
      notify("回應不完整，正在確認訂單。請勿重複送出。", "pending");
    }
  } catch {
    pending.uncertain = true;
    state.pending.set(id, pending);
    persistPending();
    notify("連線中斷，搶票結果待確認。請勿重複送出。", "pending");
  } finally {
    state.posting.delete(id);
    if (!state.posting.size) {
      $("userId").disabled = false;
      $("userForm").querySelector("button").disabled = false;
    }
    renderEvents();
    renderOrders();
    await Promise.all([loadEvents(), loadOrders()]);
    if (state.pending.size) startPolling(true);
  }
}

function setPanel(open, restoreFocus = false) {
  $("orderPanel").hidden = !open;
  $("accountButton").setAttribute("aria-expanded", String(open));
  if (open) {
    $("closeOrders").focus();
    void loadOrders();
    if (state.pending.size) startPolling(true);
  } else if (restoreFocus) $("accountButton").focus();
}

$("accountButton").addEventListener("click", () => setPanel($("orderPanel").hidden));
$("closeOrders").addEventListener("click", () => setPanel(false, true));
document.addEventListener("click", event => {
  if (!$("orderPanel").hidden && !event.target.closest(".account")) setPanel(false);
});
document.addEventListener("keydown", event => {
  if (event.key === "Escape" && !$("orderPanel").hidden) setPanel(false, true);
});
$("dismissToast").addEventListener("click", () => { $("toast").hidden = true; clearTimeout(toastTimer); });
$("refreshEvents").addEventListener("click", () => { void loadEvents(); });
$("refreshOrders").addEventListener("click", async () => {
  await loadOrders({ announceError: true });
  if (state.pending.size) startPolling(true);
});
$("eventGrid").addEventListener("click", event => {
  const button = event.target.closest("button[data-event-id]");
  if (button && !button.disabled) void buy(button.dataset.eventId);
});
document.querySelectorAll(".filter").forEach(button => {
  button.addEventListener("click", () => {
    state.category = button.dataset.category;
    document.querySelectorAll(".filter").forEach(filter => {
      const active = filter === button;
      filter.classList.toggle("active", active);
      filter.setAttribute("aria-pressed", String(active));
    });
    renderEvents();
  });
});
$("userForm").addEventListener("submit", event => {
  event.preventDefault();
  if (state.posting.size) { notify("請先等目前的搶票請求送出完成。", "pending"); return; }
  const user = $("userId").value.trim();
  if (!user || user.length > 100) { notify("請輸入 1～100 字元的用戶 ID。", "error"); return; }
  if (user === state.user) { void loadOrders({ announceError: true }); return; }
  stopPolling();
  userEpoch++;
  state.user = user;
  state.orders = [];
  state.ordersLoaded = false;
  state.orderError = false;
  storageSet("localStorage", "ticket.user", user);
  $("avatarInitial").textContent = [...user][0].toUpperCase();
  restorePending();
  renderEvents();
  renderOrders();
  void loadOrders();
  if (state.pending.size) startPolling(true);
});

const savedUser = storageGet("localStorage", "ticket.user");
if (savedUser && savedUser.trim() && savedUser.length <= 100) state.user = savedUser.trim();
$("userId").value = state.user;
$("avatarInitial").textContent = [...state.user][0].toUpperCase();
restorePending();
renderOrders();
void loadEvents();
void loadOrders();
if (state.pending.size) startPolling(true);
