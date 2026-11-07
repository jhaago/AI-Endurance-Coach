package au.com.endurancecoach;
import au.com.endurancecoach.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CloudSyncTest {
 private CoachRepository repo;
 private final List<JSONObject> requests=new ArrayList<>();
 @Before public void create(){repo=new CoachRepository(RuntimeEnvironment.getApplication());}
 @After public void close(){repo.close();}
 private BridgeClient client(boolean fail){return new BridgeClient((method,url,headers,body)->{
  try {
  JSONObject request=new JSONObject(new String(body,StandardCharsets.UTF_8));requests.add(request);
  if(fail)return new Transport.Response(503,new byte[0]);
  JSONArray ack=new JSONArray(),feedback=request.optJSONArray("feedback");if(feedback!=null)for(int i=0;i<feedback.length();i++)ack.put(feedback.getJSONObject(i).getString("feedback_id"));
  JSONObject workout=new JSONObject().put("workout_id","w").put("revision",2).put("date","2026-11-10").put("type","run");
  JSONObject snapshot=new JSONObject().put("events",new JSONArray()).put("plan",new JSONArray().put(workout)).put("activities",new JSONArray()).put("wellness",new JSONArray());
  String response=new JSONObject().put("ok",true).put("ack_feedback",ack).put("snapshot",snapshot).toString();
  return new Transport.Response(200,response.getBytes(StandardCharsets.UTF_8));
  } catch(JSONException error) {throw new java.io.IOException(error);}
 },"https://script.google.com/macros/s/test/exec","synthetic-secret-12345678901234567890");}
 private void seed() throws Exception {repo.store.put("local",new JSONObject().put("activities",new JSONArray().put(new JSONObject().put("activity_id","1").put("raw_json","{}"))));}
 @Test public void idleRefreshStillFetchesExactlyOneSnapshot() throws Exception {repo.syncCloud(client(false));assertEquals(1,requests.size());assertEquals("snapshot",requests.get(0).getString("action"));assertEquals(2,repo.store.get("snapshot").getJSONArray("plan").getJSONObject(0).getInt("revision"));}
 @Test public void uploadsUseTheirReturnedSnapshotWithoutAnExtraFetch() throws Exception {seed();repo.feedback("1","",3,2,1,"Synthetic");repo.syncCloud(client(false));assertEquals(2,requests.size());for(JSONObject r:requests)assertEquals("sync",r.getString("action"));assertEquals(0,repo.store.pending("feedback").length());assertTrue(repo.store.get("uploaded:activities").has("1"));assertEquals(2,repo.store.get("snapshot").getJSONArray("plan").getJSONObject(0).getInt("revision"));}
 @Test public void unchangedRowsAreNotUploadedAgain() throws Exception {seed();repo.syncCloud(client(false));repo.syncCloud(client(false));assertEquals(2,requests.size());assertEquals("sync",requests.get(0).getString("action"));assertEquals("snapshot",requests.get(1).getString("action"));}
 @Test public void failedUploadKeepsRetryHashesAndFeedbackUnacknowledged() throws Exception {seed();repo.feedback("1","",3,2,1,"Synthetic");assertThrows(java.io.IOException.class,()->repo.syncCloud(client(true)));assertFalse(repo.store.get("uploaded:activities").has("1"));assertEquals(1,repo.store.pending("feedback").length());requests.clear();repo.syncCloud(client(false));assertEquals(2,requests.size());assertEquals(0,repo.store.pending("feedback").length());}
}
