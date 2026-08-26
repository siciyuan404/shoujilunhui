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

// 规格相关字段（与筛选维度一一对应）
const SPEC_COLS = [
  // 上市时间（统一存 YYYY-MM，可筛选年份）
  ['release_date', "TEXT NOT NULL DEFAULT ''"],
  // CPU 厂商：高通/联发科/苹果/海思/三星/紫光展锐/谷歌
  ['cpu_brand', "TEXT NOT NULL DEFAULT ''"],
  // CPU 型号：骁龙8 Gen3 / 天玑9400 / A16 仿生
  ['cpu_model', "TEXT NOT NULL DEFAULT ''"],
  // 运行内存：8GB
  ['ram', "TEXT NOT NULL DEFAULT ''"],
  // 存储容量：256GB
  ['rom', "TEXT NOT NULL DEFAULT ''"],
  // 后置主摄像素：5000万
  ['back_camera', "TEXT NOT NULL DEFAULT ''"],
  // 前置摄像头：1600万
  ['front_camera', "TEXT NOT NULL DEFAULT ''"],
  // 屏幕尺寸：6.67英寸
  ['screen_size', "TEXT NOT NULL DEFAULT ''"],
  // 屏幕材质：LCD/OLED
  ['screen_type', "TEXT NOT NULL DEFAULT ''"],
  // 刷新率：120Hz
  ['refresh', "TEXT NOT NULL DEFAULT ''"],
  // 电池容量：5000mAh
  ['battery', "TEXT NOT NULL DEFAULT ''"],
  // 快充功率：120W
  ['charge', "TEXT NOT NULL DEFAULT ''"],
  // 网络制式：5G/4G
  ['network', "TEXT NOT NULL DEFAULT ''"],
  // 操作系统：Android / iOS / HarmonyOS
  ['os', "TEXT NOT NULL DEFAULT ''"],
  // 分容分版本价格：[{"spec":"128G","price":"100"},{"spec":"256G","price":"130"}]
  ['variants', "TEXT NOT NULL DEFAULT '[]'"],
];

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
      release_date TEXT NOT NULL DEFAULT '',
      cpu_brand TEXT NOT NULL DEFAULT '',
      cpu_model TEXT NOT NULL DEFAULT '',
      ram TEXT NOT NULL DEFAULT '',
      rom TEXT NOT NULL DEFAULT '',
      back_camera TEXT NOT NULL DEFAULT '',
      front_camera TEXT NOT NULL DEFAULT '',
      screen_size TEXT NOT NULL DEFAULT '',
      screen_type TEXT NOT NULL DEFAULT '',
      refresh TEXT NOT NULL DEFAULT '',
      battery TEXT NOT NULL DEFAULT '',
      charge TEXT NOT NULL DEFAULT '',
      network TEXT NOT NULL DEFAULT '',
      os TEXT NOT NULL DEFAULT '',
      variants TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
    );
    CREATE INDEX IF NOT EXISTS idx_models_brand ON models(brand);
    CREATE INDEX IF NOT EXISTS idx_models_cat ON models(brand, category);
    CREATE INDEX IF NOT EXISTS idx_models_model ON models(model);
  `);
  // 老库迁移：补齐缺失列（images + 规格字段）
  const cols = db.prepare('PRAGMA table_info(models)').all();
  const names = new Set(cols.map((c) => c.name));
  const addCols = [['images', "TEXT NOT NULL DEFAULT '[]'"], ...SPEC_COLS];
  for (const [col, def] of addCols) {
    if (!names.has(col)) {
      db.exec(`ALTER TABLE models ADD COLUMN ${col} ${def}`);
      console.log(`[db] 已为 models 表补充 ${col} 列`);
    }
  }
  return db;
}

// 首次启动：若表为空且存在旧 data.json，自动导入
function autoMigrate(db) {
  const cnt = db.prepare('SELECT COUNT(*) AS c FROM models').get().c;
  if (cnt > 0) return false;
  if (!fs.existsSync(LEGACY_JSON)) return false;
  const data = JSON.parse(fs.readFileSync(LEGACY_JSON, 'utf-8'));
  const ins = db.prepare('INSERT INTO models (brand, category, model, price, note, images, release_date, cpu_brand, cpu_model, ram, rom, back_camera, front_camera, screen_size, screen_type, refresh, battery, charge, network, os, variants) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
  let n = 0;
  const tx = () => {
    db.exec('BEGIN');
    for (const g of data) {
      for (const m of (g.models || [])) {
        const imgs = Array.isArray(m.images) ? JSON.stringify(m.images) : (m.images ? String(m.images) : '[]');
        const vars = Array.isArray(m.variants) ? JSON.stringify(m.variants) : '[]';
        ins.run(g.brand, g.category, m.model, String(m.price ?? ''), m.note || '', imgs,
          m.release_date || '', m.cpu_brand || '', m.cpu_model || '', m.ram || '', m.rom || '',
          m.back_camera || '', m.front_camera || '', m.screen_size || '', m.screen_type || '', m.refresh || '',
          m.battery || '', m.charge || '', m.network || '', m.os || '', vars);
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
