const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('F:/git/shoujilunhui/server/db/phone.db', { readOnly: true });
const total = db.prepare("SELECT COUNT(*) c FROM models").get().c;
const img = db.prepare("SELECT COUNT(*) c FROM models WHERE images IS NOT NULL AND images != '[]' AND images != ''").get().c;
const rd = db.prepare("SELECT COUNT(*) c FROM models WHERE release_date IS NOT NULL AND release_date != ''").get().c;
const cpu = db.prepare("SELECT COUNT(*) c FROM models WHERE cpu_model IS NOT NULL AND cpu_model != ''").get().c;
console.log(`总数=${total} 有图=${img}(${Math.round(img/total*100)}%) 有上市时间=${rd}(${Math.round(rd/total*100)}%) 有CPU=${cpu}(${Math.round(cpu/total*100)}%)`);