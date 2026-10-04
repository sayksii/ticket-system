// 本機 UI 預覽：靜態檔案讀取目前的 frontend，/api 轉發到真實 K3s。
// 不啟動假的搶票服務、不部署、不推送映像檔。
import http from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = fileURLToPath(new URL("../frontend/", import.meta.url));
const target = new URL(process.env.TICKET_API_TARGET || "http://10.10.10.10");
const hostHeader = process.env.TICKET_API_HOST || "ticket.local";
const port = Number(process.env.PORT || 4173);
if (target.protocol !== "http:") throw new Error("預覽工具只支援 http 的 K3s Ingress。");
const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".webp": "image/webp" };

const server = http.createServer(async (req, res) => {
  const requestUrl = new URL(req.url, "http://localhost");
  const pathname = requestUrl.pathname;
  if (pathname === "/api" || pathname.startsWith("/api/")) {
    const upstream = http.request({
      hostname: target.hostname, port: target.port || 80, path: req.url, method: req.method,
      headers: { host: hostHeader, accept: "application/json", ...(req.headers["content-type"] ? { "content-type": req.headers["content-type"] } : {}) },
    }, response => {
      res.writeHead(response.statusCode, { "content-type": response.headers["content-type"] || "application/json", "cache-control": "no-store" });
      response.pipe(res);
    });
    upstream.setTimeout(12000, () => upstream.destroy(new Error("K3s request timed out")));
    upstream.on("error", () => {
      if (!res.headersSent) {
        res.writeHead(502, { "content-type": "application/json" });
        res.end(JSON.stringify({ message: "K3s 暫時無法連線，請確認虛擬機已啟動。" }));
      } else res.destroy();
    });
    req.on("aborted", () => upstream.destroy());
    req.pipe(upstream);
    return;
  }
  if (req.method !== "GET" && req.method !== "HEAD") { res.writeHead(405); res.end(); return; }
  try {
    const decoded = decodeURIComponent(pathname);
    const file = path.resolve(root, "." + (decoded === "/" ? "/index.html" : decoded));
    const relative = path.relative(root, file);
    if (relative.startsWith("..") || path.isAbsolute(relative) || !types[path.extname(file)]) {
      res.writeHead(404); res.end("Not found"); return;
    }
    let data = await readFile(file);
    // 僅本機預覽提供通知樣式示意；不偽造訂單、不呼叫搶票 POST。
    // 正式 nginx 不包含這支腳本，也不會因網址參數顯示成功提示。
    if (pathname === "/" && requestUrl.searchParams.get("previewToast") === "success") {
      data = Buffer.from(data.toString("utf8")
        .replace('id="toast" class="toast" role="status" aria-live="polite" hidden', 'id="toast" class="toast" role="status" aria-live="polite" data-tone="success"')
        .replace('id="toastIcon" class="toast-icon" aria-hidden="true"></span>', 'id="toastIcon" class="toast-icon" aria-hidden="true">✓</span>')
        .replace('id="toastText"></span>', 'id="toastText">樣式預覽｜搶票成功！訂單已成立</span>'));
    }
    res.writeHead(200, { "content-type": types[path.extname(file)], "cache-control": "no-cache" });
    res.end(req.method === "HEAD" ? undefined : data);
  } catch {
    res.writeHead(404); res.end("Not found");
  }
});
server.listen(port, "127.0.0.1", () => console.log("UI preview: http://127.0.0.1:" + port + " (real API: " + target.origin + ", Host: " + hostHeader + ")"));
