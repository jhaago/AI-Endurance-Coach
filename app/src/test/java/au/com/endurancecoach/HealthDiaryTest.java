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
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.*;
import static org.junit.Assert.*;

/** Synthetic diary records. Direct screen rendering never starts cloud sync. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w320dp-h640dp-xhdpi")
public class HealthDiaryTest {
 private ActivityController<Activity> controller;private Activity activity;private CoachRepository repo;private LinearLayout root;private LocalDate day;private int writes,redraws;
 @Before public void setup(){activity=(controller=Robolectric.buildActivity(Activity.class).create()).get();day=LocalDate.now(PlanEngine.ZONE);activity.getSharedPreferences("health-ui",0).edit().clear().commit();repo=new CoachRepository(activity);root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);activity.setContentView(root);}
 @After public void close(){repo.close();controller.close();}
 private void render(){try{root.removeAllViews();new HealthScreen(activity,repo,CoachTheme.apply(activity),day,d->{day=d;redraws++;render();},()->{writes++;render();}).render(root);}catch(Exception e){throw new AssertionError(e);}}
 @Test public void sectionsAndRangeKeepSelectedDayWithoutSaving(){day=day.minusDays(3);render();click(root,"Weight");assertNotNull(find(root,"Latest weight"));click(root,"90 days");assertNotNull(description(root,"Weight · past 90 days (kg). No measurements available"));assertEquals(0,writes);assertEquals(2,redraws);assertEquals(LocalDate.now(PlanEngine.ZONE).minusDays(3),day);click(root,"Diary");for(String group:new String[]{"Breakfast","Lunch","Dinner","Snacks"})assertNotNull(find(root,group+" · —"));}
 @Test public void futureDayDisplaysReadOnlyTargetsAndDisablesActualLogging(){day=day.plusDays(2);render();assertNotNull(find(root,"No approved nutrition target"));assertFalse(find(root,"Add meal").isEnabled());assertFalse(find(root,"Quick add").isEnabled());click(root,"Weight");assertFalse(find(root,"Log weight").isEnabled());assertNull(find(root,"Set goal weight"));assertEquals(0,writes);}
 @Test public void favouriteOnePortionUsesSelectedGroupAndCustomCopyKeepsGrams() throws Exception {
  repo.saveHealth(new JSONArray().put(HealthRecords.favourite("Toast",200,"",null,System.currentTimeMillis()).put("protein_g",8)));render();click(root,"Quick add");((Spinner)description(dialog(),"Meal group")).setSelection(1);AlertDialog picker=ShadowAlertDialog.getLatestAlertDialog();click(dialog(),"Add 1 portion · Toast");assertFalse(picker.isShowing());JSONObject meal=kind("meal");assertEquals("lunch",meal.getString("meal_type"));assertEquals(8,meal.getDouble("protein_g"),0);
  click(root,"Quick add");click(dialog(),"Toast · 200 kcal");((EditText)description(dialog(),"Portions")).setText("2");((Spinner)description(dialog(),"Meal group")).setSelection(0);saveDialog();assertNotNull(find(root,"Breakfast · 400 kcal"));assertEquals(3,repo.healthRecords().length());assertEquals(2,writes);
 }
 @Test public void recentCopyKeepsLegacyOtherSeparateFromOriginal() throws Exception {JSONObject legacy=HealthRecords.meal(day.minusDays(1),"Soup",300,1,"",null,System.currentTimeMillis());legacy.remove("meal_type");repo.saveHealth(new JSONArray().put(legacy));render();click(root,"Quick add");assertNotNull(find(dialog(),"Recent"));click(dialog(),"Soup · 300 kcal");((EditText)description(dialog(),"Portions")).setText("1.5");saveDialog();assertNotNull(find(root,"Other · 450 kcal"));assertEquals(2,repo.healthRecords().length());}
 @Test public void weightHistoryEditsOriginalDayAndLegacyGoalIsReadOnly() throws Exception {LocalDate prior=day.minusDays(4);repo.saveHealth(new JSONArray().put(HealthRecords.weight(prior,100,null,System.currentTimeMillis())).put(HealthRecords.weight(day,98,null,System.currentTimeMillis())).put(HealthRecords.goal(85,null,System.currentTimeMillis())));render();click(root,"Weight");assertNotNull(find(root,"7-day measured average · 99.0 kg"));assertNotNull(find(root,"2 weigh-ins in 7 days"));assertNotNull(find(root,"Weight goal · 85.0 kg"));assertNull(find(root,"Set goal weight"));click(root,prior+" · 100.0 kg");((EditText)description(dialog(),"Weight (kg)")).setText("99");saveDialog();JSONArray rows=repo.healthRecords();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("record_id").equals("weight:"+prior))assertEquals(99,rows.getJSONObject(i).getDouble("weight_kg"),0);assertEquals(LocalDate.now(PlanEngine.ZONE),day);}
 @Test public void approvedTargetIsReadOnlyAndRemainingUsesOnlyLoggedFood() throws Exception {
  JSONArray targets=new JSONArray().put(target());repo.store.acceptSnapshot(new JSONObject().put("plan",new JSONArray()).put("nutrition_targets",targets),new JSONArray(),new JSONArray());
  repo.saveHealth(new JSONArray().put(HealthRecords.meal(day,"Soup",500,1.5,"",null,System.currentTimeMillis())));render();
  assertNotNull(find(root,"Target · 1900 kcal"));assertNotNull(find(root,"750 kcal"));assertNotNull(find(root,"Remaining · 1150 kcal"));assertNotNull(find(root,"Protein · 80 g"));assertNotNull(find(root,"Carbohydrate · 250 g"));assertNull(find(root,"Fat · 0 g"));
  assertNotNull(find(root,"Source · Synthetic test coach"));assertNotNull(find(root,"Synthetic recovery target"));assertNotNull(find(root,"Training · Recovery"));assertNotNull(find(root,"Exercise calories are not added."));assertNull(find(root,"Set calorie goal"));assertEquals(0,writes);assertEquals(1,repo.store.pending("health").length());
 }
 @Test public void staleNutritionKeepsLastTargetAndShowsSimpleWarning() throws Exception {
  repo.store.acceptSnapshot(new JSONObject().put("plan",new JSONArray()).put("nutrition_targets",new JSONArray().put(target())),new JSONArray(),new JSONArray());
  repo.store.acceptSnapshot(new JSONObject().put("plan",new JSONArray()).put("nutrition_error","Synthetic unavailable"),new JSONArray(),new JSONArray());render();assertNotNull(find(root,"Target · 1900 kcal"));assertNotNull(find(root,"Nutrition targets may be out of date. Sync again when connected."));assertEquals(0,writes);
 }
 private JSONObject target() throws Exception {LocalDate start=day.minusDays(5);return new JSONObject().put("target_id","baseline-"+start).put("revision",1).put("scope","baseline").put("date",start.toString()).put("calories_kcal",1900).put("protein_g",80).put("carbohydrate_g",250).put("fat_g",JSONObject.NULL).put("weight_goal_kg",85).put("weight_goal_date","").put("weight_change_kg_per_week",JSONObject.NULL).put("training_type","Recovery").put("exercise_included",false).put("source","Synthetic test coach").put("created_at",start+"T00:00:00.000Z").put("reason","Synthetic recovery target").put("status","approved");}
 private JSONObject kind(String kind)throws Exception{JSONArray rows=repo.healthRecords();for(int i=0;i<rows.length();i++)if(kind.equals(rows.getJSONObject(i).optString("kind")))return rows.getJSONObject(i);throw new AssertionError(kind);}
 private View dialog(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();}
 private void saveDialog(){ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();}
 private static void click(View root,String text){View v=find(root,text);assertNotNull(text,v);v.performClick();}
 private static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
 private static View description(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=description(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
}
