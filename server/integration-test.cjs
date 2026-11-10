const {spawn}=require('node:child_process'),assert=require('node:assert/strict'),path=require('node:path'),crypto=require('node:crypto');
const project=path.resolve(__dirname,'..');const dll=process.argv[2]||path.join(__dirname,'bin/Debug/net10.0/FotoEvidenciaEquans.Api.dll');
const database='FotoEvidenciaEquansTests_'+crypto.randomBytes(5).toString('hex');let child;
const env={...process.env,Urls:'http://127.0.0.1:15081',ConnectionStrings__Equans:`Server=lpc:localhost;Database=${database};Integrated Security=true;Encrypt=true;TrustServerCertificate=true`,EQUANS_BOOTSTRAP_PASSWORD:'Integration-Test-Only-12345'};
async function start(){child=spawn('C:\\Program Files\\dotnet\\dotnet.exe',[dll],{cwd:__dirname,env,stdio:['ignore','pipe','pipe']});let output='';child.stdout.on('data',x=>output+=x);child.stderr.on('data',x=>output+=x);for(let n=0;n<100;n++){if(child.exitCode!==null)throw Error(output);try{const r=await fetch('http://127.0.0.1:15081/health');if(r.ok)return}catch{}await new Promise(r=>setTimeout(r,200))}throw Error('Startup timeout: '+output)}
async function stop(){if(child&&child.exitCode===null){const exited=new Promise(r=>child.once('exit',r));child.kill();await exited}}
async function req(route,body={},token=''){return fetch('http://127.0.0.1:15081'+route,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body)})}
(async()=>{try{await start();assert.equal((await fetch('http://127.0.0.1:15081/')).status,200);assert.equal((await req('/items')).status,401);assert.equal((await req('/login',{user:'supervisor',password:'wrong'})).status,401);
const supervisor=(await(await req('/login',{user:'supervisor',password:env.EQUANS_BOOTSTRAP_PASSWORD})).json()).token;assert.ok(supervisor);
assert.equal((await req('/accounts',{user:'worker',password:'Worker-Test-12345'},supervisor)).status,200);
assert.equal((await req('/accounts',{user:'worker',password:'Worker-Test-12345'},supervisor)).status,400);
const worker=(await(await req('/login',{user:'worker',password:'Worker-Test-12345'})).json()).token;
assert.equal((await req('/items',{},worker)).status,403);assert.equal((await req('/accounts',{user:'hacker',password:'Worker-Test-12345'},worker)).status,403);
const evidence={id:crypto.randomUUID(),recordId:crypto.randomUUID(),author:'worker',type:'Mantenimiento',location:'E20 – Evitamiento',tag:'Detectores',description:'PRUEBA DE INTEGRACION',gps:'Sin GPS (prueba)',createdAt:new Date().toISOString(),image:Buffer.from([255,216,255,217]).toString('base64')};
const uploads=await Promise.all(Array.from({length:4},()=>req('/evidence',evidence,worker)));for(const r of uploads){assert.equal(r.status,200);assert.equal((await r.json()).stored,true)}
assert.equal((await req('/evidence',{...evidence,tag:'Hidrantes'},worker)).status,409);
assert.equal((await req('/evidence',{...evidence,id:'invalid'},worker)).status,400);
assert.equal((await req('/evidence',{...evidence,author:'supervisor'},worker)).status,400);
assert.equal((await(await req('/items',{},supervisor)).json()).items.length,1);
assert.equal((await req('/review',{id:evidence.id,state:'Revisada'},worker)).status,403);
assert.equal((await req('/review',{id:evidence.id,state:'Revisada'},supervisor)).status,200);
await stop();await start();const items=(await(await req('/items',{},supervisor)).json()).items;assert.equal(items.length,1);assert.equal(items[0].review,'Revisada');assert.equal(items[0].reviewer,'supervisor');
const photo=await req('/photo',{id:evidence.id},supervisor);assert.equal(photo.status,200);assert.deepEqual(Buffer.from(await photo.arrayBuffer()),Buffer.from([255,216,255,217]));
assert.equal((await req('/logout',{},worker)).status,200);assert.equal((await req('/evidence',evidence,worker)).status,401);
const secondLogin=await req('/accounts',{user:'coworker',password:'Worker-Test-12345'},supervisor);assert.equal(secondLogin.status,200);
const coworker=(await(await req('/login',{user:'coworker',password:'Worker-Test-12345'})).json()).token;
const original=Buffer.from([255,216,255,0,217,255,217]);
const shared={...evidence,id:crypto.randomUUID(),recordId:crypto.randomUUID(),shift:'Nocturno',workDate:'2026-11-10',environment:'Cuarto de Bómbas',siteCode:'STATION:E20',activity:'Inspección',originalImage:original.toString('base64'),createdAt:'2026-11-11T03:00:00Z'};
assert.equal((await req('/evidence',shared,worker)).status,401); // Worker token was explicitly revoked above.
const again=(await(await req('/login',{user:'worker',password:'Worker-Test-12345'})).json()).token;
assert.equal((await req('/evidence',shared,again)).status,200);
const other={...shared,id:crypto.randomUUID(),recordId:crypto.randomUUID(),author:'coworker',environment:'  cuarto DE bombas  ',createdAt:'2026-11-11T08:00:00Z'};
assert.equal((await req('/evidence',other,coworker)).status,200);
const day={...shared,id:crypto.randomUUID(),recordId:crypto.randomUUID(),shift:'Diurno',workDate:'2026-11-11'};
assert.equal((await req('/evidence',day,again)).status,200);
const anotherRoom={...shared,id:crypto.randomUUID(),recordId:crypto.randomUUID(),environment:'Almacén'};
assert.equal((await req('/evidence',anotherRoom,again)).status,200);
const derived={...shared,id:crypto.randomUUID(),recordId:crypto.randomUUID(),workDate:'',createdAt:'2026-11-11T08:10:00Z'};assert.equal((await req('/evidence',derived,again)).status,200);
const grouped=(await(await req('/items',{},supervisor)).json()).items;
const first=grouped.find(i=>i.id===shared.id),second=grouped.find(i=>i.id===other.id);
assert.equal(first.groupKey,second.groupKey);assert.equal(grouped.find(i=>i.id===derived.id).workDate,"2026-11-10");assert.equal(grouped.find(i=>i.id===derived.id).groupKey,first.groupKey);assert.notEqual(first.groupKey,grouped.find(i=>i.id===day.id).groupKey);assert.equal(first.groupKey,grouped.find(i=>i.id===anotherRoom.id).groupKey);
assert.equal(first.hasOriginal,true);assert.equal(first.workDate,'2026-11-10');
const originalResponse=await req('/photo',{id:shared.id,original:true},supervisor);assert.deepEqual(Buffer.from(await originalResponse.arrayBuffer()),original);
const anotherSite={...shared,id:crypto.randomUUID(),recordId:crypto.randomUUID(),siteCode:'STATION:E21',location:'E21'};
assert.equal((await req('/evidence',anotherSite,again)).status,200);
const nextNight={...shared,id:crypto.randomUUID(),recordId:crypto.randomUUID(),workDate:'2026-11-11'};
assert.equal((await req('/evidence',nextNight,again)).status,200);
const expanded=(await(await req('/items',{},supervisor)).json()).items;
assert.notEqual(first.groupKey,expanded.find(i=>i.id===anotherSite.id).groupKey);
assert.notEqual(first.groupKey,expanded.find(i=>i.id===nextNight.id).groupKey);
assert.equal((await req('/export',{groupKey:first.groupKey},coworker)).status,403);
const archive=await req('/export',{groupKey:first.groupKey},supervisor);assert.equal(archive.status,200);const zip=Buffer.from(await archive.arrayBuffer());assert.equal(zip.readUInt16LE(0),0x4b50);assert.ok(zip.includes(Buffer.from(shared.id+'.jpg')));assert.ok(zip.includes(Buffer.from(other.id+'.jpg')));assert.ok(zip.includes(Buffer.from('Originales/')));assert.ok(zip.includes(Buffer.from(anotherRoom.id+'.jpg')));assert.ok(!zip.includes(Buffer.from(anotherSite.id+'.jpg')));
await stop();await start();assert.equal((await(await req('/items',{},supervisor)).json()).items.find(i=>i.id===shared.id).groupKey,first.groupKey);
console.log('OK: SQL, roles, duplicados, originales, carpeta compartida entre trabajadores, turno nocturno, ubicación y jornada, ZIP y persistencia.');console.log('Base de pruebas aislada: '+database);
}finally{await stop()}})().catch(e=>{console.error(e);process.exitCode=1});


