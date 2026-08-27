// vivo 系列补图+补参数（第一批：X60-X200 / iQOO12 / iQOO NEO9 / Y300 / S20 / X50 / X Fold）
// 前缀匹配：先精确再最长前缀
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

// 系列前缀 -> 图片短链 + 参数（均来自网络搜索确认）
const SPEC = {
  'x200': { img: 'https://aka.doubaocdn.com/s/mVqVVd0RZF', cpu_brand: '联发科', cpu_model: '天玑9400', back_camera: '5000万像素蔡司三摄(1英寸LYT-818)', battery: '5500mAh', charge: '90W', screen_size: '6.3英寸OLED', refresh: '120Hz', release_date: '2025-11' },
  'x100': { img: 'https://aka.doubaocdn.com/s/7RU3RZKSBX', cpu_brand: '联发科', cpu_model: '天玑9300', back_camera: '5000万像素蔡司影像(IMX920)', screen_size: '6.78英寸', refresh: '120Hz', release_date: '2023-11' },
  'x90': { img: 'https://aka.doubaocdn.com/s/kyIqS6Qk81', cpu_brand: '联发科', cpu_model: '天玑9200', ram: '8GB/12GB', rom: '128GB/256GB/512GB', back_camera: '5000万像素蔡司影像', screen_size: '6.78英寸', refresh: '120Hz', battery: '4810mAh', charge: '120W', release_date: '2022-11' },
  'x80': { img: 'https://aka.doubaocdn.com/s/Q3fLwmFy09', cpu_brand: '联发科', cpu_model: '天玑9000', ram: '8GB/12GB', rom: '128GB/256GB/512GB', back_camera: '5000万像素蔡司影像', screen_size: '6.78英寸E5', refresh: '120Hz', battery: '4500mAh', charge: '80W', release_date: '2022-04' },
  'x70': { img: 'https://aka.doubaocdn.com/s/3WF1vR9tEX', cpu_brand: '联发科', cpu_model: '天玑1200', back_camera: '4000万像素微云台', screen_size: '6.56英寸', battery: '4400mAh', release_date: '2021-09' },
  'x60': { img: 'https://aka.doubaocdn.com/s/9hd44wkdaF', cpu_brand: '三星', cpu_model: 'Exynos1080', back_camera: '4800万像素微云台四摄', front_camera: '3200万像素', screen_size: '6.56英寸', refresh: '120Hz', battery: '4300mAh', charge: '33W', release_date: '2020-12' },
  'x50': { img: 'https://aka.doubaocdn.com/s/7aFkTVV814', back_camera: '4800万像素微云台四摄', release_date: '2020-06' },
  'xfold': { img: 'https://aka.doubaocdn.com/s/5Muh52RH1F', cpu_brand: '高通', cpu_model: '骁龙8Gen1', ram: '12GB', screen_size: '内外双120Hz E5折叠屏', refresh: '120Hz', release_date: '2022-04' },
  'iqoo12': { img: 'https://aka.doubaocdn.com/s/UHdzoGocBe', cpu_brand: '高通', cpu_model: '骁龙8Gen3', battery: '5000mAh', charge: '120W', front_camera: '1600万像素', release_date: '2023-11' },
  'iqooneo9': { img: 'https://aka.doubaocdn.com/s/lZkVVPo9Qe', cpu_brand: '高通', cpu_model: '骁龙8Gen2', back_camera: '5000万像素IMX920', battery: '5160mAh', charge: '120W', refresh: '144Hz', release_date: '2023-12' },
  'y300': { img: 'https://aka.doubaocdn.com/s/5Om1sU7Lgk', battery: '6500mAh', release_date: '2024-11' },
  's20': { img: 'https://aka.doubaocdn.com/s/y6xNuU1S3g', battery: '6500mAh', front_camera: '5000万像素柔光自拍', release_date: '2024-11' },
  'iqoo11': { img: 'https://aka.doubaocdn.com/s/kMWWKHv1FF', cpu_brand: '高通', cpu_model: '骁龙8Gen2', release_date: '2022-12' },
  'iqoo10': { img: 'https://aka.doubaocdn.com/s/MQuulMQNdx', cpu_brand: '高通', cpu_model: '骁龙8+Gen1', release_date: '2022-07' },
  'iqooneo5': { img: 'https://aka.doubaocdn.com/s/P4hN2A24jp', cpu_brand: '高通', cpu_model: '骁龙870', ram: '8GB/12GB', rom: '128GB/256GB/512GB', screen_size: '6.62英寸', battery: '4400mAh', charge: '66W', release_date: '2021-03' },
  'y200': { img: 'https://aka.doubaocdn.com/s/ggtZRWxfW3', cpu_brand: '高通', cpu_model: '骁龙4Gen1', back_camera: '6400万像素', screen_size: '6.67英寸AMOLED', refresh: '120Hz', battery: '6000mAh', charge: '80W', release_date: '2023-10' },
  'y100': { img: 'https://aka.doubaocdn.com/s/384HYHxUdA', back_camera: '6400万像素OIS', release_date: '2023-10' },
  'y50': { img: 'https://aka.doubaocdn.com/s/oSQVBg8TYH', ram: '6GB', rom: '128GB', release_date: '2020', exclude: ['y500'] },
  's19': { img: 'https://aka.doubaocdn.com/s/Jm5UpWsVh3', front_camera: '5000万像素柔光人像', release_date: '2024-06' },
  's18': { img: 'https://aka.doubaocdn.com/s/lUpiKJa4EP', cpu_brand: '高通', cpu_model: '第三代骁龙7', front_camera: '5000万像素', refresh: '120Hz', release_date: '2023-12' },
  'x30': { img: 'https://aka.doubaocdn.com/s/tr5RGbl1WM', cpu_brand: '三星', cpu_model: 'Exynos980', ram: '8GB', rom: '128GB/256GB', back_camera: '6400万像素三摄', screen_size: '6.44英寸', battery: '4350mAh', charge: '33W', release_date: '2019-12', exclude: ['x300'] },
  'iqooz9': { img: 'https://aka.doubaocdn.com/s/92WOCysrIy', cpu_brand: '高通', cpu_model: '骁龙7Gen3', battery: '6000mAh', release_date: '2024-04' },
  'nex3': { img: 'https://aka.doubaocdn.com/s/FxKUkTaEh6', cpu_brand: '高通', cpu_model: '骁龙855Plus', release_date: '2019-09' },
  'x27': { img: 'https://aka.doubaocdn.com/s/bJdojVRCx3', release_date: '2019-03' },
  'y77': { img: 'https://aka.doubaocdn.com/s/IEKE45FF9L', release_date: '2022-06' },
  's16': { img: 'https://aka.doubaocdn.com/s/JZbdwV1dtB', release_date: '2022-12' },
  'iqooz8': { img: 'https://aka.doubaocdn.com/s/o86ZUvugV0', screen_size: '6.64英寸LCD', release_date: '2023-08' },
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
  // 0. 先取机型列表，对每个 SPEC key 找出"无图且待补"的机型，只处理有缺口的 key
  const list = await req('GET', '/api/models?brand=vivo&limit=1000');
  const items = JSON.parse(list.body).items;
  const keyTargets = {}; // key -> [{it, nm}]
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
  // 1. 上传：仅对存在"无图机型"的 key 上传图片
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
  // 2. 写库
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
  console.log('vivo 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
