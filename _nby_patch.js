// 努比亚 系列补图+补参数（Z 系列 + 红魔 + 其他）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPEC = {
  'z60': { img: 'https://aka.doubaocdn.com/s/7m8BSPQWk2', cpu_brand: '高通', cpu_model: '骁龙8Gen3', back_camera: '6400万+5000万+5000万', release_date: '2023-12' },
  'z50': { img: 'https://aka.doubaocdn.com/s/7m8BSPQWk2', release_date: '2022-12' },
  'z40': { img: 'https://aka.doubaocdn.com/s/gYoXvVuylL', cpu_brand: '高通', cpu_model: '骁龙8Gen1', screen_size: '6.67英寸', back_camera: '6400万像素35mm', charge: '80W', release_date: '2022-02' },
  'z30': { img: 'https://aka.doubaocdn.com/s/tXE92vLwrn', back_camera: '2亿像素星空四摄', release_date: '2021-05' },
  'z20': { img: 'https://aka.doubaocdn.com/s/iQ9UMnJ84z', cpu_brand: '高通', cpu_model: '骁龙855Plus', screen_size: '6.42英寸', back_camera: '4800万+1600万+800万', battery: '4000mAh', release_date: '2019-08' },
  'z18': { img: 'https://aka.doubaocdn.com/s/7yCYDGO5N1', release_date: '2018-04' },
  'z17': { img: 'https://aka.doubaocdn.com/s/7yCYDGO5N1', cpu_brand: '高通', cpu_model: '骁龙835', screen_size: '5.5英寸无边框', release_date: '2017-06' },
  'z11': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', cpu_brand: '高通', cpu_model: '骁龙820', screen_size: '5.5英寸无边框', release_date: '2016-06' },
  'z9': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', release_date: '2015-05' },
  'z7': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', release_date: '2014-07' },
  '红魔9': { img: 'https://aka.doubaocdn.com/s/ObN1rt29lt', cpu_brand: '高通', cpu_model: '骁龙8Gen3', battery: '6500mAh', charge: '80W', release_date: '2023-11' },
  '红魔8': { img: 'https://aka.doubaocdn.com/s/ObN1rt29lt', release_date: '2022-12' },
  '红魔7': { img: 'https://aka.doubaocdn.com/s/FcNZIgqB8D', cpu_brand: '高通', cpu_model: '骁龙8Gen1', charge: '135W', release_date: '2022-02' },
  '红魔6': { img: 'https://aka.doubaocdn.com/s/FcNZIgqB8D', release_date: '2021-03' },
  '红魔5': { img: 'https://aka.doubaocdn.com/s/necBPDGWBV', cpu_brand: '高通', cpu_model: '骁龙865', screen_size: '6.65英寸', refresh: '144Hz', battery: '4500mAh', charge: '55W', release_date: '2020-03' },
  '红魔3': { img: 'https://aka.doubaocdn.com/s/necBPDGWBV', release_date: '2019-04' },
  '红魔2': { img: 'https://aka.doubaocdn.com/s/necBPDGWBV', release_date: '2018-11' },
  '红魔1': { img: 'https://aka.doubaocdn.com/s/necBPDGWBV', release_date: '2018-04' },
  '红魔mars': { img: 'https://aka.doubaocdn.com/s/necBPDGWBV', release_date: '2018-12' },
  '努比亚flip': { img: 'https://aka.doubaocdn.com/s/iQ9UMnJ84z', release_date: '2024' },
  '努比亚play': { img: 'https://aka.doubaocdn.com/s/iQ9UMnJ84z', release_date: '2020-04' },
  '努比亚x': { img: 'https://aka.doubaocdn.com/s/iQ9UMnJ84z', release_date: '2018-10' },
  '努比亚m2': { img: 'https://aka.doubaocdn.com/s/iQ9UMnJ84z', release_date: '2017' },
  '努比亚小牛': { img: 'https://aka.doubaocdn.com/s/iQ9UMnJ84z', release_date: '2024-04' },
  'n': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', release_date: '2016' },
  '513j': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', release_date: '2017' },
  's5smini': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', release_date: '2015' },
  'v18': { img: 'https://aka.doubaocdn.com/s/ubZsdpGUV3', release_date: '2018' },
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
  const list = await req('GET', '/api/models?brand=%E5%8A%AA%E6%AF%94%E4%BA%9A&limit=1000');
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
  console.log('努比亚 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
