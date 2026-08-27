// 按品牌/年份推断补全 cpu_brand（平台维度）
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

function inferCPU(brand, model, year) {
  const b = norm(brand);
  const m = norm(model);
  const y = year || 0;
  if (b === 'apple') return 'Apple A系列';
  if (b.includes('华为')) return y >= 2021 ? '海思麒麟' : '海思麒麟';
  if (b.includes('荣耀')) return y >= 2021 ? '高通/联发科' : '海思麒麟';
  if (b.includes('鼎桥') || b.includes('智选')) return '海思麒麟';
  if (b.includes('三星')) return '三星Exynos/高通';
  if (b.includes('努比亚')) return '高通';
  if (b.includes('一加')) return '高通';
  if (b.includes('谷歌')) return 'Tensor/高通';
  if (b.includes('锤子')) return '高通';
  if (b.includes('华硕')) return m.includes('zenfone') ? '高通' : '高通/联发科';
  if (b.includes('乐视')) return '联发科';
  if (b.includes('美图')) return '联发科';
  if (b.includes('金立')) return '联发科/高通';
  if (b.includes('360')) return '联发科/高通';
  if (b.includes('魅族')) return '联发科/高通';
  if (b.includes('联想')) return m.includes('拯救者') ? '高通' : '高通/联发科';
  if (b.includes('真我')) return '高通/联发科';
  if (b.includes('小米') || b.includes('红米')) return '高通/联发科';
  if (b.includes('vivo')) return '联发科/高通';
  if (b.includes('oppo')) return '联发科/高通';
  return '';
}

async function main() {
  const list = await req('GET', '/api/models?limit=2000');
  const items = JSON.parse(list.body).items;
  let n = 0;
  for (const it of items) {
    if (String(it.cpu_brand || '').trim()) continue;
    const year = parseInt(String(it.release_date || '').slice(0, 4), 10);
    const v = inferCPU(it.brand, it.model, year);
    if (!v) continue;
    const r = await req('PUT', '/api/models/' + it.id, JSON.stringify({ cpu_brand: v }), { 'Content-Type': 'application/json' });
    if (r.code !== 200) console.log('  [FAIL]', it.id, r.code);
    else n++;
    await sleep(30);
  }
  console.log('cpu_brand 补:', n);
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
