// 批量修复机型图片：重新下载短链(跟随302重定向+UA+图片头检测) -> 上传 /uploads -> 写回 images
// 根因修复: 旧脚本 node https.get 不跟随 302 重定向，拿到的是 2 字节 "{}" 占位，导致 592 个坏文件
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';
const EXACT_ALL = new Set(['小米1']);
const EXCLUDE_ALL = ['苹果64g主板', '苹果128g主板', '苹果256g主板'];

// 从 _add_imgs.js 提取小米 IMG_MAP
const miSrc = fs.readFileSync(path.join(__dirname, '_add_imgs.js'), 'utf-8');
const mm = miSrc.match(/const IMG_MAP = (\{[\s\S]*?\n\});/);
if (!mm) throw new Error('无法从 _add_imgs.js 提取 IMG_MAP');
const MI_MAP = eval('(' + mm[1] + ')');

const BRANDS_ALL = [
  { brand: '小米', map: MI_MAP, exact: ['小米1'], exclude: [] },
  { brand: '华为', map: JSON.parse(fs.readFileSync(path.join(__dirname, '_imgmap_huawei.json'), 'utf-8')), exact: [], exclude: [] },
  { brand: '荣耀', map: JSON.parse(fs.readFileSync(path.join(__dirname, '_imgmap_honor.json'), 'utf-8')), exact: [], exclude: [] },
  { brand: 'Apple', map: JSON.parse(fs.readFileSync(path.join(__dirname, '_imgmap_apple.json'), 'utf-8')), exact: [], exclude: EXCLUDE_ALL },
];
// 用法: node _fix_imgs.js [品牌名]  只处理指定品牌(品牌名=BRANDS_ALL.brand)
const ONLY = process.argv[2];
const BRANDS = ONLY ? BRANDS_ALL.filter((b) => b.brand === ONLY) : BRANDS_ALL;

function norm(s) {
  return String(s || '').toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/\s+/g, '').trim();
}
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

function req(method, p, body, headers) {
  return new Promise((resolve) => {
    const data = body ? body : null;
    const h = Object.assign({ 'X-API-Key': KEY }, headers || {});
    if (typeof data === 'string') h['Content-Length'] = Buffer.byteLength(data);
    const r = http.request({ host: '127.0.0.1', port: 8760, path: p, method, headers: h }, (res) => {
      let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    r.on('error', () => resolve({ code: 0, body: '' }));
    if (data) r.write(data);
    r.end();
  });
}

// 下载：跟随 302/301 重定向（最多5跳）+ UA + 超时
function download(url, redirects) {
  return new Promise((resolve) => {
    if (redirects > 5) return resolve(null);
    let u;
    try { u = new URL(url); } catch (e) { return resolve(null); }
    const mod = u.protocol === 'https:' ? https : http;
    const r = mod.get(u, {
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36' },
    }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        let next;
        try { next = new URL(res.headers.location, url).toString(); } catch (e) { return resolve(null); }
        return resolve(download(next, redirects + 1));
      }
      if (res.statusCode !== 200) { res.resume(); return resolve(null); }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    });
    r.on('error', () => resolve(null));
    r.setTimeout(15000, () => { r.destroy(); resolve(null); });
  });
}

// 按文件头判断真实图片类型
function sniff(buf) {
  if (!buf || buf.length < 16) return null;
  if (buf[0] === 0xFF && buf[1] === 0xD8) return ['jpg', 'image/jpeg'];
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4E && buf[3] === 0x47) return ['png', 'image/png'];
  if (buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46 && buf.toString('ascii', 8, 12) === 'WEBP') return ['webp', 'image/webp'];
  if (buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) return ['gif', 'image/gif'];
  return null;
}

async function main() {
  let totalUpload = 0, totalWrite = 0;
  for (const B of BRANDS) {
    console.log('==== 品牌:', B.brand, '| 映射 keys:', Object.keys(B.map).length);
    const uploaded = {};
    for (const [key, urls] of Object.entries(B.map)) {
      uploaded[key] = [];
      for (const url of urls) {
        const buf = await download(url, 0);
        const st = sniff(buf);
        if (!st) { console.log('  [下载失败/非图片]', key, url.slice(0, 44)); await sleep(100); continue; }
        const [ext, ct] = st;
        const r = await req('POST', '/api/upload', buf, { 'Content-Type': ct });
        if (r.code === 200 || r.code === 201) {
          let j; try { j = JSON.parse(r.body); } catch (e) { continue; }
          if (j.url) { uploaded[key].push(j.url); totalUpload++; }
        } else { console.log('  [上传失败]', key, r.code); }
        await sleep(120);
      }
    }
    // 写库
    const list = await req('GET', '/api/models?brand=' + encodeURIComponent(B.brand) + '&limit=1000');
    let items = [];
    try { items = JSON.parse(list.body).items; } catch (e) { console.log('  [获取机型失败]', B.brand); continue; }
    let done = 0, noMatch = 0;
    for (const it of items) {
      const n = norm(it.model);
      if (B.exclude.some((e) => n.includes(e))) continue;
      let bestKey = null, bestLen = 0;
      for (const key of Object.keys(B.map)) {
        if (n === key) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
        if (!B.exact.includes(key) && n.startsWith(key) && key.length > bestLen) { bestLen = key.length; bestKey = key; }
      }
      if (!bestKey || !uploaded[bestKey].length) { noMatch++; continue; }
      const r = await req('PUT', '/api/models/' + it.id, JSON.stringify({ images: uploaded[bestKey] }), { 'Content-Type': 'application/json' });
      if (r.code === 200) done++; else console.log('  [写库失败]', it.model, r.code);
      await sleep(80);
    }
    totalWrite += done;
    console.log('==', B.brand, '写库:', done, '| 未匹配/无图:', noMatch);
  }
  console.log('\n全部完成: 上传文件', totalUpload, '| 写库机型', totalWrite);
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
