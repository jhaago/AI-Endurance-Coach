package au.com.endurancecoach;
import au.com.endurancecoach.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ForegroundSyncTest {
 private final AtomicInteger lists=new AtomicInteger(),cloudCalls=new AtomicInteger();
 private boolean failGarmin;
 private CountDownLatch firstList,release,bridgeEntered,bridgeRelease,noteUploaded;
 private final List<JSONObject> uploads=Collections.synchronizedList(new ArrayList<>());
 private CoachRepository create(){return new CoachRepository(RuntimeEnvironment.getApplication(),this::request,key->key.equals("garmin")?"synthetic-key":key.equals("bridge")?"https://script.google.com/macros/s/test/exec":"synthetic-secret-12345678901234567890");}
 private Transport.Response json(String value){return new Transport.Response(200,value.getBytes(StandardCharsets.UTF_8));}
 private Transport.Response request(String method,String url,Map<String,String> headers,byte[] body) throws java.io.IOException {
  try {
   if(url.contains("script.google.com")){
    int cloudCount=cloudCalls.incrementAndGet();if(bridgeEntered!=null&&cloudCount==1){bridgeEntered.countDown();if(!bridgeRelease.await(5,TimeUnit.SECONDS))throw new java.io.IOException("Synthetic bridge barrier timed out");}JSONObject input=new JSONObject(new String(body,StandardCharsets.UTF_8));uploads.add(input);JSONArray ack=new JSONArray(),feedback=input.optJSONArray("feedback");if(feedback!=null)for(int i=0;i<feedback.length();i++){ack.put(feedback.getJSONObject(i).getString("feedback_id"));if(noteUploaded!=null)noteUploaded.countDown();}
    return json(new JSONObject().put("ok",true).put("ack_feedback",ack).put("snapshot",new JSONObject().put("plan",new JSONArray())).toString());
   }
   if(url.endsWith(GarminClient.TOKEN_PATH)){String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":"+(System.currentTimeMillis()/1000+86400)+"}").getBytes(StandardCharsets.UTF_8));return json("e30."+payload+".test");}
   if(url.contains("activitylist-service")){
    int count=lists.incrementAndGet();if(firstList!=null&&count==1){firstList.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("Synthetic barrier timed out");}
    if(failGarmin)return new Transport.Response(503,new byte[0]);
    return json(new JSONArray().put(new JSONObject().put("activityId",123).put("activityName","Morning run").put("startTimeGMT",LocalDate.now(PlanEngine.ZONE)+" 01:00:00").put("activityType",new JSONObject().put("typeKey","running")).put("distance",5000).put("duration",1800)).toString());
   }
   if(url.contains("download-service"))return new Transport.Response(404,new byte[0]);
   return json("{}");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException("Synthetic interrupted",e);}catch(JSONException e){throw new java.io.IOException(e);}
 }
 @Test public void openingTheAppImportsGarminWithoutBlockingTheCachedScreen() throws Exception {
  firstList=new CountDownLatch(1);release=new CountDownLatch(1);
  try(org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create()){
   java.lang.reflect.Field field=MainActivity.class.getDeclaredField("repo");field.setAccessible(true);((CoachRepository)field.get(c.get())).close();CoachRepository repository=create();field.set(c.get(),repository);
   c.start().resume();
   assertTrue("Opening the app must query Garmin",firstList.await(5,TimeUnit.SECONDS));
   assertNotNull(c.get().findViewById(android.R.id.content));
   release.countDown();java.lang.reflect.Field executor=MainActivity.class.getDeclaredField("network");executor.setAccessible(true);
   ((ExecutorService)executor.get(c.get())).submit(()->{}).get(10,TimeUnit.SECONDS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   assertEquals("Morning run",repository.snapshot().getJSONArray("activities").getJSONObject(0).getString("name"));
   assertEquals(1,lists.get());
  }finally{release.countDown();}
 }
 @Test public void leavingTheActivityStillUploadsANoteQueuedDuringSync() throws Exception {
  bridgeEntered=new CountDownLatch(1);bridgeRelease=new CountDownLatch(1);noteUploaded=new CountDownLatch(1);
  org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
  try{
   java.lang.reflect.Field field=MainActivity.class.getDeclaredField("repo");field.setAccessible(true);((CoachRepository)field.get(c.get())).close();CoachRepository repository=create();field.set(c.get(),repository);
   android.database.sqlite.SQLiteDatabase db=repository.store.getWritableDatabase();
   java.lang.reflect.Field executorField=MainActivity.class.getDeclaredField("network");executorField.setAccessible(true);ExecutorService executor=(ExecutorService)executorField.get(c.get());
   c.start().resume();assertTrue(bridgeEntered.await(5,TimeUnit.SECONDS));
   repository.feedback("123","",3,2,1,"Saved while import was uploading");java.lang.reflect.Method sync=MainActivity.class.getDeclaredMethod("sync",boolean.class);sync.setAccessible(true);sync.invoke(c.get(),false);
   c.pause().stop().destroy();bridgeRelease.countDown();
   boolean sent=false;for(int i=0;i<100&&!sent;i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();sent=noteUploaded.await(20,TimeUnit.MILLISECONDS);}
   assertTrue("Closing the Activity must not drop the pending note upload",sent);
   boolean ended=false;for(int i=0;i<100&&!ended;i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();ended=executor.awaitTermination(20,TimeUnit.MILLISECONDS);}
   assertTrue("Completed background work releases its executor",ended);assertFalse("Completed background work closes its database",db.isOpen());
   try(CoachRepository check=create()){assertEquals(0,check.store.pending("feedback").length());}
  }finally{bridgeRelease.countDown();c.close();}
 }
 @Test public void garminSyncImportsANewRunBeforeUploadingIt() throws Exception {
  try(CoachRepository repo=create()){repo.sync(true);JSONArray activities=repo.snapshot().getJSONArray("activities");assertEquals(1,activities.length());assertEquals("Morning run",activities.getJSONObject(0).getString("name"));assertEquals(1,lists.get());assertTrue(uploads.stream().anyMatch(r->r.optJSONArray("activities")!=null));}
 }
 @Test public void cloudOnlyRefreshUploadsNotesWithoutGarminDownloads() throws Exception {
  try(CoachRepository repo=create()){repo.feedback("123","",3,2,1,"Felt comfortable");repo.sync(false);assertEquals(0,lists.get());assertEquals(0,repo.store.pending("feedback").length());assertEquals(1,cloudCalls.get());}
 }
 @Test public void unavailableGarminDoesNotBlockUploadingSessionNotes() throws Exception {
  failGarmin=true;
  try(CoachRepository repo=create()){repo.feedback("123","",3,2,1,"Felt comfortable");java.io.IOException error=assertThrows(java.io.IOException.class,()->repo.sync(true));assertEquals(0,repo.store.pending("feedback").length());assertTrue(error.getMessage().contains("503"));assertEquals(1,cloudCalls.get());}
 }
 @Test public void foregroundSyncWaitsForBackgroundSyncThenChecksGarminAgain() throws Exception {
  firstList=new CountDownLatch(1);release=new CountDownLatch(1);ExecutorService workers=Executors.newFixedThreadPool(2);
  try(CoachRepository background=create();CoachRepository foreground=create()){
   Future<String> a=workers.submit(()->background.sync(true));assertTrue(firstList.await(5,TimeUnit.SECONDS));CountDownLatch started=new CountDownLatch(1);
   Future<String> b=workers.submit(()->{started.countDown();return foreground.sync(true);});assertTrue(started.await(5,TimeUnit.SECONDS));
   boolean skipped=false;try{b.get(200,TimeUnit.MILLISECONDS);skipped=true;}catch(TimeoutException expected){}finally{release.countDown();}
   a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);assertFalse("Foreground request must not be skipped",skipped);assertEquals(2,lists.get());
  }finally{release.countDown();workers.shutdownNow();}
 }
}
