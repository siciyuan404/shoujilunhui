// 补齐剩余损坏机型图片（华为畅玩系列 + mate8 + 小米mix3）
// 下载(跟随302+UA+图片头检测) -> 上传 /uploads -> 写库
const http = require('http');
const https = require('https');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

// 品牌 -> { 型号前缀key: [图片短链URL] } （按最长前缀匹配）
const MAP = {
  '华为': {
    'mate8': ['https://aka.doubaocdn.com/s/z8zZgCrj73'],
    '畅玩60': ['https://aka.doubaocdn.com/s/iDNIeleNWf'],
    '畅玩50': ['https://aka.doubaocdn.com/s/fOHgPaVnyh'],
    '畅玩40': ['https://aka.doubaocdn.com/s/XOhPU2R2Ry'],
    '畅玩30': ['https://aka.doubaocdn.com/s/GVpxEzSgUu'],
    '畅玩20': ['https://aka.doubaocdn.com/s/Y0SSo1spj1'],
    '畅玩9': ['https://aka.doubaocdn.com/s/qUUIV8hKQE'],
    '畅玩8': ['https://aka.doubaocdn.com/s/7BhmyTO22r'],
    '畅玩7': ['https://aka.doubaocdn.com/s/fkDBu0et9r'],
    '畅玩6': ['https://aka.doubaocdn.com/s/KpjVrVMtTy'],
    '畅玩5': ['https://aka.doubaocdn.com/s/tWQhUlGuOv'],
  },
  '小米': {
    '小米mix3': ['https://aka.doubaocdn.com/s/3keoOgXlvW'],
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

async function main() {
  let done = 0;
  for (const [brand, map] of Object.entries(MAP)) {
    console.log('==== 品牌:', brand);
    const uploaded = {};
    for (const [key, urls] of Object.entries(map)) {
      uploaded[key] = [];
      for (const url of urls) {
        const buf = await download(url, 0);
        const st = sniff(buf);
        if (!st) { console.log('  [下载失败]', key, url.slice(0, 44)); continue; }
        const [ext, ct] = st;
        const r = await req('POST', '/api/upload', buf, { 'Content-Type': ct });
        if (r.code === 200 || r.code === 201) {
          let j; try { j = JSON.parse(r.body); } catch (e) { continue; }
          if (j.url) { uploaded[key].push(j.url); console.log('  [上传]', key, '->', j.url); }
        }
        await sleep(150);
      }
    }
    const list = await req('GET', '/api/models?brand=' + encodeURIComponent(brand) + '&limit=1000');
    let items = [];
    try { items = JSON.parse(list.body).items; } catch (e) { console.log('  [获取机型失败]', brand); continue; }
    let n = 0;
    for (const it of items) {
      const nm = norm(it.model);
      let bestKey = null, bestLen = 0;
      for (const key of Object.keys(map)) {
        if (nm === key) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
        if (nm.startsWith(key) && key.length > bestLen) { bestLen = key.length; bestKey = key; }
      }
      if (!bestKey || !uploaded[bestKey].length) continue;
      const r = await req('PUT', '/api/models/' + it.id, JSON.stringify({ images: uploaded[bestKey] }), { 'Content-Type': 'application/json' });
      if (r.code === 200) { n++; console.log('  [写库]', it.model, '<-', bestKey); }
      await sleep(80);
    }
    done += n;
    console.log('==', brand, '写库:', n);
  }
  console.log('\n全部完成: 写库', done, '台');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
