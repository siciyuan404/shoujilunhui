const { DatabaseSync } = require('node:sqlite');
const fs = require('fs');
const path = require('path');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
// 各品牌统计
const brands = db.prepare('SELECT brand, COUNT(*) c FROM models GROUP BY brand ORDER BY c DESC').all();
console.log('=== 各品牌机型数 & 有图数 ===');
const rows = db.prepare('SELECT brand, model, images FROM models').all();
const imgOk = new Map(); const hasImg = new Map();
for (const r of rows) {
  let arr=[]; try{ arr = JSON.parse(r.images); }catch(e){}
  if(Array.isArray(arr) && arr.length){
    hasImg.set(r.brand, (hasImg.get(r.brand)||0)+1);
    const fname = String(arr[0]).replace(/^.*\//,'');
    const p = path.join('F:/git/shoujilunhui/server/uploads', fname);
    if(fs.existsSync(p) && fs.statSync(p).size>4) imgOk.set(r.brand,(imgOk.get(r.brand)||0)+1);
  }
}
for (const b of brands) {
  const total = b.c;
  const ok = imgOk.get(b.brand)||0;
  const has = hasImg.get(b.brand)||0;
  if (total >= 3) console.log(b.brand.padEnd(8), ('总数'+total).padEnd(6), ('有图'+has).padEnd(6), ('好图'+ok));
}
// 参数填充率
console.log('\n=== 参数字段填充率 ===');
const total = db.prepare('SELECT COUNT(*) c FROM models').get().c;
const fields = ['release_date','cpu_brand','cpu_model','ram','rom','back_camera','front_camera','screen_size','screen_type','refresh','battery','charge','network','os','variants','price','note'];
for (const f of fields) {
  const c = db.prepare(`SELECT COUNT(*) c FROM models WHERE ${f} IS NOT NULL AND ${f} != ''`).get().c;
  console.log(f.padEnd(14), (c+'/'+total).padEnd(10), (c/total*100).toFixed(0)+'%');
}