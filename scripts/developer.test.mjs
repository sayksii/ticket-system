import test from "node:test";
import assert from "node:assert/strict";
import { runRequests, validateLoad, loadCommand } from "../frontend/developer-runner.js";
import { presentation, escapeHtml } from "../frontend/catalog.js";
import vm from "node:vm";
import { readFile } from "node:fs/promises";

const createRun = (extra = {}) => ({ eventId: 2, prefix: "demo-abcdef123456-", count: 30, concurrency: 4, duplicate: false,
  sent: 0, completed: 0, queued: 0, accepted: 0, rejected: 0, uncertain: 0, ...extra });

test("壓測請求總數與同時請求數都有上限", async () => {
  const run = createRun(); let active = 0; let peak = 0; const urls = [];
  await runRequests(run, { shouldStop: () => false, progress() {}, request: async url => {
    urls.push(url); active++; peak = Math.max(peak, active);
    await new Promise(resolve => setImmediate(resolve)); active--; return { success: true, status: "QUEUED" };
  } });
  assert.equal(peak, 4); assert.equal(urls.length, 30); assert.equal(new Set(urls).size, 30);
  assert.equal(run.queued, 30); assert.equal(run.accepted, 0); assert.equal(run.completed, 30);
});
test("停止之後不送新請求，也不取消已送出的請求", async () => {
  const run = createRun(); let stop = false;
  await runRequests(run, { shouldStop: () => stop, progress() { stop = true; }, request: async () => {
    await new Promise(resolve => setImmediate(resolve)); return { success: true, status: "QUEUED" };
  } });
  assert.equal(run.sent, 4); assert.equal(run.completed, 4);
});
test("重複購買使用同一個新 ID，拒絕和連線不明分開統計", async () => {
  const run = createRun({ count: 5, concurrency: 1, duplicate: true }); const urls = [];
  await runRequests(run, { shouldStop: () => false, progress() {}, request: async url => {
    urls.push(url); if (urls.length === 1) return { success: true, status: "QUEUED" };
    if (urls.length === 5) throw new Error("timeout"); return { success: false };
  } });
  assert.equal(new Set(urls).size, 1); assert.equal(run.queued, 1); assert.equal(run.rejected, 3); assert.equal(run.uncertain, 1);
});
test("超出範圍或小數的參數不能開始壓測", () => {
  for (const [count, concurrency] of [[501, 1], [100, 11], [0, 1], [2.5, 1], [1, 0]]) assert.throws(() => validateLoad(count, concurrency));
  assert.throws(() => validateLoad(11, 1, true));
});
test("預覽指令對應選定活動、真實 ID 和併行數", () => {
  const text = loadCommand("http://ticket.local", createRun());
  assert.match(text, /seq 1 30/); assert.match(text, /xargs -P 4/);
  assert.match(text, /api\/events\/2\/buy\?userId=demo-abcdef123456-\{\}/);
  assert.doesNotMatch(text, /retry|DELETE|FLUSHALL/);
});

const source = await readFile(new URL("../frontend/developer.js", import.meta.url), "utf8");
async function uiHarness() {
  const elements = new Map(); const requests = [];
  const element = id => {
    if (!elements.has(id)) elements.set(id, { id, disabled: false, checked: false, hidden: false, open: false, value: "", textContent: "", innerHTML: "", listeners: {}, dataset: {}, classList: { toggle() {} }, setAttribute() {}, focus() {}, showModal() { this.open = true; }, close() { this.open = false; this.listeners.close?.(); }, addEventListener(type, fn) { this.listeners[type] = fn; } });
    return elements.get(id);
  };
  for (const [id, value] of Object.entries({ developerEvent: "1", developerStock: "20", developerRequests: "100", developerConcurrency: "5" })) element(id).value = value;
  const fields = [element("developerStock"), element("developerRequests"), element("developerConcurrency")];
  const menu = ["state", "load", "duplicate", "reset"].map(op => { const el = element("menu-" + op); el.dataset.operation = op; return el; });
  const context = vm.createContext({
    presentation, escapeHtml, runRequests, loadCommand, validateLoad, AbortController, crypto,
    setTimeout() { return 1; }, clearTimeout() {},
    document: { getElementById: element, querySelectorAll: selector => selector.includes("developer-menu") && !selector.includes("developer-fields") ? menu : selector.includes("select") ? [...fields, element("developerEvent"), ...menu] : fields },
    window: { location: { origin: "http://ticket.local" }, dispatchEvent() {} },
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options?.detail; } },
    navigator: { clipboard: { writeText: async () => {} } },
    fetch: async (url, options) => {
      requests.push({ url, options });
      const data = url === "/api/events" ? [{ id: 1, name: "Cloud Native Summit" }] : url.endsWith("capabilities") ? { enabled: true, resetAvailable: true } : { eventId: 1, totalStock: 20, mysqlStock: 13, redisStock: 13, orderCount: 7, buyers: 7, status: "OPEN" };
      return { ok: true, status: 200, json: async () => data };
    }
  });
  vm.runInContext(source.replace(/^import .*;\r?\n/gm, "") + "\nthis.subject = { open, preview, execute, renderResults, setOperation: value => operation = value, getResetRequest: () => resetRequest };", context);
  await context.subject.open();
  return { context, app: context.subject, elements, requests };
}

test("結果只顯示簡短狀態，保留不一致提示", async () => {
  const h = await uiHarness();
  const snapshot = { totalStock: 20, mysqlStock: 20, redisStock: 20, orderCount: 0, buyers: 0, status: "OPEN" };
  h.app.renderResults(snapshot);
  assert.equal(h.elements.get("developerResultNote").textContent, "活動狀態：OPEN。庫存與訂單數一致。");
  h.app.renderResults({ ...snapshot, redisStock: 19 });
  assert.equal(h.elements.get("developerResultNote").textContent, "活動狀態：OPEN。庫存與訂單數尚未一致。");
  h.app.setOperation("load"); await h.app.preview();
  h.app.renderResults();
  assert.equal(h.elements.get("developerResultNote").textContent, "");
  assert.doesNotMatch(source, /跨系統原子快照|取得資格不等於訂單成立|取得資格（QUEUED）/);
  assert.doesNotMatch(h.elements.get("developerCommand").textContent, /不啟動 Shell|沒有自動重送/);
});

test("正式頁面不顯示補充聲明，假資料預覽仍明確標示", async () => {
  const h = await uiHarness();
  assert.equal(h.elements.get("developerAvailability").hidden, true);
  const fetch = h.context.fetch;
  h.context.fetch = async (url, options) => url.endsWith("capabilities")
    ? { ok: true, status: 200, json: async () => ({ enabled: true, resetAvailable: true, fixture: true }) }
    : fetch(url, options);
  await h.app.open();
  assert.equal(h.elements.get("developerAvailability").hidden, false);
  assert.equal(h.elements.get("developerAvailability").textContent, "預覽使用假資料。");
});

test("開啟或預覽重設不會執行；勾選並確認才呼叫 reset", async () => {
  const h = await uiHarness();
  h.app.setOperation("reset"); await h.app.preview();
  const resets = () => h.requests.filter(item => item.url === "/api/developer/reset");
  assert.equal(resets().length, 0);
  assert.equal(h.requests.filter(item => item.options.method === "POST").length, 0);
  assert.match(h.elements.get("developerConsentText").textContent, /7 筆訂單/);
  assert.equal(h.elements.get("executeDeveloper").disabled, true);
  await h.app.execute(); assert.equal(resets().length, 0);
  const consent = h.elements.get("developerConsent"); consent.checked = true; consent.listeners.change();
  assert.equal(h.elements.get("executeDeveloper").disabled, false);
  await h.app.execute(); assert.equal(resets().length, 1);
  assert.deepEqual(JSON.parse(resets()[0].options.body), { eventId: 1, stock: 20, expectedOrders: 7, confirmed: true });
  assert.equal(h.elements.get("executeDeveloper").disabled, true);
});
test("修改參數立即撤銷舊確認與舊指令", async () => {
  const h = await uiHarness(); h.app.setOperation("reset"); await h.app.preview();
  const consent = h.elements.get("developerConsent"); consent.checked = true; consent.listeners.change();
  const field = h.elements.get("developerStock"); field.value = "25"; field.listeners.input();
  assert.equal(consent.checked, false); assert.equal(h.elements.get("executeDeveloper").disabled, true);
  assert.equal(h.elements.get("developerCommand").textContent, "");
});
test("晚回來的舊預覽不能覆蓋正在修改的參數", async () => {
  const h = await uiHarness(); h.app.setOperation("reset");
  let resolve; h.context.fetch = () => new Promise(done => { resolve = done; });
  const pending = h.app.preview();
  h.elements.get("developerStock").value = "25"; h.elements.get("developerStock").listeners.input();
  resolve({ ok: true, status: 200, json: async () => ({ orderCount: 1 }) });
  await pending;
  assert.equal(h.app.getResetRequest(), null); assert.equal(h.elements.get("executeDeveloper").disabled, true);
});
test("資料查詢失敗不能宣稱讀取完成", async () => {
  const h = await uiHarness();
  h.context.fetch = async () => ({ ok: false, status: 503, json: async () => ({ message: "database unavailable" }) });
  await h.app.execute();
  assert.equal(h.elements.get("developerMessage").textContent, "database unavailable");
});
test("檢查資料期間關閉視窗，不會在晚回應後開始壓測", async () => {
  const h = await uiHarness(); h.app.setOperation("load"); await h.app.preview();
  let resolve; let calls = 0;
  h.context.fetch = () => { calls++; return new Promise(done => { resolve = done; }); };
  const executing = h.app.execute();
  h.elements.get("developerDialog").close();
  resolve({ ok: true, status: 200, json: async () => ({ redisStock: 20, status: "OPEN" }) });
  await executing;
  assert.equal(calls, 1); assert.equal(h.elements.get("developerMessage").textContent, "已停止，沒有送出搶票請求。");
});
test("活動重設期間，壓測不送出購票請求", async () => {
  const h = await uiHarness(); h.app.setOperation("load"); await h.app.preview();
  let calls = 0; h.context.fetch = async () => { calls++; return { ok: true, status: 200, json: async () => ({ redisStock: 20, status: "RESETTING" }) }; };
  await h.app.execute();
  assert.equal(calls, 1); assert.match(h.elements.get("developerMessage").textContent, /沒有送出搶票請求/);
});
