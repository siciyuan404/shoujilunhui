// 苹果规格补录：基于机型知识，按 model 前缀匹配写入规格字段
const http = require('http');
const KEY = 'sk-e756xogvi0lmt9miamt';

const SPECS = {
  // iPhone 4-5系（数据库统点为 苹果4-5系）
  '苹果4': { release_date: '2010-2013年', cpu_brand: '苹果', cpu_model: 'A4/A5/A6', ram: '512MB-1GB', rom: '8-64GB', back_camera: '500-800万像素', front_camera: '30-120万像素', screen_size: '3.5-4英寸', screen_type: 'IPS', battery: '1420-1560mAh', charge: '5W', network: '3G', os: 'iOS' },
  // iPhone 6系
  '苹果6': { release_date: '2014年', cpu_brand: '苹果', cpu_model: 'A8', ram: '1GB', rom: '16/64/128GB', back_camera: '800万像素', front_camera: '120万像素', screen_size: '4.7英寸', screen_type: 'IPS', battery: '1810mAh', charge: '5W', network: '4G', os: 'iOS8' },
  // iPhone 7系
  '苹果7': { release_date: '2016年', cpu_brand: '苹果', cpu_model: 'A10', ram: '2GB', rom: '32/128/256GB', back_camera: '1200万像素', front_camera: '700万像素', screen_size: '4.7英寸', screen_type: 'IPS', battery: '1960mAh', charge: '5W', network: '4G', os: 'iOS10' },
  // iPhone 8系
  '苹果8': { release_date: '2017年', cpu_brand: '苹果', cpu_model: 'A11', ram: '2GB', rom: '64/256GB', back_camera: '1200万像素', front_camera: '700万像素', screen_size: '4.7英寸', screen_type: 'IPS', battery: '1821mAh', charge: '18W无线', network: '4G', os: 'iOS11' },
  // iPhone X（含 X 全系）
  '苹果xsmax': { release_date: '2018年', cpu_brand: '苹果', cpu_model: 'A12', ram: '4GB', rom: '64/256/512GB', back_camera: '双1200万像素', front_camera: '700万像素', screen_size: '6.5英寸', screen_type: 'OLED', battery: '3174mAh', charge: '18W', network: '4G', os: 'iOS12' },
  '苹果xs': { release_date: '2018年', cpu_brand: '苹果', cpu_model: 'A12', ram: '4GB', rom: '64/256/512GB', back_camera: '双1200万像素', front_camera: '700万像素', screen_size: '5.8英寸', screen_type: 'OLED', battery: '2658mAh', charge: '18W', network: '4G', os: 'iOS12' },
  '苹果xr': { release_date: '2018年', cpu_brand: '苹果', cpu_model: 'A12', ram: '3GB', rom: '64/128/256GB', back_camera: '单1200万像素', front_camera: '700万像素', screen_size: '6.1英寸', screen_type: 'LCD', battery: '2942mAh', charge: '18W', network: '4G', os: 'iOS12' },
  '苹果x': { release_date: '2017年', cpu_brand: '苹果', cpu_model: 'A11', ram: '3GB', rom: '64/256GB', back_camera: '双1200万像素', front_camera: '700万像素', screen_size: '5.8英寸', screen_type: 'OLED', battery: '2716mAh', charge: '18W', network: '4G', os: 'iOS11' },
  // iPhone 11 系
  '苹果11promax': { release_date: '2019年', cpu_brand: '苹果', cpu_model: 'A13', ram: '4GB', rom: '64/256/512GB', back_camera: '三1200万像素', front_camera: '1200万像素', screen_size: '6.5英寸', screen_type: 'OLED', battery: '3969mAh', charge: '18W', network: '4G', os: 'iOS13' },
  '苹果11pro': { release_date: '2019年', cpu_brand: '苹果', cpu_model: 'A13', ram: '4GB', rom: '64/256/512GB', back_camera: '三1200万像素', front_camera: '1200万像素', screen_size: '5.8英寸', screen_type: 'OLED', battery: '3046mAh', charge: '18W', network: '4G', os: 'iOS13' },
  '苹果11': { release_date: '2019年', cpu_brand: '苹果', cpu_model: 'A13', ram: '4GB', rom: '64/128/256GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'LCD', battery: '3110mAh', charge: '18W', network: '4G', os: 'iOS13' },
  // iPhone 12 系
  '苹果12promax': { release_date: '2020年', cpu_brand: '苹果', cpu_model: 'A14', ram: '6GB', rom: '128/256/512GB', back_camera: '三1200万像素', front_camera: '1200万像素', screen_size: '6.7英寸', screen_type: 'OLED', battery: '3687mAh', charge: '20W', network: '5G', os: 'iOS14' },
  '苹果12pro': { release_date: '2020年', cpu_brand: '苹果', cpu_model: 'A14', ram: '6GB', rom: '128/256/512GB', back_camera: '三1200万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED', battery: '2815mAh', charge: '20W', network: '5G', os: 'iOS14' },
  '苹果12mini': { release_date: '2020年', cpu_brand: '苹果', cpu_model: 'A14', ram: '4GB', rom: '64/128/256GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '5.4英寸', screen_type: 'OLED', battery: '2227mAh', charge: '20W', network: '5G', os: 'iOS14' },
  '苹果12': { release_date: '2020年', cpu_brand: '苹果', cpu_model: 'A14', ram: '4GB', rom: '64/128/256GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED', battery: '2815mAh', charge: '20W', network: '5G', os: 'iOS14' },
  // iPhone 13 系
  '苹果13promax': { release_date: '2021年', cpu_brand: '苹果', cpu_model: 'A15', ram: '6GB', rom: '128/256/512GB/1T', back_camera: '三1200万像素', front_camera: '1200万像素', screen_size: '6.7英寸', screen_type: 'OLED 120Hz', battery: '4352mAh', charge: '20W', network: '5G', os: 'iOS15' },
  '苹果13pro': { release_date: '2021年', cpu_brand: '苹果', cpu_model: 'A15', ram: '6GB', rom: '128/256/512GB/1T', back_camera: '三1200万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED 120Hz', battery: '3095mAh', charge: '20W', network: '5G', os: 'iOS15' },
  '苹果13mini': { release_date: '2021年', cpu_brand: '苹果', cpu_model: 'A15', ram: '4GB', rom: '128/256/512GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '5.4英寸', screen_type: 'OLED', battery: '2438mAh', charge: '20W', network: '5G', os: 'iOS15' },
  '苹果13': { release_date: '2021年', cpu_brand: '苹果', cpu_model: 'A15', ram: '4GB', rom: '128/256/512GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED', battery: '3227mAh', charge: '20W', network: '5G', os: 'iOS15' },
  // iPhone 14 系
  '苹果14promax': { release_date: '2022年', cpu_brand: '苹果', cpu_model: 'A16', ram: '6GB', rom: '128/256/512GB/1T', back_camera: '三4800万像素', front_camera: '1200万像素', screen_size: '6.7英寸', screen_type: 'OLED 120Hz', battery: '4323mAh', charge: '20W', network: '5G', os: 'iOS16' },
  '苹果14pro': { release_date: '2022年', cpu_brand: '苹果', cpu_model: 'A16', ram: '6GB', rom: '128/256/512GB/1T', back_camera: '三4800万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED 120Hz', battery: '3200mAh', charge: '20W', network: '5G', os: 'iOS16' },
  '苹果14plus': { release_date: '2022年', cpu_brand: '苹果', cpu_model: 'A15', ram: '6GB', rom: '128/256/512GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '6.7英寸', screen_type: 'OLED', battery: '4325mAh', charge: '20W', network: '5G', os: 'iOS16' },
  '苹果14': { release_date: '2022年', cpu_brand: '苹果', cpu_model: 'A15', ram: '6GB', rom: '128/256/512GB', back_camera: '双1200万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED', battery: '3279mAh', charge: '20W', network: '5G', os: 'iOS16' },
  // iPhone 15 系
  '苹果15promax': { release_date: '2023年', cpu_brand: '苹果', cpu_model: 'A17 Pro', ram: '8GB', rom: '256/512GB/1T', back_camera: '三4800万像素', front_camera: '1200万像素', screen_size: '6.7英寸', screen_type: 'OLED 120Hz', battery: '4441mAh', charge: '20W', network: '5G', os: 'iOS17' },
  '苹果15pro': { release_date: '2023年', cpu_brand: '苹果', cpu_model: 'A17 Pro', ram: '8GB', rom: '128/256/512GB/1T', back_camera: '三4800万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED 120Hz', battery: '3274mAh', charge: '20W', network: '5G', os: 'iOS17' },
  '苹果15plus': { release_date: '2023年', cpu_brand: '苹果', cpu_model: 'A16', ram: '6GB', rom: '128/256/512GB', back_camera: '双4800万像素', front_camera: '1200万像素', screen_size: '6.7英寸', screen_type: 'OLED', battery: '4383mAh', charge: '20W', network: '5G', os: 'iOS17' },
  '苹果15': { release_date: '2023年', cpu_brand: '苹果', cpu_model: 'A16', ram: '6GB', rom: '128/256/512GB', back_camera: '双4800万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED', battery: '3349mAh', charge: '20W', network: '5G', os: 'iOS17' },
  // iPhone 16 系
  '苹果16promax': { release_date: '2024年', cpu_brand: '苹果', cpu_model: 'A18 Pro', ram: '8GB', rom: '256/512GB/1T', back_camera: '三4800万像素', front_camera: '1200万像素', screen_size: '6.9英寸', screen_type: 'OLED 120Hz', battery: '4685mAh', charge: '20W', network: '5G', os: 'iOS18' },
  '苹果16pro': { release_date: '2024年', cpu_brand: '苹果', cpu_model: 'A18 Pro', ram: '8GB', rom: '128/256/512GB/1T', back_camera: '三4800万像素', front_camera: '1200万像素', screen_size: '6.3英寸', screen_type: 'OLED 120Hz', battery: '3582mAh', charge: '20W', network: '5G', os: 'iOS18' },
  '苹果16': { release_date: '2024年', cpu_brand: '苹果', cpu_model: 'A18', ram: '8GB', rom: '128/256/512GB', back_camera: '双4800万像素', front_camera: '1200万像素', screen_size: '6.1英寸', screen_type: 'OLED', battery: '3561mAh', charge: '20W', network: '5G', os: 'iOS18' },
  // iPad 统点类目（回收统价类，填代表规格）
  '迷你': { release_date: '2012-2021年', cpu_brand: '苹果', cpu_model: 'A5-A15', ram: '1-4GB', rom: '16-256GB', back_camera: '500-1200万像素', front_camera: '120-1200万像素', screen_size: '7.9-8.3英寸', screen_type: 'IPS', battery: '4400-5300mAh', os: 'iPadOS' },
  'ipadpro一代二代12.9': { release_date: '2015-2017年', cpu_brand: '苹果', cpu_model: 'A9X/A10X', ram: '4GB', rom: '32-512GB', back_camera: '800-1200万像素', front_camera: '120-700万像素', screen_size: '12.9英寸', screen_type: 'IPS', battery: '10307mAh', os: 'iPadOS' },
  '新版ipadpro': { release_date: '2018-2024年', cpu_brand: '苹果', cpu_model: 'A12X-M4', ram: '6-16GB', rom: '64GB-2T', back_camera: '1200-4800万像素', front_camera: '700-1200万像素', screen_size: '11/12.9英寸', screen_type: 'IPS/LED', battery: '7500-11000mAh', os: 'iPadOS' },
  'ipadpro四代': { release_date: '2020年', cpu_brand: '苹果', cpu_model: 'A12Z', ram: '6GB', rom: '128-512GB/1T', back_camera: '双1200万像素', front_camera: '700万像素', screen_size: '11/12.9英寸', screen_type: 'IPS', battery: '7500-9700mAh', os: 'iPadOS' },
  'ipadairari2pro': { release_date: '2013-2016年', cpu_brand: '苹果', cpu_model: 'A7-A9X', ram: '1-2GB', rom: '16-128GB', back_camera: '500-800万像素', front_camera: '120万像素', screen_size: '9.7英寸', screen_type: 'IPS', battery: '7340mAh', os: 'iPadOS' },
  'ipadari3ipadpro10.5': { release_date: '2017-2019年', cpu_brand: '苹果', cpu_model: 'A10X/A12', ram: '3GB', rom: '64-256GB', back_camera: '800-1200万像素', front_camera: '120-700万像素', screen_size: '10.5英寸', screen_type: 'IPS', battery: '8100-8200mAh', os: 'iPadOS' },
  'ipad1.2.3.4代': { release_date: '2010-2012年', cpu_brand: '苹果', cpu_model: 'A4/A5/A5X', ram: '256MB-1GB', rom: '16-64GB', back_camera: '无-500万像素', front_camera: '无-120万像素', screen_size: '9.7英寸', screen_type: 'IPS', battery: '6500-6900mAh', os: 'iOS' },
  'ipad5.6.7.8': { release_date: '2017-2020年', cpu_brand: '苹果', cpu_model: 'A9-A12', ram: '2-3GB', rom: '32-128GB', back_camera: '800万像素', front_camera: '120万像素', screen_size: '9.7/10.2英寸', screen_type: 'IPS', battery: '8200-8900mAh', os: 'iPadOS' },
  '苹果64g主板': { note: '回收统点类目（64G主板）', cpu_brand: '苹果', cpu_model: 'A系列', os: 'iOS' },
  '苹果128g主板': { note: '回收统点类目（128G主板）', cpu_brand: '苹果', cpu_model: 'A系列', os: 'iOS' },
  '苹果256g主板': { note: '回收统点类目（256G主板）', cpu_brand: '苹果', cpu_model: 'A系列', os: 'iOS' }
};

function norm(s) {
  return String(s || '').toLowerCase().replace(/【[^】]*】/g, '').replace(/\[[^\]]*\]/g, '').replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '').replace(/\s+/g, '').trim();
}
function req(method, p, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const h = { 'X-API-Key': KEY, 'Content-Type': 'application/json' };
    if (data) h['Content-Length'] = Buffer.byteLength(data);
    const r = http.request({ host: '127.0.0.1', port: 8760, path: p, method, headers: h }, (res) => {
      let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}
async function main() {
  const list = await req('GET', '/api/models?brand=Apple&limit=500');
  const items = JSON.parse(list.body).items;
  let done = 0, skip = 0;
  for (const it of items) {
    const n = norm(it.model);
    let bestKey = null, bestLen = 0;
    for (const key of Object.keys(SPECS)) {
      if (n === key) { bestKey = key; break; }
      if (n.startsWith(key) && key.length > bestLen) { bestLen = key.length; bestKey = key; }
    }
    if (!bestKey) { skip++; console.log('[SKIP]', it.model); continue; }
    const spec = SPECS[bestKey];
    const r = await req('PUT', '/api/models/' + it.id, spec);
    if (r.code === 200) { done++; console.log('[OK]', it.model, '<-', bestKey); }
    else console.log('[FAIL]', it.model, r.code, r.body);
  }
  console.log('苹果规格写入完成:', done, '条, 跳过', skip);
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
