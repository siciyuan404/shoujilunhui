const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT brand, COUNT(*) c FROM models GROUP BY brand ORDER BY c DESC").all();
r.forEach(x=>console.log(x.brand, x.c));
console.log('---ROG/ASUS---');
const r2 = db.prepare("SELECT model FROM models WHERE brand LIKE '%ROG%' OR brand LIKE '%ASUS%' OR model LIKE '%ROG%' OR model LIKE '%Zenfone%'").all();
r2.forEach(x=>console.log(x.model));