// 批量：下载外部图 -> 上传 server /uploads -> 写入对应小米机型 images
const http = require('http');
const fs = require('fs');
const path = require('path');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

// 型号关键字 -> 外部图片URL（可多张）
const IMG_MAP = {
  '小米13': ['https://aka.doubaocdn.com/s/gNMExVewVf'],
  '小米11ultra': ['https://aka.doubaocdn.com/s/z67hxbAGVe'],
  '小米10至尊版': ['https://aka.doubaocdn.com/s/ncVU957388']
};

function norm(s) {
  return String(s).toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/【[^】]*$/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/低|高|统货/g, '').replace(/\s+/g, '').trim();
}

function req(method, p, body, headers) {
  return new Promise((resolve, reject) => {
    const data = body ? body : null;
    const h = Object.assign({ 'X-API-Key': KEY }, headers || {});
    if (typeof data === 'string') h['Content-Length'] = Buffer.byteLength(data);
    const r = http.request({ host: '127.0.0.1', port: 8760, path: p, method, headers: h }, (res) => {
      let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function download(url) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const mod = u.protocol === 'https:' ? require('https') : require('http');
    mod.get(u, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

async function upload(buf) {
  const r = await req('POST', '/api/upload', buf, { 'Content-Type': 'image/jpeg' });
  if (r.code === 200 || r.code === 201) {
    const j = JSON.parse(r.body);
    return j.url;
  }
  throw new Error('upload failed ' + r.code + ' ' + r.body);
}

async function main() {
  const list = await req('GET', '/api/models?brand=%E5%B0%8F%E7%B1%B3&limit=500');
  const items = JSON.parse(list.body).items;
  let done = 0;
  for (const [key, urls] of Object.entries(IMG_MAP)) {
    const local = [];
    for (const url of urls) {
      const buf = await download(url);
      const u = await upload(buf);
      local.push(u);
      console.log('  [上传]', url.slice(0, 40), '->', u);
    }
    const targets = items.filter((it) => { const n = norm(it.model); return n === key || n.startsWith(key); });
    for (const it of targets) {
      const r = await req('PUT', '/api/models/' + it.id, JSON.stringify({ images: JSON.stringify(local) }), { 'Content-Type': 'application/json' });
      if (r.code === 200) { done++; console.log('  [图]', it.model, '->', local.length, '张'); }
      else console.log('  [FAIL]', it.model, r.code, r.body);
    }
  }
  console.log('写入图片完成:', done, '条');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
