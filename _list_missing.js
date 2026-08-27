// 列出小米缺图的基础机型（去版本后缀聚合）
const http = require('http');
function get(p) {
  return new Promise((r, j) => { http.get('http://127.0.0.1:8760' + p, res => { let d = ''; res.on('data', c => d += c); res.on('end', () => r(JSON.parse(d))); }).on('error', j); });
}
function norm(m) {
  return String(m || '').toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/低|高|统货|512|256|128|64|32|16|8|4|5g|全网通/g, '')
    .replace(/\s+/g, '').trim();
}
(async () => {
  const j = await get('/api/models?brand=%E5%B0%8F%E7%B1%B3&limit=500');
  const arr = j.items;
  const map = {};
  for (const it of arr) {
    const b = norm(it.model) || it.model;
    (map[b] = map[b] || { count: 0, img: 0, models: [] });
    map[b].count++;
    let imgs = [];
    try { imgs = JSON.parse(it.images || '[]'); } catch (e) {}
    if (imgs.length) map[b].img++;
    map[b].models.push(it.model);
  }
  const missing = Object.entries(map).filter(e => e[1].img === 0).sort((a, b) => b[1].count - a[1].count);
  console.log('=== 缺图基础型号', missing.length, '个 ===');
  missing.forEach(([b, info]) => console.log(b + ' | ' + info.count + '条 | ' + info.models.join(' / ')));
})().catch(e => { console.error(e); process.exit(1); });
