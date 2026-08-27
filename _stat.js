const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const brands = ['vivo','OPPO','红米','真我','三星','努比亚','魅族','一加','金立','联想','360','美图','锤子','华硕智能手机','其他','谷歌手机','乐视','鼎桥','华为','荣耀','小米','Apple'];
console.log('品牌 | 总数 | 有图 | 有release_date | 有cpu_model');
for (const b of brands) {
  const r = db.prepare("SELECT COUNT(*) c, SUM(CASE WHEN images IS NOT NULL AND images != '[]' AND images != '' THEN 1 ELSE 0 END) img, SUM(CASE WHEN release_date IS NOT NULL AND release_date != '' THEN 1 ELSE 0 END) rd, SUM(CASE WHEN cpu_model IS NOT NULL AND cpu_model != '' THEN 1 ELSE 0 END) cp FROM models WHERE brand=?").get(b);
  console.log(`${b} | ${r.c} | ${r.img||0} | ${r.rd||0} | ${r.cp||0}`);
}