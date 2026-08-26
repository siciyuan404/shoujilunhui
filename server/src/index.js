// 入口：静态文件 + REST API，监听 0.0.0.0:8760（供内网穿透反代）
const http = require('http');
const fs = require('fs');
const path = require('path');
const { openDb, autoMigrate, loadConfig, DB_FILE } = require('./db');
const { createRouter } = require('./api');

const cfg = loadConfig();
const db = openDb();
autoMigrate(db);

const WEB_ROOT = path.join(__dirname, '..', '..', 'web');
const LEGACY_ROOT = path.join(__dirname, '..', '..', 'phone-price');
const UPLOAD_ROOT = path.join(__dirname, '..', 'uploads');
const mime = {
  '.html': 'text/html; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp',
  '.svg': 'image/svg+xml', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.ico': 'image/x-icon', '.md': 'text/markdown; charset=utf-8',
};

const router = createRouter(db, cfg);

// 简单安全：阻止路径穿越
function safeJoin(root, urlPath) {
  const p = path.normalize(path.join(root, urlPath));
  if (!p.startsWith(root)) return null;
  return p;
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const pathname = decodeURIComponent(url.pathname);
  const q = url.searchParams;

  if (pathname.startsWith('/api/') || pathname === '/data.json') {
    try {
      await router(req, res, pathname, q);
    } catch (e) {
      if (!res.headersSent) {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
      }
      try { res.end(JSON.stringify({ error: e.message })); } catch (_) {}
    }
    return;
  }

  // 静态文件：优先 web/，回退 phone-price/（旧资源如 phone-specs.json），/uploads/ 走上传目录
  let file = pathname === '/' ? 'index.html' : pathname.slice(1);
  let p;
  if (pathname.startsWith('/uploads/')) {
    p = safeJoin(UPLOAD_ROOT, pathname.slice('/uploads/'.length));
    if (!p || !fs.existsSync(p) || fs.statSync(p).isDirectory()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('404 Not Found');
    }
    fs.readFile(p, (e, d) => {
      if (e) { res.writeHead(500); return res.end('500'); }
      res.writeHead(200, { 'Content-Type': mime[path.extname(p).toLowerCase()] || 'application/octet-stream' });
      res.end(d);
    });
    return;
  }
  p = safeJoin(WEB_ROOT, file);
  if (!p || !fs.existsSync(p) || fs.statSync(p).isDirectory()) {
    p = safeJoin(LEGACY_ROOT, file);
  }
  if (!p || !fs.existsSync(p) || fs.statSync(p).isDirectory()) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end('404 Not Found');
  }
  fs.readFile(p, (e, d) => {
    if (e) { res.writeHead(500); return res.end('500'); }
    res.writeHead(200, { 'Content-Type': mime[path.extname(p).toLowerCase()] || 'application/octet-stream' });
    res.end(d);
  });
}).listen(cfg.port, '0.0.0.0', () => {
  console.log(`[server] 手机回收价格 API 已启动: http://localhost:${cfg.port}`);
  console.log(`[server] 数据库文件: ${DB_FILE}`);
  console.log(`[server] API 文档: /api/health /api/stats /api/brands /api/categories /api/models`);
  console.log(`[server] 写操作需 Header: X-API-Key: ${cfg.apiKey}`);
});
