const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
for (const f of ['cpu_brand','screen_size','battery','charge','ram','rom','back_camera','network','os','screen_type','front_camera','refresh']) {
  const r = db.prepare(`SELECT brand, COUNT(*) c FROM models WHERE ${f} IS NULL OR ${f}='' GROUP BY brand ORDER BY c DESC LIMIT 6`).all();
  console.log('=== ' + f + ' 缺口按品牌 TOP6 ===');
  r.forEach(x=>console.log('  ', x.brand, x.c));
}