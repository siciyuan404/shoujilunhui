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
  latest: '1.7.0',
  url: 'https://github.com/siciyuan404/shoujilunhui/releases/download/v1.7.0/shoujilunhui-v1.7.0.apk',
  gray: 100,
  forceBelow: '1.0.0',
};

// GitHub releases/latest 动态缓存：打 tag 发布新版本后，/api/update 自动反映新版本，
// 无需再手动改 UPDATE_POLICY / config.json。失败时回退静态配置。
let _ghReleaseCache = { at: 0, latest: '', url: '' };
const GH_CACHE_MS = 5 * 60 * 1000;

async function fetchLatestRelease() {
  const now = Date.now();
  if (_ghReleaseCache.at && now - _ghReleaseCache.at < GH_CACHE_MS && _ghReleaseCache.latest) {
    return _ghReleaseCache;
  }
  try {
    const resp = await fetch('https://api.github.com/repos/siciyuan404/shoujilunhui/releases/latest', {
      headers: { 'User-Agent': 'shoujilunhui-server', 'Accept': 'application/vnd.github+json' },
      signal: AbortSignal.timeout(8000),
    });
    if (!resp.ok) return null;
    const j = await resp.json();
    const tag = String(j.tag_name || '').replace(/^v/i, '').trim();
    const apk = Array.isArray(j.assets)
      ? j.assets.find((a) => a && /\.apk$/i.test(String(a.name)))
      : null;
    const url = apk && apk.browser_download_url ? String(apk.browser_download_url) : '';
    if (!tag || !url) return null;
    _ghReleaseCache = { at: now, latest: tag, url };
    return _ghReleaseCache;
  } catch (e) {
    return null;
  }
}

// 可写入的规格字段（除基础 brand/category/model/price/note/images 外）
const SPEC_FIELDS = [
  'release_date', 'cpu_brand', 'cpu_model', 'ram', 'rom',
  'back_camera', 'front_camera', 'screen_size', 'screen_type', 'refresh',
  'battery', 'charge', 'network', 'os', 'variants', 'model_code',
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
    where.push('(model LIKE ? OR note LIKE ? OR brand LIKE ? OR category LIKE ? OR cpu_model LIKE ? OR release_date LIKE ? OR model_code LIKE ?)');
    const s = '%' + q.get('search') + '%';
    args.push(s, s, s, s, s, s, s);
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
    const baseOrigin = base.replace(/\/+$/, '');
    im = im.map((x) => {
      x = String(x).trim();
      if (!x) return x;
      // 录入图片时可能存成了本机回环地址（127.0.0.1/localhost），手机等其它设备访问不到，
      // 这里统一替换为当前请求的 Host（局域网 IP 或穿透域名），保证各端都能加载
      if (/^https?:\/\/[^/]+/i.test(x) && /^https?:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?\//i.test(x)) {
        return baseOrigin + '/' + x.replace(/^https?:\/\/[^/]+/i, '').replace(/^\/+/, '');
      }
      if (/^https?:\/\//i.test(x)) return x;
      return baseOrigin + '/' + x.replace(/^\/+/, '');
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

// ---------- 收机记账（records） ----------
const RECORD_INSERT_COLS = ['photo', 'brand', 'category', 'model', 'model_id', 'rec_price', 'sale_price', 'channel', 'day', 'status', 'note'];

function validateRecord(body, partial) {
  const err = [];
  const out = {};
  if (!partial || body.model !== undefined) {
    if (body.model === undefined || !String(body.model ?? '').trim()) err.push('model 必填');
    else out.model = String(body.model).trim();
  }
  if (body.photo !== undefined) out.photo = String(body.photo ?? '').trim();
  if (body.brand !== undefined) out.brand = String(body.brand ?? '').trim();
  if (body.category !== undefined) out.category = String(body.category ?? '').trim();
  if (body.model_id !== undefined) {
    const n = Number(body.model_id);
    out.model_id = (body.model_id === null || body.model_id === '' || !isFinite(n)) ? null : n;
  }
  if (body.rec_price !== undefined) out.rec_price = String(body.rec_price ?? '').trim();
  if (body.sale_price !== undefined) out.sale_price = String(body.sale_price ?? '').trim();
  if (body.channel !== undefined) out.channel = String(body.channel ?? '').trim();
  if (body.day !== undefined) {
    const d = String(body.day ?? '').trim();
    out.day = /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : '';
  }
  if (body.status !== undefined) out.status = String(body.status ?? '').trim() || '在库';
  if (body.note !== undefined) out.note = String(body.note ?? '').trim();
  if (err.length) throw new Error(err.join('; '));
  return out;
}

// 把 photo 相对路径/回环地址统一替换为当前请求 Host（与 parseVariants 一致）
function parseRecord(row, base) {
  if (!row) return row;
  let photo = String(row.photo || '').trim();
  if (photo && base) {
    const baseOrigin = base.replace(/\/+$/, '');
    if (/^https?:\/\/[^/]+/i.test(photo) && /^https?:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?\//i.test(photo)) {
      photo = baseOrigin + '/' + photo.replace(/^https?:\/\/[^/]+/i, '').replace(/^\/+/, '');
    } else if (!/^https?:\/\//i.test(photo)) {
      photo = baseOrigin + '/' + photo.replace(/^\/+/, '');
    }
  }
  return Object.assign({}, row, { photo });
}

// 日期范围解析：day 精确日 > start~end 区间 > period(today/week/month/lastweek/lastmonth)
function recordRange(q) {
  const fmt = (d) => d.toLocaleDateString('sv');
  const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const today = fmt(new Date());
  const day = q.get('day');
  if (day && /^\d{4}-\d{2}-\d{2}$/.test(day)) return { start: day, end: day };
  const start = q.get('start'), end = q.get('end');
  if (start && end && /^\d{4}-\d{2}-\d{2}$/.test(start) && /^\d{4}-\d{2}-\d{2}$/.test(end) && start <= end) {
    return { start, end };
  }
  const period = q.get('period') || 'today';
  const t = new Date(today + 'T00:00:00');
  if (period === 'week') {
    const dow = (t.getDay() + 6) % 7; // 周一=0
    return { start: fmt(addDays(t, -dow)), end: today };
  }
  if (period === 'month') return { start: fmt(new Date(t.getFullYear(), t.getMonth(), 1)), end: today };
  if (period === 'lastweek') {
    const dow = (t.getDay() + 6) % 7;
    const mon = addDays(t, -dow - 7);
    return { start: fmt(mon), end: fmt(addDays(mon, 6)) };
  }
  if (period === 'lastmonth') {
    const first = new Date(t.getFullYear(), t.getMonth() - 1, 1);
    const last = new Date(t.getFullYear(), t.getMonth(), 0);
    return { start: fmt(first), end: fmt(last) };
  }
  return { start: today, end: today };
}

function listRecords(db, q) {
  const where = [];
  const args = [];
  const day = q.get('day');
  if (day && /^\d{4}-\d{2}-\d{2}$/.test(day)) { where.push('day = ?'); args.push(day); }
  const start = q.get('start'), end = q.get('end');
  if (start && end && /^\d{4}-\d{2}-\d{2}$/.test(start) && /^\d{4}-\d{2}-\d{2}$/.test(end) && start <= end) {
    where.push('day >= ? AND day <= ?'); args.push(start, end);
  }
  if (q.get('channel') && q.get('channel') !== '全部') { where.push('channel LIKE ?'); args.push('%' + q.get('channel') + '%'); }
  if (q.get('status') && q.get('status') !== '全部') { where.push('status = ?'); args.push(q.get('status')); }
  if (q.get('search')) {
    where.push('(model LIKE ? OR brand LIKE ? OR channel LIKE ? OR note LIKE ?)');
    const s = '%' + q.get('search') + '%';
    args.push(s, s, s, s);
  }
  const whereSql = where.length ? 'WHERE ' + where.join(' AND ') : '';
  const total = db.prepare(`SELECT COUNT(*) AS c FROM records ${whereSql}`).get(...args).c;
  const sorts = {
    day_desc: 'day DESC, id DESC', day_asc: 'day ASC, id ASC',
    rec_desc: 'CAST(rec_price AS REAL) DESC, id ASC', rec_asc: 'CAST(rec_price AS REAL) ASC, id ASC',
    id: 'id ASC',
  };
  const orderSql = sorts[q.get('sort')] || sorts.day_desc;
  let limit = parseInt(q.get('limit') || '0', 10) || 0;
  if (limit < 0 || limit > 5000) limit = 0;
  let pageSql = '';
  if (limit > 0) {
    const page = Math.max(1, parseInt(q.get('page') || '1', 10));
    pageSql = ` LIMIT ${limit} OFFSET ${(page - 1) * limit}`;
  }
  const rows = db.prepare(`SELECT * FROM records ${whereSql} ORDER BY ${orderSql}${pageSql}`).all(...args);
  const page = limit > 0 ? Math.max(1, parseInt(q.get('page') || '1', 10)) : 1;
  return { total, page, limit: limit > 0 ? limit : rows.length, items: rows };
}

function recordStats(db, range) {
  const where = 'WHERE day >= ? AND day <= ?';
  const args = [range.start, range.end];
  const rows = db.prepare(`SELECT * FROM records ${where} ORDER BY day DESC, id DESC`).all(...args);
  const num = (v) => Number(v || 0);
  const round = (x) => Math.round(x * 100) / 100;
  const recTotal = round(rows.reduce((s, r) => s + num(r.rec_price), 0));
  const saleTotal = round(rows.reduce((s, r) => s + num(r.sale_price), 0));
  const maps = { byDay: new Map(), byChannel: new Map(), byModel: new Map(), byStatus: new Map() };
  const acc = (map, key, mk) => {
    if (!map.has(key)) map.set(key, mk(key));
    return map.get(key);
  };
  for (const r of rows) {
    const d = acc(maps.byDay, r.day, (k) => ({ day: k, count: 0, recTotal: 0, saleTotal: 0 }));
    d.count++; d.recTotal += num(r.rec_price); d.saleTotal += num(r.sale_price);
    const ch = (r.channel || '').trim();
    if (ch) {
      const c = acc(maps.byChannel, ch, (k) => ({ channel: k, count: 0, recTotal: 0, saleTotal: 0 }));
      c.count++; c.recTotal += num(r.rec_price); c.saleTotal += num(r.sale_price);
    }
    const mo = (r.model || '').trim();
    if (mo) {
      const m = acc(maps.byModel, mo, (k) => ({ model: k, count: 0, recTotal: 0, saleTotal: 0 }));
      m.count++; m.recTotal += num(r.rec_price); m.saleTotal += num(r.sale_price);
    }
    const st = (r.status || '在库').trim();
    const s = acc(maps.byStatus, st, (k) => ({ status: k, count: 0, recTotal: 0, saleTotal: 0 }));
    s.count++; s.recTotal += num(r.rec_price); s.saleTotal += num(r.sale_price);
  }
  const finalize = (map) => [...map.values()]
    .map((x) => ({ ...x, recTotal: round(x.recTotal), saleTotal: round(x.saleTotal), profit: round(x.saleTotal - x.recTotal) }))
    .sort((a, b) => b.recTotal - a.recTotal);
  return {
    range: { start: range.start, end: range.end },
    summary: {
      count: rows.length,
      recTotal, saleTotal,
      profit: round(saleTotal - recTotal),
      channels: maps.byChannel.size,
      statuses: maps.byStatus.size,
    },
    byDay: finalize(maps.byDay).sort((a, b) => (a.day < b.day ? -1 : 1)),
    byChannel: finalize(maps.byChannel),
    byModel: finalize(maps.byModel),
    byStatus: finalize(maps.byStatus),
  };
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
      let latest = String(upd.latest || '');
      let url = String(upd.url || '');
      // config 未显式覆盖版本时，动态读取 GitHub 最新 release（打 tag 即生效，带 5 分钟缓存）
      if (!cfg.update || cfg.update.latest == null) {
        const gh = await fetchLatestRelease();
        if (gh) { latest = gh.latest; url = gh.url; }
      }
      return json(res, 200, {
        enabled: true,
        latest,
        url,
        gray: Math.max(0, Math.min(100, Number(upd.gray) || 100)),
        forceBelow: String(upd.forceBelow || ''),
      });
    }

    // ---------- 拍照识别代理：转发视觉大模型（豆包方舟 / DeepSeek），规避浏览器跨域限制 ----------
    if (method === 'POST' && pathname === '/api/recognize') {
      const body = await readBody(req);
      const provider = String(body.provider || 'ark').trim() || 'ark';
      const apiKey = String(body.apiKey || '').trim();
      const b64 = String(body.imageBase64 || '').trim();
      const isDeepSeek = provider === 'deepseek';
      const aiBase = isDeepSeek ? 'https://api.deepseek.com' : 'https://ark.cn-beijing.volces.com/api/v3';
      const defaultModel = isDeepSeek ? 'deepseek-v4-flash-vision-exp' : 'doubao-seed-character-260628';
      const model = String(body.model || '').trim() || defaultModel;
      const providerName = isDeepSeek ? 'DeepSeek' : '豆包';
      if (!apiKey) return json(res, 400, { error: '未配置' + providerName + ' API Key，请在识别设置中填写' });
      if (!b64) return json(res, 400, { error: '缺少图片数据' });
      const prompt =
        '请仔细查看这张图片，识别出图中出现的所有手机。请只返回 JSON，格式：' +
        '{"phones":[{"model":"手机具体型号","box":{"x1":0,"y1":0,"x2":1000,"y2":1000}}]}。' +
        '注意：1) 一台一台列出，图中出现几台就列几台；' +
        '2) box 用 0~1000 归一化坐标表示该手机在图片中的边界框（左上角x1,y1，右下角x2,y2），框要尽量紧贴手机主体；' +
        '3) 型号尽量简洁，如 畅享9 Plus、P40 Pro、苹果15 Pro Max（不要带品牌名，不要带内存/颜色/新旧等多余描述）；' +
        '4) 如果图中有手机但看不清型号，根据外观给出最可能的型号；' +
        '5) 如果图中没有手机，返回 {"phones":[]}。不要输出任何其他内容。';
      let aiResp;
      try {
        aiResp = await fetch(aiBase + '/chat/completions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + apiKey },
          body: JSON.stringify({
            model,
            messages: [{ role: 'user', content: [
              { type: 'text', text: prompt },
              { type: 'image_url', image_url: { url: 'data:image/jpeg;base64,' + b64 } },
            ] }],
            temperature: 0.1,
            response_format: { type: 'json_object' },
          }),
        });
      } catch (e) {
        return json(res, 502, { error: '无法连接识别服务：' + e.message });
      }
      const text = await aiResp.text();
      if (!aiResp.ok) {
        let msg = '识别服务错误（' + aiResp.status + '）';
        try {
          const e = JSON.parse(text);
          if (e && e.error && e.error.message) msg += '：' + e.error.message;
        } catch (_) {}
        return json(res, 502, { error: msg });
      }
      try {
        const j = JSON.parse(text);
        const content = j.choices && j.choices[0] && j.choices[0].message
          ? j.choices[0].message.content : '';
        const parsed = JSON.parse(content);
        const phones = Array.isArray(parsed.phones)
          ? parsed.phones.map((p) => {
              const raw = typeof p === 'string' ? p : (p && p.model);
              const model = String(raw || '').trim();
              if (!model) return null;
              const b = (p && p.box) || {};
              const num = (v) => {
                const n = Number(v);
                return (typeof v !== 'boolean' && v !== null && v !== '' && isFinite(n)) ? n : NaN;
              };
              const x1 = num(b.x1), y1 = num(b.y1), x2 = num(b.x2), y2 = num(b.y2);
              const ok = [x1, y1, x2, y2].every((n) => !isNaN(n));
              return { model, box: ok ? { x1, y1, x2, y2 } : null };
            }).filter(Boolean)
          : [];
        return json(res, 200, { phones });
      } catch (e) {
        return json(res, 502, { error: '识别结果解析失败：' + e.message });
      }
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

    // ---------- 收机记账：查询与统计（公开） ----------
    if (method === 'GET' && pathname === '/api/records') {
      const result = listRecords(db, q);
      result.items = result.items.map((it) => parseRecord(it, requestBase(req)));
      return json(res, 200, result);
    }

    if (method === 'GET' && pathname === '/api/records/stats') {
      return json(res, 200, recordStats(db, recordRange(q)));
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
      ['POST', '/api/records'],
      ['POST', '/api/records/batch'],
      ['PUT', /^\/api\/records\/\d+$/],
      ['PATCH', /^\/api\/records\/\d+$/],
      ['DELETE', /^\/api\/records\/\d+$/],
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

      // ---------- 收机记账：写入（需 API Key） ----------
      if (method === 'POST' && pathname === '/api/records') {
        const body = await readBody(req);
        const f = validateRecord(body, false);
        if (!f.day) f.day = new Date().toLocaleDateString('sv');
        const r = db.prepare(`INSERT INTO records (${RECORD_INSERT_COLS.join(', ')}) VALUES (${RECORD_INSERT_COLS.map(() => '?').join(', ')})`)
          .run(f.photo || '', f.brand || '', f.category || '', f.model, f.model_id ?? null, f.rec_price || '', f.sale_price || '', f.channel || '', f.day, f.status || '在库', f.note || '');
        return json(res, 201, parseRecord(db.prepare('SELECT * FROM records WHERE id = ?').get(r.lastInsertRowid), requestBase(req)));
      }

      if (method === 'POST' && pathname === '/api/records/batch') {
        const body = await readBody(req);
        const items = Array.isArray(body.items) ? body.items : (Array.isArray(body) ? body : null);
        if (!Array.isArray(items) || !items.length) return json(res, 400, { error: 'items 数组必填' });
        const ins = db.prepare(`INSERT INTO records (${RECORD_INSERT_COLS.join(', ')}) VALUES (${RECORD_INSERT_COLS.map(() => '?').join(', ')})`);
        const today = new Date().toLocaleDateString('sv');
        let n = 0;
        db.exec('BEGIN');
        try {
          for (const it of items) {
            const f = validateRecord(it, false);
            ins.run(f.photo || '', f.brand || '', f.category || '', f.model, f.model_id ?? null, f.rec_price || '', f.sale_price || '', f.channel || '', f.day || today, f.status || '在库', f.note || '');
            n++;
          }
          db.exec('COMMIT');
        } catch (e) { db.exec('ROLLBACK'); throw e; }
        return json(res, 201, { ok: true, inserted: n });
      }

      const rm = pathname.match(/^\/api\/records\/(\d+)$/);
      if (rm && (method === 'PUT' || method === 'PATCH')) {
        const id = Number(rm[1]);
        const body = await readBody(req);
        const fields = validateRecord(body, true);
        const keys = Object.keys(fields);
        if (!keys.length) return json(res, 400, { error: '无可更新字段' });
        const sets = keys.map((k) => `${k} = ?`).join(', ');
        db.prepare(`UPDATE records SET ${sets}, updated_at = datetime('now','localtime') WHERE id = ?`).run(...keys.map((k) => fields[k]), id);
        const row = db.prepare('SELECT * FROM records WHERE id = ?').get(id);
        if (!row) return json(res, 404, { error: 'not found' });
        return json(res, 200, parseRecord(row, requestBase(req)));
      }
      if (rm && method === 'DELETE') {
        const id = Number(rm[1]);
        const r = db.prepare('DELETE FROM records WHERE id = ?').run(id);
        if (!r.changes) return json(res, 404, { error: 'not found' });
        return json(res, 200, { ok: true, id });
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
