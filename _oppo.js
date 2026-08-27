const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT model FROM models WHERE brand='OPPO' ORDER BY model").all();
console.log('OPPO 总数:', r.length);
r.forEach((x,i)=>{ process.stdout.write(x.model + '\n'); });