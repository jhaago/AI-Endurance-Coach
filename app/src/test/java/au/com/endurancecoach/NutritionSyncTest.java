package au.com.endurancecoach;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import au.com.endurancecoach.core.*;
import java.time.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NutritionSyncTest {
 private CoachRepository repo;
 @Before public void open(){repo=new CoachRepository(RuntimeEnvironment.getApplication());}
 @After public void close(){repo.close();}
 private JSONObject row(int revision,double kcal) throws Exception {return new JSONObject().put("target_id","nutrition-2026-11-09").put("revision",revision).put("scope","daily").put("date","2026-11-09").put("calories_kcal",kcal).put("protein_g",JSONObject.NULL).put("carbohydrate_g",JSONObject.NULL).put("fat_g",JSONObject.NULL).put("weight_goal_kg",JSONObject.NULL).put("weight_goal_date","").put("weight_change_kg_per_week",JSONObject.NULL).put("training_type","Easy run").put("exercise_included",true).put("source","Synthetic Coach").put("created_at","2026-11-08T01:00:00.000Z").put("reason","").put("status","approved");}
 private void accept(JSONObject snapshot) throws Exception {repo.store.acceptSnapshot(snapshot,new JSONArray(),new JSONArray());}
 @Test public void targetRevisionUpdatesDoNotChangeLoggedMealsOrWeight() throws Exception {long now=Instant.parse("2026-11-08T01:00:00Z").toEpochMilli();LocalDate day=LocalDate.parse("2026-11-08");repo.saveHealth(new JSONArray().put(HealthRecords.meal(day,"Synthetic meal",600,1,"",null,now)).put(HealthRecords.weight(day,95,null,now)));String actual=repo.healthRecords().toString();accept(new JSONObject().put("nutrition_targets",new JSONArray().put(row(1,2400))));accept(new JSONObject().put("nutrition_targets",new JSONArray().put(row(1,2400)).put(row(2,2700))));assertEquals(actual,repo.healthRecords().toString());assertEquals(2,repo.store.pending("health").length());assertEquals(2700,NutritionTargets.forDay(repo.nutritionTargets(),LocalDate.parse("2026-11-09"),new JSONArray()).calories,0);}
 @Test public void failedOrOldServerResponsePreservesLastGoodTargetAcrossRestart() throws Exception {accept(new JSONObject().put("nutrition_targets",new JSONArray().put(row(1,2400))));accept(new JSONObject().put("nutrition_error","Nutrition targets unavailable"));repo.close();repo=new CoachRepository(RuntimeEnvironment.getApplication());assertEquals(1,repo.nutritionTargets().length());assertFalse(repo.nutritionError().isEmpty());accept(new JSONObject());assertEquals(1,repo.nutritionTargets().length());}
 @Test public void invalidNutritionIsNonFatalToTrainingAndFeedbackAcknowledgement() throws Exception {accept(new JSONObject().put("nutrition_targets",new JSONArray().put(row(1,2400))));repo.feedback("1","",0,0,0,"Synthetic");String id=repo.store.pending("feedback").getJSONObject(0).getString("feedback_id");repo.store.acceptSnapshot(new JSONObject().put("plan",new JSONArray().put(new JSONObject().put("workout_id","w").put("revision",1).put("date","2026-11-09"))).put("nutrition_targets",new JSONArray().put(row(2,0))),new JSONArray().put(id),new JSONArray());assertEquals(0,repo.store.pending("feedback").length());assertEquals(1,repo.nutritionTargets().length());assertFalse(repo.nutritionError().isEmpty());assertEquals(1,repo.snapshot().getJSONArray("plan").length());}
 @Test public void validEmptyLedgerClearsApprovedTargetCache() throws Exception {accept(new JSONObject().put("nutrition_targets",new JSONArray().put(row(1,2400))));accept(new JSONObject().put("nutrition_targets",new JSONArray()));assertEquals(0,repo.nutritionTargets().length());assertEquals("",repo.nutritionError());}
}
