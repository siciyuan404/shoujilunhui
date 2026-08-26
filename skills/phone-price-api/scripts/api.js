#!/usr/bin/env node
// API CLI：node api.js GET "/api/models?search=xxx"
// 写操作自动携带 X-API-Key（默认读 server/config.json）
// 图片上传：node api.js UPLOAD <本地图片路径>  → 返回 { url }，url 可写入 images 字段
const http = require('http');
const fs = require('fs');
const path = require('path');

const BASE = (process.env.PHONE_API_BASE || 'http://localhost:8760').replace(/\/+$/, '');

function loadKey() {
  if (process.env.PHONE_API_KEY) return process.env.PHONE_API_KEY;
  const cfgPaths = [
    path.join(__dirname, '..', '..', '..', 'server', 'config.json'),
    path.join(process.env.USERPROFILE || '', '.phone-api', 'config.json'),
  ];
  for (const p of cfgPaths) {
    try { return JSON.parse(fs.readFileSync(p, 'utf-8')).apiKey; } catch (e) {}
  }
  return '';
}

const args = process.argv.slice(2);
const method = (args[0] || '').toUpperCase();
const arg2 = args[1];
const bodyRaw = args[2];
if (!method || !arg2) {
  console.error('用法: node api.js <GET|POST|PUT|PATCH|DELETE> <path> [jsonBody]');
  console.error('      node api.js UPLOAD <本地图片路径>');
  console.error('示例: node api.js GET "/api/models?search=iphone16"');
  process.exit(1);
}
const key = loadKey();

// ---------- UPLOAD：上传本地图片文件，返回 { url } ----------
if (method === 'UPLOAD') {
  const filePath = arg2;
  if (!fs.existsSync(filePath)) { console.error('文件不存在: ' + filePath); process.exit(1); }
  const ext = path.extname(filePath).toLowerCase();
  const ctMap = {
    '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp',
    '.gif': 'image/gif', '.svg': 'image/svg+xml', '.bmp': 'image/bmp', '.avif': 'image/avif',
  };
  const ct = ctMap[ext];
  if (!ct) { console.error('不支持的图片格式（支持 png/jpg/jpeg/webp/gif/svg/bmp/avif）: ' + ext); process.exit(1); }
  const buf = fs.readFileSync(filePath);
  const u = new URL('/api/upload', BASE);
  const opts = { method: 'POST', headers: { 'Content-Type': ct, 'Content-Length': buf.length } };
  if (key) opts.headers['X-API-Key'] = key;
  const req = http.request(u, opts, (res) => {
    let b = '';
    res.setEncoding('utf-8');
    res.on('data', (c) => (b += c));
    res.on('end', () => {
      try { console.log(JSON.stringify(JSON.parse(b), null, 2)); }
      catch (e) { console.log(b); }
      process.exit(res.statusCode >= 400 ? 2 : 0);
    });
  });
  req.on('error', (e) => { console.error('上传失败:', e.message); process.exit(1); });
  req.write(buf);
  req.end();
  // 等待响应回调完成后退出（勿在此 process.exit）
  return;
}

// ---------- 通用 JSON API ----------
const u = new URL(arg2, BASE);
const opts = { method: method, headers: {} };
if (bodyRaw !== undefined) {
  // 支持 @文件路径 读取 JSON body（避免命令行引号转义问题）
  let jsonBody = bodyRaw;
  if (bodyRaw.startsWith('@')) {
    const fp = bodyRaw.slice(1);
    if (!fs.existsSync(fp)) { console.error('JSON body 文件不存在: ' + fp); process.exit(1); }
    jsonBody = fs.readFileSync(fp, 'utf-8');
  }
  JSON.parse(jsonBody); // 校验
  opts.headers['Content-Type'] = 'application/json; charset=utf-8';
  opts.body = jsonBody;
}
if (key && method !== 'GET') opts.headers['X-API-Key'] = key;

const req = http.request(u, opts, (res) => {
  let b = '';
  res.setEncoding('utf-8');
  res.on('data', (c) => (b += c));
  res.on('end', () => {
    try {
      const data = JSON.parse(b);
      console.log(JSON.stringify(data, null, 2));
    } catch (e) {
      console.log(b);
    }
    process.exit(res.statusCode >= 400 ? 2 : 0);
  });
});
req.on('error', (e) => { console.error('请求失败:', e.message); process.exit(1); });
if (opts.body) req.write(opts.body);
req.end();
