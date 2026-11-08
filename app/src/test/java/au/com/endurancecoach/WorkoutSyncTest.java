package au.com.endurancecoach;
import au.com.endurancecoach.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WorkoutSyncTest {
 private CoachRepository repo;
 @Before public void open(){repo=new CoachRepository(RuntimeEnvironment.getApplication());}
 @After public void close(){repo.close();}
 private BridgeClient bridge(boolean supported){return new BridgeClient((method,url,headers,body)->{try{
  JSONObject request=new JSONObject(new String(body,StandardCharsets.UTF_8));JSONArray statuses=request.optJSONArray("workout_status"),ack=new JSONArray();if(statuses==null)statuses=new JSONArray();for(int i=0;i<statuses.length();i++)ack.put(statuses.getJSONObject(i).getString("change_id"));
  JSONObject snapshot=new JSONObject().put("plan",new JSONArray()).put("nutrition_targets",new JSONArray());JSONObject response=new JSONObject().put("ok",true).put("ack_feedback",new JSONArray()).put("snapshot",snapshot);if(supported){snapshot.put("workout_status",statuses);response.put("ack_workout_status",ack);}
  return new Transport.Response(200,response.toString().getBytes(StandardCharsets.UTF_8));
 }catch(JSONException e){throw new java.io.IOException(e);}},"https://script.google.com/macros/s/test/exec","synthetic-secret-12345678901234567890");}
 @Test public void completionAndResetSurviveRestartWithoutResurrectingOldCloudState() throws Exception {
  repo.saveWorkoutStatus("synthetic-workout","completed",45.0,"");JSONObject completed=repo.workoutStatuses().getJSONObject(0);repo.saveWorkoutStatus("synthetic-workout","scheduled",null,"");repo.close();repo=new CoachRepository(RuntimeEnvironment.getApplication());
  repo.store.acceptSnapshot(new JSONObject().put("workout_status",new JSONArray().put(completed)),new JSONArray(),new JSONArray());assertEquals("scheduled",repo.workoutStatuses().getJSONObject(0).getString("status"));assertEquals(2,repo.store.pending("workout_status").length());
 }
 @Test public void supportedBridgeAcknowledgesExplicitCompletionIdsOnly() throws Exception {repo.saveWorkoutStatus("synthetic-workout","completed",45.0,"");repo.syncCloud(bridge(true));assertEquals(0,repo.store.pending("workout_status").length());assertEquals("completed",repo.workoutStatuses().getJSONObject(0).getString("status"));}
 @Test public void oldBridgeRetainsCompletionQueue() throws Exception {repo.saveWorkoutStatus("synthetic-workout","completed",null,"");assertThrows(java.io.IOException.class,()->repo.syncCloud(bridge(false)));assertEquals(1,repo.store.pending("workout_status").length());}
 @Test public void feedbackCompletionIsAtomicAndNotesHaveTheirOwnLimit() throws Exception {repo.feedback("","synthetic-workout",2,1,1,"x".repeat(1500),true);assertEquals(1,repo.store.pending("feedback").length());assertEquals(1,repo.store.pending("workout_status").length());assertEquals("",repo.workoutStatuses().getJSONObject(0).getString("note"));assertThrows(IllegalArgumentException.class,()->repo.feedback("","invalid id",2,1,1,"Synthetic",true));assertEquals(1,repo.store.pending("feedback").length());}
}
