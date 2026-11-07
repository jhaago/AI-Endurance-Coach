const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function harness(){
 const tables={}; const files=[];
 class Sheet {constructor(name,headers){this.name=name;this.rows=[headers];} getDataRange(){return {getValues:()=>this.rows.map(r=>r.slice())};} getLastRow(){return this.rows.length;} getRange(row,col,nr,nc){return {setValues:values=>{values.forEach((v,i)=>this.rows[row-1+i]=v.slice());},getValues:()=>this.rows.slice(row-1,row-1+nr).map(r=>r.slice(col-1,col-1+nc))};} appendRow(row){this.rows.push(row);} }
 const ctx={console, JSON,Date,Math,Map,Set, Error, Utilities:{DigestAlgorithm:{SHA_256:1},Charset:{UTF_8:1},computeDigest:(_,s)=>[...require('node:crypto').createHash('sha256').update(s).digest()],newBlob:(s,m,n)=>({s,m,n})},
 PropertiesService:{getScriptProperties:()=>({getProperty:k=>({BRIDGE_SECRET:'x'.repeat(40),SPREADSHEET_ID:'sheet',ARCHIVE_FOLDER_ID:'folder'})[k]})},
 SpreadsheetApp:{openById:()=>({getSheetByName:name=>tables[name]})},
 DriveApp:{getFolderById:()=>({getId:()=> 'folder',getFilesByName:name=>({hasNext:()=>files.some(f=>f.n===name)}),createFile:blob=>{files.push(blob);return {getId:()=>String(files.length)};}})},
 LockService:{getScriptLock:()=>({waitLock(){},releaseLock(){}})},
 ContentService:{MimeType:{JSON:1},createTextOutput:s=>({setMimeType:()=>({text:s})})}};
 vm.createContext(ctx); vm.runInContext(fs.readFileSync('backend/Code.gs','utf8'),ctx);
 for(const [name,headers] of Object.entries(ctx.HEADERS))tables[name]=new Sheet(name,headers);
 const call=(payload)=>JSON.parse(ctx.doPost({postData:{contents:JSON.stringify({secret:'x'.repeat(40),...payload})}}).text);
 return {ctx,tables,files,call};
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
