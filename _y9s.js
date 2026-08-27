const http = require('http');
const API = 'http://127.0.0.1:8760';
const KEY = 'sk-e756xogvi0lmt9miamt';
function req(m,p,b,h){return new Promise(r=>{const d=b||null;const hh=Object.assign({'X-API-Key':KEY},h||{});if(typeof d==='string')hh['Content-Length']=Buffer.byteLength(d);const q=http.request({host:'127.0.0.1',port:8760,path:p,method:m,headers:hh},s=>{let x='';s.on('data',c=>x+=c);s.on('end',()=>r({c:s.statusCode,b:x}))});q.on('error',()=>r({c:0,b:''}));if(d)q.write(d);q.end()})}
(async()=>{
  const list=await req('GET','/api/models?brand=vivo&limit=1000');
  const items=JSON.parse(list.body).items;
  const it=items.find(x=>String(x.model).trim().toLowerCase()==='y9s');
  if(!it){console.log('未找到Y9S');return;}
  const img='https://aka.doubaocdn.com/s/IEKE45FF9L';
  const https=require('https');
  const buf=await new Promise(res=>{https.get(img,{headers:{'User-Agent':'Mozilla/5.0'}},r=>{const ch=[];r.on('data',c=>ch.push(c));r.on('end',()=>res(Buffer.concat(ch)))})});
  const up=await req('POST','/api/upload',buf,{'Content-Type':'image/jpeg'});
  const j=JSON.parse(up.b);
  const r2=await req('PUT','/api/models/'+it.id,JSON.stringify({images:[j.url],release_date:'2018'}),{'Content-Type':'application/json'});
  console.log('Y9S', r2.c, j.url);
})();