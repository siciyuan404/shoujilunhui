const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
console.log('=== vivo 机型(61-282) ===');
db.prepare("SELECT model FROM models WHERE brand='vivo' ORDER BY model LIMIT 221 OFFSET 60").all().forEach(r=>console.log(r.model));