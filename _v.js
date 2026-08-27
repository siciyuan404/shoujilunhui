const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
console.log('=== vivo 机型(前60) ===');
db.prepare("SELECT model FROM models WHERE brand='vivo' ORDER BY model LIMIT 60").all().forEach(r=>console.log(r.model));
console.log('--- vivo 总数:', db.prepare("SELECT COUNT(*) c FROM models WHERE brand='vivo'").get().c);