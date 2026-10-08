// 僅供 UI 測試：完全使用記憶體假資料，沒有 K3s 代理或資料庫連線。
import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { catalog } from "../frontend/catalog.js";

const root = fileURLToPath(new URL("../frontend/", import.meta.url));
const events = Object.keys(catalog).map((name, i) => ({ id: i + 1, name, stock: 20, totalStock: 20 }));
const orders = [];
const types = { ".html": "text/html; charset=utf-8", ".css": "text/css", ".js": "text/javascript", ".webp": "image/webp" };
http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  const reply = (body, code = 200) => { res.writeHead(code, { "Content-Type": "application/json", "Cache-Control": "no-store" }); res.end(JSON.stringify(body)); };
  const snapshot = event => ({ eventId: event.id, name: event.name, totalStock: event.totalStock, mysqlStock: event.stock,
    redisStock: event.stock, buyers: orders.filter(o => o.eventId === event.id).length, status: "OPEN", orderCount: orders.filter(o => o.eventId === event.id).length,
    runOrders: orders.filter(o => o.eventId === event.id && o.userId.startsWith(url.searchParams.get("runPrefix") || "none")).length });
  if (url.pathname.startsWith("/api/")) {
    if (url.pathname === "/api/events") return reply(events);
    if (url.pathname === "/api/orders") return reply(orders.filter(o => o.userId === url.searchParams.get("userId")));
    if (url.pathname === "/api/developer/capabilities") return reply({ enabled: true, resetAvailable: true, fixture: true });
    const state = url.pathname.match(/^\/api\/developer\/events\/(\d+)\/state$/);
    if (state) return reply(snapshot(events[Number(state[1]) - 1]));
    const buy = url.pathname.match(/^\/api\/events\/(\d+)\/buy$/);
    if (buy && req.method === "POST") {
      await new Promise(resolve => setTimeout(resolve, 150));
      const event = events[Number(buy[1]) - 1]; const userId = url.searchParams.get("userId");
      if (orders.some(o => o.eventId === event.id && o.userId === userId)) return reply({ success: false, message: "同一使用者不能重複購買" });
      if (!event.stock) return reply({ success: false, message: "票已售完" });
      event.stock--;
      orders.push({ id: orders.length + 1, eventId: event.id, eventName: event.name, userId, status: "SUCCESS", createdAt: new Date().toISOString() });
      return reply({ success: true, status: "QUEUED" });
    }
    let body = ""; for await (const chunk of req) { body += chunk; if (body.length > 4096) return reply({ message: "Request too large" }, 413); }
    const data = body ? JSON.parse(body) : {};
    if (url.pathname === "/api/developer/reset" && req.method === "POST") {
      if (data.confirmed !== true) return reply({ message: "請先確認刪除範圍" }, 400);
      const event = events.find(e => e.id === data.eventId);
      if (!event || !Number.isInteger(data.stock) || data.stock < 1 || data.stock > 1000 || !Number.isInteger(data.expectedOrders) || data.expectedOrders < 0) return reply({ message: "重設參數不正確" }, 400);
      if (orders.filter(o => o.eventId === event.id).length !== data.expectedOrders) return reply({ message: "資料已變動，尚未刪除，請重新預覽" }, 409);
      event.stock = event.totalStock = data.stock;
      for (let i = orders.length - 1; i >= 0; i--) if (orders[i].eventId === event.id) orders.splice(i, 1);
      return reply(snapshot(event));
    }
    return reply({ message: "Unknown fixture API" }, 404);
  }
  try {
    const file = path.resolve(root, "." + (url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname)));
    if (path.relative(root, file).startsWith("..") || !types[path.extname(file)]) throw new Error("not found");
    res.writeHead(200, { "Content-Type": types[path.extname(file)], "Cache-Control": "no-store" }); res.end(await readFile(file));
  } catch { res.writeHead(404); res.end(); }
}).listen(4174, "127.0.0.1", () => console.log("ISOLATED UI FIXTURES ONLY: http://127.0.0.1:4174 (no real backend / no real data)"));
