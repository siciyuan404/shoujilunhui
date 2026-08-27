const http = require('http');
const KEY = 'sk-e756xogvi0lmt9miamt';
function req(m,p,b,h){return new Promise(r=>{const d=b||null;const hh=Object.assign({'X-API-Key':KEY},h||{});if(typeof d==='string')hh['Content-Length']=Buffer.byteLength(d);const q=http.request({host:'127.0.0.1',port:8760,path:p,method:m,headers:hh},s=>{let x='';s.on('data',c=>x+=c);s.on('end',()=>r({c:s.statusCode,b:x}))});q.on('error',()=>r({c:0,b:''}));if(d)q.write(d);q.end()})}
(async()=>{
  const list=await req('GET','/api/models?brand=vivo&limit=1000');
  const items=JSON.parse(list.body).items;
  const y77=items.find(x=>String(x.model).trim().toLowerCase()==='y77');
  const y9s=items.find(x=>String(x.model).trim().toLowerCase()==='y9s');
  console.log('Y77 images:', JSON.stringify(y77&&y77.images));
  if(y9s && y77 && y77.images && y77.images.length){
    const r=await req('PUT','/api/models/'+y9s.id,JSON.stringify({images:y77.images,release_date:'2018'}),{'Content-Type':'application/json'});
    console.log('Y9S PUT', r.c, r.b.slice(0,80));
  }
})();