const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const r = db.prepare("SELECT model FROM models WHERE brand='谷歌手机' ORDER BY model").all();
console.log('谷歌手机 总数:', r.length);
r.forEach(x=>console.log(x.model));
console.log('---其他品牌剩余---');
for (const b of ['其他','乐视','鼎桥','智选']) {
  const rr = db.prepare("SELECT model FROM models WHERE brand=?").all(b);
  console.log(b, rr.length, rr.map(x=>x.model).join(' / '));
}