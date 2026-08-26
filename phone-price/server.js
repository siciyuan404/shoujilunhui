// 静态服务器 + 数据保存API：监听 8760，供 sj 隧道反代
const http = require("http");
const fs = require("fs");
const path = require("path");

const root = __dirname;
const mime = {
  ".html": "text/html; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".css": "text/css",
  ".js": "text/javascript",
  ".ico": "image/x-icon",
};

http
  .createServer((req, res) => {
    // POST /api/save - 保存 data.json
    if (req.method === "POST" && req.url === "/api/save") {
      let body = "";
      req.on("data", (chunk) => (body += chunk));
      req.on("end", () => {
        try {
          const data = JSON.parse(body);
          fs.writeFileSync(
            path.join(root, "data.json"),
            JSON.stringify(data, null, 1),
            "utf-8"
          );
          res.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
          res.end(JSON.stringify({ success: true }));
        } catch (e) {
          res.writeHead(500, { "Content-Type": "application/json; charset=utf-8" });
          res.end(JSON.stringify({ success: false, error: e.message }));
        }
      });
      return;
    }

    // 静态文件
    let p = path.join(root, decodeURIComponent((req.url || "/").split("?")[0]));
    if (p.endsWith(path.sep)) p += "index.html";
    fs.readFile(p, (e, d) => {
      if (e) {
        res.writeHead(404);
        return res.end("404");
      }
      res.writeHead(200, {
        "Content-Type": mime[path.extname(p).toLowerCase()] || "application/octet-stream",
      });
      res.end(d);
    });
  })
  .listen(8760, () => console.log("OK serving on 8760"));
