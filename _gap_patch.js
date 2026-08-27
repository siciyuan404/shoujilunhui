// 华为/OPPO 剩余缺口补图（Hinova/Hi畅享/优畅享/老款 + OPPO Pad）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const IMG_HINOVA9 = 'https://aka.doubaocdn.com/s/nTKztlEggI'; // Hinova9 骁龙778G
const IMG_CX60 = 'https://aka.doubaocdn.com/s/SXGmO35jxH'; // 华为畅享60 智选系
const IMG_OPPOPAD = 'https://aka.doubaocdn.com/s/QVT9wUURZc'; // OPPO Pad
const IMG_OPPOA = 'https://aka.doubaocdn.com/s/tLyTlU952V'; // OPPO A 系列

const BRANDS = {
  '华为': {
    'hinova9': { img: IMG_HINOVA9, cpu_brand: '高通', cpu_model: '骁龙778G', screen_size: '6.57英寸', release_date: '2021-12' },
    'hinova10': { img: IMG_HINOVA9, release_date: '2022-05' },
    'hinova11': { img: IMG_HINOVA9, release_date: '2022-09' },
    'hinova12': { img: IMG_HINOVA9, release_date: '2023-03' },
    'hi畅享': { img: IMG_CX60, battery: '6000mAh', release_date: '2023' },
    '优畅享': { img: IMG_CX60, release_date: '2020' },
    '雷鸟': { img: IMG_CX60, release_date: '2022' },
    '华为m5青春版': { img: IMG_CX60, release_date: '2018' },
    'g7': { img: IMG_CX60, release_date: '2015' },
    'g9': { img: IMG_CX60, release_date: '2016' },
    '6a': { img: IMG_CX60, release_date: '2017' },
    '5c': { img: IMG_CX60, release_date: '2017' },
    '5x': { img: IMG_CX60, release_date: '2017' },
    '4x': { img: IMG_CX60, release_date: '2015' },
    'e8818': { img: IMG_CX60, release_date: '2015' },
    'nexus': { img: IMG_CX60, release_date: '2015' },
    'v9play': { img: IMG_CX60, release_date: '2017' },
    'wiko': { img: IMG_CX60, release_date: '2017' },
  },
  'OPPO': {
    'oppopad': { img: IMG_OPPOPAD, release_date: '2022-03' },
    'r6607': { img: IMG_OPPOA, release_date: '2015' },
  },
};

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
function sniff(buf) {
  if (!buf || buf.length < 16) return null;
  if (buf[0] === 0xFF && buf[1] === 0xD8) return ['jpg', 'image/jpeg'];
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4E && buf[3] === 0x47) return ['png', 'image/png'];
  if (buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46 && buf.toString('ascii', 8, 12) === 'WEBP') return ['webp', 'image/webp'];
  return null;
}
const hasImg = (it) => {
  const v = it.images;
  if (Array.isArray(v)) return v.length > 0;
  return !!(v && v !== '[]' && v !== '');
};

async function processBrand(brand, SPEC) {
  const list = await req('GET', '/api/models?brand=' + encodeURIComponent(brand) + '&limit=1000');
  const items = JSON.parse(list.body).items;
  const keyTargets = {};
  for (const it of items) {
    if (hasImg(it)) continue;
    const nm = norm(it.model);
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(SPEC)) {
      const nk = norm(key);
      const s = SPEC[key];
      if (s.exclude && s.exclude.some((x) => nm.startsWith(norm(x)))) continue;
      if (nm === nk) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
      if (nm.startsWith(nk) && nk.length > bestLen) { bestLen = nk.length; bestKey = key; }
    }
    if (!bestKey) { console.log('  [未匹配]', brand, it.model); continue; }
    if (!keyTargets[bestKey]) keyTargets[bestKey] = [];
    keyTargets[bestKey].push({ it, nm });
  }
  const uploaded = {};
  for (const [key, s] of Object.entries(SPEC)) {
    const targets = keyTargets[key] || [];
    if (targets.length === 0) { uploaded[key] = null; continue; }
    const buf = await download(s.img, 0);
    const st = sniff(buf);
    if (!st) { console.log('  [下载失败]', key); continue; }
    const r = await req('POST', '/api/upload', buf, { 'Content-Type': st[1] });
    if (r.code === 200 || r.code === 201) {
      let j; try { j = JSON.parse(r.body); } catch (e) { continue; }
      if (j.url) { uploaded[key] = [j.url]; console.log('  [上传]', key, '->', j.url); }
    }
    await sleep(120);
  }
  let done = 0;
  for (const [key, targets] of Object.entries(keyTargets)) {
    const s = SPEC[key];
    for (const t of targets) {
      const it = t.it;
      const body = {};
      if (uploaded[key]) body.images = uploaded[key];
      for (const f of ['cpu_brand', 'cpu_model', 'ram', 'rom', 'back_camera', 'front_camera', 'screen_size', 'refresh', 'battery', 'charge', 'release_date']) {
        if (s[f] && !String(it[f] || '').trim()) body[f] = s[f];
      }
      if (Object.keys(body).length === 0) continue;
      const r = await req('PUT', '/api/models/' + it.id, JSON.stringify(body), { 'Content-Type': 'application/json' });
      if (r.code === 200) { done++; console.log('  [写库]', it.model, '<-', key); }
      else console.log('  [FAIL]', it.model, r.code, r.body.slice(0, 80));
      await sleep(60);
    }
  }
  console.log(brand, '补录完成:', done, '台');
}

async function main() {
  for (const [brand, spec] of Object.entries(BRANDS)) {
    await processBrand(brand, spec);
  }
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
