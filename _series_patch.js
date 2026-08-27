// 主流系列典型参数补全（screen_size/battery/charge/ram/rom/cpu_model/back_camera 等）
// 仅对空缺字段填写，同系列取公开典型值
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

// 系列典型参数表：norm(model) 前缀 -> 参数
const SPEC = {
  // === vivo/iQOO ===
  'x200': { screen_size: '6.67英寸', battery: '5500mAh', charge: '90W' },
  'x100': { screen_size: '6.78英寸', battery: '5000mAh', charge: '120W' },
  'x90': { screen_size: '6.78英寸', battery: '4810mAh', charge: '120W' },
  'x80': { screen_size: '6.78英寸', battery: '4500mAh', charge: '80W' },
  'x70': { screen_size: '6.56英寸', battery: '4400mAh', charge: '44W' },
  'x60': { screen_size: '6.56英寸', battery: '4300mAh', charge: '33W' },
  'x50': { screen_size: '6.56英寸', battery: '4315mAh', charge: '33W' },
  'x30': { screen_size: '6.44英寸', battery: '4350mAh', charge: '33W' },
  'x27': { screen_size: '6.4英寸', battery: '4000mAh', charge: '22.5W' },
  'x23': { screen_size: '6.4英寸', battery: '3400mAh' },
  'x21': { screen_size: '6.28英寸', battery: '3200mAh' },
  'x20': { screen_size: '6.01英寸', battery: '3245mAh' },
  'x9': { screen_size: '5.5英寸', battery: '3050mAh' },
  'x7': { screen_size: '5.2英寸', battery: '3000mAh' },
  'x5': { screen_size: '5.7英寸', battery: '4150mAh' },
  'iqoo12': { screen_size: '6.78英寸', battery: '5000mAh', charge: '120W' },
  'iqoo11': { screen_size: '6.78英寸', battery: '5000mAh', charge: '120W' },
  'iqoo10': { screen_size: '6.78英寸', battery: '4700mAh', charge: '120W' },
  'iqooneo9': { screen_size: '6.78英寸', battery: '5160mAh', charge: '120W' },
  'iqooneo8': { screen_size: '6.78英寸', battery: '5000mAh', charge: '120W' },
  'iqooneo7': { screen_size: '6.78英寸', battery: '5000mAh', charge: '120W' },
  'iqooneo6': { screen_size: '6.62英寸', battery: '4700mAh', charge: '80W' },
  'iqooneo5': { screen_size: '6.62英寸', battery: '4400mAh', charge: '66W' },
  'iqooz9': { screen_size: '6.67英寸', battery: '6000mAh', charge: '80W' },
  'iqooz8': { screen_size: '6.64英寸', battery: '5000mAh', charge: '120W' },
  'iqooz7': { screen_size: '6.64英寸', battery: '4500mAh', charge: '80W' },
  'y300': { screen_size: '6.77英寸', battery: '6500mAh', charge: '80W' },
  'y200': { screen_size: '6.67英寸', battery: '6000mAh', charge: '80W' },
  'y100': { screen_size: '6.67英寸', battery: '5000mAh', charge: '44W' },
  'y50': { screen_size: '6.53英寸', battery: '5000mAh', charge: '18W' },
  's20': { screen_size: '6.67英寸', battery: '6500mAh', charge: '80W' },
  's19': { screen_size: '6.78英寸', battery: '6000mAh', charge: '80W' },
  's18': { screen_size: '6.78英寸', battery: '6000mAh', charge: '80W' },
  's16': { screen_size: '6.78英寸', battery: '4600mAh', charge: '66W' },
  'nex3': { screen_size: '6.89英寸', battery: '4500mAh', charge: '44W' },
  'nex': { screen_size: '6.59英寸', battery: '4000mAh' },
  's30': { screen_size: '6.78英寸', battery: '4600mAh', charge: '80W' },
  's17': { screen_size: '6.78英寸', battery: '4600mAh', charge: '80W' },
  's15': { screen_size: '6.62英寸', battery: '4500mAh', charge: '66W' },
  's12': { screen_size: '6.44英寸', battery: '4050mAh', charge: '44W' },
  's10': { screen_size: '6.44英寸', battery: '4050mAh', charge: '44W' },
  // === 华为 ===
  'mate60': { screen_size: '6.69英寸', battery: '4750mAh', charge: '66W' },
  'mate50': { screen_size: '6.7英寸', battery: '4460mAh', charge: '66W' },
  'mate40': { screen_size: '6.76英寸', battery: '4200mAh', charge: '66W' },
  'mate30': { screen_size: '6.62英寸', battery: '4200mAh', charge: '40W' },
  'mate20': { screen_size: '6.53英寸', battery: '4000mAh', charge: '22.5W' },
  'mate10': { screen_size: '5.9英寸', battery: '4000mAh', charge: '22.5W' },
  'p60': { screen_size: '6.67英寸', battery: '4815mAh', charge: '66W' },
  'p50': { screen_size: '6.5英寸', battery: '4100mAh', charge: '66W' },
  'p40': { screen_size: '6.1英寸', battery: '3800mAh', charge: '40W' },
  'p30': { screen_size: '6.1英寸', battery: '3650mAh', charge: '40W' },
  'p20': { screen_size: '5.8英寸', battery: '3400mAh', charge: '22.5W' },
  'nova12': { screen_size: '6.7英寸', battery: '4600mAh', charge: '100W' },
  'nova11': { screen_size: '6.7英寸', battery: '4500mAh', charge: '66W' },
  'nova10': { screen_size: '6.67英寸', battery: '4000mAh', charge: '66W' },
  'nova9': { screen_size: '6.57英寸', battery: '4300mAh', charge: '66W' },
  'nova8': { screen_size: '6.57英寸', battery: '4000mAh', charge: '66W' },
  'nova7': { screen_size: '6.53英寸', battery: '4000mAh', charge: '40W' },
  'nova6': { screen_size: '6.57英寸', battery: '4100mAh', charge: '40W' },
  'nova5': { screen_size: '6.39英寸', battery: '3500mAh', charge: '40W' },
  '畅享': { screen_size: '6.75英寸', battery: '6000mAh', charge: '22.5W' },
  // === OPPO ===
  'findx8': { screen_size: '6.59英寸', battery: '5630mAh', charge: '80W' },
  'findx7': { screen_size: '6.78英寸', battery: '5000mAh', charge: '100W' },
  'findx6': { screen_size: '6.74英寸', battery: '4800mAh', charge: '80W' },
  'findx5': { screen_size: '6.55英寸', battery: '4800mAh', charge: '80W' },
  'findx3': { screen_size: '6.7英寸', battery: '4500mAh', charge: '65W' },
  'findx2': { screen_size: '6.7英寸', battery: '4200mAh', charge: '65W' },
  'findx': { screen_size: '6.42英寸', battery: '3730mAh', charge: '50W' },
  'reno13': { screen_size: '6.59英寸', battery: '5600mAh', charge: '80W' },
  'reno12': { screen_size: '6.7英寸', battery: '5000mAh', charge: '80W' },
  'reno11': { screen_size: '6.7英寸', battery: '5000mAh', charge: '80W' },
  'reno10': { screen_size: '6.7英寸', battery: '4600mAh', charge: '67W' },
  'reno9': { screen_size: '6.7英寸', battery: '4500mAh', charge: '67W' },
  'reno8': { screen_size: '6.43英寸', battery: '4500mAh', charge: '80W' },
  'reno7': { screen_size: '6.43英寸', battery: '4500mAh', charge: '60W' },
  'reno6': { screen_size: '6.43英寸', battery: '4300mAh', charge: '65W' },
  'reno5': { screen_size: '6.43英寸', battery: '4300mAh', charge: '65W' },
  'renox': { screen_size: '6.6英寸', battery: '4800mAh', charge: '80W' },
  'a5': { screen_size: '6.7英寸', battery: '5800mAh', charge: '45W' },
  'a3': { screen_size: '6.67英寸', battery: '5000mAh', charge: '45W' },
  // === 荣耀 ===
  'magic7': { screen_size: '6.78英寸', battery: '5650mAh', charge: '100W' },
  'magic6': { screen_size: '6.78英寸', battery: '5450mAh', charge: '66W' },
  'magic5': { screen_size: '6.73英寸', battery: '5100mAh', charge: '66W' },
  'magic4': { screen_size: '6.81英寸', battery: '4800mAh', charge: '66W' },
  'magic3': { screen_size: '6.76英寸', battery: '4600mAh', charge: '66W' },
  '80': { screen_size: '6.7英寸', battery: '4800mAh', charge: '66W' },
  '70': { screen_size: '6.67英寸', battery: '4800mAh', charge: '66W' },
  '60': { screen_size: '6.67英寸', battery: '4800mAh', charge: '66W' },
  '50': { screen_size: '6.57英寸', battery: '4300mAh', charge: '66W' },
  'x50': { screen_size: '6.78英寸', battery: '5800mAh', charge: '35W' },
  'x40': { screen_size: '6.67英寸', battery: '5100mAh', charge: '40W' },
  'x30': { screen_size: '6.75英寸', battery: '4800mAh', charge: '22.5W' },
  // === 小米/红米 ===
  '小米15': { screen_size: '6.36英寸', battery: '5400mAh', charge: '90W' },
  '小米14': { screen_size: '6.36英寸', battery: '4610mAh', charge: '90W' },
  '小米13': { screen_size: '6.36英寸', battery: '4500mAh', charge: '67W' },
  '小米12': { screen_size: '6.28英寸', battery: '4500mAh', charge: '67W' },
  '小米11': { screen_size: '6.81英寸', battery: '4600mAh', charge: '55W' },
  '小米10': { screen_size: '6.67英寸', battery: '4780mAh', charge: '30W' },
  '小米9': { screen_size: '6.39英寸', battery: '3300mAh', charge: '27W' },
  '小米8': { screen_size: '6.21英寸', battery: '3400mAh', charge: '18W' },
  '红米k': { screen_size: '6.67英寸', battery: '5000mAh', charge: '67W' },
  '红米note': { screen_size: '6.67英寸', battery: '5000mAh', charge: '33W' },
  '红米turbo': { screen_size: '6.67英寸', battery: '5000mAh', charge: '90W' },
  // === 三星 ===
  'galaxys24': { screen_size: '6.2英寸', battery: '4000mAh', charge: '25W' },
  'galaxys23': { screen_size: '6.1英寸', battery: '3900mAh', charge: '25W' },
  'galaxys22': { screen_size: '6.1英寸', battery: '3700mAh', charge: '25W' },
  'galaxys21': { screen_size: '6.2英寸', battery: '4000mAh', charge: '25W' },
  'galaxys20': { screen_size: '6.2英寸', battery: '4000mAh', charge: '25W' },
  'galaxys10': { screen_size: '6.1英寸', battery: '3400mAh', charge: '15W' },
  'galaxyzf': { screen_size: '7.6英寸', battery: '4400mAh', charge: '25W' },
  'galaxya': { screen_size: '6.5英寸', battery: '5000mAh', charge: '25W' },
  // === Apple（按代） ===
  'iphone16': { screen_size: '6.1英寸', battery: '3561mAh', charge: '20W', ram: '8GB', cpu_model: 'A18' },
  'iphone15': { screen_size: '6.1英寸', battery: '3349mAh', charge: '20W', ram: '6GB', cpu_model: 'A16' },
  'iphone14': { screen_size: '6.1英寸', battery: '3279mAh', charge: '20W', ram: '6GB', cpu_model: 'A15' },
  'iphone13': { screen_size: '6.1英寸', battery: '3227mAh', charge: '20W', ram: '4GB', cpu_model: 'A15' },
  'iphone12': { screen_size: '6.1英寸', battery: '2815mAh', charge: '20W', ram: '4GB', cpu_model: 'A14' },
  'iphone11': { screen_size: '6.1英寸', battery: '3110mAh', charge: '18W', ram: '4GB', cpu_model: 'A13' },
  'iphonex': { screen_size: '5.8英寸', battery: '2716mAh', charge: '18W', ram: '3GB', cpu_model: 'A11' },
  'iphone8': { screen_size: '4.7英寸', battery: '1821mAh', charge: '15W', ram: '2GB', cpu_model: 'A11' },
  'iphone7': { screen_size: '4.7英寸', battery: '1960mAh', charge: '10W', ram: '2GB', cpu_model: 'A10' },
  'iphone6': { screen_size: '4.7英寸', battery: '1810mAh', charge: '10W', ram: '1GB', cpu_model: 'A9' },
  // === 魅族 ===
  '魅族21': { screen_size: '6.55英寸', battery: '4800mAh', charge: '80W' },
  '魅族20': { screen_size: '6.55英寸', battery: '4700mAh', charge: '67W' },
  '魅族18': { screen_size: '6.2英寸', battery: '4000mAh', charge: '36W' },
  '魅族17': { screen_size: '6.6英寸', battery: '4500mAh', charge: '30W' },
  '魅族16': { screen_size: '6.0英寸', battery: '3010mAh', charge: '24W' },
  // === 真我 ===
  '真我gt': { screen_size: '6.7英寸', battery: '5000mAh', charge: '80W' },
  '真我neo': { screen_size: '6.7英寸', battery: '5500mAh', charge: '80W' },
  // === 努比亚 ===
  'z60': { screen_size: '6.8英寸', battery: '6000mAh', charge: '80W' },
  'z50': { screen_size: '6.67英寸', battery: '5000mAh', charge: '80W' },
  'z40': { screen_size: '6.67英寸', battery: '5000mAh', charge: '80W' },
  '红魔9': { screen_size: '6.8英寸', battery: '6500mAh', charge: '80W' },
  '红魔8': { screen_size: '6.8英寸', battery: '6000mAh', charge: '165W' },
  '红魔7': { screen_size: '6.8英寸', battery: '4500mAh', charge: '120W' },
  // === 一加 ===
  '1+13': { screen_size: '6.82英寸', battery: '6000mAh', charge: '100W' },
  '1+12': { screen_size: '6.82英寸', battery: '5400mAh', charge: '100W' },
  '1+11': { screen_size: '6.7英寸', battery: '5000mAh', charge: '100W' },
  '一加ace': { screen_size: '6.7英寸', battery: '5500mAh', charge: '100W' },
  // === 金立 ===
  'm2017': { screen_size: '5.7英寸', battery: '7000mAh', charge: '18W' },
  's10': { screen_size: '5.5英寸', battery: '3450mAh' },
};

async function main() {
  const list = await req('GET', '/api/models?limit=2000');
  const items = JSON.parse(list.body).items;
  const FIELDS = ['screen_size', 'battery', 'charge', 'ram', 'rom', 'cpu_model'];
  let total = 0;
  const matched = {};
  for (const it of items) {
    const nm = norm(it.model);
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(SPEC)) {
      const nk = norm(key);
      if (nm === nk) { bestKey = key; bestLen = Number.MAX_SAFE_INTEGER; break; }
      if (nm.startsWith(nk) && nk.length > bestLen) { bestLen = nk.length; bestKey = key; }
    }
    if (!bestKey) continue;
    const body = {};
    for (const f of FIELDS) {
      if (!String(it[f] || '').trim() && SPEC[bestKey][f]) body[f] = SPEC[bestKey][f];
    }
    if (Object.keys(body).length === 0) continue;
    const r = await req('PUT', '/api/models/' + it.id, JSON.stringify(body), { 'Content-Type': 'application/json' });
    if (r.code !== 200) console.log('  [FAIL]', it.id, r.code);
    else { total++; matched[bestKey] = (matched[bestKey] || 0) + 1; }
    await sleep(30);
  }
  console.log('系列参数补录台次:', total);
  const ks = Object.keys(matched);
  console.log('涉及系列数:', ks.length, ' 前20:', ks.slice(0, 20).join(','));
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
