package au.com.endurancecoach;
import android.app.*;
import android.view.*;
import android.widget.*;
import au.com.endurancecoach.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.time.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w320dp-h640dp-xhdpi")
public class DietQualityUiTest {
 private ActivityController<Activity> controller;private Activity activity;private CoachRepository repo;private LinearLayout root;private LocalDate day;private JSONObject meal;
 @Before public void setup() throws Exception {activity=(controller=Robolectric.buildActivity(Activity.class).create()).get();activity.getSharedPreferences("health-ui",0).edit().clear().commit();day=LocalDate.now(PlanEngine.ZONE);repo=new CoachRepository(activity);root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);activity.setContentView(root);meal=HealthRecords.meal(day,"Synthetic lunch",500,1,"",null,System.currentTimeMillis(),"lunch");repo.saveHealth(new JSONArray().put(meal));}
 @After public void close(){repo.close();controller.close();}
 private JSONObject row(String status) throws Exception {return new JSONObject().put("assessment_id","diet-quality-"+day).put("revision",1).put("date",day.toString()).put("status",status).put("score",70).put("fruit_veg_points",20).put("fibre_points",15).put("protein_calcium_points",15).put("fat_quality_points",10).put("discretionary_points",10).put("confidence","medium").put("summary","Synthetic balanced lunch").put("strength","Varied protein sources").put("improvement","Add vegetables at dinner").put("fruit_veg_reason","Some vegetables logged").put("fibre_reason","Some wholegrains logged").put("protein_calcium_reason","Calcium uncertain").put("fat_quality_reason","Oil type uncertain").put("discretionary_reason","Training fuel considered").put("food_change_ids",meal.getString("change_id")).put("source","Synthetic Coach").put("assessed_at","2026-11-11T02:00:00.000Z").put("rubric_version","dq-v1");}
 private void accept(JSONObject snapshot) throws Exception {repo.store.acceptSnapshot(snapshot,new JSONArray(),new JSONArray());}
 private void render() throws Exception {root.removeAllViews();new HealthScreen(activity,repo,CoachTheme.apply(activity),day,d->{day=d;try{render();}catch(Exception e){throw new AssertionError(e);}},()->{}).render(root);}
 @Test public void provisionalScoreExpandsToReasonsWithoutWritingActuals() throws Exception {
  accept(new JSONObject().put("diet_quality",new JSONArray().put(row("provisional"))));render();assertNotNull(find(root,"70 / 100"));assertNotNull(find(root,"Provisional · food logged so far"));assertNotNull(find(root,"Confidence · Medium"));assertNotNull(find(root,"Next step · Add vegetables at dinner"));assertNull(find(root,"Some vegetables logged"));
  find(root,"Show breakdown").performClick();assertNotNull(find(root,"Fruit and vegetables · 20 / 30"));assertNotNull(find(root,"Some vegetables logged"));assertNotNull(find(root,"Strength · Varied protein sources"));assertEquals(1,repo.store.pending("health").length());assertNotNull(find(root,"7-day average · —"));
  DietQualityTrendView chart=(DietQualityTrendView)findType(root,DietQualityTrendView.class);assertTrue(chart.getContentDescription().toString().contains("Provisional today 70"));assertTrue(chart.getContentDescription().toString().contains("0 completed days"));
 }
 @Test public void completedAssessmentEntersAverageAndFoodEditMarksItStaleImmediately() throws Exception {
  accept(new JSONObject().put("diet_quality",new JSONArray().put(row("complete"))));render();assertNotNull(find(root,"7-day average · 70 / 100"));assertNotNull(find(root,"1 completed day in the past 7 days"));
  repo.saveHealth(new JSONArray().put(HealthRecords.meal(day,"Changed portion",500,2,"",meal,System.currentTimeMillis(),"lunch")));render();assertNotNull(find(root,"Needs review · food log changed"));assertNotNull(find(root,"7-day average · —"));assertEquals(2,repo.store.pending("health").length());
 }
 @Test public void unavailableCacheSurvivesRestartWithoutBlockingFoodAcknowledgement() throws Exception {
  accept(new JSONObject().put("diet_quality",new JSONArray().put(row("provisional"))));String id=meal.getString("change_id");repo.store.acceptSnapshot(new JSONObject().put("diet_quality",new JSONArray().put(row("complete").put("score",101))).put("plan",new JSONArray()),new JSONArray(),new JSONArray(),new JSONArray().put(id));
  assertEquals(0,repo.store.pending("health").length());assertEquals(1,repo.dietQuality().length());assertFalse(repo.dietQualityError().isEmpty());repo.close();repo=new CoachRepository(activity);assertEquals(1,repo.dietQuality().length());render();assertNotNull(find(root,"Saved assessment may be out of date. Sync again when connected."));
  accept(new JSONObject());assertEquals(1,repo.dietQuality().length());accept(new JSONObject().put("diet_quality",new JSONArray()));assertEquals(0,repo.dietQuality().length());assertEquals("",repo.dietQualityError());
 }
 @Test public void emptyStateAndRangeControlsNeverCreateAssessments() throws Exception {
  accept(new JSONObject().put("diet_quality",new JSONArray()));render();assertNotNull(find(root,"No diet quality assessment yet"));find(root,"30 days").performClick();assertEquals(0,repo.dietQuality().length());assertEquals(1,repo.healthRecords().length());
  find(root,"Weight").performClick();assertNull(find(root,"Diet Quality"));
 }
 @Test public void lateSnapshotCannotResurrectWithdrawnAssessment() throws Exception {
  JSONObject one=row("provisional"),two=row("withdrawn").put("revision",2);
  accept(new JSONObject().put("diet_quality",new JSONArray().put(one).put(two)));
  accept(new JSONObject().put("diet_quality",new JSONArray().put(one)));assertEquals(2,repo.dietQuality().length());assertFalse(repo.dietQualityError().isEmpty());render();assertNull(find(root,"70 / 100"));
  accept(new JSONObject().put("diet_quality",new JSONArray().put(one).put(new JSONObject(two.toString()).put("summary","Same revision changed"))));assertEquals("Synthetic balanced lunch",repo.dietQuality().getJSONObject(1).getString("summary"));
 }
 private static View find(View v,String text){if(v.getVisibility()!=View.VISIBLE)return null;if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
 private static View findType(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findType(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
}
