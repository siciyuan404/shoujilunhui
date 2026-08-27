// 通用批量补图：node _add_imgs2.js <brand> <imgmap.json>
// imgmap.json: { "型号前缀": ["外部图片URL"] } 先精确匹配再最长前缀
const http = require('http');
const fs = require('fs');
const brand = process.argv[2];
const mapFile = process.argv[3];
if (!brand || !mapFile) { console.error('用法: node _add_imgs2.js <brand> <imgmap.json>'); process.exit(1); }
const IMG_MAP = JSON.parse(fs.readFileSync(mapFile, 'utf-8'));
const KEY = 'sk-e756xogvi0lmt9miamt';
const EXACT = new Set(['小米1']);
const EXCLUDE = ['苹果64g主板', '苹果128g主板', '苹果256g主板'];

function norm(s) {
  return String(s || '').toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/\s+/g, '').trim();
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
async function main() {
  const list = await req('GET', '/api/models?brand=' + encodeURIComponent(brand) + '&limit=500');
  const items = JSON.parse(list.body).items;
  // 上传所有图（每个 key 只传一次）
  const uploaded = {};
  for (const [key, urls] of Object.entries(IMG_MAP)) {
    uploaded[key] = [];
    for (const url of urls) {
      const buf = await download(url);
      const u = await req('POST', '/api/upload', buf, { 'Content-Type': 'image/jpeg' });
      const url2 = (u.code === 200 || u.code === 201) ? JSON.parse(u.body).url : null;
      if (url2) { uploaded[key].push(url2); console.log('  [上传]', key, '<-', url.slice(0, 40)); }
    }
  }
  // 对每条记录精确/最长前缀匹配挂图
  let done = 0;
  for (const it of items) {
    const n = norm(it.model);
    if (EXCLUDE.some((e) => n.startsWith(e))) { console.log('  [跳过]', it.model); continue; }
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(IMG_MAP)) {
      if (n === key) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
      if (!EXACT.has(key) && n.startsWith(key) && key.length > bestLen) { bestLen = key.length; bestKey = key; }
    }
    if (!bestKey || !uploaded[bestKey] || !uploaded[bestKey].length) continue;
    const r = await req('PUT', '/api/models/' + it.id, JSON.stringify({ images: JSON.stringify(uploaded[bestKey]) }), { 'Content-Type': 'application/json' });
    if (r.code === 200) { done++; console.log('  [图]', it.model, '<-', bestKey); }
  }
  console.log('品牌[' + brand + '] 写入图片完成:', done, '条');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
