const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT model, id FROM models WHERE brand='OPPO' AND (images IS NULL OR images='' OR images='[]') ORDER BY model").all();
console.log('OPPO 未覆盖:', r.length, '台');
r.forEach(x=>console.log(' -', x.model));
const total = db.prepare("SELECT COUNT(*) c FROM models WHERE brand='OPPO'").get().c;
const img = db.prepare("SELECT COUNT(*) c FROM models WHERE brand='OPPO' AND images IS NOT NULL AND images!='' AND images!='[]'").get().c;
console.log(`OPPO 有图: ${img}/${total}`);