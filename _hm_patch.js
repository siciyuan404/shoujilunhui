// 红米 系列补图+补参数（K/Note/Turbo/数字 全系列）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPEC = {
  '红米k90': { img: 'https://aka.doubaocdn.com/s/gMUKYzg1Oe', release_date: '2025-11' },
  '红米k80': { img: 'https://aka.doubaocdn.com/s/gMUKYzg1Oe', cpu_brand: '高通', cpu_model: '第三代骁龙8', charge: '90W', release_date: '2024-11' },
  '红米k70': { img: 'https://aka.doubaocdn.com/s/HkrS6m3B1G', cpu_brand: '高通', cpu_model: '骁龙8Gen2', back_camera: '5000万像素', battery: '5110mAh', release_date: '2023-11' },
  '红米k60': { img: 'https://aka.doubaocdn.com/s/qOns2FRSS5', cpu_brand: '高通', cpu_model: '骁龙8+Gen1', release_date: '2022-12' },
  '红米k50': { img: 'https://aka.doubaocdn.com/s/a2LU3a5tyf', cpu_brand: '联发科', cpu_model: '天玑8100', ram: '8GB/12GB', rom: '128GB/256GB', back_camera: '4800万像素OIS', screen_size: '6.67英寸2K AMOLED', refresh: '120Hz', battery: '5500mAh', release_date: '2022-03' },
  '红米k40': { img: 'https://aka.doubaocdn.com/s/vgybvc3khP', cpu_brand: '高通', cpu_model: '骁龙870', back_camera: '1.08亿像素', release_date: '2021-02' },
  '红米k30': { img: 'https://aka.doubaocdn.com/s/v9b7ts4wEl', cpu_brand: '高通', cpu_model: '骁龙730G', back_camera: '6400万像素', release_date: '2019-12' },
  '红米k20': { img: 'https://aka.doubaocdn.com/s/gyYROBTSTw', cpu_brand: '高通', cpu_model: '骁龙730', back_camera: '4800万像素', screen_size: '6.39英寸AMOLED', battery: '4000mAh', charge: '27W', release_date: '2019-05' },
  '红米note15': { img: 'https://aka.doubaocdn.com/s/Jixze0Hzgm', release_date: '2025-09' },
  '红米note14': { img: 'https://aka.doubaocdn.com/s/Jixze0Hzgm', screen_size: '6.67英寸', refresh: '120Hz', release_date: '2024-12' },
  '红米note13': { img: 'https://aka.doubaocdn.com/s/c0WouvTWVn', back_camera: '1亿像素', release_date: '2023-09' },
  '红米note12': { img: 'https://aka.doubaocdn.com/s/6poz6RrdCI', cpu_brand: '高通', cpu_model: '骁龙4Gen1', back_camera: '4800万像素', screen_size: '6.67英寸', battery: '5000mAh', charge: '33W', release_date: '2022-10' },
  '红米note11': { img: 'https://aka.doubaocdn.com/s/tzEqDmuPlO', back_camera: '5000万像素', release_date: '2021-10' },
  '红米note10': { img: 'https://aka.doubaocdn.com/s/dRZEwoMB8y', cpu_brand: '联发科', cpu_model: '天玑700', screen_size: '6.5英寸', charge: '18W', release_date: '2021-05' },
  '红米note9': { img: 'https://aka.doubaocdn.com/s/BicFKj3yYg', back_camera: '4800万像素', release_date: '2020-11' },
  '红米note8': { img: 'https://aka.doubaocdn.com/s/UrF6V0yiVy', screen_size: '6.3英寸水滴屏', back_camera: '4800万像素四摄', release_date: '2019-08' },
  '红米note7': { img: 'https://aka.doubaocdn.com/s/hVsg5PYGiI', screen_size: '6.3英寸水滴屏', back_camera: '4800万像素', release_date: '2019-01' },
  '红米note5': { img: 'https://aka.doubaocdn.com/s/65ZGcAWBem', release_date: '2018-03' },
  '红米note4': { img: 'https://aka.doubaocdn.com/s/65ZGcAWBem', release_date: '2016-08' },
  '红米note3': { img: 'https://aka.doubaocdn.com/s/65ZGcAWBem', release_date: '2015-11' },
  '红米note2': { img: 'https://aka.doubaocdn.com/s/65ZGcAWBem', release_date: '2015-08' },
  '红米note1': { img: 'https://aka.doubaocdn.com/s/65ZGcAWBem', release_date: '2014-03', exclude: ['红米note10', '红米note11', '红米note12', '红米note13', '红米note14', '红米note15'] },
  '红米turbo': { img: 'https://aka.doubaocdn.com/s/UZKxVQm1qe', release_date: '2024-04' },
  '红米14c': { img: 'https://aka.doubaocdn.com/s/CHNwXUy1u8', screen_size: '6.88英寸', release_date: '2024-12' },
  '红米13c': { img: 'https://aka.doubaocdn.com/s/CHNwXUy1u8', release_date: '2024-01' },
  '红米12c': { img: 'https://aka.doubaocdn.com/s/CHNwXUy1u8', release_date: '2022-12' },
  '红米15r': { img: 'https://aka.doubaocdn.com/s/CHNwXUy1u8', release_date: '2025' },
  '红米10x': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2020-05' },
  '红米9': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', cpu_brand: '联发科', cpu_model: 'Helio G80', screen_size: '6.53英寸水滴屏', battery: '5020mAh', charge: '18W', release_date: '2020-06' },
  '红米8': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2019-10' },
  '红米7': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2019-03' },
  '红米6': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2018-06' },
  '红米5': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2017-12' },
  '红米4': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2016-11' },
  '红米3': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2016-01' },
  '红米2': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2015-01' },
  '红米1': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2013-07', exclude: ['红米10x', '红米12c', '红米13c', '红米14c', '红米15r'] },
  '红米pro': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2016-07' },
  '红米s2': { img: 'https://aka.doubaocdn.com/s/0n56jIbjiA', release_date: '2018-05' },
  'redmipad': { img: 'https://aka.doubaocdn.com/s/CHNwXUy1u8', release_date: '2022' },
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
  const list = await req('GET', '/api/models?brand=%E7%BA%A2%E7%B1%B3&limit=1000');
  const items = JSON.parse(list.body).items;
  const keyTargets = {};
  for (const it of items) {
    const nm = norm(it.model);
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(SPEC)) {
      const s = SPEC[key];
      if (s.exclude && s.exclude.some((x) => nm.startsWith(x))) continue;
      if (nm === key) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
      if (nm.startsWith(key) && key.length > bestLen) { bestLen = key.length; bestKey = key; }
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
  console.log('红米 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
