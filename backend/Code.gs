/* Single-user bridge. Credentials belong ONLY in Script Properties. Never in this source. */
var HEADERS = {
  Events: ['event_id','name','date','primary_yards','primary_km','stretch_yards','stretch_km','runs_per_week','optional_fourth','timezone','active'],
  Plan: ['workout_id','revision','event_id','date','type','title','duration_min','intensity','optional','status','author','created_at','reason'],
  Activities: ['activity_id','name','start_utc','type','distance_m','duration_s','pace_s_km','average_hr','elevation_m','cadence_spm','source_hash','updated_at'],
  Wellness: ['date','sleep_hours','sleep_score','hrv_ms','resting_hr','coverage_note','source_hash','updated_at'],
  Feedback: ['feedback_id','activity_id','workout_id','created_at','rpe','soreness','fatigue','note'],
  Matches: ['activity_id','workout_id','updated_at'],
  Archive: ['record_id','sha256','drive_file_id','created_at'],
  Health: ['change_id','record_id','kind','date','name','calories_kcal','portions','weight_kg','note','deleted','updated_at']
};

function doGet() { return output({ok:false,error:'Use authenticated POST'}); }
function output(value) {return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);}
function doPost(e) {
  var lock=null;
  try {
    if(!e || !e.postData || e.postData.contents.length>4000000) throw Error('Invalid request');
    var req=JSON.parse(e.postData.contents), props=PropertiesService.getScriptProperties();
    var secret=props.getProperty('BRIDGE_SECRET');
    if(!secret || secret.length<32 || !constantEquals(secret,req.secret))throw Error('Unauthorized');
    if(['snapshot','sync'].indexOf(req.action)<0)throw Error('Invalid action');
    var db=SpreadsheetApp.openById(props.getProperty('SPREADSHEET_ID'));
    lock=LockService.getScriptLock(); lock.waitLock(20000);
    var payload=validate(req,db);
    if(req.action==='sync') {
      // Archives are durable before normalized rows are acknowledged. Retries are content-addressed.
      payload.activities.forEach(function(a){if(a.raw_json)archive(db,props,'activity-'+a.activity_id,a.raw_json);});
      payload.wellness.forEach(function(w){if(w.raw_json)archive(db,props,'wellness-'+w.date,w.raw_json);});
      payload.activities.forEach(function(a){var row=Object.assign({},a,{source_hash:hash(a.raw_json||JSON.stringify(a)),updated_at:new Date().toISOString()});upsert(db,'Activities','activity_id',row);});
      payload.wellness.forEach(function(w){var row=Object.assign({},w,{source_hash:hash(w.raw_json||JSON.stringify(w)),updated_at:new Date().toISOString()});upsert(db,'Wellness','date',row);});
      payload.feedback.forEach(function(f){if(!find(db,'Feedback','feedback_id',f.feedback_id))append(db,'Feedback',f);});
      payload.matches.forEach(function(m){upsert(db,'Matches','activity_id',m);});
      appendHealth(db,payload.health_new);
    }
    return output({ok:true,ack_feedback:payload.feedback.map(function(f){return f.feedback_id;}),ack_health:req.action==='sync'?payload.health_ack:[],snapshot:snapshot(db)});
  } catch(err) {
    // No stack, upstream response, tokens, or arbitrary exception text in the public response.
    var allowed=['Unauthorized','Invalid request','Invalid action','Invalid activity','Invalid wellness','Invalid feedback','Feedback conflict','Invalid match','Invalid health','Health conflict','Schema mismatch'];
    return output({ok:false,error:allowed.indexOf(err.message)>=0?err.message:'Bridge unavailable; check setup and retry'});
  } finally {if(lock)lock.releaseLock();}
}
function constantEquals(a,b) {if(typeof b!=='string')return false;var diff=a.length^b.length;for(var i=0;i<a.length;i++)diff|=a.charCodeAt(i)^(b.charCodeAt(i)||0);return diff===0;}
function hash(text) {return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,text,Utilities.Charset.UTF_8).map(function(b){return ('0'+((b+256)%256).toString(16)).slice(-2);}).join('');}
function sheet(db,name) {
 var s=db.getSheetByName(name);if(!s)throw Error('Schema mismatch');
 var actual=s.getRange(1,1,1,HEADERS[name].length).getValues()[0];
 if(JSON.stringify(actual)!==JSON.stringify(HEADERS[name]))throw Error('Schema mismatch');return s;
}
function records(db,name) {
 var values=sheet(db,name).getDataRange().getValues();
 return values.slice(1).filter(function(r){return r[0]!=='';}).map(function(r){var obj={};HEADERS[name].forEach(function(k,i){var v=r[i]; obj[k]=(v===''||v===undefined)?null:v instanceof Date?v.toISOString():v;});return obj;});
}
function safe(value){if(value===null||value===undefined)return '';if(typeof value==='string'&&/^[=+@\-]/.test(value))return "'"+value;return value;}
function row(name,obj){return HEADERS[name].map(function(k){return safe(obj[k]);});}
function append(db,name,obj){sheet(db,name).appendRow(row(name,obj));}
function find(db,name,key,id){return records(db,name).find(function(r){return String(r[key])===String(id);});}
function upsert(db,name,key,obj) {
 if(name==='Wellness')obj=preserveWellness(obj,find(db,'Wellness',key,obj[key]));
 var s=sheet(db,name), rows=s.getDataRange().getValues(), col=HEADERS[name].indexOf(key);
 for(var i=1;i<rows.length;i++)if(String(rows[i][col])===String(obj[key])){s.getRange(i+1,1,1,HEADERS[name].length).setValues([row(name,obj)]);return;}
 s.appendRow(row(name,obj));
}
function preserveWellness(fresh,previous) {
 if(!previous)return fresh;
 var result=Object.assign({},fresh),retained=false;
 ['sleep_hours','sleep_score','hrv_ms','resting_hr'].forEach(function(k){if((result[k]===null||result[k]===undefined)&&typeof previous[k]==='number'&&isFinite(previous[k])){result[k]=previous[k];retained=true;}});
 if(retained)result.coverage_note=(result.coverage_note||'')+'\nPrevious measured values retained where the latest values are unavailable.';
 return result;
}
function validId(value){return (typeof value==='string'||typeof value==='number')&&/^[a-zA-Z0-9_:.\-]{1,100}$/.test(String(value));}
function validDate(value){return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)&&!isNaN(Date.parse(value))&&new Date(value).toISOString().slice(0,10)===value;}
function validTime(value){return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(value)&&value.endsWith('Z')&&!isNaN(Date.parse(value));}
function finiteOrNull(value,min,max){return value===null||value===undefined||(typeof value==='number'&&isFinite(value)&&value>=min&&value<=max);}
function validate(req,db) {
 var p={activities:req.activities||[],wellness:req.wellness||[],feedback:req.feedback||[],matches:req.matches||[]};
 Object.keys(p).forEach(function(k){if(!Array.isArray(p[k])||p[k].length>100)throw Error('Invalid request');});
 p.activities.forEach(function(a){if(!validId(a.activity_id)||!validTime(a.start_utc)||typeof a.type!=='string'||!finiteOrNull(a.distance_m,0,10000000)||!finiteOrNull(a.duration_s,0,10000000)||!finiteOrNull(a.average_hr,0,300)||typeof(a.raw_json||'')!=='string'||(a.raw_json||'').length>1000000)throw Error('Invalid activity');});
 p.wellness.forEach(function(w){if(!validDate(w.date)||!finiteOrNull(w.sleep_hours,0,24)||!finiteOrNull(w.sleep_score,0,100)||!finiteOrNull(w.hrv_ms,0,1000)||!finiteOrNull(w.resting_hr,0,300)||typeof(w.raw_json||'')!=='string'||(w.raw_json||'').length>1000000)throw Error('Invalid wellness');});
 var feedbackIds={};
 p.feedback.forEach(function(f){
   if(!validId(f.feedback_id)||(!validId(f.activity_id)&&!validId(f.workout_id))||!validTime(f.created_at)||![f.rpe,f.soreness,f.fatigue].every(function(v){return Number.isInteger(v)&&v>=0&&v<=10;})||typeof f.note!=='string'||f.note.length>2000)throw Error('Invalid feedback');
   var old=find(db,'Feedback','feedback_id',f.feedback_id), fingerprint=JSON.stringify(row('Feedback',f));
   if((old&&JSON.stringify(row('Feedback',old))!==fingerprint)||(feedbackIds[f.feedback_id]&&feedbackIds[f.feedback_id]!==fingerprint))throw Error('Feedback conflict');
   feedbackIds[f.feedback_id]=fingerprint;
 });
 p.matches.forEach(function(m){if(!validId(m.activity_id)||(m.workout_id!==''&&!validId(m.workout_id))||!validTime(m.updated_at))throw Error('Invalid match');});
 p.health=req.health===undefined?[]:req.health;
 var health=validateHealth(p.health,db);p.health_new=health.changes;p.health_ack=health.ack;
 return p;
}

// Health has its own reader/writer: other sheets keep their existing representation.
// Prefix every string, including the ID, with a Sheets literal marker. Native Sheets
// removes one marker on read; literal-only adapters retain it. The ID cannot itself
// contain an apostrophe, so it tells us whether to remove exactly one from each cell.
// This also preserves intentional leading apostrophes without conflating payloads.
function healthRow(obj) {
 return HEADERS.Health.map(function(k){var v=obj[k];return typeof v==='string'?"'"+v:safe(v);});
}
function healthRecords(db) {
 return records(db,'Health').map(function(obj){
   var literal=typeof obj.change_id==='string'&&obj.change_id.charAt(0)==="'";
   HEADERS.Health.forEach(function(k){if(literal&&typeof obj[k]==='string'&&obj[k].charAt(0)==="'")obj[k]=obj[k].slice(1);});
   ['date','name','note'].forEach(function(k){if(obj[k]===null)obj[k]='';});
   return obj;
 });
}
function healthFingerprint(obj) {return JSON.stringify(HEADERS.Health.map(function(k){return obj[k];}));}
function validHealth(h) {
 if(!h||typeof h!=='object'||Array.isArray(h)||typeof h.change_id!=='string'||!validId(h.change_id)||typeof h.record_id!=='string'||!validId(h.record_id)||['meal','favourite','weight','goal'].indexOf(h.kind)<0||typeof h.note!=='string'||h.note.length>1000||typeof h.deleted!=='boolean'||typeof h.updated_at!=='string'||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(h.updated_at)||isNaN(Date.parse(h.updated_at))||new Date(h.updated_at).toISOString()!==h.updated_at)return false;
 var food=h.kind==='meal'||h.kind==='favourite',dated=h.kind==='meal'||h.kind==='weight';
 if(dated?!validDate(h.date):h.date!=='')return false;
 if(food)return typeof h.name==='string'&&h.name.length>=1&&h.name.length<=120&&typeof h.calories_kcal==='number'&&isFinite(h.calories_kcal)&&h.calories_kcal>=0&&h.calories_kcal<=20000&&h.weight_kg===null&&(h.kind==='favourite'?h.portions===1:typeof h.portions==='number'&&isFinite(h.portions)&&h.portions>0&&h.portions<=100);
 return h.name===''&&h.calories_kcal===null&&h.portions===null&&typeof h.weight_kg==='number'&&isFinite(h.weight_kg)&&h.weight_kg>0&&h.weight_kg<=500&&h.record_id===(h.kind==='weight'?'weight:'+h.date:'goal-weight');
}
function validateHealth(changes,db) {
 if(!Array.isArray(changes)||changes.length>100)throw Error('Invalid health');
 changes.forEach(function(h){if(!validHealth(h))throw Error('Invalid health');});
 var fingerprints=new Map(),known=new Set(),ack=[],fresh=[];
 healthRecords(db).forEach(function(h){
   var fingerprint=healthFingerprint(h);
   if(fingerprints.has(h.change_id)&&fingerprints.get(h.change_id)!==fingerprint)throw Error('Health conflict');
   fingerprints.set(h.change_id,fingerprint);known.add(h.change_id);
 });
 changes.forEach(function(h){
   var fingerprint=healthFingerprint(h);
   if(fingerprints.has(h.change_id)&&fingerprints.get(h.change_id)!==fingerprint)throw Error('Health conflict');
   if(ack.indexOf(h.change_id)<0)ack.push(h.change_id);
   if(!known.has(h.change_id)){fresh.push(h);known.add(h.change_id);}
   fingerprints.set(h.change_id,fingerprint);
 });
 return {changes:fresh,ack:ack};
}
function appendHealth(db,changes) {
 if(!changes.length)return;
 var s=sheet(db,'Health'),start=s.getLastRow()+1,required=start+changes.length-1,capacity=s.getMaxRows();
 if(required>capacity)s.insertRowsAfter(capacity,required-capacity);
 s.getRange(start,1,changes.length,HEADERS.Health.length).setValues(changes.map(healthRow));
}
function currentHealth(changes) {
 var latest=new Map();
 changes.forEach(function(h){
   var old=latest.get(h.record_id),time=Date.parse(h.updated_at),oldTime=old?Date.parse(old.updated_at):0;
   if(!old||time>oldTime||(time===oldTime&&h.change_id>old.change_id))latest.set(h.record_id,h);
 });
 return Array.from(latest.values());
}

function archive(db,props,id,json) {
 var digest=hash(json), record=id+':'+digest;
 if(find(db,'Archive','record_id',record))return;
 var folder=DriveApp.getFolderById(props.getProperty('ARCHIVE_FOLDER_ID')),name=id+'-'+digest+'.json';
 var existing=folder.getFilesByName(name), file=existing.hasNext()?existing.next():folder.createFile(Utilities.newBlob(json,'application/json',name));
 append(db,'Archive',{record_id:record,sha256:digest,drive_file_id:file.getId(),created_at:new Date().toISOString()});
}
function snapshot(db) {
 var activities=records(db,'Activities').sort(function(a,b){return String(b.start_utc).localeCompare(String(a.start_utc));}).slice(0,500);
 return {events:records(db,'Events'),plan:records(db,'Plan'),activities:activities,wellness:records(db,'Wellness').sort(function(a,b){return String(b.date).localeCompare(String(a.date));}).slice(0,60),feedback:records(db,'Feedback'),matches:records(db,'Matches'),health:currentHealth(healthRecords(db)),server_time:new Date().toISOString()};
}

// Run once from the editor after authorisation. Only counts are logged; never credentials or health records.
function verifySetup() {
 var props=PropertiesService.getScriptProperties(), secret=props.getProperty('BRIDGE_SECRET');
 if(!secret || secret.length<32)throw Error('Set BRIDGE_SECRET to at least 32 characters in Script Properties.');
 var db=SpreadsheetApp.openById(props.getProperty('SPREADSHEET_ID'));
 Object.keys(HEADERS).forEach(function(name){sheet(db,name);});
 var ready=!!DriveApp.getFolderById(props.getProperty('ARCHIVE_FOLDER_ID')).getId();
 var result={ok:ready,events:records(db,'Events').length,plan_revisions:records(db,'Plan').length,archive_ready:ready};
 console.log(JSON.stringify(result));return result;
}

// Editor-only, read-only Health setup check. It returns no individual records.
function verifyHealthSetup() {
 var props=PropertiesService.getScriptProperties(),db=SpreadsheetApp.openById(props.getProperty('SPREADSHEET_ID'));
 var changes=healthRecords(db);
 return {ok:true,health_changes:changes.length,health_records:currentHealth(changes).length};
}
