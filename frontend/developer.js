import { presentation, escapeHtml } from "./catalog.js";
import { runRequests, loadCommand, validateLoad } from "./developer-runner.js";

const $ = id => document.getElementById(id);
let operation = "state";
let enabled = false;
let resetAvailable = false;
let resetRequest = null;
let previewSequence = 0;
let busy = false;
let stopped = false;
let run = null;
let command = "";
let refreshTimer;
let observations = 0;
const names = { state: "庫存與訂單", load: "模擬多人搶票", duplicate: "測試重複購買", reset: "重設單一活動" };

async function api(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal, cache: "no-store", headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}) } });
    if (response.status === 404) throw new Error("後端尚未啟用開發者工具，請先完成 Backend 與 Worker 的部署及啟用設定。");
    const data = await response.json();
    if (!response.ok && !(options.acceptRejection && data.success === false)) throw new Error(data.message || "操作失敗（HTTP " + response.status + "）");
    return data;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("連線逾時，執行結果待確認。請先查看資料，勿自動重送。");
    throw error;
  } finally { clearTimeout(timer); }
}

function eventId() { return Number($("developerEvent").value); }
function message(text) { $("developerMessage").textContent = text; }
function showCommand(text) { command = text; $("developerCommand").textContent = text; }
function canExecute() {
  $("executeDeveloper").disabled = busy || !enabled || !eventId() || (operation === "reset" && (!resetRequest || !resetAvailable || !$("developerConsent").checked)) || command === "";
}
function setBusy(value) {
  busy = value;
  document.querySelectorAll(".developer-fields input, .developer-fields select, .developer-menu button").forEach(el => { el.disabled = value; });
  $("previewDeveloper").disabled = value;
  $("developerConsent").disabled = value;
  $("refreshDeveloperState").disabled = value;
  canExecute();
}

async function preview() {
  const sequence = ++previewSequence;
  clearTimeout(refreshTimer);
  resetRequest = null;
  run = null;
  observations = 0;
  $("developerConsent").checked = false;
  $("developerResults").hidden = true;
  $("developerStockField").hidden = operation !== "reset";
  $("developerRequestsField").hidden = !["load", "duplicate"].includes(operation);
  $("developerRequests").max = operation === "duplicate" ? "10" : "500";
  $("developerConcurrencyField").hidden = operation !== "load";
  $("developerConsentField").hidden = operation !== "reset";
  $("developerOperationTitle").textContent = names[operation];
  $("developerSteps").innerHTML = "";
  message("");
  showCommand("");
  canExecute();
  const id = eventId();
  if (!id) { canExecute(); return; }
  const origin = window.location.origin;
  try {
    if (operation === "state") {
      $("developerDescription").textContent = "讀取 Redis 庫存、購買資格數及 MySQL 訂單。不更動任何資料。";
      showCommand(`curl -sS '${origin}/api/developer/events/${id}/state'`);
    } else if (operation === "reset") {
      $("developerDescription").textContent = "刪除所選活動的訂單，重設票數及購買紀錄。其他活動不變。";
      $("developerConsentText").textContent = "正在取得刪除範圍…";
      if (!enabled || !resetAvailable) throw new Error("重設需要後端啟用工具，且使用 REDIS_LUA_MQ 模式。");
      const stock = Number($("developerStock").value);
      if (!Number.isInteger(stock) || stock < 1 || stock > 1000) throw new Error("庫存請輸入 1～1000 的整數。");
      const result = await api(`/api/developer/events/${id}/state`);
      if (sequence !== previewSequence) return;
      resetRequest = { eventId: id, stock, expectedOrders: result.orderCount, confirmed: true };
      $("developerSteps").innerHTML = [
        "請先等搶票測試與訂單處理結束。",
        `DELETE FROM ticket_order WHERE event_id = ${id};`,
        `UPDATE ticket_event SET total_stock = ${stock}, stock = ${stock}, version = 0 WHERE id = ${id};`,
        "重設 Redis 庫存與購買紀錄，重新開放活動。"
      ].map(step => `<li>${escapeHtml(step)}</li>`).join("");
      $("developerConsentText").textContent = `我確認刪除活動 #${id} 的 ${result.orderCount} 筆訂單（包含其他使用者），並將票數重設為 ${stock} 張。刪除後無法直接還原。`;
      showCommand(`curl -sS -X POST '${origin}/api/developer/reset' \\\n  -H 'Content-Type: application/json' \\\n  -d '${JSON.stringify(resetRequest)}'`);
    } else {
      const duplicate = operation === "duplicate";
      const count = Number($("developerRequests").value);
      const concurrency = duplicate ? 1 : Number($("developerConcurrency").value);
      validateLoad(count, concurrency, duplicate);
      const prefix = "demo-" + crypto.randomUUID().replaceAll("-", "").slice(0, 12) + "-";
      run = { eventId: id, prefix, count, concurrency, duplicate, sent: 0, completed: 0, queued: 0, accepted: 0, rejected: 0, uncertain: 0 };
      $("developerDescription").textContent = duplicate ? "用同一個測試 ID 連續搶票，檢查是否擋下重複購買。會消耗活動庫存。" : "用不同的測試 ID 同時搶票。會建立訂單並消耗活動庫存。";
      $("developerSteps").innerHTML = [
        duplicate ? `同一測試 ID 連續送出 ${count} 次請求。` : `使用 ${count} 個不同的測試 ID，同時最多 ${concurrency} 個請求。`,
        "統計請求結果與成立訂單數。",
        "停止後不再送出新請求，已送出的請求仍會處理。"
      ].map(step => `<li>${escapeHtml(step)}</li>`).join("");
      showCommand(loadCommand(origin, run));
    }
  } catch (error) { if (sequence === previewSequence) message(error.message); }
  if (sequence === previewSequence) canExecute();
}

function renderResults(snapshot) {
  const metrics = [];
  if (run) metrics.push(["請求完成／送出", `${run.completed} / ${run.sent}`], ["取得資格／已接受", run.queued + run.accepted], ["明確拒絕", run.rejected], ["結果待確認", run.uncertain], ["本次成立訂單", snapshot ? snapshot.runOrders : "待查詢"]);
  if (snapshot) metrics.push(["Redis 剩餘票數", snapshot.redisStock ?? "未預熱"], ["MySQL 剩餘票數", snapshot.mysqlStock], ["此活動訂單數", snapshot.orderCount], ["Redis 購買資格數", snapshot.buyers]);
  $("developerMetrics").innerHTML = metrics.map(([label, value]) => `<div class="developer-metric"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join("");
  $("developerResults").hidden = false;
  let note = "";
  if (snapshot) {
    const balanced = snapshot.mysqlStock + snapshot.orderCount === snapshot.totalStock;
    const settled = balanced && snapshot.redisStock === snapshot.mysqlStock && snapshot.buyers === snapshot.orderCount;
    note = `活動狀態：${snapshot.status}。${settled ? "庫存與訂單數一致。" : "庫存與訂單數尚未一致。"}`;
  }
  $("developerResultNote").textContent = note;
}

async function refreshState(schedule = false) {
  const sequence = previewSequence;
  const id = run?.eventId || eventId();
  const currentRun = run;
  try {
    const snapshot = await api(`/api/developer/events/${id}/state` + (run ? "?runPrefix=" + run.prefix : ""));
    if (sequence !== previewSequence || currentRun !== run) return;
    renderResults(snapshot);
    if (schedule && run && $("developerDialog").open && observations++ < 15) {
      refreshTimer = setTimeout(() => { void refreshState(true); }, 2000);
    }
    return true;
  } catch (error) { if (sequence === previewSequence) message(error.message); return false; }
}

async function execute() {
  if (busy || $("executeDeveloper").disabled) return;
  setBusy(true);
  message("正在執行…");
  const selected = operation;
  try {
    if (selected === "state") {
      if (await refreshState()) message("資料已更新。");
    } else if (selected === "reset") {
      const result = await api("/api/developer/reset", { method: "POST", body: JSON.stringify(resetRequest) });
      renderResults(result);
      message(`活動 #${result.eventId} 已重設，票數 ${result.totalStock} 張。其他活動未變更。`);
      window.dispatchEvent(new CustomEvent("ticket:developer-reset", { detail: { eventId: result.eventId } }));
    } else {
      stopped = false;
      $("stopDeveloper").hidden = false;
      $("stopDeveloper").disabled = false;
      const before = await api(`/api/developer/events/${run.eventId}/state`);
      if (stopped || !$("developerDialog").open) { message("已停止，沒有送出搶票請求。"); return; }
      if (before.redisStock === null || before.status !== "OPEN") throw new Error("活動尚未開放，沒有送出搶票請求。請先查看資料。");
      await runRequests(run, {
        shouldStop: () => stopped,
        request: url => api(url, { method: "POST", acceptRejection: true }),
        progress: () => { renderResults(); message(`已完成 ${run.completed} / ${run.count} 次請求${stopped ? "，等待已送出的請求結束…" : "…"}`); }
      });
      message(`${stopped ? "已停止送出" : "請求已送完"}，共 ${run.sent} 次。訂單數持續更新 30 秒。`);
      await refreshState(true);
      window.dispatchEvent(new CustomEvent("ticket:developer-refresh"));
    }
  } catch (error) { message(error.message); }
  finally {
    resetRequest = null;
    $("developerConsent").checked = false;
    $("stopDeveloper").hidden = true;
    setBusy(false);
    // 重設/壓測不可連按重送；必須重新預覽，取得新的確認範圍或測試 ID。
    if (selected !== "state") $("executeDeveloper").disabled = true;
  }
}

async function open() {
  if (busy) { $("developerDialog").showModal(); return; }
  enabled = false;
  resetAvailable = false;
  resetRequest = null;
  ++previewSequence;
  showCommand("");
  canExecute();
  $("orderPanel").hidden = true;
  $("accountButton").setAttribute("aria-expanded", "false");
  $("developerDialog").showModal();
  message("正在連線…");
  try {
    const events = await api("/api/events");
    $("developerEvent").innerHTML = events.map(event => `<option value="${Number(event.id)}">${escapeHtml(presentation(event.name).title)} · #${Number(event.id)}</option>`).join("");
    const capability = await api("/api/developer/capabilities");
    enabled = capability.enabled === true;
    resetAvailable = capability.resetAvailable === true;
    $("developerAvailability").textContent = capability.fixture ? "預覽使用假資料。" : "";
  } catch (error) {
    enabled = false;
    $("developerAvailability").textContent = error.message;
  }
  $("developerAvailability").hidden = !$("developerAvailability").textContent;
  await preview();
}

$("developerButton").addEventListener("click", () => { void open(); });
$("closeDeveloper").addEventListener("click", () => $("developerDialog").close());
$("developerDialog").addEventListener("close", () => { stopped = true; clearTimeout(refreshTimer); $("developerButton").focus(); });
$("developerDialog").addEventListener("click", event => { if (event.target === $("developerDialog")) { const rect = event.target.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) event.target.close(); } });
document.querySelectorAll(".developer-menu button").forEach(button => button.addEventListener("click", () => {
  operation = button.dataset.operation;
  $("developerRequests").value = operation === "duplicate" ? 5 : 100;
  document.querySelectorAll(".developer-menu button").forEach(item => { item.classList.toggle("selected", item === button); item.setAttribute("aria-pressed", String(item === button)); });
  void preview();
}));
document.querySelectorAll(".developer-fields input, .developer-fields select").forEach(field => field.addEventListener("change", () => { void preview(); }));
// 正在編輯時先撤銷確認，避免按鈕仍執行舊參數。
document.querySelectorAll(".developer-fields input").forEach(field => field.addEventListener("input", () => { ++previewSequence; resetRequest = null; showCommand(""); $("developerConsent").checked = false; message("參數已變更，請更新預覽後再確認。"); canExecute(); }));
$("developerConsent").addEventListener("change", canExecute);
$("previewDeveloper").addEventListener("click", () => { void preview(); });
$("executeDeveloper").addEventListener("click", () => { void execute(); });
$("stopDeveloper").addEventListener("click", () => { stopped = true; $("stopDeveloper").disabled = true; message("已停止新請求，等待已送出的請求回應。已排隊的訂單不會被取消。"); });
$("refreshDeveloperState").addEventListener("click", () => { void refreshState(); });
$("copyDeveloperCommand").addEventListener("click", async () => { if (!command) { message("請先更新預覽，再複製指令。"); return; } try { await navigator.clipboard.writeText(command); message("已複製 Bash 指令。"); } catch { message("無法自動複製，請選取上方指令自行複製。"); } });
