package au.com.endurancecoach;

import android.content.Context;
import org.json.*;
import au.com.endurancecoach.core.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

final class CoachRepository implements AutoCloseable {
 private static final ReentrantLock SYNC=new ReentrantLock();
 final LocalStore store; final Vault vault;private final Context context;private final Transport transport=new HttpsTransport();
 CoachRepository(Context context){this.context=context.getApplicationContext();store=new LocalStore(context);vault=new Vault(context);}
 static JSONArray array(JSONObject object,String key){JSONArray a=object.optJSONArray(key);return a==null?new JSONArray():a;}
 JSONObject snapshot() throws Exception {
  JSONObject s=store.get("snapshot");
  if(!s.has("events")){
   JSONObject event=new JSONObject().put("event_id","herdys-2027").put("name","Herdy's Frontyard Ultra").put("date","2027-03-12").put("primary_yards",10).put("primary_km",67.06).put("stretch_yards",12).put("stretch_km",80.47).put("runs_per_week",3).put("active",true);
   s.put("events",new JSONArray().put(event)).put("plan",new JSONArray());
  }
  s.put("activities",merge(array(s,"activities"),array(store.get("local"),"activities"),"activity_id"));
  s.put("wellness",merge(array(s,"wellness"),array(store.get("local"),"wellness"),"date"));return s;
 }
 JSONObject overrides(JSONObject s) throws Exception {
  JSONObject o=new JSONObject();JSONArray rows=merge(array(s,"matches"),store.pending("match"),"activity_id");for(int i=0;i<rows.length();i++){JSONObject m=rows.getJSONObject(i);o.put(m.getString("activity_id"),m.optString("workout_id",""));}return o;
 }
 static JSONArray merge(JSONArray first,JSONArray second,String key) throws Exception {
  LinkedHashMap<String,JSONObject> map=new LinkedHashMap<>();for(JSONArray a:new JSONArray[]{first,second})for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);map.put(v.getString(key),v);}return new JSONArray(new ArrayList<>(map.values()));
 }
 String sync(boolean garmin) throws Exception {
  if(!SYNC.tryLock())return "Sync already in progress";
  try{
   ArrayList<String> warnings=new ArrayList<>();
   if(garmin && !vault.get("garmin").isEmpty()){
    try{importGarmin(warnings);store.put("status",store.get("status").put("garmin_message",warnings.isEmpty()?"Up to date":String.join(" · ",warnings)));warnings.clear();}
    catch(Exception e){String error=e instanceof IOException?e.getMessage():"Garmin import incomplete; retry sync";store.put("status",store.get("status").put("garmin_message",error).put("message",error));throw e;}
   }
   String bridge=vault.get("bridge");
   if(!bridge.isEmpty()){
    BridgeClient client=new BridgeClient(transport,bridge,vault.get("secret"));
    syncCloud(client);
    store.put("status",store.get("status").put("cloud_sync",Instant.now().toString()));
   }else warnings.add("Training database not connected; data stays on this phone");
   if(garmin && vault.get("garmin").isEmpty())warnings.add("Garmin key not configured");
   String message=SyncStatus.message(store.get("status").optString("garmin_message",""),warnings);
   store.put("status",store.get("status").put("message",message));return message;
  }finally{SYNC.unlock();}
 }
 // Each successful upload already returns a full snapshot. An idle refresh still needs one read.
 void syncCloud(BridgeClient client) throws Exception {
  JSONObject local=store.get("local");JSONArray feedback=store.pending("feedback"),matches=store.pending("match");
  int requests=uploadChanged(client,"activities",array(local,"activities"),"activity_id",10)
    +uploadChanged(client,"wellness",array(local,"wellness"),"date",3);
  for(int start=0;start<feedback.length();start+=30){send(client,new JSONObject().put("action","sync").put("feedback",slice(feedback,start,30)));requests++;}
  for(int start=0;start<matches.length();start+=30){send(client,new JSONObject().put("action","sync").put("matches",slice(matches,start,30)));requests++;}
  if(requests==0)send(client,new JSONObject().put("action","snapshot"));
 }
 private void send(BridgeClient client,JSONObject request) throws Exception {
  JSONObject result=client.call(request);JSONArray ack=array(result,"ack_feedback");
  store.acceptSnapshot(result.getJSONObject("snapshot"),ack,array(request,"matches"));
 }
 private static JSONArray slice(JSONArray source,int start,int size) throws Exception {JSONArray out=new JSONArray();for(int i=start;i<Math.min(source.length(),start+size);i++)out.put(source.get(i));return out;}
 private int uploadChanged(BridgeClient client,String kind,JSONArray rows,String key,int batchSize) throws Exception {
  JSONObject sent=store.get("uploaded:"+kind);JSONArray changed=new JSONArray();
  for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!hash(row.toString().getBytes(StandardCharsets.UTF_8)).equals(sent.optString(row.getString(key))))changed.put(row);}
  for(int start=0;start<changed.length();start+=batchSize){JSONArray batch=slice(changed,start,batchSize);send(client,new JSONObject().put("action","sync").put(kind,batch));for(int i=0;i<batch.length();i++){JSONObject row=batch.getJSONObject(i);sent.put(row.getString(key),hash(row.toString().getBytes(StandardCharsets.UTF_8)));}store.put("uploaded:"+kind,sent);}
  return (changed.length()+batchSize-1)/batchSize;
 }
 private void importGarmin(List<String> warnings) throws Exception {
  GarminClient client=new GarminClient(transport,vault.get("garmin"),()->System.currentTimeMillis()/1000);
  LocalDate today=LocalDate.now(PlanEngine.ZONE);JSONObject local=store.get("local");String last=store.get("status").optString("garmin_sync","");
  LocalDate detailStart=SyncWindow.start(today,last,6,30);
  LocalDate importStart=WeeklyProgress.importStart(today,last,store.get("status").optString("activity_start",""));
  // Include the previous date as a guard around Garmin/account versus UTC midnight boundaries.
  JSONArray list=client.activities(importStart.minusDays(1).toString(),today.toString(),50);
  JSONArray cloudActivities=array(store.get("snapshot"),"activities");
  JSONObject detailTimes=store.get("activity-detail-times");
  for(int i=0;i<list.length();i++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   JSONObject raw=list.getJSONObject(i);String id=String.valueOf(raw.get("activityId"));if(!id.matches("[0-9]+"))throw new IOException("Garmin returned an invalid activity reference; retry sync.");
   JSONObject summary=null;try{summary=Metrics.activity(raw);}catch(RuntimeException incomplete){/* Detail may supply fields absent from the list summary. */}
   if(summary!=null&&LocalDate.parse(PlanEngine.day(summary)).isBefore(detailStart)){
    // Older chart history needs only the original list summary; preserve any richer existing record.
    if(find(array(local,"activities"),"activity_id",id)!=null||find(cloudActivities,"activity_id",id)!=null)continue;
    String original=new JSONObject().put("list",raw).toString();
    archive("activity-"+id,original.getBytes(StandardCharsets.UTF_8),"json");summary.put("raw_json",original);
    local.put("activities",merge(array(local,"activities"),new JSONArray().put(summary),"activity_id"));store.put("local",local);continue;
   }
   JSONObject detail=new JSONObject(),previousOriginal=new JSONObject();long fetchedAt=System.currentTimeMillis()/1000;boolean refreshed=false;
   JSONObject previous=find(array(local,"activities"),"activity_id",id);
   if(previous!=null)try{previousOriginal=new JSONObject(previous.optString("raw_json","{}"));}catch(Exception invalid){/* Refetch invalid cache entries. */}
   if(ActivityCache.reusable(raw,previousOriginal,detailTimes.optLong(id,0),fetchedAt))detail=previousOriginal.getJSONObject("detail");
   else try{Object response=client.json("/activity-service/activity/"+id);if(response instanceof JSONObject){detail=(JSONObject)response;refreshed=detail.length()>0;}}catch(IOException e){warnings.add("Activity detail unavailable for "+id);}
   JSONObject combined=new JSONObject(raw.toString());for(Iterator<String> keys=detail.keys();keys.hasNext();){String k=keys.next();combined.put(k,detail.get(k));}
   JSONObject activity=Metrics.activity(combined);String original=new JSONObject().put("list",raw).put("detail",detail).toString();
   archive("activity-"+id,original.getBytes(StandardCharsets.UTF_8),"json");activity.put("raw_json",original);
   // Persist each activity before a later network failure can interrupt this import.
   local.put("activities",merge(array(local,"activities"),new JSONArray().put(activity),"activity_id"));store.put("local",local);
   if(refreshed){detailTimes.put(id,fetchedAt);store.put("activity-detail-times",detailTimes);}
   File fit=new File(context.getFilesDir(),"originals/activity-"+id+"-"+hash(original.getBytes(StandardCharsets.UTF_8))+".zip");
   if(!fit.exists())try{
    byte[] bytes=client.get("/download-service/files/activity/"+id);JSONArray samples=FitRecords.decodeZip(bytes,activity.optString("type").contains("running"));
    atomic(fit.toPath(),bytes);store.put("samples:"+id,new JSONObject().put("records",samples));
   }catch(Exception e){warnings.add("Original FIT unavailable for "+id+"; will retry");}
  }
  // Commit coverage only after the complete paginated list has been processed and persisted.
  JSONObject activityStatus=store.get("status");String oldStart=activityStatus.optString("activity_start","");
  if(oldStart.isEmpty()||importStart.isBefore(LocalDate.parse(oldStart)))oldStart=importStart.toString();
  store.put("status",activityStatus.put("activity_start",oldStart).put("activity_end",today.toString()));
  String recoveryLast=array(local,"wellness").length()==0?"":last;
  TreeSet<String> dates=new TreeSet<>();for(LocalDate d=SyncWindow.start(today,recoveryLast,2,7);!d.isAfter(today);d=d.plusDays(1))dates.add(d.toString());
  JSONArray retryDates=array(store.get("wellness-retry"),"dates");for(int i=0;i<retryDates.length();i++)dates.add(retryDates.getString(i));JSONArray remaining=new JSONArray();
  RecoveryReader recovery=new RecoveryReader(client);int missingDays=0;
  for(String date:dates){
   JSONObject old=find(array(local,"wellness"),"date",date);JSONObject raw=old==null?new JSONObject():new JSONObject(old.optString("raw_json","{}"));boolean retry=false,retained=false;JSONObject notes=new JSONObject(),attempts=new JSONObject();
   for(String kind:new String[]{"sleep","hrv","heart"}){
    RecoveryReader.Result result=recovery.read(kind,date);notes.put(kind,result.note);attempts.put(kind,result.raw);retry|=result.retry;
    if(RecoveryReader.measured(kind,result.data))raw.put(kind,result.data);else if(raw.optJSONObject(kind)!=null)retained=true;
   }
   if(retry)remaining.put(date);
   raw.put("latest_attempts",attempts).put("diagnostics",notes);
   JSONObject fresh=Metrics.wellness(date,raw.optJSONObject("sleep")==null?new JSONObject():raw.getJSONObject("sleep"),raw.optJSONObject("hrv")==null?new JSONObject():raw.getJSONObject("hrv"),raw.optJSONObject("heart")==null?new JSONObject():raw.getJSONObject("heart"));
   JSONObject cloudKnown=find(array(store.get("snapshot"),"wellness"),"date",date),known=old==null?cloudKnown:RecoveryRecord.merge(old,cloudKnown);
   JSONObject m=RecoveryRecord.merge(fresh,known);JSONArray retainedFields=m.optJSONArray("retained_fields");raw.remove("retained_values");
   if(retainedFields!=null){retained=true;JSONObject values=new JSONObject();for(int i=0;i<retainedFields.length();i++){String field=retainedFields.getString(i);values.put(field,m.get(field));}raw.put("retained_values",values);}
   if(m.isNull("sleep_hours")||m.isNull("hrv_ms")||m.isNull("resting_hr"))missingDays++;
   String note=notes.getString("sleep")+"\n"+notes.getString("hrv")+"\n"+notes.getString("heart");
   if(retained)note+="\nPreviously imported values retained where the latest response was unavailable.";
   m.put("coverage_note",note+"\nWatch removed during work; daytime data may be incomplete.").put("raw_json",raw.toString());
   archive("wellness-"+date,raw.toString().getBytes(StandardCharsets.UTF_8),"json");local.put("wellness",merge(array(local,"wellness"),new JSONArray().put(m),"date"));store.put("local",local);
  }
  if(missingDays>0)warnings.add("Recovery data incomplete on "+missingDays+" date(s); see Settings → Sync & import details");
  store.put("wellness-retry",new JSONObject().put("dates",remaining));
  store.put("status",store.get("status").put("garmin_sync",Instant.now().toString()));
 }
 private JSONObject find(JSONArray a,String key,String value) throws Exception {for(int i=0;i<a.length();i++)if(value.equals(a.getJSONObject(i).optString(key)))return a.getJSONObject(i);return null;}
 private String hash(byte[] data) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:md.digest(data))hash.append(String.format(Locale.ROOT,"%02x",b&255));return hash.toString();}
 private void archive(String id,byte[] data,String ext) throws Exception {File file=new File(context.getFilesDir(),"originals/"+id+"-"+hash(data)+"."+ext);if(!file.exists())atomic(file.toPath(),data);}
 private void atomic(Path path,byte[] data) throws IOException {Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.write(temp,data);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
 void feedback(String activityId,String workoutId,int rpe,int soreness,int fatigue,String note) throws Exception {
  String id=UUID.randomUUID().toString();JSONObject f=new JSONObject().put("feedback_id",id).put("activity_id",activityId).put("workout_id",workoutId).put("created_at",Instant.now().toString()).put("rpe",rpe).put("soreness",soreness).put("fatigue",fatigue).put("note",note);
  store.queue(id,"feedback",f);
 }
 void match(String activityId,String workoutId) throws Exception {store.queue("match:"+activityId,"match",new JSONObject().put("activity_id",activityId).put("workout_id",workoutId).put("updated_at",Instant.now().toString()));}
 @Override public void close(){store.close();}
}
