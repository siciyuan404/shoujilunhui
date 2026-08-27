const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT model FROM models WHERE brand='锤子' ORDER BY model").all();
console.log('锤子 总数:', r.length);
r.forEach(x=>console.log(x.model));