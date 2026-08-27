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

// 滚动更新策略（默认值）。可在 config.json 中用 update 字段覆盖，例如只放 30% 灰度：
//   { "update": { "gray": 30 } }
// gray: 0-100 灰度比例；forceBelow: 低于此版本强制更新（即使不在灰度内）。
const UPDATE_POLICY = {
  latest: '1.5.0',
  url: 'https://github.com/siciyuan404/shoujilunhui/releases/download/v1.5.0/shoujilunhui-v1.5.0.apk',
  gray: 100,
  forceBelow: '1.0.0',
};

// 可写入的规格字段（除基础 brand/category/model/price/note/images 外）
const SPEC_FIELDS = [
  'release_date', 'cpu_brand', 'cpu_model', 'ram', 'rom',
  'back_camera', 'front_camera', 'screen_size', 'screen_type', 'refresh',
  'battery', 'charge', 'network', 'os', 'variants',
];

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
  release_desc: "substr(release_date,1,4) DESC, id ASC",
  release_asc: "substr(release_date,1,4) ASC, id ASC",
};

function listModels(db, q) {
  const where = [];
  const args = [];
  if (q.get('brand') && q.get('brand') !== '全部') { where.push('brand = ?'); args.push(q.get('brand')); }
  if (q.get('category')) { where.push('category = ?'); args.push(q.get('category')); }
  if (q.get('search')) {
    where.push('(model LIKE ? OR note LIKE ? OR brand LIKE ? OR category LIKE ? OR cpu_model LIKE ? OR release_date LIKE ?)');
    const s = '%' + q.get('search') + '%';
    args.push(s, s, s, s, s, s);
  }
  if (q.get('min_price')) { where.push('CAST(price AS REAL) >= ?'); args.push(Number(q.get('min_price'))); }
  if (q.get('max_price')) { where.push('CAST(price AS REAL) <= ?'); args.push(Number(q.get('max_price'))); }
  // ---------- 规格细节筛选 ----------
  if (q.get('year') && q.get('year') !== '全部') { where.push("substr(release_date, 1, 4) = ?"); args.push(q.get('year')); }
  if (q.get('cpu_brand') && q.get('cpu_brand') !== '全部') { where.push('cpu_brand = ?'); args.push(q.get('cpu_brand')); }
  if (q.get('ram')) { where.push('ram LIKE ?'); args.push('%' + q.get('ram') + '%'); }
  if (q.get('rom')) { where.push('rom LIKE ?'); args.push('%' + q.get('rom') + '%'); }
  if (q.get('network') && q.get('network') !== '全部') { where.push('network LIKE ?'); args.push('%' + q.get('network') + '%'); }
  if (q.get('screen_type') && q.get('screen_type') !== '全部') { where.push('screen_type = ?'); args.push(q.get('screen_type')); }
  if (q.get('camera_min')) {
    where.push("(CASE WHEN back_camera LIKE '%万%' THEN CAST(substr(back_camera, 1, instr(back_camera, '万') - 1) AS REAL) ELSE 0 END) >= ?");
    args.push(Number(q.get('camera_min')));
  }
  if (q.get('camera_max')) {
    where.push("(CASE WHEN back_camera LIKE '%万%' THEN CAST(substr(back_camera, 1, instr(back_camera, '万') - 1) AS REAL) ELSE 0 END) <= ?");
    args.push(Number(q.get('camera_max')));
  }
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

// 根据请求派生对外基础地址（本地 http://127.0.0.1:8760，穿透 https://sj.6200052.xyz）
function requestBase(req) {
  const proto = String(req.headers['x-forwarded-proto'] || 'http').split(',')[0].trim() || 'http';
  const host = req.headers.host || '127.0.0.1:8760';
  return proto + '://' + host;
}

// 把 variants/images JSON 字符串解析成数组，并把 images 相对路径拼成完整 URL
// （列表返回时便于前端直接使用，手机端无需再拼 baseUrl）
function parseVariants(row, base) {
  if (!row) return row;
  let v = [], im = [];
  try { v = JSON.parse(row.variants || '[]'); } catch (e) { v = []; }
  try { im = JSON.parse(row.images || '[]'); } catch (e) { im = []; }
  if (base && Array.isArray(im)) {
    im = im.map((x) => {
      x = String(x).trim();
      if (!x) return x;
      if (/^https?:\/\//i.test(x)) return x;
      return base.replace(/\/+$/, '') + '/' + x.replace(/^\/+/, '');
    });
  }
  return Object.assign({}, row, { variants: v, images: im });
}

// 各筛选维度的可选值（供前端构建筛选器）
function filters(db) {
  const one = (sql) => db.prepare(sql).all().map((r) => r.v).filter(Boolean);
  return {
    years: one("SELECT DISTINCT substr(release_date,1,4) AS v FROM models WHERE release_date != '' ORDER BY v DESC"),
    cpu_brands: one("SELECT DISTINCT cpu_brand AS v FROM models WHERE cpu_brand != '' ORDER BY v"),
    cpus: one("SELECT DISTINCT cpu_model AS v FROM models WHERE cpu_model != '' ORDER BY v"),
    rams: one("SELECT DISTINCT ram AS v FROM models WHERE ram != '' ORDER BY v"),
    roms: one("SELECT DISTINCT rom AS v FROM models WHERE rom != '' ORDER BY v"),
    networks: one("SELECT DISTINCT network AS v FROM models WHERE network != '' ORDER BY v"),
    screen_types: one("SELECT DISTINCT screen_type AS v FROM models WHERE screen_type != '' ORDER BY v"),
    cameras: one("SELECT DISTINCT back_camera AS v FROM models WHERE back_camera != '' ORDER BY v"),
  };
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
  // 规格字段：普通字符串
  for (const f of SPEC_FIELDS.filter((f) => f !== 'variants')) {
    if (body[f] !== undefined) out[f] = String(body[f] ?? '').trim();
  }
  // variants：JSON 数组 [{spec, price}]
  if (body.variants !== undefined) {
    let arr = body.variants;
    if (typeof arr === 'string') { try { arr = JSON.parse(arr); } catch (e) { arr = []; } }
    if (!Array.isArray(arr)) arr = [];
    arr = arr
      .filter((v) => v && typeof v === 'object')
      .map((v) => ({ spec: String(v.spec ?? '').trim(), price: String(v.price ?? '').trim() }))
      .filter((v) => v.spec || v.price);
    out.variants = JSON.stringify(arr);
  }
  if (err.length) throw new Error(err.join('; '));
  return out;
}

// INSERT 用完整列
const INSERT_COLS = ['brand', 'category', 'model', 'price', 'note', 'images',
  ...SPEC_FIELDS];
const INSERT_PLACE = INSERT_COLS.map(() => '?').join(', ');

function rowValues(f) {
  return INSERT_COLS.map((c) => (f[c] !== undefined ? f[c] : (c === 'images' || c === 'variants' ? '[]' : '')));
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

    if (method === 'GET' && pathname === '/api/update') {
      // 无感更新 + 滚动更新（灰度）策略接口，供 APP 启动后台静默检查
      const upd = Object.assign({}, UPDATE_POLICY, cfg.update || {});
      return json(res, 200, {
        enabled: true,
        latest: String(upd.latest),
        url: String(upd.url || ''),
        gray: Math.max(0, Math.min(100, Number(upd.gray) || 100)),
        forceBelow: String(upd.forceBelow || ''),
      });
    }

    if (method === 'GET' && pathname === '/api/health') {
      const c = db.prepare('SELECT COUNT(*) AS c FROM models').get().c;
      const withSpec = db.prepare("SELECT COUNT(*) AS c FROM models WHERE cpu_brand != '' OR release_date != ''").get().c;
      return json(res, 200, { ok: true, db: 'phone.db', models: c, withSpec, time: new Date().toISOString() });
    }

    if (method === 'GET' && pathname === '/api/stats') {
      const total = db.prepare('SELECT COUNT(*) AS c FROM models').get().c;
      const brands = db.prepare('SELECT COUNT(DISTINCT brand) AS c FROM models').get().c;
      const cats = db.prepare("SELECT COUNT(DISTINCT brand || '|' || category) AS c FROM models").get().c;
      const last = db.prepare('SELECT MAX(updated_at) AS t FROM models').get().t;
      const withSpec = db.prepare("SELECT COUNT(*) AS c FROM models WHERE cpu_brand != '' OR release_date != ''").get().c;
      return json(res, 200, { total, brands, categories: cats, lastUpdated: last, withSpec });
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

    if (method === 'GET' && pathname === '/api/filters') {
      return json(res, 200, filters(db));
    }

    if (method === 'GET' && pathname === '/api/models') {
      const result = listModels(db, q);
      result.items = result.items.map((it) => parseVariants(it, requestBase(req)));
      return json(res, 200, result);
    }

    let m = pathname.match(/^\/api\/models\/(\d+)$/);
    if (m) {
      const id = Number(m[1]);
      if (method === 'GET') {
        const row = db.prepare('SELECT * FROM models WHERE id = ?').get(id);
        if (!row) return json(res, 404, { error: 'not found' });
        return json(res, 200, parseVariants(row, requestBase(req)));
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
        return json(res, 200, parseVariants(row, requestBase(req)));
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
        const vals = rowValues(f);
        const r = db.prepare(`INSERT INTO models (${INSERT_COLS.join(', ')}) VALUES (${INSERT_PLACE})`).run(...vals);
        return json(res, 201, parseVariants(db.prepare('SELECT * FROM models WHERE id = ?').get(r.lastInsertRowid), requestBase(req)));
      }

      if (method === 'POST' && pathname === '/api/models/bulk') {
        const body = await readBody(req);
        const items = Array.isArray(body.items) ? body.items : body;
        if (!Array.isArray(items) || !items.length) return json(res, 400, { error: 'items 数组必填' });
        const ins = db.prepare(`INSERT INTO models (${INSERT_COLS.join(', ')}) VALUES (${INSERT_PLACE})`);
        let n = 0;
        db.exec('BEGIN');
        try {
          for (const it of items) {
            const f = validateModel(it, false);
            ins.run(...rowValues(f));
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
        const ins = db.prepare(`INSERT INTO models (${INSERT_COLS.join(', ')}) VALUES (${INSERT_PLACE})`);
        let n = 0;
        db.exec('BEGIN');
        try {
          if (body.replace !== false) db.exec('DELETE FROM models');
          for (const g of data) {
            for (const mm of (g.models || [])) {
              const imgs = Array.isArray(mm.images) ? JSON.stringify(mm.images) : (mm.images ? String(mm.images) : '[]');
              const vars = Array.isArray(mm.variants) ? JSON.stringify(mm.variants) : '[]';
              ins.run(g.brand, g.category, mm.model, String(mm.price ?? ''), mm.note || '', imgs,
                mm.release_date || '', mm.cpu_brand || '', mm.cpu_model || '', mm.ram || '', mm.rom || '',
                mm.back_camera || '', mm.front_camera || '', mm.screen_size || '', mm.screen_type || '', mm.refresh || '',
                mm.battery || '', mm.charge || '', mm.network || '', mm.os || '', vars);
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
      const ins = db.prepare(`INSERT INTO models (${INSERT_COLS.join(', ')}) VALUES (${INSERT_PLACE})`);
      let n = 0;
      db.exec('BEGIN');
      try {
        db.exec('DELETE FROM models');
        for (const g of data) for (const mm of (g.models || [])) {
          const imgs = Array.isArray(mm.images) ? JSON.stringify(mm.images) : (mm.images ? String(mm.images) : '[]');
          const vars = Array.isArray(mm.variants) ? JSON.stringify(mm.variants) : '[]';
          ins.run(g.brand, g.category, mm.model, String(mm.price ?? ''), mm.note || '', imgs,
            mm.release_date || '', mm.cpu_brand || '', mm.cpu_model || '', mm.ram || '', mm.rom || '',
            mm.back_camera || '', mm.front_camera || '', mm.screen_size || '', mm.screen_type || '', mm.refresh || '',
            mm.battery || '', mm.charge || '', mm.network || '', mm.os || '', vars);
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
