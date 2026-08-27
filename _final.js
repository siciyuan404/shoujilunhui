const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const total = db.prepare("SELECT COUNT(*) c FROM models").get().c;
const withImg = db.prepare("SELECT COUNT(*) c FROM models WHERE images IS NOT NULL AND images != '' AND images != '[]'").get().c;
console.log('全库总数:', total, ' 有图:', withImg, '(', Math.round(withImg/total*100) + '%', ')');
console.log('--- 各品牌有图率 ---');
const r = db.prepare("SELECT brand, COUNT(*) c, SUM(CASE WHEN images IS NOT NULL AND images!='' AND images!='[]' THEN 1 ELSE 0 END) img FROM models GROUP BY brand ORDER BY c DESC").all();
r.forEach(x=>console.log(x.brand.padEnd(10), ('有图 ' + x.img + '/' + x.c).padEnd(12), x.img===x.c?'✓':''));