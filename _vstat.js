const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const total = db.prepare("SELECT COUNT(*) c FROM models").get().c;
const withImg = db.prepare("SELECT COUNT(*) c FROM models WHERE images IS NOT NULL AND images != '' AND images != '[]'").get().c;
console.log('全库总数:', total, ' 有图:', withImg, '(', Math.round(withImg/total*100) + '%', ')');
console.log('---vivo 无图机型---');
const r = db.prepare("SELECT model FROM models WHERE brand='vivo' AND (images IS NULL OR images='' OR images='[]') ORDER BY model").all();
console.log('vivo 无图:', r.length);
r.forEach(x=>console.log(x.model));