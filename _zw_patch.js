// 真我 realme 系列补图+补参数（GT/X/数字/Q/V/C 全系列）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPEC = {
  'realme GT7': { img: 'https://aka.doubaocdn.com/s/8ITcPpH2mf', cpu_brand: '联发科', cpu_model: '天玑9400+', screen_size: '6.8英寸', refresh: '144Hz', release_date: '2025-07' },
  'realme GT6': { img: 'https://aka.doubaocdn.com/s/i71XpzvS2f', cpu_brand: '高通', cpu_model: '骁龙8Gen3', release_date: '2024-07' },
  'realme GT5': { img: 'https://aka.doubaocdn.com/s/i71XpzvS2f', cpu_brand: '高通', cpu_model: '骁龙8Gen2', charge: '240W', release_date: '2023-08' },
  'realme GTNE05': { img: 'https://aka.doubaocdn.com/s/Ru9IGOJhzG', cpu_brand: '高通', cpu_model: '骁龙8+', charge: '150W', release_date: '2023-02' },
  'realme GTNE': { img: 'https://aka.doubaocdn.com/s/ONpZ8qwIRQ', cpu_brand: '高通', cpu_model: '第三代骁龙8s', screen_size: '6.78英寸', refresh: '120Hz', battery: '5500mAh', charge: '100W', release_date: '2024-05' },
  'realme GT': { img: 'https://aka.doubaocdn.com/s/RDBxlF5L3U', cpu_brand: '高通', cpu_model: '骁龙888', screen_size: '6.43英寸', refresh: '120Hz', charge: '65W', release_date: '2021-03' },
  'realmeNE07': { img: 'https://aka.doubaocdn.com/s/ONpZ8qwIRQ', cpu_brand: '高通', cpu_model: '第三代骁龙8s', refresh: '120Hz', release_date: '2024' },
  '真我GT': { img: 'https://aka.doubaocdn.com/s/RDBxlF5L3U', cpu_brand: '高通', cpu_model: '骁龙888', refresh: '120Hz', charge: '65W', release_date: '2021-03' },
  'realme X50': { img: 'https://aka.doubaocdn.com/s/5P5pP5EeER', cpu_brand: '高通', cpu_model: '骁龙765G', screen_size: '6.5英寸', refresh: '120Hz', back_camera: '6400万四摄', release_date: '2020-01' },
  'realme X7': { img: 'https://aka.doubaocdn.com/s/3tnoaHhxkg', cpu_brand: '联发科', cpu_model: '天玑800U', screen_size: '6.4英寸', battery: '4300mAh', charge: '65W', back_camera: '6400万像素', front_camera: '3200万像素', release_date: '2020-09' },
  'realme X2': { img: 'https://aka.doubaocdn.com/s/S2j8kvK62l', cpu_brand: '高通', cpu_model: '骁龙730G', back_camera: '6400万四摄', release_date: '2019-09' },
  'realme X': { img: 'https://aka.doubaocdn.com/s/S2j8kvK62l', release_date: '2019' },
  'realme C': { img: 'https://aka.doubaocdn.com/s/59io2fRFPs', cpu_brand: '联发科', cpu_model: 'Helio G70', screen_size: '6.5英寸', battery: '5000mAh', release_date: '2020-02' },
  '真我14系列': { img: 'https://aka.doubaocdn.com/s/0JaKW9D3VM', release_date: '2025' },
  '真我13': { img: 'https://aka.doubaocdn.com/s/0JaKW9D3VM', release_date: '2024-08' },
  '真我12pro系列': { img: 'https://aka.doubaocdn.com/s/0JaKW9D3VM', cpu_brand: '高通', cpu_model: '骁龙7sGen2', screen_size: '6.7英寸', refresh: '120Hz', back_camera: '5000万像素IMX882', battery: '5000mAh', charge: '67W', release_date: '2024-02' },
  '真我11系列': { img: 'https://aka.doubaocdn.com/s/0JaKW9D3VM', release_date: '2023-05' },
  '真我10': { img: 'https://aka.doubaocdn.com/s/0JaKW9D3VM', release_date: '2022-11' },
  '真我Q5': { img: 'https://aka.doubaocdn.com/s/OLLtEUJh0S', cpu_brand: '高通', cpu_model: '骁龙695', back_camera: '5000万像素', refresh: '120Hz', release_date: '2022-04' },
  '真我Q3': { img: 'https://aka.doubaocdn.com/s/OLLtEUJh0S', release_date: '2021-04' },
  '真我Q2': { img: 'https://aka.doubaocdn.com/s/OLLtEUJh0S', release_date: '2020-10' },
  '真我Q': { img: 'https://aka.doubaocdn.com/s/OLLtEUJh0S', release_date: '2019-09' },
  '真我V': { img: 'https://aka.doubaocdn.com/s/N97Rod6aFL', release_date: '2022' },
  '真我5': { img: 'https://aka.doubaocdn.com/s/OLLtEUJh0S', release_date: '2023-03' },
  '真我7i': { img: 'https://aka.doubaocdn.com/s/OLLtEUJh0S', release_date: '2024' },
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

async function main() {
  const list = await req('GET', '/api/models?brand=%E7%9C%9F%E6%88%91&limit=1000');
  const items = JSON.parse(list.body).items;
  const keyTargets = {};
  for (const it of items) {
    const nm = norm(it.model);
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(SPEC)) {
      const nk = norm(key);
      const s = SPEC[key];
      if (s.exclude && s.exclude.some((x) => nm.startsWith(norm(x)))) continue;
      if (nm === nk) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
      if (nm.startsWith(nk) && nk.length > bestLen) { bestLen = nk.length; bestKey = key; }
    }
    if (!bestKey) continue;
    if (!keyTargets[bestKey]) keyTargets[bestKey] = [];
    keyTargets[bestKey].push({ it, nm });
  }
  const uploaded = {};
  const hasImg = (it) => {
    const v = it.images;
    if (Array.isArray(v)) return v.length > 0;
    return !!(v && v !== '[]' && v !== '');
  };
  for (const [key, s] of Object.entries(SPEC)) {
    const targets = keyTargets[key] || [];
    const needImg = targets.some((t) => !hasImg(t.it));
    if (!needImg) { uploaded[key] = null; continue; }
    const buf = await download(s.img, 0);
    const st = sniff(buf);
    if (!st) { console.log('  [下载失败]', key); continue; }
    const r = await req('POST', '/api/upload', buf, { 'Content-Type': st[1] });
    if (r.code === 200 || r.code === 201) {
      let j; try { j = JSON.parse(r.body); } catch (e) { continue; }
      if (j.url) { uploaded[key] = [j.url]; console.log('  [上传]', key, '->', j.url); }
    }
    await sleep(150);
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
      await sleep(80);
    }
  }
  console.log('真我 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
