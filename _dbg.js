const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const rows = db.prepare("SELECT id, model, substr(images,1,80) imgs FROM models WHERE brand='OPPO' AND model IN ('Reno8','find x8','R11/R11s','A3','K11','reno13')").all();
rows.forEach(r=>console.log(r.id, '|', r.model, '|', r.imgs));
console.log('---vivo 对照---');
const v = db.prepare("SELECT id, model, substr(images,1,80) imgs FROM models WHERE brand='vivo' AND model IN ('X90','x200','S20')").all();
v.forEach(r=>console.log(r.id, '|', r.model, '|', r.imgs));