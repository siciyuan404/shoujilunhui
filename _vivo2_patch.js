// vivo 第二批补图+补参数（老X/iQOO全系/Y老款/S老款/NEX/平板 覆盖剩余无图机型）
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

// 图源复用已有上传图 + 少量新图
const IMG_X30 = 'https://aka.doubaocdn.com/s/tr5RGbl1WM'; // x30 直屏老旗舰
const IMG_IQOO12 = 'https://aka.doubaocdn.com/s/UHdzoGocBe'; // iQOO12
const IMG_NEO9 = 'https://aka.doubaocdn.com/s/lZkVVPo9Qe'; // iQOO Neo9
const IMG_IQOO10 = 'https://aka.doubaocdn.com/s/MQuulMQNdx'; // iQOO10
const IMG_NEO5 = 'https://aka.doubaocdn.com/s/P4hN2A24jp'; // iQOO Neo5
const IMG_IQOOZ9 = 'https://aka.doubaocdn.com/s/92WOCysrIy'; // iQOO Z9
const IMG_IQOOZ8 = 'https://aka.doubaocdn.com/s/o86ZUvugV0'; // iQOO Z8
const IMG_Y77 = 'https://aka.doubaocdn.com/s/IEKE45FF9L'; // Y77 直屏中端
const IMG_S16 = 'https://aka.doubaocdn.com/s/JZbdwV1dtB'; // S16
const IMG_NEX3 = 'https://aka.doubaocdn.com/s/FxKUkTaEh6'; // NEX3
const IMG_XFOLD = 'https://aka.doubaocdn.com/s/5Muh52RH1F'; // X Fold
const IMG_Y50 = 'https://aka.doubaocdn.com/s/oSQVBg8TYH'; // Y50
const IMG_X30NEW = 'https://aka.doubaocdn.com/s/bJdojVRCx3'; // X27

const SPEC = {
  // 老 X 系列
  'xplay': { img: IMG_X30, release_date: '2016-03' },
  'xflip': { img: IMG_XFOLD, release_date: '2023-04' },
  'xnote': { img: IMG_XFOLD, release_date: '2021-12' },
  'x300': { img: IMG_X30NEW, release_date: '2025' },
  'x27': { img: IMG_X30NEW, release_date: '2019-03' },
  'x23': { img: IMG_X30, release_date: '2018-11' },
  'x21': { img: IMG_X30, release_date: '2018-03' },
  'x20': { img: IMG_X30, release_date: '2017-09' },
  'x9': { img: IMG_X30, release_date: '2016-11' },
  'x7': { img: IMG_X30, release_date: '2016-07' },
  'x710': { img: IMG_X30, release_date: '2015' },
  'x6': { img: IMG_X30, release_date: '2015-11' },
  'x5': { img: IMG_X30, release_date: '2015-05' },
  // iQOO 系列
  'iqooneo10': { img: IMG_NEO9, release_date: '2024-10' },
  'iqooneo8': { img: IMG_NEO9, release_date: '2023-05' },
  'iqooneo7': { img: IMG_NEO9, release_date: '2022-12' },
  'iqooneo6': { img: IMG_NEO9, release_date: '2022-04' },
  'iqooneo3': { img: IMG_NEO5, release_date: '2020-04' },
  'iqooneo': { img: IMG_NEO9, release_date: '2021' },
  'iqoo15': { img: IMG_IQOO12, release_date: '2025' },
  'iqoo13': { img: IMG_IQOO12, release_date: '2024-10' },
  'iqoo9': { img: IMG_IQOO10, release_date: '2022-01' },
  'iqoo8': { img: IMG_IQOO10, release_date: '2021-08' },
  'iqoo7': { img: IMG_IQOO10, release_date: '2021-01' },
  'iqoo5': { img: IMG_IQOO10, release_date: '2020-08' },
  'iqoo3': { img: IMG_IQOO10, release_date: '2020-02' },
  'iqoopro': { img: IMG_IQOO12, release_date: '2019-08' },
  'iqoo': { img: IMG_IQOO12, release_date: '2019-03' },
  'iqoou': { img: IMG_IQOOZ8, release_date: '2021-03' },
  'iqooz11': { img: IMG_IQOOZ9, release_date: '2024' },
  'iqooz10': { img: IMG_IQOOZ9, release_date: '2024' },
  'iqooz9': { img: IMG_IQOOZ9, release_date: '2024-04' },
  'iqooz8': { img: IMG_IQOOZ8, release_date: '2023-08' },
  'iqooz7': { img: IMG_IQOOZ8, release_date: '2022-08' },
  'iqooz6': { img: IMG_IQOOZ8, release_date: '2022-02' },
  'iqooz5': { img: IMG_IQOOZ8, release_date: '2021-05' },
  'iqooz3': { img: IMG_IQOOZ8, release_date: '2020-10' },
  'iqooz1': { img: IMG_IQOOZ8, release_date: '2020-05' },
  'iqoopad': { img: IMG_IQOO12, release_date: '2023-05' },
  // Y 系列老款
  'y600': { img: IMG_Y77, release_date: '2024' },
  'y500': { img: IMG_Y77, release_date: '2024' },
  'y97': { img: IMG_Y77, release_date: '2018' },
  'y93': { img: IMG_Y77, release_date: '2018' },
  'y91': { img: IMG_Y77, release_date: '2017' },
  'y85': { img: IMG_Y77, release_date: '2017' },
  'y83': { img: IMG_Y77, release_date: '2017' },
  'y81': { img: IMG_Y77, release_date: '2017' },
  'y79': { img: IMG_Y77, release_date: '2017' },
  'y78': { img: IMG_Y77, release_date: '2023-06' },
  'y77': { img: IMG_Y77, release_date: '2022-06' },
  'y76': { img: IMG_Y77, release_date: '2022' },
  'y75': { img: IMG_Y77, release_date: '2021-09' },
  'y73': { img: IMG_Y77, release_date: '2021-07' },
  'y72': { img: IMG_Y77, release_date: '2021' },
  'y71': { img: IMG_Y77, release_date: '2020' },
  'y70': { img: IMG_Y77, release_date: '2020' },
  'y69': { img: IMG_Y77, release_date: '2018' },
  'y67': { img: IMG_Y77, release_date: '2017' },
  'y66': { img: IMG_Y77, release_date: '2017' },
  'y60': { img: IMG_Y77, release_date: '2019' },
  'y55': { img: IMG_Y77, release_date: '2017' },
  'y53': { img: IMG_Y77, release_date: '2017' },
  'y52': { img: IMG_Y77, release_date: '2021' },
  'y51': { img: IMG_Y77, release_date: '2020' },
  'y50': { img: IMG_Y50, release_date: '2020', exclude: ['y500'] },
  'y37': { img: IMG_Y77, release_date: '2019' },
  'y36': { img: IMG_Y77, release_date: '2019' },
  'y35': { img: IMG_Y77, release_date: '2019' },
  'y33': { img: IMG_Y77, release_date: '2019' },
  'y32': { img: IMG_Y77, release_date: '2019' },
  'y31': { img: IMG_Y77, release_date: '2021' },
  'y30': { img: IMG_Y77, release_date: '2019' },
  'y27': { img: IMG_Y77, release_date: '2020' },
  'y11': { img: IMG_Y77, release_date: '2021' },
  'y10': { img: IMG_Y77, release_date: '2021' },
  'y7s': { img: IMG_Y77, release_date: '2016' },
  'y5s': { img: IMG_Y77, release_date: '2015' },
  'y3': { img: IMG_Y77, release_date: '2019' },
  // S 系列
  's50': { img: IMG_S16, release_date: '2025' },
  's30': { img: IMG_S16, release_date: '2022-11' },
  's17': { img: IMG_S16, release_date: '2023-05' },
  's16': { img: IMG_S16, release_date: '2022-12' },
  's15': { img: IMG_S16, release_date: '2022-05' },
  's12': { img: IMG_S16, release_date: '2021-11' },
  's10': { img: IMG_S16, release_date: '2020-11' },
  's9': { img: IMG_S16, release_date: '2019-11' },
  's7': { img: IMG_S16, release_date: '2019-01' },
  's6': { img: IMG_S16, release_date: '2018-08' },
  's5': { img: IMG_S16, release_date: '2018-03' },
  's1': { img: IMG_S16, release_date: '2017-05' },
  // NEX
  'nex3': { img: IMG_NEX3, release_date: '2019-09' },
  'nex': { img: IMG_NEX3, release_date: '2018-06' },
  // U/T/V/Z 杂项
  'u3': { img: IMG_Y77, release_date: '2016' },
  'u1': { img: IMG_Y77, release_date: '2016' },
  't1': { img: IMG_Y77, release_date: '2019' },
  't2': { img: IMG_Y77, release_date: '2021' },
  'v3': { img: IMG_Y77, release_date: '2015' },
  'z6': { img: IMG_Y77, release_date: '2018' },
  'z5': { img: IMG_Y77, release_date: '2018' },
  'z3': { img: IMG_Y77, release_date: '2018' },
  'z1': { img: IMG_Y77, release_date: '2018' },
  'vivopad': { img: IMG_Y77, release_date: '2022-04' },
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
  const list = await req('GET', '/api/models?brand=vivo&limit=1000');
  const items = JSON.parse(list.body).items;
  const hasImg = (it) => {
    const v = it.images;
    if (Array.isArray(v)) return v.length > 0;
    return !!(v && v !== '[]' && v !== '');
  };
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
    if (!bestKey) { console.log('  [未匹配]', it.model); continue; }
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
  console.log('vivo第二批 补录完成: 处理', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
