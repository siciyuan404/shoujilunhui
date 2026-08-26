// REST API 处理函数
const fs = require('fs');
const path = require('path');
const { toLegacyExport } = require('./db');

const UPLOAD_DIR = path.join(__dirname, '..', 'uploads');
const EXT_BY_CT = {
  'image/png': 'png', 'image/jpeg': 'jpg', 'image/jpg': 'jpg', 'image/webp': 'webp',
  'image/gif': 'gif', 'image/svg+xml': 'svg', 'image/bmp': 'bmp', 'image/x-icon': 'ico',
  'image/avif': 'avif',
};

function json(res, code, data) {
  const body = JSON.stringify(data);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let b = '';
    req.on('data', (c) => {
      b += c;
      if (b.length > 50 * 1024 * 1024) { reject(new Error('body too large')); req.destroy(); }
    });
    req.on('end', () => {
      if (!b) return resolve({});
      try { resolve(JSON.parse(b)); } catch (e) { reject(new Error('invalid JSON body')); }
    });
    req.on('error', reject);
  });
}

// 原始二进制 body（用于图片上传）
function readRawBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      chunks.push(c);
      size += c.length;
      if (size > 50 * 1024 * 1024) { reject(new Error('body too large')); req.destroy(); }
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function requireKey(req, cfg) {
  const h = req.headers['x-api-key'] || '';
  const q = new URL(req.url, 'http://x').searchParams.get('key') || '';
  return h === cfg.apiKey || q === cfg.apiKey;
}

// 排序白名单
const SORTS = {
  price_asc: 'CAST(price AS REAL) ASC, id ASC',
  price_desc: 'CAST(price AS REAL) DESC, id ASC',
  name: 'model ASC',
  name_desc: 'model DESC',
  updated: 'updated_at DESC',
  brand: 'brand ASC, category ASC, id ASC',
  id: 'id ASC',
};

function listModels(db, q) {
  const where = [];
  const args = [];
  if (q.get('brand') && q.get('brand') !== '全部') { where.push('brand = ?'); args.push(q.get('brand')); }
  if (q.get('category')) { where.push('category = ?'); args.push(q.get('category')); }
  if (q.get('search')) {
    where.push('(model LIKE ? OR note LIKE ? OR brand LIKE ? OR category LIKE ?)');
    const s = '%' + q.get('search') + '%';
    args.push(s, s, s, s);
  }
  if (q.get('min_price')) { where.push('CAST(price AS REAL) >= ?'); args.push(Number(q.get('min_price'))); }
  if (q.get('max_price')) { where.push('CAST(price AS REAL) <= ?'); args.push(Number(q.get('max_price'))); }
  const whereSql = where.length ? 'WHERE ' + where.join(' AND ') : '';

  const total = db.prepare(`SELECT COUNT(*) AS c FROM models ${whereSql}`).get(...args).c;

  const sortKey = q.get('sort') || 'brand';
  const orderSql = SORTS[sortKey] || SORTS.brand;

  let limit = parseInt(q.get('limit') || '0', 10) || 0;
  if (limit < 0 || limit > 5000) limit = 0;
  let pageSql = '';
  if (limit > 0) {
    const page = Math.max(1, parseInt(q.get('page') || '1', 10));
    pageSql = ` LIMIT ${limit} OFFSET ${(page - 1) * limit}`;
  }

  const rows = db.prepare(`SELECT * FROM models ${whereSql} ORDER BY ${orderSql}${pageSql}`).all(...args);
  const limitUsed = limit > 0 ? limit : rows.length;
  const page = limit > 0 ? Math.max(1, parseInt(q.get('page') || '1', 10)) : 1;
  return { total, page, limit: limitUsed, items: rows };
}

function validateModel(body, partial) {
  const err = [];
  const out = {};
  if (!partial || body.brand !== undefined) {
    if (!body.brand || !String(body.brand).trim()) err.push('brand 必填');
    else out.brand = String(body.brand).trim();
  }
  if (!partial || body.category !== undefined) {
    if (!body.category || !String(body.category).trim()) err.push('category 必填');
    else out.category = String(body.category).trim();
  }
  if (!partial || body.model !== undefined) {
    if (!body.model || !String(body.model).trim()) err.push('model 必填');
    else out.model = String(body.model).trim();
  }
  if (body.price !== undefined) out.price = String(body.price ?? '').trim();
  if (body.note !== undefined) out.note = String(body.note ?? '').trim();
  if (body.images !== undefined) {
    // 接受数组或 JSON 字符串或单个 URL，统一存为 JSON 数组字符串
    let arr = body.images;
    if (!Array.isArray(arr)) {
      if (typeof arr === 'string') {
        try { const p = JSON.parse(arr); arr = Array.isArray(p) ? p : [arr]; }
        catch (e) { arr = [arr]; }
      } else { arr = [String(arr ?? '')]; }
    }
    out.images = JSON.stringify(arr.map((x) => String(x).trim()).filter(Boolean));
  }
  if (err.length) throw new Error(err.join('; '));
  return out;
}

function createRouter(db, cfg) {
  return async function handle(req, res, pathname, q) {
    const method = req.method;

    // ---------- 只读公开接口 ----------
    if (method === 'GET' && pathname === '/api/config') {
      // 仅"本机浏览器"访问可拿到 apiKey（穿透流量虽来自回环 IP，但 Host 头是公网域名）
      const ra = req.socket.remoteAddress || '';
      const host = String(req.headers.host || '').toLowerCase();
      const isLoopback = ra === '127.0.0.1' || ra === '::1' || ra === '::ffff:127.0.0.1';
      const isLocalHost = /^localhost(:\d+)?$/.test(host) || /^127\.0\.0\.1(:\d+)?$/.test(host) || /^\[::1\]:\d+$/.test(host);
      const isLocal = isLoopback && isLocalHost;
      return json(res, 200, { ok: true, readOnly: !!cfg.readOnly, apiKey: isLocal ? cfg.apiKey : null, local: isLocal });
    }

    if (method === 'GET' && pathname === '/api/health') {
      const c = db.prepare('SELECT COUNT(*) AS c FROM models').get().c;
      return json(res, 200, { ok: true, db: 'phone.db', models: c, time: new Date().toISOString() });
    }

    if (method === 'GET' && pathname === '/api/stats') {
      const total = db.prepare('SELECT COUNT(*) AS c FROM models').get().c;
      const brands = db.prepare('SELECT COUNT(DISTINCT brand) AS c FROM models').get().c;
      const cats = db.prepare("SELECT COUNT(DISTINCT brand || '|' || category) AS c FROM models").get().c;
      const last = db.prepare('SELECT MAX(updated_at) AS t FROM models').get().t;
      return json(res, 200, { total, brands, categories: cats, lastUpdated: last });
    }

    if (method === 'GET' && pathname === '/api/brands') {
      const rows = db.prepare('SELECT brand, COUNT(*) AS count FROM models GROUP BY brand ORDER BY brand').all();
      return json(res, 200, { items: rows, total: rows.length });
    }

    if (method === 'GET' && pathname === '/api/categories') {
      const brand = q.get('brand');
      const rows = brand
        ? db.prepare('SELECT brand, category, COUNT(*) AS count FROM models WHERE brand = ? GROUP BY brand, category ORDER BY category').all(brand)
        : db.prepare('SELECT brand, category, COUNT(*) AS count FROM models GROUP BY brand, category ORDER BY brand, category').all();
      return json(res, 200, { items: rows, total: rows.length });
    }

    if (method === 'GET' && pathname === '/api/models') {
      return json(res, 200, listModels(db, q));
    }

    let m = pathname.match(/^\/api\/models\/(\d+)$/);
    if (m) {
      const id = Number(m[1]);
      if (method === 'GET') {
        const row = db.prepare('SELECT * FROM models WHERE id = ?').get(id);
        if (!row) return json(res, 404, { error: 'not found' });
        return json(res, 200, row);
      }
      if (method === 'PUT' || method === 'PATCH') {
        const body = await readBody(req);
        const fields = validateModel(body, true);
        const keys = Object.keys(fields);
        if (!keys.length) return json(res, 400, { error: '无可更新字段' });
        const sets = keys.map((k) => `${k} = ?`).join(', ');
        db.prepare(`UPDATE models SET ${sets}, updated_at = datetime('now','localtime') WHERE id = ?`).run(...keys.map((k) => fields[k]), id);
        const row = db.prepare('SELECT * FROM models WHERE id = ?').get(id);
        if (!row) return json(res, 404, { error: 'not found' });
        return json(res, 200, row);
      }
      if (method === 'DELETE') {
        const r = db.prepare('DELETE FROM models WHERE id = ?').run(id);
        if (!r.changes) return json(res, 404, { error: 'not found' });
        return json(res, 200, { ok: true, id });
      }
    }

    // ---------- 写操作（需 API Key） ----------
    const writeOps = [
      ['POST', '/api/models'],
      ['POST', '/api/models/bulk'],
      ['POST', '/api/import'],
      ['POST', '/api/upload'],
      ['PUT', /^\/api\/models\/\d+$/],
      ['PATCH', /^\/api\/models\/\d+$/],
      ['DELETE', /^\/api\/models\/\d+$/],
    ];
    const isWrite = writeOps.some(([mm, p]) => method === mm && (p instanceof RegExp ? p.test(pathname) : p === pathname));

    if (isWrite) {
      if (!requireKey(req, cfg)) {
        res.setHeader('WWW-Authenticate', 'ApiKey');
        return json(res, 401, { error: '未授权：请在 Header 中携带 X-API-Key' });
      }
      if (cfg.readOnly) return json(res, 403, { error: '服务器为只读模式' });

      // ---------- 图片上传：原始二进制 body（Content-Type: image/*） ----------
      if (method === 'POST' && pathname === '/api/upload') {
        const ct = String(req.headers['content-type'] || '').toLowerCase().split(';')[0].trim();
        const ext = EXT_BY_CT[ct];
        if (!ext) return json(res, 400, { error: '仅支持图片文件（Content-Type 需为 image/*）' });
        const buf = await readRawBody(req);
        if (!buf || !buf.length) return json(res, 400, { error: '文件内容为空' });
        if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });
        const fname = `u_${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${ext}`;
        fs.writeFileSync(path.join(UPLOAD_DIR, fname), buf);
        return json(res, 201, { ok: true, url: '/uploads/' + fname, name: fname });
      }

      if (method === 'POST' && pathname === '/api/models') {
        const body = await readBody(req);
        const f = validateModel(body, false);
        f.price = f.price ?? ''; f.note = f.note ?? ''; f.images = f.images ?? '[]';
        const r = db.prepare('INSERT INTO models (brand, category, model, price, note, images) VALUES (?, ?, ?, ?, ?, ?)')
          .run(f.brand, f.category, f.model, f.price, f.note, f.images);
        return json(res, 201, db.prepare('SELECT * FROM models WHERE id = ?').get(r.lastInsertRowid));
      }

      if (method === 'POST' && pathname === '/api/models/bulk') {
        const body = await readBody(req);
        const items = Array.isArray(body.items) ? body.items : body;
        if (!Array.isArray(items) || !items.length) return json(res, 400, { error: 'items 数组必填' });
        const ins = db.prepare('INSERT INTO models (brand, category, model, price, note, images) VALUES (?, ?, ?, ?, ?, ?)');
        let n = 0;
        db.exec('BEGIN');
        try {
          for (const it of items) {
            const f = validateModel(it, false);
            ins.run(f.brand, f.category, f.model, f.price ?? '', f.note ?? '', f.images ?? '[]');
            n++;
          }
          db.exec('COMMIT');
        } catch (e) { db.exec('ROLLBACK'); throw e; }
        return json(res, 201, { ok: true, inserted: n });
      }

      if (method === 'POST' && pathname === '/api/import') {
        const body = await readBody(req);
        const data = Array.isArray(body) ? body : body.data;
        if (!Array.isArray(data)) return json(res, 400, { error: '需要 [{brand,category,models:[...]}] 格式数组' });
        const ins = db.prepare('INSERT INTO models (brand, category, model, price, note, images) VALUES (?, ?, ?, ?, ?, ?)');
        let n = 0;
        db.exec('BEGIN');
        try {
          if (body.replace !== false) db.exec('DELETE FROM models');
          for (const g of data) {
            for (const mm of (g.models || [])) {
              const imgs = Array.isArray(mm.images) ? JSON.stringify(mm.images) : (mm.images ? String(mm.images) : '[]');
              ins.run(g.brand, g.category, mm.model, String(mm.price ?? ''), mm.note || '', imgs);
              n++;
            }
          }
          db.exec('COMMIT');
        } catch (e) { db.exec('ROLLBACK'); throw e; }
        return json(res, 200, { ok: true, imported: n });
      }
    }

    // ---------- 兼容接口 ----------
    if (method === 'GET' && pathname === '/api/export') {
      return json(res, 200, toLegacyExport(db));
    }

    if (method === 'GET' && pathname === '/data.json') {
      return json(res, 200, toLegacyExport(db));
    }

    if (method === 'POST' && pathname === '/api/save') { // 旧版整包保存
      if (!requireKey(req, cfg)) return json(res, 401, { error: '未授权' });
      const body = await readBody(req);
      const data = Array.isArray(body) ? body : [];
      const ins = db.prepare('INSERT INTO models (brand, category, model, price, note, images) VALUES (?, ?, ?, ?, ?, ?)');
      let n = 0;
      db.exec('BEGIN');
      try {
        db.exec('DELETE FROM models');
        for (const g of data) for (const mm of (g.models || [])) {
          const imgs = Array.isArray(mm.images) ? JSON.stringify(mm.images) : (mm.images ? String(mm.images) : '[]');
          ins.run(g.brand, g.category, mm.model, String(mm.price ?? ''), mm.note || '', imgs);
          n++;
        }
        db.exec('COMMIT');
      } catch (e) { db.exec('ROLLBACK'); throw e; }
      return json(res, 200, { ok: true, saved: n });
    }

    return json(res, 404, { error: 'not found', path: pathname });
  };
}

module.exports = { createRouter, readBody };
