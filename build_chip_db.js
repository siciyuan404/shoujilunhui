/**
 * 存储芯片数据库构建 + 机型匹配脚本
 * 1) 备份 phone.db
 * 2) 创建 storage_chips 表，从 storage_chips.json 灌入 812 条芯片数据
 * 3) models 表补列: vendor / vendor_pnp / package / rom_type / rom_size / chip_id
 * 4) 对回收价 >= 100 的机型，按 ROM/RAM 容量匹配芯片并回写
 */
const { DatabaseSync } = require('node:sqlite');
const fs = require('fs');
const path = require('path');

const DB = path.join(__dirname, 'server', 'db', 'phone.db');
const CHIPS_JSON = path.join(__dirname, 'storage_chips.json');

// ---------- 1. 备份 ----------
const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const bak = DB + '.bak-' + ts;
fs.copyFileSync(DB, bak);
console.log('[1] 已备份数据库 ->', path.basename(bak));

const db = new DatabaseSync(DB);

// ---------- 2. storage_chips 表 ----------
db.exec(`
  CREATE TABLE IF NOT EXISTS storage_chips (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chip_class TEXT NOT NULL DEFAULT '',
    vendor TEXT NOT NULL DEFAULT '',
    vendor_pnp TEXT NOT NULL DEFAULT '',
    package TEXT NOT NULL DEFAULT '',
    rom_type TEXT NOT NULL DEFAULT '',
    rom_size TEXT NOT NULL DEFAULT '',
    ram_type TEXT NOT NULL DEFAULT '',
    ram_size TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
  );
  CREATE INDEX IF NOT EXISTS idx_chips_class ON storage_chips(chip_class);
  CREATE INDEX IF NOT EXISTS idx_chips_rom ON storage_chips(rom_size);
  CREATE INDEX IF NOT EXISTS idx_chips_ram ON storage_chips(ram_size);
  CREATE INDEX IF NOT EXISTS idx_chips_pnp ON storage_chips(vendor_pnp);
`);

const chips = JSON.parse(fs.readFileSync(CHIPS_JSON, 'utf8'));
const del = db.prepare('DELETE FROM storage_chips');
const ins = db.prepare(`INSERT INTO storage_chips
  (chip_class, vendor, vendor_pnp, package, rom_type, rom_size, ram_type, ram_size)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?)`);
db.exec('BEGIN');
del.run();
for (const c of chips) {
  ins.run(c.chip_class, c.vendor, c.vendor_pnp, c.package, c.rom_type, c.rom_size, c.ram_type, c.ram_size);
}
db.exec('COMMIT');
console.log('[2] storage_chips 表已构建，共', chips.length, '条芯片数据');

// ---------- 3. models 表补列 ----------
const NEW_COLS = [
  ['vendor', "TEXT NOT NULL DEFAULT ''"],
  ['vendor_pnp', "TEXT NOT NULL DEFAULT ''"],
  ['package', "TEXT NOT NULL DEFAULT ''"],
  ['rom_type', "TEXT NOT NULL DEFAULT ''"],
  ['rom_size', "TEXT NOT NULL DEFAULT ''"],
  ['chip_id', "INTEGER NOT NULL DEFAULT 0"],
];
const cols = db.prepare('PRAGMA table_info(models)').all();
const names = new Set(cols.map((c) => c.name));
for (const [col, def] of NEW_COLS) {
  if (!names.has(col)) {
    db.exec(`ALTER TABLE models ADD COLUMN ${col} ${def}`);
    console.log('[3] models 表新增列:', col);
  } else {
    console.log('[3] models 表已有列:', col);
  }
}

// ---------- 4. 容量解析 ----------
// "128/256GB" -> [128, 256]; "256/512GB/1T" -> [256, 512, 1024]; "6-8GB" -> [6, 8]
function parseCaps(v) {
  if (!v) return [];
  const tokens = String(v).split(/[\/\-+、,，\s]+/).map((t) => t.trim()).filter(Boolean);
  if (!tokens.length) return [];
  // 无单位的分量继承默认 GB（"256/512GB/1T" 中 256 视为 256GB）
  const out = [];
  for (const t of tokens) {
    const m = t.match(/(\d+(?:\.\d+)?)/);
    if (!m) continue;
    let n = parseFloat(m[1]);
    const um = t.match(/(TB|T|GB|G)$/i);
    if (!um) {
      // 无单位：继承 GB
    } else {
      const u = um[1].toUpperCase();
      if (u === 'TB' || u === 'T') n *= 1024;
    }
    if (n > 0 && !out.includes(n)) out.push(n);
  }
  return out;
}

// 芯片 RAM 容量 -> GB（64Gb=8GB; "4GB"->4; "8Gb+8Gb"->2GB; "48G"视为Gb->6GB）
function chipRamGB(v) {
  if (!v) return [];
  const re = /(\d+(?:\.\d+)?)\s*(Gb|GB|G)\b/gi;
  let total = 0;
  let m;
  while ((m = re.exec(v)) !== null) {
    let n = parseFloat(m[1]);
    const u = m[2]; // 保留原始大小写区分 Gb 与 GB
    if (u === 'Gb') n /= 8;
    else if (/^G$/i.test(u)) n /= 8; // 文章里 "48G" 实际指 48Gb
    // GB 按字节计
    total += n;
  }
  return total > 0 ? [total] : [];
}

// 芯片 ROM 容量 -> GB
function chipRomGB(v) {
  if (!v) return [];
  const re = /(\d+(?:\.\d+)?)\s*(TB|T|GB|G)\b/gi;
  const out = [];
  let m;
  while ((m = re.exec(v)) !== null) {
    let n = parseFloat(m[1]);
    const u = m[2].toUpperCase();
    if (u === 'TB' || u === 'T') n *= 1024;
    if (n > 0 && !out.includes(n)) out.push(n);
  }
  return out;
}

// 芯片类型优先级（组合封装优先，其次独立ROM，再其次独立RAM）
const CLASS_PRIORITY = {
  'UMCP-UFS+LPDDR5': 1,
  'UMCP-UFS+LPDDR4X': 2,
  'eMCP-eMMC+LPDDR4X': 3,
  'eMCP-eMMC+LPDDR4': 4,
  'eMCP-eMMC+LPDDR3': 5,
  'eMCP-eMMC+LPDDR2': 6,
  'UFS': 7,
  'eMMC': 8,
  'POP-LPDDR5X': 9,
  'POP-LPDDR5': 9,
  'POP-LPDDR4X': 9,
  'LPDDR4X': 10,
  'LPDDR4': 10,
  'LPDDR3': 10,
  'LPDDR2': 10,
};
const VENDOR_PRIORITY = [
  'Samsung', 'Hynix', 'Micron', 'Kioxia', 'Toshiba', 'WD', 'Sandisk',
  'YMTC(长江存储)', 'Kingston', 'Longsys', 'FORESEE(longsys)', 'FORESEE',
  'BIWIN', 'Phison', 'CXMT', 'Nanya', 'GigaDevice', 'GCAI', 'Leahkinn', 'FOXCONN',
];

// 读取全部芯片并预解析
const allChips = db.prepare('SELECT * FROM storage_chips').all().map((c) => ({
  ...c,
  romGbs: chipRomGB(c.rom_size),
  ramGbs: chipRamGB(c.ram_size),
}));

function score(c) {
  return {
    classP: CLASS_PRIORITY[c.chip_class] || 99,
    vendorP: (() => {
      const i = VENDOR_PRIORITY.indexOf(c.vendor);
      return i === -1 ? 99 : i;
    })(),
  };
}

function findBestChip(romCaps, ramCaps) {
  const hasRom = romCaps.length > 0;
  const hasRam = ramCaps.length > 0;
  if (!hasRom && !hasRam) return null;

  let best = null;
  let bestKey = null;
  for (const c of allChips) {
    const s = score(c);
    let tier = null;
    let ramDist = null; // 芯片RAM与机型RAM的最小差距（越小越贴近）
    if (hasRom && hasRam && c.romGbs.length && c.ramGbs.length &&
        c.romGbs.some((g) => romCaps.includes(g)) && c.ramGbs.some((g) => ramCaps.includes(g))) {
      // tier1: 组合封装 ROM+RAM 双精确匹配
      tier = 1;
      ramDist = 0;
    } else if (hasRom && c.romGbs.length && c.romGbs.some((g) => romCaps.includes(g)) &&
               /^(UMCP|eMCP)/.test(c.chip_class)) {
      // tier1.5: 组合封装仅 ROM 匹配（按 RAM 距离、类别、厂商排序）
      tier = 1;
      ramDist = hasRam && c.ramGbs.length
        ? Math.min(...c.ramGbs.map((g) => Math.min(...ramCaps.map((r) => Math.abs(g - r)))))
        : 0;
    } else if (hasRom && c.romGbs.length && c.romGbs.some((g) => romCaps.includes(g)) &&
               /^(UFS|eMMC)$/.test(c.chip_class)) {
      tier = 2;
      ramDist = 0;
    } else if (hasRam && c.ramGbs.length && c.ramGbs.some((g) => ramCaps.includes(g)) &&
               /^(POP|LPDDR)/.test(c.chip_class)) {
      tier = 3;
      ramDist = 0;
    }
    if (!tier) continue;
    const d = String(ramDist).padStart(3, '0');
    const key = `${tier}_${d}_${String(s.classP).padStart(2, '0')}_${String(s.vendorP).padStart(2, '0')}`;
    if (!best || key < bestKey) {
      best = c;
      bestKey = key;
    }
  }
  return best;
}

// ---------- 4.5 从型号名【配置】提取容量 ----------
// 例：Reno7【8+128】→ ram=8GB rom=128GB；K9X【128G】→ rom=128GB；Reno7【8/12+256】→ ram=8/12GB rom=256GB
function parseConfigFromName(model) {
  const m = String(model || '').match(/【([^】]+)】/);
  if (!m) return null;
  const inner = m[1].trim();
  if (!/\d/.test(inner)) return null;
  if (/[^0-9\/＋+\.\sGgBbTt]/.test(inner)) return null;
  const norm = (s) => {
    if (/^\d+(\.\d+)?$/.test(s)) return s + 'GB';
    if (/^(\d+(\.\d+)?)\s*G$/i.test(s)) return s.replace(/\s*G$/i, 'GB');
    if (/^(\d+(\.\d+)?)\s*T$/i.test(s)) return s.replace(/\s*T$/i, 'TB');
    return s;
  };
  const parts = inner.split(/[＋+]/).map((s) => s.trim()).filter(Boolean);
  if (!parts.length) return null;
  if (parts.length === 1) return { ram: '', rom: norm(parts[0]) };
  return { ram: norm(parts[0]), rom: parts.slice(1).map(norm).join('/') };
}

// ---------- 5. 匹配并回写 ----------
const models = db.prepare(`
  SELECT id, brand, category, model, price, rom, ram
  FROM models
  WHERE CAST(price AS REAL) >= 100
  ORDER BY id
`).all();
console.log('[5] 回收价>=100 的机型共', models.length, '条，开始匹配...');

const upd = db.prepare(`UPDATE models SET
  vendor = ?, vendor_pnp = ?, package = ?, rom_type = ?, rom_size = ?, chip_id = ?
  WHERE id = ?`);
const fillCaps = db.prepare('UPDATE models SET rom = ?, ram = ? WHERE id = ?');

const stats = { total: models.length, full5: 0, partial: 0, none: 0, withRom: 0, withRam: 0, filledFromConfig: 0 };
const byClass = {};
const byBrand = {};
const samples = [];

db.exec('BEGIN');
for (const m of models) {
  let romCaps = parseCaps(m.rom);
  let ramCaps = parseCaps(m.ram);
  // 容量缺失时尝试从型号名【配置】提取
  if (!romCaps.length && !ramCaps.length) {
    const cfg = parseConfigFromName(m.model);
    if (cfg && (cfg.rom || cfg.ram)) {
      fillCaps.run(cfg.rom, cfg.ram, m.id);
      m.rom = cfg.rom;
      m.ram = cfg.ram;
      romCaps = parseCaps(m.rom);
      ramCaps = parseCaps(m.ram);
      stats.filledFromConfig++;
    }
  }
  if (romCaps.length) stats.withRom++;
  if (ramCaps.length) stats.withRam++;

  const chip = findBestChip(romCaps, ramCaps);
  if (!chip) {
    stats.none++;
    continue;
  }
  const romType = chip.rom_type || '';
  const romSize = chip.rom_size || '';
  if (romType && romSize) stats.full5++;
  else stats.partial++;

  upd.run(chip.vendor, chip.vendor_pnp, chip.package, romType, romSize, chip.id, m.id);
  byClass[chip.chip_class] = (byClass[chip.chip_class] || 0) + 1;
  byBrand[m.brand] = (byBrand[m.brand] || 0) + 1;
  if (samples.length < 12) samples.push({ id: m.id, model: m.model, rom: m.rom, ram: m.ram, chip: chip.vendor_pnp, cls: chip.chip_class });
}
db.exec('COMMIT');

console.log('匹配统计:', JSON.stringify(stats, null, 2));
console.log('按芯片类别命中:', JSON.stringify(byClass, null, 2));
console.log('按品牌命中:', JSON.stringify(byBrand, null, 2));
console.log('示例:');
for (const s of samples) console.log('  ', JSON.stringify(s));
db.close();
