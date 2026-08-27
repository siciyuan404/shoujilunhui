const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
for (const b of ['华为','OPPO','Apple']) {
  const r = db.prepare("SELECT model FROM models WHERE brand=? AND (images IS NULL OR images='' OR images='[]') ORDER BY model").all(b);
  console.log('===' + b + ' 无图 ' + r.length + '===');
  r.forEach(x=>console.log(x.model));
}