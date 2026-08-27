// OPPO 系列补图+补参数（Find/Reno/A/K/R 全系列）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPEC = {
  'findx9': { img: 'https://aka.doubaocdn.com/s/cWOQQk5ZFP', release_date: '2026' },
  'findx8': { img: 'https://aka.doubaocdn.com/s/cWOQQk5ZFP', cpu_brand: '联发科', cpu_model: '天玑9400', release_date: '2024-11' },
  'findx7': { img: 'https://aka.doubaocdn.com/s/P7OMi2trAp', cpu_brand: '联发科', cpu_model: '天玑9300', ram: '12GB/16GB', back_camera: '5000万像素哈苏四摄', front_camera: '3200万像素', screen_size: '6.78英寸', battery: '5000mAh', charge: '100W', release_date: '2024-01' },
  'findx6': { img: 'https://aka.doubaocdn.com/s/7xJH80IPud', cpu_brand: '高通', cpu_model: '骁龙8Gen2', back_camera: '5000万像素哈苏三摄', refresh: '120Hz', release_date: '2023-03' },
  'findx5': { img: 'https://aka.doubaocdn.com/s/ssYTnrL5Pf', cpu_brand: '高通', cpu_model: '骁龙888', back_camera: '5000万双主摄+1300万长焦', front_camera: '3200万像素', screen_size: '6.55英寸OLED', battery: '4800mAh', release_date: '2022-02' },
  'findx3': { img: 'https://aka.doubaocdn.com/s/xG9dywUERn', cpu_brand: '高通', cpu_model: '骁龙870', back_camera: '5000万双主摄IMX766', screen_size: '10亿色臻彩屏', release_date: '2021-03' },
  'findx2': { img: 'https://aka.doubaocdn.com/s/8DtZLlo8ZU', release_date: '2020-03' },
  'findx': { img: 'https://aka.doubaocdn.com/s/xG9dywUERn', release_date: '2018-06' },
  'findn': { img: 'https://aka.doubaocdn.com/s/e2M1jCgqxk', release_date: '2023-10' },
  'reno16': { img: 'https://aka.doubaocdn.com/s/jHoeyCRu4e', release_date: '2026' },
  'reno15': { img: 'https://aka.doubaocdn.com/s/jHoeyCRu4e', release_date: '2025' },
  'reno14': { img: 'https://aka.doubaocdn.com/s/jHoeyCRu4e', release_date: '2025' },
  'reno13': { img: 'https://aka.doubaocdn.com/s/jHoeyCRu4e', release_date: '2024-11' },
  'reno12': { img: 'https://aka.doubaocdn.com/s/YUSNmL8lgQ', cpu_brand: '联发科', cpu_model: '天玑7300', back_camera: '5000万像素OIS三摄', front_camera: '3200万像素', refresh: '120Hz', battery: '5000mAh', release_date: '2024-05' },
  'reno11': { img: 'https://aka.doubaocdn.com/s/IS2AMtrTXu', cpu_brand: '联发科', cpu_model: '天玑8200', back_camera: '5000万+3200万长焦+800万广角', front_camera: '3200万像素', screen_size: '6.7英寸', battery: '4800mAh', charge: '67W', release_date: '2023-11' },
  'reno10': { img: 'https://aka.doubaocdn.com/s/zoowerxUyI', cpu_brand: '高通', cpu_model: '骁龙778G', screen_size: '6.7英寸OLED', refresh: '120Hz', battery: '4600mAh', release_date: '2023-05' },
  'reno9': { img: 'https://aka.doubaocdn.com/s/DHCONFTlSw', release_date: '2022-11' },
  'reno8': { img: 'https://aka.doubaocdn.com/s/Zr5NM5lwjM', charge: '80W', release_date: '2022-05' },
  'reno7': { img: 'https://aka.doubaocdn.com/s/nRVsvyy7ZJ', cpu_brand: '高通', cpu_model: '骁龙778G', screen_size: '6.43英寸', refresh: '90Hz', back_camera: '6400万像素', release_date: '2021-11' },
  'reno6': { img: 'https://aka.doubaocdn.com/s/U02tEnb4AV', cpu_brand: '联发科', cpu_model: '天玑900', ram: '8GB/12GB', rom: '128GB/256GB', screen_size: '6.43英寸AMOLED', battery: '4300mAh', release_date: '2021-05' },
  'reno5': { img: 'https://aka.doubaocdn.com/s/Qw46kB0sOe', cpu_brand: '高通', cpu_model: '骁龙765G', back_camera: '6400万像素', release_date: '2020-12' },
  'reno4': { img: 'https://aka.doubaocdn.com/s/t06mC33N2L', cpu_brand: '高通', cpu_model: '骁龙765', screen_size: '6.4英寸', back_camera: '4800万像素', battery: '4000mAh', charge: '65W', release_date: '2020-07' },
  'reno3': { img: 'https://aka.doubaocdn.com/s/gvPQWCYwbe', cpu_brand: '联发科', cpu_model: '天玑1000L', back_camera: '6400万四摄', battery: '4035mAh', charge: '30W', release_date: '2019-12' },
  'reno2': { img: 'https://aka.doubaocdn.com/s/tB6s2lDJyn', back_camera: '4800万四摄', release_date: '2019-09' },
  'reno': { img: 'https://aka.doubaocdn.com/s/tB6s2lDJyn', release_date: '2019-04', exclude: ['renoz'] },
  'renoz': { img: 'https://aka.doubaocdn.com/s/tB6s2lDJyn', release_date: '2019-05' },
  'renoace': { img: 'https://aka.doubaocdn.com/s/cVqf4nKVpA', cpu_brand: '高通', cpu_model: '骁龙855Plus', screen_size: '6.5英寸', refresh: '90Hz', back_camera: '4800万+800万+1300万+200万', battery: '4000mAh', charge: '65W', release_date: '2019-10' },
  'a3': { img: 'https://aka.doubaocdn.com/s/tLyTlU952V', cpu_brand: '高通', cpu_model: '骁龙695', screen_size: '6.7英寸', battery: '5000mAh', release_date: '2024-07', exclude: ['a31', 'a32', 'a33', 'a35', 'a36', 'a37'] },
  'a31': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2017' },
  'a32': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2018' },
  'a33': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2018' },
  'a35': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2018' },
  'a36': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2018' },
  'a37': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2018' },
  'a5': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', cpu_brand: '高通', cpu_model: '骁龙450', screen_size: '6.2英寸', back_camera: '1300万像素', battery: '4230mAh', release_date: '2019', exclude: ['a51', 'a52', 'a53'] },
  'a51': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2019' },
  'a52': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2020' },
  'a53': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2020' },
  'a6': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2019' },
  'a7': { img: 'https://aka.doubaocdn.com/s/6OMcJVLrrt', release_date: '2019' },
  'a8': { img: 'https://aka.doubaocdn.com/s/GZ3jzmVdiC', release_date: '2018' },
  'a9': { img: 'https://aka.doubaocdn.com/s/6OMcJVLrrt', battery: '4020mAh', release_date: '2019' },
  'a11': { img: 'https://aka.doubaocdn.com/s/TzmTID2AD9', cpu_brand: '高通', cpu_model: '骁龙665', screen_size: '6.5英寸', battery: '5000mAh', release_date: '2019-10' },
  'a17': { img: 'https://aka.doubaocdn.com/s/TzmTID2AD9', release_date: '2023' },
  'a2': { img: 'https://aka.doubaocdn.com/s/tLyTlU952V', release_date: '2022-12' },
  'a95': { img: 'https://aka.doubaocdn.com/s/ZDzHQ5Erxo', screen_size: '6.43英寸', back_camera: '4800万像素', release_date: '2021-05' },
  'a1': { img: 'https://aka.doubaocdn.com/s/tLyTlU952V', release_date: '2021' },
  'a57': { img: 'https://aka.doubaocdn.com/s/dCBzobSaNx', release_date: '2022' },
  'a79': { img: 'https://aka.doubaocdn.com/s/yPtOCaRDQM', cpu_brand: '联发科', cpu_model: 'Helio P23', screen_size: '6.01英寸AMOLED', back_camera: '1600万像素', front_camera: '1600万像素', battery: '3000mAh', release_date: '2017-11' },
  'k15': { img: 'https://aka.doubaocdn.com/s/nMJTkF8FkY', release_date: '2026' },
  'k13': { img: 'https://aka.doubaocdn.com/s/nMJTkF8FkY', release_date: '2025' },
  'k12': { img: 'https://aka.doubaocdn.com/s/nMJTkF8FkY', release_date: '2024-04' },
  'k11': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2023-07' },
  'k10': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2021' },
  'k9': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2019' },
  'k7': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2019' },
  'k5': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2018' },
  'k3': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2017' },
  'k1': { img: 'https://aka.doubaocdn.com/s/qp0px8GPrv', release_date: '2016' },
  'r11': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', cpu_brand: '高通', cpu_model: '骁龙660', release_date: '2017-06' },
  'r9': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2016' },
  'r8': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2016' },
  'r7': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2015' },
  'r15': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2018-03' },
  'r17': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2018-08' },
  'x9': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2015' },
  'n1': { img: 'https://aka.doubaocdn.com/s/GUMrkyZroL', release_date: '2014' },
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
  const list = await req('GET', '/api/models?brand=OPPO&limit=1000');
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
  console.log('OPPO 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
