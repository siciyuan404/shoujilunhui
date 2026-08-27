const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT model FROM models WHERE brand='真我' AND model LIKE 'Realme%' ORDER BY model LIMIT 20").all();
r.forEach(x=>console.log(JSON.stringify(x.model)));