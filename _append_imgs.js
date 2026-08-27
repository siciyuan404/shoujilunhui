// 把细节图URL追加到机型 images（保留原图）
// 用法: node _append_imgs.js <jsonfile>
// json: {"id": ["url1","url2"], ...}
const http = require('http');
const fs = require('fs');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';
const map = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));

function req(method, p, body) {
  return new Promise((resolve) => {
    const data = body ? body : null;
    const h = { 'X-API-Key': KEY };
    if (typeof data === 'string') h['Content-Length'] = Buffer.byteLength(data);
    if (data) h['Content-Type'] = 'application/json';
    const r = http.request({ host: '127.0.0.1', port: 8760, path: p, method, headers: h }, (res) => {
      let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    r.on('error', () => resolve({ code: 0, body: '' }));
    if (data) r.write(data);
    r.end();
  });
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  for (const [id, urls] of Object.entries(map)) {
    // 读当前 images
    const g = await req('GET', '/api/models/' + id);
    let cur = [];
    try { cur = JSON.parse(JSON.parse(g.body).images || '[]'); } catch (e) { cur = []; }
    // 追加不重复
    for (const u of urls) { if (!cur.includes(u)) cur.push(u); }
    const r = await req('PUT', '/api/models/' + id, JSON.stringify({ images: cur }));
    if (r.code !== 200) console.log('[FAIL]', id, r.code, r.body.slice(0, 80));
    else console.log('[OK]', id, 'imgs:', cur.length);
    await sleep(30);
  }
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
