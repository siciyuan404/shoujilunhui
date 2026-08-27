// 三星 系列补图+补参数（S/Note/Z Fold/Flip/A/C/W 全系列）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPEC = {
  's24': { img: 'https://aka.doubaocdn.com/s/O3Um21C63i', cpu_brand: '高通', cpu_model: '骁龙8Gen3', back_camera: '2亿像素', screen_size: '6.8英寸AMOLED', battery: '5000mAh', charge: '45W', release_date: '2024-01' },
  's23': { img: 'https://aka.doubaocdn.com/s/O3Um21C63i', cpu_brand: '高通', cpu_model: '骁龙8Gen2', release_date: '2023-02' },
  's22': { img: 'https://aka.doubaocdn.com/s/O3Um21C63i', cpu_brand: '高通', cpu_model: '骁龙8Gen1', release_date: '2022-02' },
  's21': { img: 'https://aka.doubaocdn.com/s/O3Um21C63i', cpu_brand: '高通', cpu_model: '骁龙888', release_date: '2021-01' },
  's20': { img: 'https://aka.doubaocdn.com/s/LUzGqTV0y6', cpu_brand: '高通', cpu_model: '骁龙865', screen_size: '6.2英寸AMOLED', battery: '4000mAh', release_date: '2020-02' },
  's10': { img: 'https://aka.doubaocdn.com/s/b21GCQkavF', cpu_brand: '高通', cpu_model: '骁龙855', screen_size: '6.1英寸', release_date: '2019-02' },
  's9': { img: 'https://aka.doubaocdn.com/s/eVoF8JQajs', cpu_brand: '高通', cpu_model: '骁龙845', release_date: '2018-03' },
  's8': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', cpu_brand: '高通', cpu_model: '骁龙835', screen_size: '5.8英寸', release_date: '2017-03' },
  's7edge': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2016-02' },
  's6edge': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2015-04' },
  'galaxys': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2017-03' },
  'note20': { img: 'https://aka.doubaocdn.com/s/XyZcRK4UUr', cpu_brand: '高通', cpu_model: '骁龙865+', screen_size: '6.7英寸', refresh: '120Hz', battery: '4300mAh', release_date: '2020-08' },
  'note10': { img: 'https://aka.doubaocdn.com/s/TpXpgMolSd', release_date: '2019-08' },
  'note9': { img: 'https://aka.doubaocdn.com/s/TpXpgMolSd', release_date: '2018-08' },
  'note8': { img: 'https://aka.doubaocdn.com/s/TpXpgMolSd', release_date: '2017-08' },
  'z f0ld': { img: 'https://aka.doubaocdn.com/s/yczbIsNocV', release_date: '2023-07' },
  'f0ld': { img: 'https://aka.doubaocdn.com/s/yczbIsNocV', release_date: '2019-02' },
  'z flip': { img: 'https://aka.doubaocdn.com/s/EzP35YJjGH', release_date: '2022-08' },
  'w': { img: 'https://aka.doubaocdn.com/s/yczbIsNocV', release_date: '2022' },
  'a9': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2018' },
  'a8': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2018' },
  'c': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2016' },
  'g': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2019' },
  '三星': { img: 'https://aka.doubaocdn.com/s/1G7YYugTqu', release_date: '2020' },
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
  const list = await req('GET', '/api/models?brand=%E4%B8%89%E6%98%9F&limit=1000');
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
  console.log('三星 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
