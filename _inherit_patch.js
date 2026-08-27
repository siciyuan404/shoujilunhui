// 同系列参数继承：把已填机型(同品牌+同系列)的参数推广到未填机型
// 覆盖字段: screen_size / battery / charge / refresh / ram / rom / back_camera / front_camera / cpu_model
const http = require('http');

const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';

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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function norm(s) {
  return String(s || '').toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/\s+/g, '').trim();
}
// 系列名：去掉型号变体后缀
function series(model) {
  return norm(model)
    .replace(/(pro|ultra|ultra\+|plus|max|se|mini|turbo|青春|低配|高配|旗舰|系列|统点|new|新款|老款|\([^)]*\))/g, '')
    .replace(/\+$/, '');
}

const FIELDS = ['screen_size', 'battery', 'charge', 'refresh', 'ram', 'rom', 'back_camera', 'front_camera', 'cpu_model'];

async function main() {
  const list = await req('GET', '/api/models?limit=2000');
  const items = JSON.parse(list.body).items;
  // 按 brand+series 分组，收集已填字段的众数
  const groups = {};
  for (const it of items) {
    const key = norm(it.brand) + '|' + series(it.model);
    if (!groups[key]) groups[key] = { items: [], vals: {} };
    groups[key].items.push(it);
    for (const f of FIELDS) {
      const v = String(it[f] || '').trim();
      if (!v) continue;
      if (!groups[key].vals[f]) groups[key].vals[f] = {};
      groups[key].vals[f][v] = (groups[key].vals[f][v] || 0) + 1;
    }
  }
  let total = 0;
  for (const [key, g] of Object.entries(groups)) {
    // 该系列下每个字段取众数
    const inherit = {};
    for (const f of FIELDS) {
      if (!g.vals[f]) continue;
      let best = null, bestN = 0;
      for (const [v, n] of Object.entries(g.vals[f])) {
        if (n > bestN) { bestN = n; best = v; }
      }
      inherit[f] = best;
    }
    if (Object.keys(inherit).length === 0) continue;
    // 对未填的机型补
    for (const it of g.items) {
      const body = {};
      for (const f of FIELDS) {
        if (!String(it[f] || '').trim() && inherit[f]) body[f] = inherit[f];
      }
      if (Object.keys(body).length === 0) continue;
      const r = await req('PUT', '/api/models/' + it.id, JSON.stringify(body), { 'Content-Type': 'application/json' });
      if (r.code !== 200) console.log('  [FAIL]', it.id, r.code);
      else total++;
      await sleep(30);
    }
  }
  console.log('字段继承补录:', total, '台次');
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
