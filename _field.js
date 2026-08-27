const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const total = db.prepare("SELECT COUNT(*) c FROM models").get().c;
const fields = ['cpu_brand','cpu_model','ram','rom','back_camera','front_camera','screen_size','screen_type','refresh','battery','charge','network','os','release_date'];
console.log('总机型:', total);
for (const f of fields) {
  const r = db.prepare(`SELECT COUNT(*) c FROM models WHERE ${f} IS NOT NULL AND ${f}!=''`).get().c;
  console.log(f.padEnd(14), Math.round(r/total*100)+'%', '(', r, '台 )');
}
// 图片数量分布
console.log('--- 图片数量分布 ---');
const img = db.prepare("SELECT images FROM models WHERE images IS NOT NULL AND images!='' AND images!='[]'").all();
const dist = {};
for (const row of img) {
  let arr;
  try { arr = JSON.parse(row.images); } catch(e) { arr = []; }
  const n = Array.isArray(arr) ? arr.length : 0;
  dist[n] = (dist[n]||0) + 1;
}
for (const k of Object.keys(dist).sort((a,b)=>a-b)) console.log(k + '张:', dist[k], '台');