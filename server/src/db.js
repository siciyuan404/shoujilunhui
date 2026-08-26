// SQLite 数据库层：node:sqlite（Node 22+ 内置，零依赖）
const { DatabaseSync } = require('node:sqlite');
const fs = require('fs');
const path = require('path');

const DB_DIR = path.join(__dirname, '..', 'db');
const DB_FILE = path.join(DB_DIR, 'phone.db');
const CONFIG_FILE = path.join(__dirname, '..', 'config.json');
const LEGACY_JSON = path.join(__dirname, '..', '..', 'phone-price', 'data.json');

function ensureDir(p) { if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true }); }

function loadConfig() {
  let cfg = null;
  if (fs.existsSync(CONFIG_FILE)) {
    try { cfg = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8')); } catch (e) {}
  }
  if (!cfg) {
    cfg = {
      port: 8760,
      apiKey: 'sk-' + Math.random().toString(36).slice(2) + Date.now().toString(36),
      readOnly: false,
    };
    ensureDir(path.dirname(CONFIG_FILE));
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2), 'utf-8');
    console.log('[config] 已生成配置文件:', CONFIG_FILE);
    console.log('[config] API Key（写操作需携带）:', cfg.apiKey);
  }
  return cfg;
}

function openDb() {
  ensureDir(DB_DIR);
  const db = new DatabaseSync(DB_FILE);
  db.exec(`
    CREATE TABLE IF NOT EXISTS models (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      brand TEXT NOT NULL,
      category TEXT NOT NULL,
      model TEXT NOT NULL,
      price TEXT NOT NULL DEFAULT '',
      note TEXT NOT NULL DEFAULT '',
      images TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
    );
    CREATE INDEX IF NOT EXISTS idx_models_brand ON models(brand);
    CREATE INDEX IF NOT EXISTS idx_models_cat ON models(brand, category);
    CREATE INDEX IF NOT EXISTS idx_models_model ON models(model);
  `);
  // 老库迁移：补 images 列
  const cols = db.prepare('PRAGMA table_info(models)').all();
  if (!cols.some((c) => c.name === 'images')) {
    db.exec(`ALTER TABLE models ADD COLUMN images TEXT NOT NULL DEFAULT '[]'`);
    console.log('[db] 已为 models 表补充 images 列（参考图片，JSON 数组）');
  }
  return db;
}

// 首次启动：若表为空且存在旧 data.json，自动导入
function autoMigrate(db) {
  const cnt = db.prepare('SELECT COUNT(*) AS c FROM models').get().c;
  if (cnt > 0) return false;
  if (!fs.existsSync(LEGACY_JSON)) return false;
  const data = JSON.parse(fs.readFileSync(LEGACY_JSON, 'utf-8'));
  const ins = db.prepare('INSERT INTO models (brand, category, model, price, note, images) VALUES (?, ?, ?, ?, ?, ?)');
  let n = 0;
  const tx = () => {
    db.exec('BEGIN');
    for (const g of data) {
      for (const m of (g.models || [])) {
        const imgs = Array.isArray(m.images) ? JSON.stringify(m.images) : (m.images ? String(m.images) : '[]');
        ins.run(g.brand, g.category, m.model, String(m.price ?? ''), m.note || '', imgs);
        n++;
      }
    }
    db.exec('COMMIT');
  };
  try { tx(); } catch (e) { db.exec('ROLLBACK'); throw e; }
  console.log(`[migrate] 已从 data.json 导入 ${n} 条型号数据`);
  return true;
}

function toLegacyExport(db) {
  const rows = db.prepare('SELECT brand, category, model, price, note, images FROM models ORDER BY id').all();
  const out = [];
  const idx = new Map();
  for (const r of rows) {
    const key = r.brand + '||' + r.category;
    if (!idx.has(key)) {
      idx.set(key, { brand: r.brand, category: r.category, models: [] });
      out.push(idx.get(key));
    }
    let imgs = [];
    try { imgs = JSON.parse(r.images || '[]'); } catch (e) {}
    idx.get(key).models.push({ model: r.model, price: r.price, note: r.note, images: imgs });
  }
  return out;
}

module.exports = { openDb, autoMigrate, loadConfig, toLegacyExport, DB_FILE };
