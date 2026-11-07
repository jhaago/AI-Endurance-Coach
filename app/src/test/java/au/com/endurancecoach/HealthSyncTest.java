package au.com.endurancecoach;
import au.com.endurancecoach.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class HealthSyncTest {
 private CoachRepository repo;
 private final long now=Instant.parse("2026-11-08T12:00:00Z").toEpochMilli();
 private final LocalDate day=LocalDate.parse("2026-11-08");
 @Before public void setup(){repo=new CoachRepository(RuntimeEnvironment.getApplication());}
 @After public void close(){repo.close();}
 private JSONObject meal(){return HealthRecords.meal(day,"Lunch",500,1,"",null,now);}
 private BridgeClient bridge(boolean supported,boolean fail){return new BridgeClient((method,url,headers,body)->{
  try{if(fail)return new Transport.Response(503,new byte[0]);JSONObject request=new JSONObject(new String(body,StandardCharsets.UTF_8));
   JSONArray health=request.optJSONArray("health"),ack=new JSONArray();if(health==null)health=new JSONArray();for(int i=0;i<health.length();i++)ack.put(health.getJSONObject(i).getString("change_id"));
   JSONObject snapshot=new JSONObject().put("plan",new JSONArray());JSONObject result=new JSONObject().put("ok",true).put("snapshot",snapshot);
   if(supported){snapshot.put("health",health);result.put("ack_health",ack);}return new Transport.Response(200,result.toString().getBytes(StandardCharsets.UTF_8));
  }catch(JSONException e){throw new java.io.IOException(e);}
 },"https://script.google.com/macros/s/test/exec","synthetic-secret-12345678901234567890");}
 @Test public void offlineMealAndFavouriteSurviveRestart() throws Exception {
  repo.saveHealth(new JSONArray().put(meal()).put(HealthRecords.favourite("Lunch",500,"",null,now)));repo.close();repo=new CoachRepository(RuntimeEnvironment.getApplication());
  assertEquals(2,repo.healthRecords().length());assertEquals(2,repo.store.pending("health").length());
 }
 @Test public void successfulSyncAcknowledgesChangesAndRetainsCurrentRecords() throws Exception {
  repo.saveHealth(new JSONArray().put(meal()));repo.syncCloud(bridge(true,false));assertEquals(0,repo.store.pending("health").length());assertEquals(500,HealthRecords.summary(repo.healthRecords(),day).calories,0);
 }
 @Test public void oldBridgeAndNetworkFailureNeverDiscardQueuedHealth() throws Exception {
  repo.saveHealth(new JSONArray().put(meal()));assertThrows(java.io.IOException.class,()->repo.syncCloud(bridge(false,false)));assertEquals(1,repo.store.pending("health").length());
  assertThrows(java.io.IOException.class,()->repo.syncCloud(bridge(true,true)));assertEquals(1,repo.store.pending("health").length());
 }
 @Test public void staleCloudSnapshotCannotResurrectDeletedMeal() throws Exception {
  JSONObject first=meal();repo.saveHealth(new JSONArray().put(first));repo.saveHealth(new JSONArray().put(HealthRecords.deleted(first,now)));
  repo.store.acceptSnapshot(new JSONObject().put("health",new JSONArray().put(first)),new JSONArray(),new JSONArray());
  assertNull(HealthRecords.summary(repo.healthRecords(),day).calories);assertEquals(2,repo.store.pending("health").length());
 }
}
