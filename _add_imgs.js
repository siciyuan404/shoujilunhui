// 批量：下载外部图 -> 上传 server /uploads -> 写入对应小米机型 images
const http = require('http');
const fs = require('fs');
const path = require('path');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

// 完整型号关键字 -> 外部图片URL（先精确匹配再最长前缀，小米1 仅精确）
const IMG_MAP = {
  // 17系
  '小米17promax': ['https://aka.doubaocdn.com/s/rux0Sg8zHE'],
  '小米17pro': ['https://aka.doubaocdn.com/s/FXRhTjpUzC'],
  '小米17ultra': ['https://aka.doubaocdn.com/s/9lo634DJ9J'],
  '小米17t': ['https://aka.doubaocdn.com/s/qIybLnmlTW'],
  '小米17max': ['https://aka.doubaocdn.com/s/yy1JJ5JOF5'],
  // 15系
  '小米15pro': ['https://aka.doubaocdn.com/s/2YGqpsyngK'],
  '小米15ultra': ['https://aka.doubaocdn.com/s/nCiKpX5ZIY'],
  '小米15': ['https://aka.doubaocdn.com/s/UeDAH8gMBv'],
  // 14系
  '小米14ultra': ['https://aka.doubaocdn.com/s/KI2KoDQtfq'],
  '小米14pro': ['https://aka.doubaocdn.com/s/p1WbLTB5Lr'],
  '小米14': ['https://aka.doubaocdn.com/s/KI2KoDQtfq'],
  // 13系
  '小米13ultra': ['https://aka.doubaocdn.com/s/gNMExVewVf'],
  '小米13pro': ['https://aka.doubaocdn.com/s/gNMExVewVf'],
  '小米13': ['https://aka.doubaocdn.com/s/gNMExVewVf'],
  // 12系
  '小米12pro': ['https://aka.doubaocdn.com/s/0o63VN7cTQ'],
  '小米12sultr': ['https://aka.doubaocdn.com/s/Xx5IFDndSN'],
  '小米12s': ['https://aka.doubaocdn.com/s/WXOtjokYcF'],
  '小米12': ['https://aka.doubaocdn.com/s/O9mOH5nynF'],
  // 11系
  '小米11ultra': ['https://aka.doubaocdn.com/s/z67hxbAGVe'],
  '小米11pro': ['https://aka.doubaocdn.com/s/q4Ti5hJy3N'],
  '小米11青春版': ['https://aka.doubaocdn.com/s/emWRuUayoH'],
  '小米11': ['https://aka.doubaocdn.com/s/a3odGGuuN8'],
  // 10系
  '小米10至尊版': ['https://aka.doubaocdn.com/s/ncVU957388'],
  '小米10pro': ['https://aka.doubaocdn.com/s/xLk7V1hU36'],
  '小米10青春': ['https://aka.doubaocdn.com/s/OD1RVlM4Vb'],
  '小米10s': ['https://aka.doubaocdn.com/s/Kuiof7H7HZ'],
  '小米10': ['https://aka.doubaocdn.com/s/053GtdGmIr'],
  // 9系
  '小米9pro': ['https://aka.doubaocdn.com/s/chFiicMBZA'],
  '小米9se': ['https://aka.doubaocdn.com/s/WMOMlZe270'],
  '小米9透明版': ['https://aka.doubaocdn.com/s/bqNe2558zg'],
  '小米9': ['https://aka.doubaocdn.com/s/0wsTj522Ey'],
  // 8系
  '小米8探索版': ['https://aka.doubaocdn.com/s/gRRpwcnV0D'],
  '小米8se': ['https://aka.doubaocdn.com/s/aRu06RdqvH'],
  '小米8青春': ['https://aka.doubaocdn.com/s/LWj6zt1XI7'],
  '小米8': ['https://aka.doubaocdn.com/s/reQ6vueY21'],
  // 6/5/4/3/2/1
  '小米6x': ['https://aka.doubaocdn.com/s/DS0gt1ndGk'],
  '小米6': ['https://aka.doubaocdn.com/s/F151doRGgf'],
  '小米5sp': ['https://aka.doubaocdn.com/s/p3sjTlgfvz'],
  '小米5s': ['https://aka.doubaocdn.com/s/tZEhkVEipi'],
  '小米5c': ['https://aka.doubaocdn.com/s/bZIKZxwobU'],
  '小米5': ['https://aka.doubaocdn.com/s/2OmEeSPNpI'],
  '小米4c': ['https://aka.doubaocdn.com/s/ilFJ5ltiIf'],
  '小米4s': ['https://aka.doubaocdn.com/s/iBES2y2xp6'],
  '小米4': ['https://aka.doubaocdn.com/s/KybqqnyRjY'],
  '小米3': ['https://aka.doubaocdn.com/s/uoqDTD9xID'],
  '小米2s': ['https://aka.doubaocdn.com/s/aVgNzdT0HL'],
  '小米2a': ['https://aka.doubaocdn.com/s/mPmYrPmdhb'],
  '小米2': ['https://aka.doubaocdn.com/s/eeEnRjosdF'],
  '小米1': ['https://aka.doubaocdn.com/s/NbfNiLSzM5'],
  // MIX 系列
  '小米mix4': ['https://aka.doubaocdn.com/s/zpC1J0SXat'],
  '小米mix3': ['https://aka.doubaocdn.com/s/oCfBlbcgU7'],
  '小米mix2s': ['https://aka.doubaocdn.com/s/77JeeEWWq8'],
  '小米mix2': ['https://aka.doubaocdn.com/s/AYDYBNoJCa'],
  '小米mix1': ['https://aka.doubaocdn.com/s/tmjqivnawu'],
  '小米mixfold4': ['https://aka.doubaocdn.com/s/JhgEfoKANw'],
  '小米mixfold3': ['https://aka.doubaocdn.com/s/NofkfRU8cK'],
  '小米mixfold2': ['https://aka.doubaocdn.com/s/QsfU96k6kQ'],
  '小米mixflip': ['https://aka.doubaocdn.com/s/j1dXVkBIiv'],
  '小米mixfoid2代': ['https://aka.doubaocdn.com/s/QsfU96k6kQ'],
  'mlxfold折叠': ['https://aka.doubaocdn.com/s/lT9iJsVZem'],
  // 黑鲨
  '小米黑鲨5': ['https://aka.doubaocdn.com/s/jiNBLeLnM5'],
  '小米黑鲨4': ['https://aka.doubaocdn.com/s/Bqe0fe3t7U'],
  '小米黑鲨3pro': ['https://aka.doubaocdn.com/s/F7VzKlWI3W'],
  '小米黑鲨3': ['https://aka.doubaocdn.com/s/UI7ZBRBeIl'],
  '小米黑鲨2pro': ['https://aka.doubaocdn.com/s/gNVqtoQlee'],
  '小米黑鲨2': ['https://aka.doubaocdn.com/s/7EmPaXLVAN'],
  '小米黑鲨': ['https://aka.doubaocdn.com/s/hUGAMXVXV0'],
  // Civi
  '小米civi5pro': ['https://aka.doubaocdn.com/s/jrA5chWc6D'],
  '小米civi4pro': ['https://aka.doubaocdn.com/s/2CXUC5UwUE'],
  '小米civi3': ['https://aka.doubaocdn.com/s/XqqvBCxJnJ'],
  '小米civi2': ['https://aka.doubaocdn.com/s/SPt9DIZ68d'],
  '小米civi': ['https://aka.doubaocdn.com/s/jpLybppX6Y'],
  // CC9
  '小米cc9pro': ['https://aka.doubaocdn.com/s/znfY9jz50C'],
  '小米cc9美图': ['https://aka.doubaocdn.com/s/izUrddAdWv'],
  '小米cc9e': ['https://aka.doubaocdn.com/s/qSvOnW1VKb'],
  '小米cc9': ['https://aka.doubaocdn.com/s/gSKhlpEdu2'],
  // POCO
  '小米pocox4pro': ['https://aka.doubaocdn.com/s/PfnJkAVbVb'],
  '小米pococ3': ['https://aka.doubaocdn.com/s/VkKGU3ZHVd'],
  // Note / Max / Play
  '小米note3': ['https://aka.doubaocdn.com/s/UplhszQPyt'],
  '小米note2': ['https://aka.doubaocdn.com/s/9NUNVLlGvr'],
  '小米note': ['https://aka.doubaocdn.com/s/gkEaCopbc7'],
  '小米max3': ['https://aka.doubaocdn.com/s/eZr4SsgSwU'],
  '小米max2': ['https://aka.doubaocdn.com/s/NrrpH9smUh'],
  '小米max': ['https://aka.doubaocdn.com/s/XbnhKNVVm8'],
  '小米play': ['https://aka.doubaocdn.com/s/YpO461yUzW'],
  // 平板
  '小米平板6pro': ['https://aka.doubaocdn.com/s/HmFydbc4n3'],
  '小米平板6max': ['https://aka.doubaocdn.com/s/zKZoG348wW'],
  '小米平板6spro': ['https://aka.doubaocdn.com/s/YKRF4iUOtV'],
  '小米平板6': ['https://aka.doubaocdn.com/s/C6mj5Ju5xk'],
  '小米平板5pro': ['https://aka.doubaocdn.com/s/VyZFV1b4KU'],
  '小米平板5': ['https://aka.doubaocdn.com/s/OSfEC60xuC'],
  '小米平板4pro': ['https://aka.doubaocdn.com/s/NtoVwT9z89'],
  '小米平板4代': ['https://aka.doubaocdn.com/s/iTH3ER2a6g'],
  '小米平板3代': ['https://aka.doubaocdn.com/s/T3F17X2rlu'],
  '小米平板2代': ['https://aka.doubaocdn.com/s/iPiUUujIia'],
  '小米平板一代': ['https://aka.doubaocdn.com/s/Mt80MIF18m']
};

function norm(s) {
  return String(s).toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/\s+/g, '').trim();
}

function req(method, p, body, headers) {
  return new Promise((resolve, reject) => {
    const data = body ? body : null;
    const h = Object.assign({ 'X-API-Key': KEY }, headers || {});
    if (typeof data === 'string') h['Content-Length'] = Buffer.byteLength(data);
    const r = http.request({ host: '127.0.0.1', port: 8760, path: p, method, headers: h }, (res) => {
      let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function download(url) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const mod = u.protocol === 'https:' ? require('https') : require('http');
    mod.get(u, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

async function upload(buf) {
  const r = await req('POST', '/api/upload', buf, { 'Content-Type': 'image/jpeg' });
  if (r.code === 200 || r.code === 201) {
    const j = JSON.parse(r.body);
    return j.url;
  }
  throw new Error('upload failed ' + r.code + ' ' + r.body);
}

async function main() {
  const list = await req('GET', '/api/models?brand=%E5%B0%8F%E7%B1%B3&limit=500');
  const items = JSON.parse(list.body).items;
  let done = 0;
  // 先上传所有图片（每个 key 只传一次）
  const uploaded = {};
  for (const [key, urls] of Object.entries(IMG_MAP)) {
    uploaded[key] = [];
    for (const url of urls) {
      const buf = await download(url);
      const u = await upload(buf);
      uploaded[key].push(u);
      console.log('  [上传]', url.slice(0, 40), '->', u);
    }
  }
  // 只精确匹配的 key（避免 小米1 前缀命中 小米10/13/15...）
  const EXACT = new Set(['小米1']);
  // 对每条记录：先精确匹配，再找最长前缀匹配 key
  for (const it of items) {
    const n = norm(it.model);
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(IMG_MAP)) {
      if (n === key) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
      if (!EXACT.has(key) && n.startsWith(key) && key.length > bestLen) { bestLen = key.length; bestKey = key; }
    }
    if (!bestKey) continue;
    const r = await req('PUT', '/api/models/' + it.id, JSON.stringify({ images: JSON.stringify(uploaded[bestKey]) }), { 'Content-Type': 'application/json' });
    if (r.code === 200) { done++; console.log('  [图]', it.model, '<-', bestKey); }
    else console.log('  [FAIL]', it.model, r.code, r.body);
  }
  console.log('写入图片完成:', done, '条');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
