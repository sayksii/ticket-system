import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import { readFile } from "node:fs/promises";
import { presentation, escapeHtml, statusLabel, formatTime, catalog } from "../frontend/catalog.js";

const source = await readFile(new URL("../frontend/app.js", import.meta.url), "utf8");
class Element {
  constructor(id) { this.id = id; this.hidden = false; this.disabled = false; this.innerHTML = ""; this.textContent = ""; this.value = ""; this.dataset = {}; this.listeners = {}; this.attributes = {}; this.classList = { toggle() {} }; }
  addEventListener(type, fn) { this.listeners[type] = fn; }
  setAttribute(key, value) { this.attributes[key] = value; }
  querySelector() { return this.child ||= new Element("child"); }
  focus() {}
}
async function harness(buyResponse) {
  const elements = new Map();
  const timers = new Map();
  let nextTimer = 0;
  const orders = [];
  const requests = [];
  const filters = ["all", "music", "talk", "culture"].map(category => { const el = new Element(category); el.dataset.category = category; return el; });
  const storage = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value) }; };
  const context = vm.createContext({
    presentation, esc: escapeHtml, statusLabel, formatTime,
    document: { getElementById(id) { if (!elements.has(id)) elements.set(id, new Element(id)); return elements.get(id); }, querySelectorAll() { return filters; }, addEventListener() {} },
    window: { sessionStorage: storage(), localStorage: storage() },
    AbortController,
    setTimeout(fn, delay) { const id = ++nextTimer; timers.set(id, { fn, delay }); return id; },
    clearTimeout(id) { timers.delete(id); },
    fetch: async (url, options) => {
      requests.push({ url, method: options.method || "GET" });
      if (options.method === "POST") {
        if (buyResponse instanceof Error) throw buyResponse;
        return { ok: true, json: async () => buyResponse };
      }
      return { ok: true, json: async () => url.startsWith("/api/orders") ? [...orders] : [{ id: 2, name: "Cloud Native Summit", totalStock: 120, stock: 120 }] };
    },
  });
  vm.runInContext(source.replace(/^import .*;\r?\n/, "") + "\nthis.testApp = { state, buy, loadOrders, renderEvents };", context);
  await new Promise(resolve => setImmediate(resolve));
  return { app: context.testApp, elements, orders, requests, timers, context };
}

test("活動展示資料、未知活動 fallback 與安全文字 escaping", () => {
  assert.equal(Object.keys(catalog).length, 6);
  assert.equal(presentation("Cloud Native Summit").category, "talk");
  assert.equal(presentation("<新活動>").title, "<新活動>");
  assert.equal(escapeHtml('<img onerror="x"> &'), "&lt;img onerror=&quot;x&quot;&gt; &amp;");
  assert.equal(statusLabel("SUCCESS"), "已成立");
  assert.equal(statusLabel("UNKNOWN"), "狀態待確認");
  assert.equal(formatTime("2026-10-04T14:03:59.123"), "2026.10.04 · 14:03");
});

test("QUEUED 不能提早顯示成功；真實訂單 SUCCESS 後才顯示成功並禁止重複送出", async () => {
  const h = await harness({ success: true, status: "QUEUED" });
  await h.app.buy("2");
  assert.equal(h.app.state.pending.has("2"), true);
  assert.equal(h.elements.get("toast").dataset.tone, "pending");
  assert.match(h.elements.get("ordersList").innerHTML, /處理中/);
  assert.doesNotMatch(h.elements.get("ordersList").innerHTML, /已成立/);
  assert.doesNotMatch(h.elements.get("ordersList").innerHTML, /status-badge success/);
  await h.app.buy("2");
  assert.equal(h.requests.filter(r => r.method === "POST").length, 1);
  h.orders.push({ id: 7, eventId: 2, eventName: "Cloud Native Summit", userId: "user001", status: "SUCCESS", createdAt: "2026-10-04T14:03:00" });
  await h.app.loadOrders();
  assert.equal(h.app.state.pending.size, 0);
  assert.equal(h.elements.get("toast").dataset.tone, "success");
  assert.match(h.elements.get("toastText").textContent, /城市創意論壇/);
  assert.match(h.elements.get("ordersList").innerHTML, /訂單 #7/);
  assert.match(h.elements.get("ordersList").innerHTML, /class="status-badge success"/);
  assert.match(h.elements.get("ordersList").innerHTML, /城市創意論壇/);
  assert.match(h.elements.get("eventGrid").innerHTML, /City Ideas Forum/);
  for (const id of ["toastText", "ordersList", "eventGrid"]) {
    const element = h.elements.get(id);
    assert.doesNotMatch(element.innerHTML + element.textContent, /Kubernetes|DevOps|Cloud Native|Spring Boot/);
  }
  assert.match(h.elements.get("eventGrid").innerHTML, /已取得票券/);
  await h.app.buy("2");
  assert.equal(h.requests.filter(r => r.method === "POST").length, 1);
});

test("POST 連線失敗時結果待確認，不自動重送或捏造失敗訂單", async () => {
  const h = await harness(new TypeError("network disconnected"));
  await h.app.buy("2");
  assert.equal(h.app.state.pending.get("2").uncertain, true);
  assert.equal(h.elements.get("toast").dataset.tone, "pending");
  assert.match(h.elements.get("ordersList").innerHTML, /結果待確認/);
  await h.app.buy("2");
  assert.equal(h.requests.filter(r => r.method === "POST").length, 1);
});

test("後端明確拒絕時顯示錯誤、解除按鈕忙碌，不建立假的訂單", async () => {
  const h = await harness({ success: false, message: "票已售完" });
  await h.app.buy("2");
  assert.equal(h.app.state.pending.size, 0);
  assert.equal(h.app.state.posting.size, 0);
  assert.equal(h.elements.get("toast").dataset.tone, "error");
  assert.equal(h.elements.get("toastText").textContent, "票已售完");
  assert.equal(h.elements.get("userId").disabled, false);
});

test("背景輪詢有上限，不會無限發送請求", async () => {
  const h = await harness({ success: true, status: "QUEUED" });
  await h.app.buy("2");
  let polls = 0;
  for (;;) {
    const entry = [...h.timers.entries()].find(([, timer]) => timer.delay === 1500);
    if (!entry) break;
    h.timers.delete(entry[0]);
    await entry[1].fn();
    polls++;
    assert.ok(polls <= 30);
  }
  assert.equal(polls, 30);
  assert.equal(h.requests.filter(r => r.method === "POST").length, 1);
  assert.equal(h.app.state.pending.size, 1);
  assert.match(h.elements.get("toastText").textContent, /仍待確認/);
});

test("後端訂單不是 SUCCESS 時，不能顯示搶票成功", async () => {
  const h = await harness({ success: true, status: "QUEUED" });
  await h.app.buy("2");
  h.orders.push({ id: 8, eventId: 2, eventName: "Cloud Native Summit", status: "FAILED" });
  await h.app.loadOrders();
  assert.equal(h.app.state.pending.size, 0);
  assert.notEqual(h.elements.get("toast").dataset.tone, "success");
  assert.match(h.elements.get("ordersList").innerHTML, /未成立/);
  assert.doesNotMatch(h.elements.get("ordersList").innerHTML, /status-badge success/);
});

test("切換用戶後，舊用戶的延遲訂單回應不會污染新用戶畫面", async () => {
  const h = await harness({ success: true, status: "QUEUED" });
  const replies = [];
  h.context.fetch = () => new Promise(resolve => { replies.push(resolve); });
  const oldRequest = h.app.loadOrders();
  h.app.state.orders = [];
  // 用公開表單互動切換，觸發正式的 userEpoch 保護。
  h.elements.get("userId").value = "another-user";
  const submit = h.elements.get("userForm").listeners.submit;
  submit({ preventDefault() {} });
  assert.equal(h.app.state.user, "another-user");
  assert.equal(h.app.state.orders.length, 0);
  assert.equal(replies.length, 2);
  replies[1]({ ok: true, json: async () => [] });
  await new Promise(resolve => setImmediate(resolve));
  replies[0]({ ok: true, json: async () => [{ id: 8, eventId: 2, status: "SUCCESS" }] });
  await oldRequest;
  assert.equal(h.app.state.orders.length, 0);
});

test("資料庫種子和 K8s 初始化包含相同六場活動，安全新增腳本沒有重置指令", async () => {
  const sql = await readFile(new URL("../backend/src/main/resources/data.sql", import.meta.url), "utf8");
  const yaml = await readFile(new URL("../k8s/part7/10-mysql.yaml", import.meta.url), "utf8");
  const seed = await readFile(new URL("./seed-demo-events.sh", import.meta.url), "utf8");
  for (const name of Object.keys(catalog)) { assert.ok(sql.includes(name)); assert.ok(yaml.includes(name)); }
  assert.equal((sql.match(/WHERE NOT EXISTS/g) || []).length, 6);
  assert.doesNotMatch(sql, /DELETE|UPDATE|TRUNCATE|DROP/i);
  assert.match(seed, /redis.call\('EXISTS', KEYS\[1\], KEYS\[2\], KEYS\[3\]\)/);
  assert.doesNotMatch(seed, /redis.call\('DEL'|redis-cli.*FLUSH|DELETE FROM/i);
});

test("中性介面搭配新彩色照片：不再套飽和度、對比或黑白濾鏡", async () => {
  const css = await readFile(new URL("../frontend/styles.css", import.meta.url), "utf8");
  const html = await readFile(new URL("../frontend/index.html", import.meta.url), "utf8");
  const neutralCss = css
    .replace(/\.toast\[data-tone="success"\](?: \.toast-icon)? \{[^}]*\}/g, "")
    .replace(/\.order-panel \{[^}]*\}/g, "");
  for (const match of neutralCss.matchAll(/#([0-9a-f]{6})(?:[0-9a-f]{2})?\b/gi)) {
    const rgb = match[1].toLowerCase();
    assert.equal(rgb.slice(0, 2), rgb.slice(2, 4), "非中性色：" + match[0]);
    assert.equal(rgb.slice(2, 4), rgb.slice(4, 6), "非中性色：" + match[0]);
  }
  assert.match(css, /--photo-filter: none/);
  assert.doesNotMatch(css, /(?:grayscale|saturate|contrast)\(/);
  assert.match(css, /\.card-cover img, \.order-thumb \{ filter: var\(--photo-filter\)/);
  assert.match(css, /indie-music-night-v2\.webp"\] \{ object-position: center 25%; \}/);
  assert.doesNotMatch(css, /--coral|--green|translateY\(-2px\)|scale\(1\.035\)/);
  assert.doesNotMatch(html, /GOOD TIMES|sparkle|把期待/);
});

test("六張封面使用重新生成的 v2 WebP，舊圖仍完整保留", async () => {
  const originals = ["devops-concert", "cloud-native-summit", "spring-boot-workshop", "indie-music-night", "future-design-expo", "weekend-jazz-market"];
  const images = Object.values(catalog).map(meta => meta.image);
  assert.equal(new Set(images).size, 6);
  assert.equal(presentation("未收錄的活動").image, "future-design-expo-v2");
  for (const [index, image] of images.entries()) {
    assert.match(image, /-v2$/);
    const bytes = await readFile(new URL(`../frontend/images/${image}.webp`, import.meta.url));
    const original = await readFile(new URL(`../frontend/images/${originals[index]}.webp`, import.meta.url));
    assert.equal(bytes.toString("ascii", 0, 4), "RIFF");
    assert.equal(bytes.toString("ascii", 8, 12), "WEBP");
    assert.ok(!bytes.equals(original), "不能只沿用原圖：" + image);
  }
});

test("成功通知保留綠色，活動展示文案不含工程技術名稱", async () => {
  const css = await readFile(new URL("../frontend/styles.css", import.meta.url), "utf8");
  assert.match(css, /\.toast\[data-tone="success"\] \{ border-color: #b8d9c4; color: #22633b; \}/);
  assert.match(css, /\.toast\[data-tone="success"\] \.toast-icon \{ background: #28804b; color: white; \}/);
  for (const meta of Object.values(catalog)) {
    assert.ok(meta.title && meta.subtitle);
    assert.doesNotMatch([meta.title, meta.subtitle, meta.label, meta.description].join(" "), /DevOps|Kubernetes|Cloud Native|雲原生|雲端原生|Spring\s?Boot/i);
  }
});

test("訂單面板使用局部綠色與陰影，綠色狀態只標示成功訂單", async () => {
  const css = await readFile(new URL("../frontend/styles.css", import.meta.url), "utf8");
  const panel = css.match(/\.order-panel \{[^}]*\}/)[0];
  assert.match(panel, /--panel-accent: #28804b/);
  assert.match(panel, /box-shadow: 0 18px 48px #00000024, 0 4px 12px #00000012/);
  assert.match(css, /\.panel-heading \{[^}]*background: var\(--panel-accent-soft\)/);
  assert.match(css, /\.count-badge \{[^}]*background: var\(--panel-accent\); color: white/);
  assert.match(css, /\.status-badge.success \{[^}]*color: var\(--panel-accent-ink\)/);
  assert.match(css, /\.status-badge.success::before \{ content: "✓ "; \}/);
  assert.doesNotMatch(css, /\.status-badge\.(?:pending|other) \{[^}]*--panel-accent/);
});

test("訂單面板跟隨靠右頭像，手機仍保留兩側安全邊距", async () => {
  const css = await readFile(new URL("../frontend/styles.css", import.meta.url), "utf8");
  assert.match(css, /\.header-inner \{ padding: 0 20px 0 max\(32px, calc\(\(100% - 1200px\) \/ 2\)\)/);
  assert.match(css, /\.order-panel \{ position: absolute; right: 0; top: 51px; width: 340px/);
  assert.match(css, /\.order-panel \{ position: fixed; top: 76px; left: 12px; right: 12px; width: auto/);
});

test("正常網頁只保留產品資訊，不顯示實作與展示補充文字", async () => {
  const html = await readFile(new URL("../frontend/index.html", import.meta.url), "utf8");
  assert.doesNotMatch(html, /示範模式|非登入帳號|圖片為\s*AI|示範活動|體驗用戶|高併發/);
  assert.match(html, /<label for="userId">使用者 ID<\/label>/);
  assert.match(html, /<footer class="site-footer"><span>ticket\.<\/span><\/footer>/);
  // 仍使用原來的 userId 操作，不把移除說明文字當作新增登入功能。
  assert.match(html, /id="userId" name="userId"/);
});
