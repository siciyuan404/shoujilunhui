const http = require('http');

// 型号关键字(归一化后包含关系) -> 图片 URL 数组
const IMG_MAP = {
  '小米15ultra': ['/uploads/u_1787800072186_i5wgea.jpg'],
  '小米15pro': ['/uploads/u_1787800072070_fkksg3.jpg'],
  '小米15': ['/uploads/u_1787800072070_fkksg3.jpg'],
  '小米14pro': ['/uploads/u_1787800072248_b3ya5a.jpg']
};

function norm(s) {
  return String(s).toLowerCase()
    .replace(/【[^】]*】/g, '').replace(/【[^】]*$/g, '').replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '').replace(/（[^）]*）/g, '')
    .replace(/低|高|统货/g, '').replace(/\s+/g, '').trim();
}

function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const r = http.request({ host: '127.0.0.1', port: 8760, path, method, headers: {
        'Content-Type': 'application/json', 'X-API-Key': 'sk-e756xogvi0lmt9miamt',
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {})
      } }, (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d })); });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function main() {
  const list = await req('GET', '/api/models?brand=%E5%B0%8F%E7%B1%B3&limit=500');
  const items = JSON.parse(list.body).items;
  let ok = 0;
  const seen = new Set();
  for (const it of items) {
    const n = norm(it.model);
    for (const key of Object.keys(IMG_MAP)) {
      if (n === key || n.startsWith(key)) {
        if (seen.has(it.id)) continue;
        const r = await req('PUT', '/api/models/' + it.id, { images: JSON.stringify(IMG_MAP[key]) });
        if (r.code === 200) { ok++; seen.add(it.id); console.log('  [图]', it.model, '->', IMG_MAP[key][0]); }
        else console.log('  [FAIL]', it.model, r.code);
      }
    }
  }
  console.log('写入图片:', ok, '条');
}
main();
