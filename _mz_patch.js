// 魅族 系列补图+补参数（数字/MX/Pro/魅蓝 全系列）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPEC = {
  '魅族21': { img: 'https://aka.doubaocdn.com/s/JJtTPBFeay', cpu_brand: '高通', cpu_model: '骁龙8Gen3', back_camera: '2亿像素主摄', battery: '4800mAh', refresh: '120Hz', release_date: '2023-11' },
  '魅族20': { img: 'https://aka.doubaocdn.com/s/FTPM5V9hwR', cpu_brand: '高通', cpu_model: '骁龙8Gen2', release_date: '2023-03' },
  '魅族18': { img: 'https://aka.doubaocdn.com/s/FTPM5V9hwR', cpu_brand: '高通', cpu_model: '骁龙888', release_date: '2021-03' },
  '魅族17': { img: 'https://aka.doubaocdn.com/s/FTPM5V9hwR', cpu_brand: '高通', cpu_model: '骁龙865', release_date: '2020-05' },
  '魅族lucky08': { img: 'https://aka.doubaocdn.com/s/FTPM5V9hwR', release_date: '2024-09' },
  '魅族16': { img: 'https://aka.doubaocdn.com/s/sU06kCb21v', cpu_brand: '高通', cpu_model: '骁龙845', release_date: '2018-08' },
  '魅族15': { img: 'https://aka.doubaocdn.com/s/u7sA7xdaUd', cpu_brand: '高通', cpu_model: '骁龙660', screen_size: '5.46英寸', battery: '3000mAh', release_date: '2018-04' },
  '魅族pro': { img: 'https://aka.doubaocdn.com/s/sU06kCb21v', release_date: '2016-04' },
  '魅族x8': { img: 'https://aka.doubaocdn.com/s/sU06kCb21v', release_date: '2018-10' },
  '魅族v8': { img: 'https://aka.doubaocdn.com/s/sU06kCb21v', release_date: '2018-09' },
  '魅族mx': { img: 'https://aka.doubaocdn.com/s/K4WDQhW5y3', cpu_brand: '联发科', cpu_model: 'MT6595', screen_size: '5.36英寸', release_date: '2014-09' },
  '魅蓝note16': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2024-05' },
  '魅蓝note': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', cpu_brand: '高通', cpu_model: '骁龙675', back_camera: '4800万像素', battery: '4000mAh', release_date: '2019-03' },
  '魅蓝2': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2015-09' },
  '魅蓝3': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-04' },
  '魅蓝5': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-10' },
  '魅蓝a5': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2017-07' },
  '魅蓝e': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-08' },
  '魅蓝u': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-09' },
  '魅蓝matal': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-07' },
  '魅蓝max': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-09' },
  '魅蓝s6': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2018-01' },
  '魅蓝x': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2016-11' },
  '魅蓝10': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2022-01' },
  '魅蓝20': { img: 'https://aka.doubaocdn.com/s/ISKRoyJc44', release_date: '2024-07' },
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
  const list = await req('GET', '/api/models?brand=%E9%AD%85%E6%97%8F&limit=1000');
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
  console.log('魅族 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
