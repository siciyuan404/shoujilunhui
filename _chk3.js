const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT model FROM models WHERE brand='真我' AND (images IS NULL OR images='' OR images='[]') ORDER BY model").all();
console.log('真我 未覆盖:', r.length, '台');
r.forEach(x=>console.log(' -', x.model));
const total = db.prepare("SELECT COUNT(*) c FROM models WHERE brand='真我'").get().c;
const img = db.prepare("SELECT COUNT(*) c FROM models WHERE brand='真我' AND images IS NOT NULL AND images!='' AND images!='[]'").get().c;
console.log(`真我 有图: ${img}/${total}`);