const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function harness(options={}){
 const tables={}; const files=[]; const writes=[];
 class Sheet {constructor(name,headers){this.name=name;this.rows=[headers.slice()];this.maxRows=options.maxRows||1000;} read(rows){return rows.map(r=>r.map(v=>options.nativeLiterals&&['Health','WorkoutStatus','NutritionTargets'].includes(this.name)&&typeof v==='string'&&v.startsWith("'")?v.slice(1):v));} getDataRange(){return {getValues:()=>this.read(this.rows)};} getMaxRows(){return this.maxRows;} insertRowsAfter(after,count){assert.equal(after,this.maxRows);this.maxRows+=count;} getLastRow(){return this.rows.length;} getRange(row,col,nr,nc){return {setValues:values=>{assert.ok(row+nr-1<=this.maxRows);assert.equal(values.length,nr);assert.ok(values.every(v=>v.length===nc));writes.push({sheet:this.name,row,col,nr,nc});values.forEach((v,i)=>this.rows[row-1+i]=v.slice());},getValues:()=>this.rows.slice(row-1,row-1+nr).map(r=>r.slice(col-1,col-1+nc))};} appendRow(row){this.rows.push(row);} }
 const ctx={console, JSON,Date,Math,Map,Set, Error, Utilities:{DigestAlgorithm:{SHA_256:1},Charset:{UTF_8:1},computeDigest:(_,s)=>[...require('node:crypto').createHash('sha256').update(s).digest()],newBlob:(s,m,n)=>({s,m,n}),formatDate:(value,tz)=>new Intl.DateTimeFormat('en-CA',{timeZone:tz,year:'numeric',month:'2-digit',day:'2-digit'}).format(value)},
 PropertiesService:{getScriptProperties:()=>({getProperty:k=>({BRIDGE_SECRET:'x'.repeat(40),SPREADSHEET_ID:'sheet',ARCHIVE_FOLDER_ID:'folder'})[k]})},
 SpreadsheetApp:{openById:()=>({getSheetByName:name=>tables[name],getSpreadsheetTimeZone:()=>options.timezone||'Etc/UTC'})},
 DriveApp:{getFolderById:()=>({getId:()=> 'folder',getFilesByName:name=>({hasNext:()=>files.some(f=>f.n===name)}),createFile:blob=>{files.push(blob);return {getId:()=>String(files.length)};}})},
 LockService:{getScriptLock:()=>({waitLock(){},releaseLock(){}})},
 ContentService:{MimeType:{JSON:1},createTextOutput:s=>({setMimeType:()=>({text:s})})}};
 vm.createContext(ctx); vm.runInContext(fs.readFileSync('backend/Code.gs','utf8'),ctx);
 for(const [name,headers] of Object.entries(ctx.HEADERS))tables[name]=new Sheet(name,headers);
 const call=(payload)=>JSON.parse(ctx.doPost({postData:{contents:JSON.stringify({secret:'x'.repeat(40),...payload})}}).text);
 return {ctx,tables,files,writes,call};
}
test('unauthorized requests expose no data and mutate nothing',()=>{const h=harness();assert.equal(h.call({secret:'bad',action:'sync',activities:[]}).error,'Unauthorized');assert.equal(h.tables.Activities.rows.length,1);});
test('repeated delivery of activity creates one normalized record and one original archive',()=>{
 const h=harness(), a={activity_id:'1',start_utc:'2026-11-10T00:00:00Z',type:'running',duration_s:1800,distance_m:5000,raw_json:'{"activityId":1}'};
 assert.equal(h.call({action:'sync',activities:[a]}).ok,true);assert.equal(h.call({action:'sync',activities:[a]}).ok,true);
 assert.equal(h.tables.Activities.rows.length,2);assert.equal(h.files.length,1);
});
test('validates entire batch before any mutation',()=>{const h=harness();assert.equal(h.call({action:'sync',activities:[{activity_id:'1',start_utc:'2026-11-10T00:00:00Z',type:'running'}, {activity_id:''}]}).ok,false);assert.equal(h.tables.Activities.rows.length,1);});
test('feedback retry is idempotent and same ID different payload rejected',()=>{
 const h=harness(), feedback={feedback_id:'uuid-1',activity_id:'1',created_at:'2026-11-10T00:00:00Z',rpe:4,soreness:2,fatigue:3,note:'=IMPORTXML("bad")'};
 assert.equal(h.call({action:'sync',feedback:[feedback]}).ok,true);assert.equal(h.call({action:'sync',feedback:[feedback]}).ok,true);
 assert.equal(h.tables.Feedback.rows.length,2);assert.equal(h.call({action:'sync',feedback:[{...feedback,rpe:9}]}).ok,false);
 assert.ok(h.tables.Feedback.rows[1].some(v=>String(v).startsWith("'=IMPORTXML")));
});
test('snapshot includes append-only revisions without accepting plan writes from phone',()=>{
 const h=harness();h.tables.Plan.rows.push(['w',1,'event','2026-11-10','run','Easy',30,'easy',false,'scheduled','chat','2026-11-07T00:00:00Z','initial']);
 h.tables.Plan.rows.push(['w',2,'event','2026-11-11','run','Easy',20,'easy',false,'scheduled','chat','2026-11-08T00:00:00Z','reduce']);
 const result=h.call({action:'snapshot',plan:[]});assert.equal(result.snapshot.plan.length,2);assert.equal(h.tables.Plan.rows.length,3);
});
test('null wellness does not become zero and snapshots omit secrets',()=>{const h=harness();const r=h.call({action:'sync',wellness:[{date:'2026-11-10',sleep_hours:null,hrv_ms:null,resting_hr:null}]});assert.equal(r.ok,true);assert.equal(r.snapshot.wellness[0].sleep_hours,null);assert.ok(!JSON.stringify(r).includes('x'.repeat(40)));});
test('setup verification reads configuration and schema without returning credentials',()=>{const h=harness();const result=h.ctx.verifySetup();assert.equal(result.ok,true);assert.equal(result.plan_revisions,0);assert.ok(!JSON.stringify(result).includes('x'.repeat(40)));});
test('null diagnostic upload cannot erase same-day measured cloud wellness',()=>{
 const h=harness();h.call({action:'sync',wellness:[{date:'2026-11-07',sleep_hours:7,sleep_score:80,hrv_ms:44,resting_hr:52}]});
 const r=h.call({action:'sync',wellness:[{date:'2026-11-07',sleep_hours:null,sleep_score:75,hrv_ms:null,resting_hr:null,coverage_note:'HTTP 204 no data'}]});
 assert.equal(r.ok,true);const w=r.snapshot.wellness[0];assert.equal(w.sleep_hours,7);assert.equal(w.sleep_score,75);assert.equal(w.hrv_ms,44);assert.equal(w.resting_hr,52);assert.ok(w.coverage_note.includes('retained'));
});

const health=(overrides={})=>({change_id:'00000000-0000-4000-8000-000000000001',record_id:'meal-1',kind:'meal',date:'2026-11-07',name:'Synthetic meal',calories_kcal:450,portions:1,weight_kg:null,note:'',deleted:false,updated_at:'2026-11-07T12:00:00.001Z',meal_type:!overrides.kind||overrides.kind==='meal'?'other':'',protein_g:null,carbohydrate_g:null,fat_g:null,...overrides});
const change=(n,overrides={})=>health({change_id:`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`,...overrides});
test('Health accepts all kinds in one bounded append and acknowledges explicit unique IDs',()=>{
 const h=harness();const batch=[health(),change(2,{record_id:'favourite-1',kind:'favourite',date:'',portions:1}),change(3,{record_id:'weight:2026-11-07',kind:'weight',name:'',calories_kcal:null,portions:null,weight_kg:72}),change(4,{record_id:'goal-weight',kind:'goal',date:'',name:'',calories_kcal:null,portions:null,weight_kg:70})];
 const r=h.call({action:'sync',health:[...batch,batch[0]]});assert.equal(r.ok,true);assert.deepEqual(r.ack_health,batch.map(v=>v.change_id));assert.deepEqual(r.snapshot.health,batch);assert.equal(h.tables.Health.rows.length,5);assert.deepEqual(h.writes.filter(w=>w.sheet==='Health'),[{sheet:'Health',row:2,col:1,nr:4,nc:15}]);
});
test('Health retry is immutable and conflicts reject the entire mixed batch before archives',()=>{
 const h=harness(), first=health();assert.equal(h.call({action:'sync',health:[first]}).ok,true);
 assert.deepEqual(h.call({action:'sync',health:[first]}).ack_health,[first.change_id]);assert.equal(h.tables.Health.rows.length,2);
 const r=h.call({action:'sync',health:[change(2),{...first,calories_kcal:451}],activities:[{activity_id:'1',start_utc:'2026-11-07T00:00:00Z',type:'running',raw_json:'synthetic'}]});
 assert.equal(r.error,'Health conflict');assert.equal(h.tables.Health.rows.length,2);assert.equal(h.tables.Activities.rows.length,1);assert.equal(h.files.length,0);
 assert.equal(h.call({action:'sync',health:[change(3),change(3,{name:'conflict'})]}).error,'Health conflict');assert.equal(h.tables.Health.rows.length,2);
});
test('Health materialisation chooses parsed timestamp then change ID and keeps tombstones',()=>{
 const h=harness(), latest=change(4,{deleted:true,updated_at:'2026-11-08T12:00:00.002Z'});
 const r=h.call({action:'sync',health:[latest,health(),change(3,{updated_at:latest.updated_at,name:'tie loses'}),change(5,{updated_at:'2026-11-08T12:00:00.001Z'})]});
 assert.deepEqual(r.snapshot.health,[latest]);assert.equal(h.tables.Health.rows.length,5);assert.deepEqual(h.call({action:'snapshot'}).snapshot.health,[latest]);
});
for(const nativeLiterals of [false,true])test(`Health formula-safe strings and intentional apostrophes survive retries (native=${nativeLiterals})`,()=>{
 const h=harness({nativeLiterals});
 const batch=['=SUM(1,2)','+meal','@meal','-meal',"'=literal","''=literal","'plain"].map((name,i)=>change(i+1,{record_id:`meal-${i}`,name,note:name}));
 const r=h.call({action:'sync',health:batch});assert.deepEqual(r.snapshot.health,batch);assert.deepEqual(h.call({action:'sync',health:batch}).ack_health,batch.map(v=>v.change_id));assert.equal(h.tables.Health.rows.length,batch.length+1);
 assert.equal(h.call({action:'sync',health:[{...batch[0],name:"'=SUM(1,2)"}]}).error,'Health conflict');
});
test('Health restores blank date name and note while preserving null numeric fields',()=>{
 const h=harness();const value=health({record_id:'goal-weight',kind:'goal',date:'',name:'',note:'',calories_kcal:null,portions:null,weight_kg:70});
 assert.deepEqual(h.call({action:'sync',health:[value]}).snapshot.health,[value]);
});
test('Health rejects malformed full records before any mixed batch write',()=>{
 const variants=[null,[],{},...['change_id','record_id','kind','date','name','calories_kcal','portions','weight_kg','note','deleted','updated_at'].map(k=>{const value=health();delete value[k];return value;}),
 health({change_id:'bad id'}),health({record_id:'bad id'}),health({kind:'unknown'}),health({date:'2026-02-30'}),health({date:'2026-11-07T00:00:00Z'}),health({name:''}),health({name:'x'.repeat(121)}),health({calories_kcal:-1}),health({calories_kcal:20001}),health({calories_kcal:'2'}),health({portions:0}),health({portions:100.1}),health({weight_kg:70}),health({note:null}),health({note:'x'.repeat(1001)}),health({deleted:0}),health({updated_at:'2026-11-07T12:00:00Z'}),health({updated_at:'2026-02-30T12:00:00.000Z'}),health({updated_at:'2026-11-07T12:00:00.000+00:00'}),
 health({kind:'favourite',date:'',portions:2}),health({kind:'favourite',portions:1}),health({kind:'weight',name:'',calories_kcal:null,portions:null,weight_kg:70}),health({kind:'weight',record_id:'weight:2026-11-07',name:'',calories_kcal:null,portions:null,weight_kg:0}),health({kind:'goal',record_id:'goal-weight',date:'',name:'',calories_kcal:null,portions:null,weight_kg:500.1})];
 for(const value of variants){const h=harness();const r=h.call({action:'sync',health:[health(),value],feedback:[{feedback_id:'f',activity_id:'1',created_at:'2026-11-07T00:00:00Z',rpe:1,soreness:1,fatigue:1,note:''}]});assert.equal(r.error,'Invalid health',JSON.stringify(value));assert.equal(h.tables.Feedback.rows.length,1);assert.equal(h.tables.Health.rows.length,1);}
});
test('Health limits batches and validates its schema before any mixed writes',()=>{
 for(const healthValue of [null,{},'text',Array.from({length:101},(_,i)=>change(i+1))]){const h=harness();assert.equal(h.call({action:'sync',health:healthValue}).error,'Invalid health');}
 const h=harness();h.tables.Health.rows[0][1]='wrong';assert.equal(h.call({action:'sync',health:[health()],activities:[{activity_id:'1',type:'running',start_utc:'2026-11-07T00:00:00Z'}]}).error,'Schema mismatch');assert.equal(h.tables.Activities.rows.length,1);
});
test('snapshot requests never acknowledge unpersisted Health changes and old requests stay valid',()=>{
 const h=harness();const r=h.call({action:'snapshot',health:[health()]});assert.equal(r.ok,true);assert.deepEqual(r.ack_health,[]);assert.deepEqual(r.snapshot.health,[]);assert.equal(h.tables.Health.rows.length,1);assert.equal(h.call({action:'sync'}).ok,true);
});
test('Health accepts 100 changes and preserves exact numeric bounds and canonical fields',()=>{
 const h=harness();const batch=Array.from({length:100},(_,i)=>change(i+1,{record_id:`meal-${i}`,name:'x'.repeat(120),note:'n'.repeat(1000),calories_kcal:i===0?0:20000,portions:100}));
 const result=h.call({action:'sync',health:batch});assert.equal(result.ok,true);assert.equal(result.ack_health.length,100);assert.deepEqual(result.snapshot.health,batch);assert.equal(h.writes.length,1);
 assert.equal(h.call({action:'sync',health:[{...batch[0],ignored:'not a header'}]}).ok,true);assert.equal(h.tables.Health.rows.length,101);
});
test('Health validation failure in another record family leaves ledger untouched',()=>{
 const h=harness();assert.equal(h.call({action:'sync',health:[health()],matches:[{activity_id:'',workout_id:''}]}).error,'Invalid match');assert.equal(h.tables.Health.rows.length,1);
});
test('Health setup verification returns only schema and counts without changing rows',()=>{
 const h=harness();h.call({action:'sync',health:[health(),change(2,{deleted:true,updated_at:'2026-11-08T12:00:00.000Z'})]});
 assert.deepEqual(JSON.parse(JSON.stringify(h.ctx.verifyHealthSetup())),{ok:true,health_changes:2,health_records:1});assert.equal(h.tables.Health.rows.length,3);
 delete h.tables.Health;assert.throws(()=>h.ctx.verifyHealthSetup(),/Schema mismatch/);
});

test('Health append grows the row grid when the bounded batch crosses capacity',()=>{
 const h=harness({maxRows:2});const batch=[health(),change(2,{record_id:'meal-2'}),change(3,{record_id:'meal-3'})];
 const result=h.call({action:'sync',health:batch});assert.equal(result.ok,true);assert.deepEqual(result.snapshot.health,batch);assert.equal(h.tables.Health.maxRows,4);assert.equal(h.writes.filter(w=>w.sheet==='Health').length,1);
});

const status=(overrides={})=>({change_id:'status-1',workout_id:'w-1',status:'completed',actual_duration_min:null,note:'',updated_at:'2026-11-08T12:00:00.000Z',...overrides});
const statusChange=(n,overrides={})=>status({change_id:`status-${String(n).padStart(4,'0')}`,...overrides});
test('WorkoutStatus writes one bounded immutable batch with explicit idempotent acknowledgements',()=>{
 const h=harness({maxRows:2});const batch=[status(),statusChange(2,{workout_id:'w-2',status:'skipped'}),statusChange(3,{workout_id:'w-3',status:'scheduled'})];
 const r=h.call({action:'sync',workout_status:[...batch,batch[0]]});assert.equal(r.ok,true);assert.deepEqual(r.ack_workout_status,batch.map(v=>v.change_id));assert.deepEqual(r.snapshot.workout_status,batch);assert.equal(h.tables.WorkoutStatus.rows.length,4);assert.equal(h.tables.WorkoutStatus.maxRows,4);assert.deepEqual(h.writes.filter(w=>w.sheet==='WorkoutStatus'),[{sheet:'WorkoutStatus',row:2,col:1,nr:3,nc:6}]);
 assert.deepEqual(h.call({action:'sync',workout_status:batch}).ack_workout_status,batch.map(v=>v.change_id));assert.equal(h.tables.WorkoutStatus.rows.length,4);assert.equal(h.writes.length,1);
 const snapshot=h.call({action:'snapshot',workout_status:[statusChange(4)]});assert.deepEqual(snapshot.ack_workout_status,[]);assert.deepEqual(snapshot.snapshot.workout_status,batch);
});
test('WorkoutStatus current state includes reset, uses time then ID and retains all changes',()=>{
 const h=harness();const latest=statusChange(3,{status:'scheduled',updated_at:'2026-11-08T12:00:00.001Z'});
 const r=h.call({action:'sync',workout_status:[latest,status(),statusChange(2,{updated_at:latest.updated_at}),statusChange(4,{updated_at:'2026-11-08T12:00:00.000Z'})]});assert.deepEqual(r.snapshot.workout_status,[latest]);assert.equal(h.tables.WorkoutStatus.rows.length,5);
});
for(const nativeLiterals of [false,true])test(`WorkoutStatus preserves literal notes and exact retry identity (native=${nativeLiterals})`,()=>{
 const h=harness({nativeLiterals});const batch=['', '=SUM(1,2)', "'=SUM(1,2)", "''note"].map((note,i)=>statusChange(i+1,{workout_id:`w-${i}`,note}));
 assert.deepEqual(h.call({action:'sync',workout_status:batch}).snapshot.workout_status,batch);assert.deepEqual(h.call({action:'sync',workout_status:batch}).ack_workout_status,batch.map(v=>v.change_id));assert.equal(h.tables.WorkoutStatus.rows.length,5);
 assert.equal(h.call({action:'sync',workout_status:[{...batch[1],note:"'=SUM(1,2)"}]}).error,'Workout status conflict');
});
test('WorkoutStatus conflicts and malformed records reject the complete mixed batch before archives',()=>{
 const variants=[null,[],{},...['change_id','workout_id','status','actual_duration_min','note','updated_at'].map(k=>{const value=status();delete value[k];return value;}),status({change_id:'bad id'}),status({workout_id:'bad id'}),status({status:'unknown'}),status({actual_duration_min:-1}),status({actual_duration_min:10080.1}),status({actual_duration_min:'30'}),status({status:'skipped',actual_duration_min:0}),status({status:'scheduled',actual_duration_min:1}),status({note:'n'.repeat(1001)}),status({note:null}),status({updated_at:'2026-11-08T12:00:00Z'}),status({updated_at:'2026-02-30T12:00:00.000Z'})];
 for(const value of variants){const h=harness();const result=h.call({action:'sync',health:[health()],workout_status:[statusChange(2),value],activities:[{activity_id:'1',start_utc:'2026-11-08T00:00:00Z',type:'running',raw_json:'synthetic'}]});assert.equal(result.error,'Invalid workout status',JSON.stringify(value));assert.equal(h.tables.Health.rows.length,1);assert.equal(h.files.length,0);assert.equal(h.tables.Activities.rows.length,1);}
 const h=harness();h.call({action:'sync',workout_status:[status()]});assert.equal(h.call({action:'sync',workout_status:[statusChange(2),status({actual_duration_min:0})],health:[health()]}).error,'Workout status conflict');assert.equal(h.tables.Health.rows.length,1);assert.equal(h.tables.WorkoutStatus.rows.length,2);
 assert.equal(h.call({action:'sync',workout_status:[statusChange(2),statusChange(2,{note:'different'})]}).error,'Workout status conflict');assert.equal(h.tables.WorkoutStatus.rows.length,2);
});
test('WorkoutStatus enforces server batch limit, exact schema, boundaries and old requests',()=>{
 for(const value of [null,{},Array.from({length:101},(_,i)=>statusChange(i+1))]){const h=harness();assert.equal(h.call({action:'sync',workout_status:value}).error,'Invalid workout status');}
 const h=harness();const batch=Array.from({length:100},(_,i)=>statusChange(i+1,{workout_id:`w-${i}`,actual_duration_min:i===0?0:10080,note:'n'.repeat(1000)}));assert.equal(h.call({action:'sync',workout_status:batch}).snapshot.workout_status.length,100);assert.deepEqual(h.call({action:'sync'}).ack_workout_status,[]);
 h.tables.WorkoutStatus.rows[0][1]='wrong';assert.equal(h.call({action:'sync',health:[health()]}).error,'Schema mismatch');assert.equal(h.tables.Health.rows.length,1);
});
for(const nativeLiterals of [false,true])test(`Health canonicalises legacy rows and macro-free payloads before retries (native=${nativeLiterals})`,()=>{
 const h=harness({nativeLiterals});const legacy=health();for(const k of ['meal_type','protein_g','carbohydrate_g','fat_g'])delete legacy[k];
 h.tables.Health.rows.push(h.ctx.healthRow(legacy).slice(0,11));
 assert.deepEqual(h.call({action:'sync',health:[legacy]}).snapshot.health,[health()]);assert.equal(h.tables.Health.rows.length,2);
 assert.deepEqual(h.call({action:'sync',health:[{...legacy,meal_type:''}]}).ack_health,[legacy.change_id]);assert.equal(h.tables.Health.rows.length,2);
 assert.equal(h.call({action:'sync',health:[{...legacy,meal_type:'breakfast'}]}).error,'Health conflict');assert.equal(h.call({action:'sync',health:[{...legacy,protein_g:1}]}).error,'Health conflict');
});
test('Health accepts meal groups and optional per-portion macros but no app-managed calorie goal',()=>{
 const h=harness();const batch=['breakfast','lunch','dinner','snacks','other'].map((meal_type,i)=>change(i+1,{record_id:`meal-${i}`,meal_type,protein_g:0,carbohydrate_g:5000,fat_g:12.5}));batch.push(change(6,{kind:'favourite',record_id:'fav-1',date:'',meal_type:'',protein_g:22}));
 assert.deepEqual(h.call({action:'sync',health:batch}).snapshot.health,batch);
 const invalid=[health({meal_type:'dessert'}),health({meal_type:null}),health({kind:'favourite',date:'',meal_type:'breakfast'}),health({kind:'calorie_goal'}),health({protein_g:-1}),health({fat_g:5000.1}),health({carbohydrate_g:'2'}),health({protein_g:''}),health({kind:'goal',record_id:'goal-weight',date:'',name:'',calories_kcal:null,portions:null,weight_kg:70,protein_g:1})];
 for(const value of invalid)assert.equal(harness().call({action:'sync',health:[value]}).error,'Invalid health',JSON.stringify(value));
});
test('calendar Date cells use spreadsheet days and repeated Wellness writes keep one literal day key',()=>{
 const h=harness({timezone:'Australia/Perth'});const midnight=new Date('2026-11-06T16:00:00.000Z');
 h.tables.Plan.rows.push(['w',1,'event',midnight,'run','Synthetic',30,'easy',false,'scheduled','test','2026-11-07T00:00:00Z','synthetic']);h.tables.Plan.rows.push(['w',2,'event',"'2026-11-08",'run','Synthetic',20,'easy',false,'scheduled','test','2026-11-08T00:00:00Z','synthetic']);
 h.tables.Wellness.rows.push([midnight,7,80,44,52,'synthetic','hash','2026-11-07T01:00:00.000Z']);
 const r=h.call({action:'sync',wellness:[{date:'2026-11-07',sleep_hours:null,hrv_ms:null,resting_hr:null}]});assert.equal(r.ok,true);assert.deepEqual(r.snapshot.plan.map(v=>v.date),['2026-11-07','2026-11-08']);assert.equal(r.snapshot.wellness[0].date,'2026-11-07');assert.equal(r.snapshot.wellness[0].sleep_hours,7);assert.equal(h.tables.Wellness.rows.length,2);assert.equal(h.tables.Wellness.rows[1][0],"'2026-11-07");
 assert.equal(h.call({action:'sync',wellness:[{date:'2026-11-07',sleep_score:81}]}).ok,true);assert.equal(h.tables.Wellness.rows.length,2);assert.equal(h.tables.Plan.rows.length,3);
});
test('Wellness duplicate-day materialisation retains latest available measurements without editing history',()=>{
 const h=harness({timezone:'Australia/Perth'});const rows=[[new Date('2026-11-06T16:00:00.000Z'),7,80,44,52,'older','a','2026-11-07T01:00:00.000Z'],['2026-11-07','',82,'','','newer','b','2026-11-07T03:00:00.000Z'],["'2026-11-07",8,'','','','middle','c','2026-11-07T02:00:00.000Z']];h.tables.Wellness.rows.push(...rows.map(r=>r.slice()));
 const before=JSON.stringify(h.tables.Wellness.rows);const result=h.call({action:'snapshot'});assert.equal(result.snapshot.wellness.length,1);const w=result.snapshot.wellness[0];assert.equal(w.date,'2026-11-07');assert.equal(w.sleep_hours,8);assert.equal(w.sleep_score,82);assert.equal(w.hrv_ms,44);assert.equal(w.resting_hr,52);assert.equal(w.source_hash,'b');assert.equal(JSON.stringify(h.tables.Wellness.rows),before);
 const synced=h.call({action:'sync',wellness:[{date:'2026-11-07',sleep_hours:null,sleep_score:83}]});assert.equal(h.tables.Wellness.rows.length,4);assert.equal(synced.snapshot.wellness.length,1);assert.equal(synced.snapshot.wellness[0].sleep_hours,8);assert.equal(synced.snapshot.wellness[0].hrv_ms,44);
});
const target=(overrides={})=>({target_id:'baseline-2026-11-01',revision:1,scope:'baseline',date:'2026-11-01',calories_kcal:2000,protein_g:null,carbohydrate_g:null,fat_g:null,weight_goal_kg:null,weight_goal_date:'',weight_change_kg_per_week:null,training_type:'',exercise_included:false,source:'Synthetic coach fixture',created_at:'2026-11-01T12:00:00.000Z',reason:'',status:'approved',...overrides});
function seedTarget(h,value){h.tables.NutritionTargets.rows.push(h.ctx.HEADERS.NutritionTargets.map(k=>value[k]===null||value[k]===undefined?'':value[k]));}
test('NutritionTargets snapshot exposes the complete valid ledger including drafts and cancellations',()=>{
 const h=harness();const batch=[target(),target({revision:2,status:'draft',calories_kcal:2200}),target({revision:3,status:'cancelled'}),target({target_id:'nutrition-2027-01-01',scope:'daily',date:'2027-01-01',protein_g:100,carbohydrate_g:250,fat_g:60,weight_goal_kg:70,weight_goal_date:'2027-06-01',weight_change_kg_per_week:-0.2,training_type:'Synthetic run',exercise_included:true})];batch.forEach(t=>seedTarget(h,t));seedTarget(h,batch[0]);
 const before=JSON.stringify(h.tables.NutritionTargets.rows);const r=h.call({action:'snapshot'});assert.equal(r.ok,true);assert.deepEqual(r.snapshot.nutrition_targets,batch);assert.ok(!('nutrition_error' in r.snapshot));assert.equal(JSON.stringify(h.tables.NutritionTargets.rows),before);
});
test('NutritionTargets malformed, missing and conflicting sheets are nonfatal to training sync',()=>{
 for(const setup of [h=>delete h.tables.NutritionTargets,h=>{h.tables.NutritionTargets.rows[0][1]='wrong';},h=>{seedTarget(h,target());seedTarget(h,target({calories_kcal:2200,reason:'PRIVATE synthetic value'}));},h=>{seedTarget(h,target({target_id:''}));}]){
  const h=harness();setup(h);const r=h.call({action:'sync',workout_status:[status()],health:[health()],wellness:[{date:'2026-11-08',sleep_hours:7}]});assert.equal(r.ok,true);assert.deepEqual(r.ack_workout_status,[status().change_id]);assert.deepEqual(r.ack_health,[health().change_id]);assert.equal(r.snapshot.wellness.length,1);assert.ok(!('nutrition_targets' in r.snapshot));assert.equal(typeof r.snapshot.nutrition_error,'string');assert.ok(!JSON.stringify(r).includes('PRIVATE'));assert.equal(h.tables.Health.rows.length,2);
 }
});
test('NutritionTargets validates every header field and excludes the complete ledger on any malformed row',()=>{
 const invalid=[target({target_id:'wrong'}),target({revision:0}),target({revision:1.5}),target({scope:'weekly'}),target({date:'2026-02-30'}),target({calories_kcal:0}),target({calories_kcal:20000.1}),target({calories_kcal:'2000'}),target({protein_g:-1}),target({carbohydrate_g:5000.1}),target({fat_g:'20'}),target({weight_goal_kg:0}),target({weight_goal_kg:500.1}),target({weight_goal_date:'2026-02-30'}),target({weight_change_kg_per_week:-5.1}),target({weight_change_kg_per_week:5.1}),target({training_type:'x'.repeat(121)}),target({exercise_included:''}),target({source:''}),target({source:'x'.repeat(121)}),target({created_at:'2026-11-01T12:00:00Z'}),target({created_at:'2026-02-30T12:00:00.000Z'}),target({reason:'x'.repeat(2001)}),target({status:'pending'})];
 for(const value of invalid){const h=harness();seedTarget(h,target());seedTarget(h,{...value,revision:value.revision===1?2:value.revision});const r=h.call({action:'snapshot'});assert.equal(r.ok,true);assert.ok(!('nutrition_targets' in r.snapshot),JSON.stringify(value));assert.equal(typeof r.snapshot.nutrition_error,'string');}
});
test('NutritionTargets normalises typed calendar cells, blank optional cells and signed bounds',()=>{
 const h=harness({timezone:'Australia/Perth'});const first=target({protein_g:0,carbohydrate_g:5000,fat_g:0,weight_goal_kg:500,weight_goal_date:'2027-01-01',weight_change_kg_per_week:-5});const second=target({target_id:'nutrition-2026-11-02',scope:'daily',date:'2026-11-02',revision:2,calories_kcal:20000,weight_change_kg_per_week:5});seedTarget(h,first);seedTarget(h,second);
 h.tables.NutritionTargets.rows[1][3]=new Date('2026-10-31T16:00:00.000Z');h.tables.NutritionTargets.rows[1][9]=new Date('2026-12-31T16:00:00.000Z');h.tables.NutritionTargets.rows[1][14]=new Date(first.created_at);
 assert.deepEqual(h.call({action:'snapshot'}).snapshot.nutrition_targets,[first,second]);
});
test('phone NutritionTargets writes are rejected before any mixed mutation even for null or empty payloads',()=>{
 for(const nutrition_targets of [[],null,[target()]]){const h=harness();const r=h.call({action:'sync',nutrition_targets,health:[health()],workout_status:[status()],activities:[{activity_id:'1',type:'running',start_utc:'2026-11-08T00:00:00Z',raw_json:'synthetic'}]});assert.equal(r.error,'Invalid request');assert.equal(h.tables.Health.rows.length,1);assert.equal(h.tables.Activities.rows.length,1);assert.equal(h.files.length,0);}
});
test('Coach setup verification returns schema readiness and counts only',()=>{
 const h=harness();h.call({action:'sync',health:[health()],workout_status:[status()]});seedTarget(h,target());const r=JSON.parse(JSON.stringify(h.ctx.verifyCoachSetup()));assert.deepEqual(r,{ok:true,health_changes:1,health_records:1,workout_status_changes:1,workout_status_records:1,nutrition_targets_ready:true,nutrition_revisions:1});assert.equal(h.writes.length,2);assert.ok(!JSON.stringify(r).includes('Synthetic'));delete h.tables.NutritionTargets;assert.equal(h.ctx.verifyCoachSetup().nutrition_targets_ready,false);
});
for(const nativeLiterals of [false,true])test(`NutritionTargets safely reads literal-prefixed rows and preserves intentional apostrophes (native=${nativeLiterals})`,()=>{
 const h=harness({nativeLiterals});const value=target({source:"'Synthetic coach",reason:'=A1',training_type:'-Synthetic recovery'});
 h.tables.NutritionTargets.rows.push(h.ctx.HEADERS.NutritionTargets.map(k=>typeof value[k]==='string'?"'"+value[k]:value[k]===null?'':value[k]));
 assert.deepEqual(h.call({action:'snapshot'}).snapshot.nutrition_targets,[value]);assert.equal(h.writes.length,0);
});
test('NutritionTargets ignores completely blank rows and remains unset with a valid empty ledger',()=>{
 const h=harness();h.tables.NutritionTargets.rows.push(Array(17).fill(''));const r=h.call({action:'snapshot'});assert.deepEqual(r.snapshot.nutrition_targets,[]);assert.ok(!('nutrition_error' in r.snapshot));
});
