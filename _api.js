const http = require('http');
function req(method, p, body, headers) {
  return new Promise((resolve) => {
    const data = body ? body : null;
    const h = Object.assign({ 'X-API-Key': 'sk-e756xogvi0lmt9miamt' }, headers || {});
    if (typeof data === 'string') h['Content-Length'] = Buffer.byteLength(data);
    const r = http.request({ host: '127.0.0.1', port: 8760, path: p, method, headers: h }, (res) => {
      let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    r.on('error', (e) => resolve({ code: 0, body: String(e) }));
    if (data) r.write(data);
    r.end();
  });
}
(async () => {
  const r = await req('GET', '/api/models?brand=OPPO&limit=5');
  console.log('GET code:', r.code);
  console.log('body head:', r.body.slice(0, 300));
})();