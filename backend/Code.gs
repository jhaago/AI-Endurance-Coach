/* Single-user bridge. Credentials belong ONLY in Script Properties. Never in this source. */
var HEADERS = {
  Events: ['event_id','name','date','primary_yards','primary_km','stretch_yards','stretch_km','runs_per_week','optional_fourth','timezone','active'],
  Plan: ['workout_id','revision','event_id','date','type','title','duration_min','intensity','optional','status','author','created_at','reason'],
  Activities: ['activity_id','name','start_utc','type','distance_m','duration_s','pace_s_km','average_hr','elevation_m','cadence_spm','source_hash','updated_at'],
  Wellness: ['date','sleep_hours','sleep_score','hrv_ms','resting_hr','coverage_note','source_hash','updated_at'],
  Feedback: ['feedback_id','activity_id','workout_id','created_at','rpe','soreness','fatigue','note'],
  Matches: ['activity_id','workout_id','updated_at'],
  Archive: ['record_id','sha256','drive_file_id','created_at']
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
    }
    return output({ok:true,ack_feedback:payload.feedback.map(function(f){return f.feedback_id;}),snapshot:snapshot(db)});
  } catch(err) {
    // No stack, upstream response, tokens, or arbitrary exception text in the public response.
    var allowed=['Unauthorized','Invalid request','Invalid action','Invalid activity','Invalid wellness','Invalid feedback','Feedback conflict','Invalid match','Schema mismatch'];
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
 var s=sheet(db,name), rows=s.getDataRange().getValues(), col=HEADERS[name].indexOf(key);
 for(var i=1;i<rows.length;i++)if(String(rows[i][col])===String(obj[key])){s.getRange(i+1,1,1,HEADERS[name].length).setValues([row(name,obj)]);return;}
 s.appendRow(row(name,obj));
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
 return p;
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
 return {events:records(db,'Events'),plan:records(db,'Plan'),activities:activities,wellness:records(db,'Wellness').sort(function(a,b){return String(b.date).localeCompare(String(a.date));}).slice(0,60),feedback:records(db,'Feedback'),matches:records(db,'Matches'),server_time:new Date().toISOString()};
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
