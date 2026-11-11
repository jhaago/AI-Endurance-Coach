/* Single-user bridge. Credentials belong ONLY in Script Properties. Never in this source. */
var HEADERS = {
  RoutePlanner: ['change_id','record_id','kind','payload_json','deleted','updated_at'],
  Events: ['event_id','name','date','primary_yards','primary_km','stretch_yards','stretch_km','runs_per_week','optional_fourth','timezone','active','primary_confidence_pct','stretch_confidence_pct','confidence_as_of','confidence_quality','confidence_reason'],
  Plan: ['workout_id','revision','event_id','date','type','title','duration_min','intensity','optional','status','author','created_at','reason'],
  Activities: ['activity_id','name','start_utc','type','distance_m','duration_s','pace_s_km','average_hr','elevation_m','cadence_spm','source_hash','updated_at'],
  Wellness: ['date','sleep_hours','sleep_score','hrv_ms','resting_hr','coverage_note','source_hash','updated_at'],
  Feedback: ['feedback_id','activity_id','workout_id','created_at','rpe','soreness','fatigue','note'],
  Matches: ['activity_id','workout_id','updated_at'],
  Archive: ['record_id','sha256','drive_file_id','created_at'],
  Health: ['change_id','record_id','kind','date','name','calories_kcal','portions','weight_kg','note','deleted','updated_at','meal_type','protein_g','carbohydrate_g','fat_g'],
  WorkoutStatus: ['change_id','workout_id','status','actual_duration_min','note','updated_at'],
  NutritionTargets: ['target_id','revision','scope','date','calories_kcal','protein_g','carbohydrate_g','fat_g','weight_goal_kg','weight_goal_date','weight_change_kg_per_week','training_type','exercise_included','source','created_at','reason','status'],
  DietQuality: ['assessment_id','revision','date','status','score','fruit_veg_points','fibre_points','protein_calcium_points','fat_quality_points','discretionary_points','confidence','summary','strength','improvement','fruit_veg_reason','fibre_reason','protein_calcium_reason','fat_quality_reason','discretionary_reason','food_change_ids','source','assessed_at','rubric_version']
};

function doGet() { return output({ok:false,error:'Use authenticated POST',bridge_version:'0.8.0'}); }
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
      appendLedger(db,'WorkoutStatus',payload.workout_status_new);
      appendLedger(db,'RoutePlanner',payload.routes_new);
    }
    return output({ok:true,ack_routes:req.action==='sync'?payload.routes_ack:[],ack_feedback:payload.feedback.map(function(f){return f.feedback_id;}),ack_health:req.action==='sync'?payload.health_ack:[],ack_workout_status:req.action==='sync'?payload.workout_status_ack:[],snapshot:snapshot(db)});
  } catch(err) {
    // No stack, upstream response, tokens, or arbitrary exception text in the public response.
    var allowed=['Unauthorized','Invalid request','Invalid action','Invalid activity','Invalid wellness','Invalid feedback','Feedback conflict','Invalid match','Invalid health','Health conflict','Invalid workout status','Workout status conflict','Schema mismatch','Invalid route change','Route conflict'];
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
 return values.slice(1).filter(function(r){return r[0]!=='';}).map(function(r){return recordFromRow(name,r,db);});
}
function recordFromRow(name,r,db) {
 var obj={};HEADERS[name].forEach(function(k,i){var v=r[i];obj[k]=(v===''||v===undefined)?null:(k==='date'||k==='weight_goal_date'||k==='confidence_as_of')?calendarDay(v,db):v instanceof Date?v.toISOString():v;});return obj;
}
function calendarDay(value,db) {
 if(value instanceof Date)return Utilities.formatDate(value,db.getSpreadsheetTimeZone(),'yyyy-MM-dd');
 if(typeof value==='string'&&value.charAt(0)==="'"&&validDate(value.slice(1)))return value.slice(1);
 return value;
}
function safe(value){if(value===null||value===undefined)return '';if(typeof value==='string'&&/^[=+@\-]/.test(value))return "'"+value;return value;}
function row(name,obj){return HEADERS[name].map(function(k){return (k==='date'||k==='weight_goal_date'||k==='confidence_as_of')&&validDate(obj[k])?"'"+obj[k]:safe(obj[k]);});}
function append(db,name,obj){sheet(db,name).appendRow(row(name,obj));}
function find(db,name,key,id){return (name==='Wellness'?currentWellness(db):records(db,name)).find(function(r){return String(r[key])===String(id);});}
function upsert(db,name,key,obj) {
 if(name==='Wellness')obj=preserveWellness(obj,find(db,'Wellness',key,obj[key]));
 var s=sheet(db,name), rows=s.getDataRange().getValues(), col=HEADERS[name].indexOf(key);
 var match=-1;
 for(var i=1;i<rows.length;i++)if(String(key==='date'?calendarDay(rows[i][col],db):rows[i][col])===String(obj[key])){match=i;if(name!=='Wellness')break;}
 if(match>=0){s.getRange(match+1,1,1,HEADERS[name].length).setValues([row(name,obj)]);return;}
 s.appendRow(row(name,obj));
}
function preserveWellness(fresh,previous) {
 if(!previous)return fresh;
 var result=Object.assign({},fresh),retained=false;
 ['sleep_hours','sleep_score','hrv_ms','resting_hr'].forEach(function(k){if((result[k]===null||result[k]===undefined)&&typeof previous[k]==='number'&&isFinite(previous[k])){result[k]=previous[k];retained=true;}});
 if(retained)result.coverage_note=(result.coverage_note||'')+'\nPrevious measured values retained where the latest values are unavailable.';
 return result;
}
function currentWellness(db) {
 var latest=new Map();
 records(db,'Wellness').map(function(w,i){return {value:w,index:i};}).sort(function(a,b){return (Date.parse(a.value.updated_at)||0)-(Date.parse(b.value.updated_at)||0)||a.index-b.index;}).forEach(function(entry){
   var w=entry.value;latest.set(w.date,preserveWellness(w,latest.get(w.date)));
 });
 return Array.from(latest.values()).sort(function(a,b){return String(b.date).localeCompare(String(a.date));});
}
function validId(value){return (typeof value==='string'||typeof value==='number')&&/^[a-zA-Z0-9_:.\-]{1,100}$/.test(String(value));}
function validDate(value){return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)&&!isNaN(Date.parse(value))&&new Date(value).toISOString().slice(0,10)===value;}
function validTime(value){return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(value)&&value.endsWith('Z')&&!isNaN(Date.parse(value));}
function finiteOrNull(value,min,max){return value===null||value===undefined||(typeof value==='number'&&isFinite(value)&&value>=min&&value<=max);}
function validate(req,db) {
 if(Object.prototype.hasOwnProperty.call(req,'nutrition_targets')||Object.prototype.hasOwnProperty.call(req,'diet_quality'))throw Error('Invalid request');
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
 p.workout_status=req.workout_status===undefined?[]:req.workout_status;
 var statuses=validateLedger(p.workout_status,db,'WorkoutStatus',workoutStatusRecords,validWorkoutStatus,'Invalid workout status','Workout status conflict');
 p.workout_status_new=statuses.changes;p.workout_status_ack=statuses.ack;
 p.routes_new=[];p.routes_ack=[];
 if(req.route_changes!==undefined){var routes=validateLedger(req.route_changes,db,'RoutePlanner',routeRecords,validRouteChange,'Invalid route change','Route conflict');p.routes_new=routes.changes;p.routes_ack=routes.ack;}
 return p;
}

// Immutable ledgers prefix every string with a Sheets literal marker. Native
// Sheets removes one marker; literal-only adapters retain it. Since valid IDs
// cannot contain apostrophes, change_id tells us whether to remove one marker
// from every text cell. Intentional leading apostrophes remain distinct.
function literalRow(name,obj) {
 return HEADERS[name].map(function(k){var v=obj[k];return typeof v==='string'?"'"+v:safe(v);});
}
function decodeLiterals(obj,name,key) {
 if(typeof obj[key]==='string'&&obj[key].charAt(0)==="'")HEADERS[name].forEach(function(k){if(typeof obj[k]==='string'&&obj[k].charAt(0)==="'")obj[k]=obj[k].slice(1);});
 return obj;
}
function literalRecords(db,name) {return records(db,name).map(function(obj){return decodeLiterals(obj,name,'change_id');});}
function canonicalHealth(h) {
 if(!h||typeof h!=='object'||Array.isArray(h))return h;
 var result=Object.assign({},h);
 if(result.meal_type===undefined||result.meal_type==='')result.meal_type=result.kind==='meal'?'other':'';
 ['protein_g','carbohydrate_g','fat_g'].forEach(function(k){if(result[k]===undefined)result[k]=null;});
 return result;
}
function healthRow(obj) {return literalRow('Health',canonicalHealth(obj));}
function healthRecords(db) {
 return literalRecords(db,'Health').map(function(obj){
   ['date','name','note'].forEach(function(k){if(obj[k]===null)obj[k]='';});
   if(obj.meal_type===null)obj.meal_type='';
   return canonicalHealth(obj);
 });
}
function workoutStatusRecords(db) {
 return literalRecords(db,'WorkoutStatus').map(function(obj){if(obj.note===null)obj.note='';return obj;});
}
function canonicalTime(value) {
 return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value)&&!isNaN(Date.parse(value))&&new Date(value).toISOString()===value;
}
function validChange(h) {return !!h&&typeof h==='object'&&!Array.isArray(h)&&typeof h.change_id==='string'&&validId(h.change_id)&&typeof h.note==='string'&&h.note.length<=1000&&canonicalTime(h.updated_at);}
function validHealth(h) {
 if(!validChange(h)||typeof h.record_id!=='string'||!validId(h.record_id)||['meal','favourite','weight','goal'].indexOf(h.kind)<0||typeof h.deleted!=='boolean')return false;
 var food=h.kind==='meal'||h.kind==='favourite',dated=h.kind==='meal'||h.kind==='weight';
 if(dated?!validDate(h.date):h.date!=='')return false;
 if(h.kind==='meal'?['breakfast','lunch','dinner','snacks','other'].indexOf(h.meal_type)<0:h.meal_type!=='')return false;
 if(!['protein_g','carbohydrate_g','fat_g'].every(function(k){return h[k]===null||(food&&typeof h[k]==='number'&&isFinite(h[k])&&h[k]>=0&&h[k]<=5000);}))return false;
 if(food)return typeof h.name==='string'&&h.name.length>=1&&h.name.length<=120&&typeof h.calories_kcal==='number'&&isFinite(h.calories_kcal)&&h.calories_kcal>=0&&h.calories_kcal<=20000&&h.weight_kg===null&&(h.kind==='favourite'?h.portions===1:typeof h.portions==='number'&&isFinite(h.portions)&&h.portions>0&&h.portions<=100);
 return h.name===''&&h.calories_kcal===null&&h.portions===null&&typeof h.weight_kg==='number'&&isFinite(h.weight_kg)&&h.weight_kg>0&&h.weight_kg<=500&&h.record_id===(h.kind==='weight'?'weight:'+h.date:'goal-weight');
}
function validWorkoutStatus(h) {
 return validChange(h)&&typeof h.workout_id==='string'&&validId(h.workout_id)&&['completed','skipped','scheduled'].indexOf(h.status)>=0&&(h.actual_duration_min===null||(h.status==='completed'&&typeof h.actual_duration_min==='number'&&isFinite(h.actual_duration_min)&&h.actual_duration_min>=0&&h.actual_duration_min<=10080));
}
function validateHealth(changes,db) {
 if(!Array.isArray(changes)||changes.length>100)throw Error('Invalid health');
 return validateLedger(changes.map(canonicalHealth),db,'Health',healthRecords,validHealth,'Invalid health','Health conflict');
}
function validateLedger(changes,db,name,read,isValid,invalid,conflict) {
 if(!Array.isArray(changes)||changes.length>100)throw Error(invalid);
 changes.forEach(function(h){if(!isValid(h))throw Error(invalid);});
 var fingerprints=new Map(),known=new Set(),ack=[],fresh=[];
 function fingerprint(h){return JSON.stringify(HEADERS[name].map(function(k){return h[k];}));}
 read(db).forEach(function(h){
   var value=fingerprint(h);
   if(fingerprints.has(h.change_id)&&fingerprints.get(h.change_id)!==value)throw Error(conflict);
   fingerprints.set(h.change_id,value);known.add(h.change_id);
 });
 changes.forEach(function(h){
   var value=fingerprint(h);
   if(fingerprints.has(h.change_id)&&fingerprints.get(h.change_id)!==value)throw Error(conflict);
   if(ack.indexOf(h.change_id)<0)ack.push(h.change_id);
   if(!known.has(h.change_id)){fresh.push(h);known.add(h.change_id);}
   fingerprints.set(h.change_id,value);
 });
 return {changes:fresh,ack:ack};
}
function appendHealth(db,changes) {appendLedger(db,'Health',changes);}
function appendLedger(db,name,changes) {
 if(!changes.length)return;
 var s=sheet(db,name),start=s.getLastRow()+1,required=start+changes.length-1,capacity=s.getMaxRows();
 if(required>capacity)s.insertRowsAfter(capacity,required-capacity);
 s.getRange(start,1,changes.length,HEADERS[name].length).setValues(changes.map(function(h){return literalRow(name,h);}));
}
function currentHealth(changes) {return currentChanges(changes,'record_id');}
function currentChanges(changes,key) {
 var latest=new Map();
 changes.forEach(function(h){
   var old=latest.get(h[key]),time=Date.parse(h.updated_at),oldTime=old?Date.parse(old.updated_at):0;
   if(!old||time>oldTime||(time===oldTime&&h.change_id>old.change_id))latest.set(h[key],h);
 });
 return Array.from(latest.values());
}
function validNutritionTarget(t) {
 if(!t||['baseline','daily'].indexOf(t.scope)<0||!validDate(t.date)||t.target_id!==(t.scope==='baseline'?'baseline-':'nutrition-')+t.date||!Number.isSafeInteger(t.revision)||t.revision<1||t.revision>2147483647||typeof t.calories_kcal!=='number'||!isFinite(t.calories_kcal)||t.calories_kcal<=0||t.calories_kcal>20000)return false;
 if(!['protein_g','carbohydrate_g','fat_g'].every(function(k){return t[k]===null||(typeof t[k]==='number'&&isFinite(t[k])&&t[k]>=0&&t[k]<=5000);}))return false;
 return (t.weight_goal_kg===null||(typeof t.weight_goal_kg==='number'&&isFinite(t.weight_goal_kg)&&t.weight_goal_kg>0&&t.weight_goal_kg<=500))&&(t.weight_goal_date===''||validDate(t.weight_goal_date))&&(t.weight_change_kg_per_week===null||(typeof t.weight_change_kg_per_week==='number'&&isFinite(t.weight_change_kg_per_week)&&t.weight_change_kg_per_week>=-5&&t.weight_change_kg_per_week<=5))&&typeof t.training_type==='string'&&t.training_type.length<=120&&typeof t.exercise_included==='boolean'&&typeof t.source==='string'&&t.source.trim().length>0&&t.source.length<=120&&canonicalTime(t.created_at)&&typeof t.reason==='string'&&t.reason.length<=2000&&['approved','draft','cancelled'].indexOf(t.status)>=0;
}
function nutritionTargets(db) {
 // A row with a blank ID but other content is malformed, not an omitted record.
 var values=sheet(db,'NutritionTargets').getDataRange().getValues(),unique=new Map();
 values.slice(1).filter(function(r){return r.some(function(v){return v!==''&&v!==null&&v!==undefined;});}).forEach(function(r){
   var t=decodeLiterals(recordFromRow('NutritionTargets',r,db),'NutritionTargets','target_id');
   ['weight_goal_date','training_type','reason'].forEach(function(k){if(t[k]===null)t[k]='';});
   if(!validNutritionTarget(t))throw Error('Invalid nutrition targets');
   var key=t.target_id+':'+t.revision,old=unique.get(key);
   if(old&&JSON.stringify(HEADERS.NutritionTargets.map(function(k){return old[k];}))!==JSON.stringify(HEADERS.NutritionTargets.map(function(k){return t[k];})))throw Error('Nutrition targets conflict');
   if(!old)unique.set(key,t);
 });
 return Array.from(unique.values());
}

// Coach-owned estimates are a separate optional ledger. Never accept phone writes.
function validDietQuality(r) {
 if(!r||typeof r!=='object'||r.assessment_id!=='diet-quality-'+r.date||!validDate(r.date)||!Number.isInteger(r.revision)||r.revision<1||r.revision>2147483647||['provisional','complete','withdrawn'].indexOf(r.status)<0||['low','medium','high'].indexOf(r.confidence)<0||r.rubric_version!=='dq-v1'||!canonicalTime(r.assessed_at))return false;
 var components=['fruit_veg','fibre','protein_calcium','fat_quality','discretionary'],maximums=[30,20,20,15,15],sum=0;
 for(var i=0;i<components.length;i++){var points=r[components[i]+'_points'],reason=r[components[i]+'_reason'];if(!Number.isInteger(points)||points<0||points>maximums[i]||typeof reason!=='string'||!reason.trim()||reason.length>500)return false;sum+=points;}
 if(!Number.isInteger(r.score)||r.score!==sum)return false;
 if(!['summary','strength','improvement','source'].every(function(k){return typeof r[k]==='string'&&r[k].trim().length>0&&r[k].length<=(k==='source'?120:k==='summary'?1000:500);}))return false;
 if(typeof r.food_change_ids!=='string'||!r.food_change_ids||r.food_change_ids.length>10100)return false;
 var ids=r.food_change_ids.split('|');return ids.length<=100&&ids.every(function(id,i){return validId(id)&&(i===0||id>ids[i-1]);});
}
function dietQuality(db) {
 var values=sheet(db,'DietQuality').getDataRange().getValues(),unique=new Map();
 values.slice(1).filter(function(r){return r.some(function(v){return v!==''&&v!==null&&v!==undefined;});}).forEach(function(row){
  var r=decodeLiterals(recordFromRow('DietQuality',row,db),'DietQuality','assessment_id');if(!validDietQuality(r))throw Error('Invalid diet quality');
  var key=r.assessment_id+':'+r.revision,old=unique.get(key);
  if(old&&JSON.stringify(HEADERS.DietQuality.map(function(k){return old[k];}))!==JSON.stringify(HEADERS.DietQuality.map(function(k){return r[k];})))throw Error('Diet quality conflict');
  if(!old)unique.set(key,r);
 });return Array.from(unique.values());
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
 var result={bridge_version:'0.8.0',health_schema_version:2,events:records(db,'Events'),plan:records(db,'Plan'),activities:activities,wellness:currentWellness(db).slice(0,60),feedback:records(db,'Feedback'),matches:records(db,'Matches'),health:currentHealth(healthRecords(db)),workout_status:currentChanges(workoutStatusRecords(db),'workout_id'),server_time:new Date().toISOString()};
 try {result.route_changes=currentChanges(routeRecords(db),'record_id');result.route_schema_version=1;}catch(err){result.route_error='Routes saved on phone; update or check the route planner bridge';}
 try {result.nutrition_targets=nutritionTargets(db);}catch(err){result.nutrition_error='Nutrition targets unavailable; ask your coach to check setup';}
 try {result.diet_quality=dietQuality(db);}catch(err){result.diet_quality_error='Diet quality assessments unavailable; ask your coach to check setup';}
 return result;
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


// Editor-only schema/count check. No schema mutation or individual records.
function verifyCoachSetup() {
 var props=PropertiesService.getScriptProperties(),db=SpreadsheetApp.openById(props.getProperty('SPREADSHEET_ID'));
 var health=healthRecords(db),statuses=workoutStatusRecords(db),ready=true,nutrition=[];
 try {nutrition=nutritionTargets(db);}catch(err){ready=false;}
 var result={ok:ready,health_changes:health.length,health_records:currentHealth(health).length,workout_status_changes:statuses.length,workout_status_records:currentChanges(statuses,'workout_id').length,nutrition_targets_ready:ready,nutrition_revisions:nutrition.length};console.log(JSON.stringify(result));return result;
}


// Route planner uses a separate immutable ledger, including tombstones.
function routeRecords(db) {var rows=literalRecords(db,'RoutePlanner');if(!rows.every(validRouteChange))throw Error('Invalid route change');return rows;}
function routeNumber(o,k,min,max){return !!o&&typeof o[k]==='number'&&isFinite(o[k])&&o[k]>=min&&o[k]<=max;}
function routeText(o,k,min,max){return !!o&&typeof o[k]==='string'&&o[k].length>=min&&o[k].length<=max;}
function routePoint(p){return Array.isArray(p)&&p.length>=2&&p.length<=3&&typeof p[0]==='number'&&isFinite(p[0])&&Math.abs(p[0])<=85.05112878&&typeof p[1]==='number'&&isFinite(p[1])&&Math.abs(p[1])<=180&&(p.length<3||p[2]===null||typeof p[2]==='number'&&isFinite(p[2])&&p[2]>=-500&&p[2]<=10000);}
function validRoute(r){
 if(!r||!routeText(r,'name',1,120)||['imported','established','generated','drawn'].indexOf(r.source)<0||!routeText(r,'attribution',1,500)||!routeText(r,'source_url',0,1000)||r.source_url!==''&&!/^https:\/\/www\.openstreetmap\.org\/relation\/[0-9]+$/.test(r.source_url)||typeof r.favourite!=='boolean'||typeof r.simplified!=='boolean'||['unknown','road','path','trail','mixed'].indexOf(r.terrain)<0)return false;
 if(!Array.isArray(r.points)||r.points.length<2||r.points.length>1000||!r.points.every(routePoint)||!Array.isArray(r.waypoints)||r.waypoints.length>24||!r.waypoints.every(routePoint)||!routeNumber(r,'distance_m',0.01,1000000)||!(r.ascent_m===null||routeNumber(r,'ascent_m',0,100000)))return false;
 return r.profile===undefined||Array.isArray(r.profile)&&r.profile.length<=200&&r.profile.every(routePoint);
}
function validRouteRequirements(r){return !!r&&routeNumber(r,'target_km',0.1,200)&&routeNumber(r,'radius_km',0.1,100)&&['any','road','path','trail'].indexOf(r.terrain)>=0&&['any','flat','rolling','hilly'].indexOf(r.climbing)>=0&&['any','loop','out_back','point_to_point'].indexOf(r.shape)>=0&&(r.start===undefined||routePoint(r.start))&&(r.duration_min===undefined||routeNumber(r,'duration_min',1,1440));}
function validRouteChange(c){
 if(!c||typeof c.change_id!=='string'||!validId(c.change_id)||typeof c.record_id!=='string'||!validId(c.record_id)||typeof c.deleted!=='boolean'||!canonicalTime(c.updated_at)||!routeText(c,'payload_json',2,45000))return false;
 try{var p=JSON.parse(c.payload_json);if(c.kind==='route')return c.record_id.indexOf('route:')===0&&validRoute(p);if(c.kind==='attachment')return validId(p.workout_id)&&c.record_id==='attachment:'+p.workout_id&&validRoute(p.route)&&validRouteRequirements(p.requirements);if(c.kind==='preference')return c.record_id.indexOf('preference:')===0&&validRouteRequirements(p);return false;}catch(e){return false;}
}
// Editor-only, additive migration. No existing worksheet or personal row is changed.
function setupRoutePlanner(){
 var props=PropertiesService.getScriptProperties(),db=SpreadsheetApp.openById(props.getProperty('SPREADSHEET_ID'));
 if(!db.getSheetByName('RoutePlanner'))db.insertSheet('RoutePlanner').getRange(1,1,1,HEADERS.RoutePlanner.length).setValues([HEADERS.RoutePlanner]);
 sheet(db,'RoutePlanner');return {ok:true,route_schema_version:1};
}
