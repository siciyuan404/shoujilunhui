// 批量推断补全 network / os / screen_type 字段
// network: 按上市年份（5G 2020+ / 4G 之前）
// os: 按品牌 + 年份
// screen_type: 按品牌/系列（旗舰 OLED / 中端 LCD），仅对已知可推断的补
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

// 系统推断
function inferOS(brand, model, year) {
  const b = norm(brand);
  const m = norm(model);
  const y = year || 0;
  if (b === 'apple') return 'iOS';
  if (b.includes('华为')) {
    return y >= 2021 ? 'HarmonyOS' : 'EMUI（基于Android）';
  }
  if (b.includes('荣耀')) {
    return y >= 2021 ? 'MagicOS（基于Android）' : 'MagicUI（基于Android）';
  }
  if (b.includes('vivo') || m.includes('iqoo')) {
    return y >= 2020 ? 'OriginOS（基于Android）' : 'Funtouch OS（基于Android）';
  }
  if (b.includes('oppo') || b.includes('一加') || b.includes('真我')) {
    return 'ColorOS（基于Android）';
  }
  if (b.includes('小米') || b.includes('红米')) {
    return y >= 2024 ? 'HyperOS（基于Android）' : 'MIUI（基于Android）';
  }
  if (b.includes('三星')) return 'One UI（基于Android）';
  if (b.includes('魅族')) return 'Flyme（基于Android）';
  if (b.includes('努比亚')) return m.includes('红魔') ? 'RedMagic OS（基于Android）' : 'nubia UI（基于Android）';
  if (b.includes('一加')) return 'ColorOS（基于Android）';
  if (b.includes('谷歌')) return 'Android';
  if (b.includes('金立')) return 'amigo OS（基于Android）';
  if (b.includes('锤子')) return 'Smartisan OS（基于Android）';
  if (b.includes('联想')) return 'ZUI（基于Android）';
  if (b.includes('360')) return '360 OS（基于Android）';
  if (b.includes('美图')) return 'MEIOS（基于Android）';
  if (b.includes('华硕')) return 'Android';
  if (b.includes('乐视')) return 'EUI（基于Android）';
  if (b.includes('鼎桥') || b.includes('智选')) return 'HarmonyOS（基于Android）';
  if (b.includes('诺基亚')) return 'Android';
  if (b.includes('中兴')) return 'Android';
  return 'Android';
}

// 网络推断
function inferNetwork(year) {
  const y = year || 0;
  if (y >= 2020) return '5G全网通';
  return '4G全网通';
}

// 屏幕类型（按品牌/系列，仅旗舰/明显 OLED 系列）
function inferScreenType(brand, model) {
  const b = norm(brand);
  const m = norm(model);
  // 折叠屏
  if (m.includes('fold') || m.includes('flip') || m.includes('折叠') || m.includes('翻转')) return 'OLED折叠屏';
  // 苹果全系 OLED（iPhone 12 后）——按年份区分较难，直接给 OLED（新机型）或由年份判断
  if (b === 'apple') return 'OLED（Super Retina）';
  // 华为 Mate/P 旗舰
  if (b.includes('华为') && (m.startsWith('mate') || m.startsWith('p') || m.startsWith('x') || m.startsWith('magic'))) return 'OLED';
  // 华为中低端 LCD
  if (b.includes('华为')) return 'LCD';
  // OPPO Find/Reno 部分 OLED，A 系列 LCD
  if (b.includes('oppo')) {
    if (m.startsWith('find') || m.startsWith('reno')) return 'AMOLED';
    if (m.startsWith('a')) return 'LCD';
    return '';
  }
  // vivo X/S/NEX OLED，Y 系列 LCD
  if (b.includes('vivo')) {
    if (m.startsWith('x') || m.startsWith('s') || m.startsWith('nex') || m.includes('iqoo')) return 'AMOLED';
    if (m.startsWith('y')) return 'LCD';
    return '';
  }
  // 荣耀
  if (b.includes('荣耀')) {
    if (m.startsWith('magic') || m.startsWith('80') || m.startsWith('70') || m.startsWith('60') || m.startsWith('50') || m.startsWith('90') || m.startsWith('100') || m.startsWith('200')) return 'OLED';
    return 'LCD';
  }
  // 小米/红米
  if (b.includes('小米')) {
    if (m.startsWith('mi') || m.startsWith('14') || m.startsWith('13') || m.startsWith('12') || m.startsWith('11') || m.startsWith('10') || m.startsWith('15')) return 'AMOLED';
    return 'LCD';
  }
  if (b.includes('红米')) {
    if (m.startsWith('k') || m.startsWith('note') || m.startsWith('turbo')) return 'LCD';
    return 'LCD';
  }
  // 三星 S/Note/Z 旗舰 OLED
  if (b.includes('三星')) {
    if (m.startsWith('galaxys') || m.startsWith('galaxynote') || m.startsWith('zf') || m.startsWith('galaxyzf')) return 'AMOLED';
    return 'LCD';
  }
  return '';
}

async function main() {
  const list = await req('GET', '/api/models?limit=2000');
  const items = JSON.parse(list.body).items;
  console.log('总机型:', items.length);
  let nNet = 0, nOS = 0, nST = 0;
  for (const it of items) {
    const year = parseInt(String(it.release_date || '').slice(0, 4), 10);
    const body = {};
    if (!String(it.network || '').trim()) {
      const v = inferNetwork(year);
      if (v) { body.network = v; nNet++; }
    }
    if (!String(it.os || '').trim()) {
      const v = inferOS(it.brand, it.model, year);
      if (v) { body.os = v; nOS++; }
    }
    if (!String(it.screen_type || '').trim()) {
      const v = inferScreenType(it.brand, it.model);
      if (v) { body.screen_type = v; nST++; }
    }
    if (Object.keys(body).length === 0) continue;
    const r = await req('PUT', '/api/models/' + it.id, JSON.stringify(body), { 'Content-Type': 'application/json' });
    if (r.code !== 200) console.log('  [FAIL]', it.id, r.code, r.body.slice(0, 60));
    await sleep(30);
  }
  console.log('network 补:', nNet, ' os 补:', nOS, ' screen_type 补:', nST);
}
main().catch((e) => { console.error('ERR', e); process.exit(1); });
