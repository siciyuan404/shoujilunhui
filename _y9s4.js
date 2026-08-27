const http = require('http');
const KEY = 'sk-e756xogvi0lmt9miamt';
function req(m,p,b,h){return new Promise((r,j)=>{const d=b||null;const hh=Object.assign({'X-API-Key':KEY},h||{});if(typeof d==='string')hh['Content-Length']=Buffer.byteLength(d);const q=http.request({host:'127.0.0.1',port:8760,path:p,method:m,headers:hh},s=>{let x='';s.on('data',c=>x+=c);s.on('end',()=>r({c:s.statusCode,b:x}))});q.on('error',j);if(d)q.write(d);q.end()})}
async function getList(){for(let i=0;i<4;i++){try{const l=await req('GET','/api/models?brand=vivo&limit=1000');if(l.b&&l.b.length>10)return JSON.parse(l.b);}catch(e){}await new Promise(r=>setTimeout(r,800));}throw new Error('GET fail');}
(async()=>{
  const items=(await getList()).items;
  const y77=items.find(x=>String(x.model).trim().toLowerCase()==='y77');
  const y9s=items.find(x=>String(x.model).trim().toLowerCase()==='y9s');
  console.log('Y77 img:', JSON.stringify(y77&&y77.images));
  if(y9s&&y77&&y77.images&&y77.images.length){
    const r=await req('PUT','/api/models/'+y9s.id,JSON.stringify({images:y77.images,release_date:'2018'}),{'Content-Type':'application/json'});
    console.log('Y9S PUT', r.c);
  }
})().catch(e=>console.log('ERR',e.message));