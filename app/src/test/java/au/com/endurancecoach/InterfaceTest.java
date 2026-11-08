package au.com.endurancecoach;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import au.com.endurancecoach.core.PlanEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import static org.junit.Assert.*;

/** Synthetic offline UI checks; never authenticate, import or alter the real training database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35}, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InterfaceTest {
    private Context context;
    private LocalDate today;

    @Before public void seedOfflineData() throws Exception {
        context = RuntimeEnvironment.getApplication(); today = LocalDate.now(PlanEngine.ZONE);
        context.getSharedPreferences("appearance", 0).edit().clear().commit();
        JSONObject event = new JSONObject().put("event_id", "test-event").put("active", true)
                .put("name", "Sample endurance event").put("date", today.plusMonths(4).toString());
        JSONObject workout = new JSONObject().put("workout_id", "test-recovery").put("event_id", "test-event")
                .put("revision", 1).put("date", today.plusDays(2).toString()).put("type", "recovery")
                .put("title", "Recovery check-in").put("status", "scheduled").put("duration_min", 0)
                .put("intensity", "Record soreness and fatigue").put("reason", "Technical revision explanation");
        JSONObject activity = new JSONObject().put("activity_id", "10001").put("type", "running")
                .put("name", "Sample run").put("start_utc", today.atTime(2, 0).toInstant(java.time.ZoneOffset.UTC).toString())
                .put("distance_m", 10000).put("duration_s", JSONObject.NULL);
        JSONObject wellness = new JSONObject().put("date", today.toString()).put("sleep_hours", JSONObject.NULL)
                .put("sleep_score", JSONObject.NULL).put("hrv_ms", JSONObject.NULL).put("resting_hr", 57)
                .put("coverage_note", "Synthetic diagnostic: HTTP 200");
        try (LocalStore store = new LocalStore(context)) {
            store.put("snapshot", new JSONObject().put("events", new JSONArray().put(event))
                    .put("plan", new JSONArray().put(workout)).put("activities", new JSONArray().put(activity))
                    .put("wellness", new JSONArray().put(wellness)));
            store.put("status", new JSONObject().put("message", "Recovery data incomplete; see Insights import details"));
        }
    }

    @Test public void unavailableActivityDurationDoesNotBecomeZeroMinutes() {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); clickDescription(root, "Progress");
            assertNotNull(findText(root, "10.0 km · —"));
            clickText(root, "View activity");
            assertNotNull(findText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), "10.0 km · —"));
        }
    }

    @Test public void futureRecoveryWorkoutDoesNotHideTodaysDailyCheckIn() {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            assertNotNull(findText(c.get().getWindow().getDecorView(), "Daily check-in"));
        }
    }

    @Test public void workoutDetailsShowCurrentInstructionsAndKeepHistoryOutOfTheInterface() throws Exception {
        try (LocalStore store = new LocalStore(context)) {
            JSONObject snapshot = store.get("snapshot"); JSONArray plan = snapshot.getJSONArray("plan");
            plan.put(new JSONObject(plan.getJSONObject(0).toString()).put("revision", 2)
                    .put("title", "Current session").put("intensity", "Easy mobility only"));
            store.put("snapshot", snapshot);
        }
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            clickText(c.get().getWindow().getDecorView(), "Workout details");
            View dialog = ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();
            assertNotNull(findText(dialog, "Easy mobility only"));
            assertNotNull(findText(dialog, "Add feedback"));
            assertFalse(allText(dialog).contains("Plan revisions"));
            assertFalse(allText(dialog).contains("Technical revision explanation"));
            assertNull(findText(dialog, "Recovery check-in"));
        }
        try (LocalStore store = new LocalStore(context)) {
            assertEquals(2, store.get("snapshot").getJSONArray("plan").length());
        }
    }

    @Test public void syncDetailsShowCurrentFailureInsteadOfAnOlderStoredMessage() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            java.lang.reflect.Method update = MainActivity.class.getDeclaredMethod("updateStatus", String.class);
            update.setAccessible(true);
            String failure = "Synthetic training database error: please retry this sync when connected";
            update.invoke(c.get(), failure);
            clickText(c.get().getWindow().getDecorView(), "Settings");
            clickText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), "Sync & import details");
            assertTrue(allText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView()).contains(failure));
            update.invoke(c.get(), "Syncing…");
            assertTrue(allText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView()).contains("Syncing…"));
        }
    }

    @Test public void syncControlsAndWarningsAreInSettingsAndAllTabsUseFullBrand() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView();
            for (String tab : new String[]{"Today", "Plan", "Progress", "Insights"}) {
                clickDescription(root, tab);
                assertNotNull(findText(root, "ENDURANCE COACH"));
                assertFalse(allText(root).contains("Recovery data incomplete"));
                assertNull(findText(root, "Sync Garmin + plan"));
                assertNull(findText(root, "Refresh plan"));
            }
            clickDescription(root, "Insights");
            assertTrue(allText(root).contains("— means no measurement"));
            assertFalse(allText(root).contains("Synthetic diagnostic"));
            clickText(root, "Settings");
            clickText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), "Sync & import details");
            assertTrue(allText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView()).contains("Synthetic diagnostic: HTTP 200"));
            assertNotNull(findText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), "Sync Garmin + plan"));
            assertNotNull(findText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), "Refresh plan"));
            assertTrue(allText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView()).contains("Recovery data incomplete"));
        }
    }

    @Test public void appearanceSelectionPreservesOfflineRecordsAndPendingFeedback() throws Exception {
        try (CoachRepository repo = new CoachRepository(context)) { repo.feedback("10001", "", 4, 2, 3, "Synthetic check-in"); }
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            clickText(c.get().getWindow().getDecorView(), "Settings");
            clickText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), "Dark  ›");
            AlertDialog appearance = ShadowAlertDialog.getLatestAlertDialog();
            appearance.getListView().performItemClick(appearance.getListView().getChildAt(1), 1, 1);
            assertEquals("light", CoachTheme.preference(context));
        }
        try (CoachRepository repo = new CoachRepository(context)) {
            assertEquals(1, repo.store.pending("feedback").length());
            assertEquals(1, repo.snapshot().getJSONArray("activities").length());
            assertEquals(1, repo.snapshot().getJSONArray("plan").length());
        }
    }

    @Test public void renderAllTabsInDarkAndLight() throws Exception {
        for (int mode = 0; mode < 2; mode++) {
            CoachTheme.save(context, mode);
            try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
                MainActivity activity = c.get(); View root = activity.getWindow().getDecorView();
                for (String tab : new String[]{"Today", "Plan", "Progress", "Insights"}) {
                    clickDescription(root, tab);
                    assertNull(findText(root, "Training data needs attention"));
                    capture(root, CoachTheme.MODES[mode] + "-" + tab.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    @Test public void weeklyGraphSelectsHistoryAndRendersBothThemes() throws Exception {
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        double[] kilometres = {5, 12, 10, 0, 20, 6, 10, 16, 4, 23, 5, 38};
        JSONArray activities = new JSONArray();
        for (int i = 0; i < kilometres.length; i++) if (kilometres[i] > 0) {
            activities.put(new JSONObject().put("activity_id", "synthetic-" + i).put("type", "running").put("name", "Sample run")
                    .put("start_utc", monday.minusWeeks(11 - i).atTime(2, 0).toInstant(java.time.ZoneOffset.UTC).toString())
                    .put("distance_m", kilometres[i] * 1000).put("duration_s", kilometres[i] * 450).put("elevation_m", kilometres[i] * 10));
        }
        try (LocalStore store = new LocalStore(context)) {
            store.put("snapshot", store.get("snapshot").put("activities", activities));
            store.put("status", store.get("status").put("activity_start", monday.minusWeeks(11).toString()).put("activity_end", today.toString()));
        }
        for (int mode = 0; mode < 2; mode++) {
            CoachTheme.save(context, mode);
            try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
                View root = c.get().getWindow().getDecorView(); clickDescription(root, "Progress");
                assertNotNull(findText(root, "38.00 km"));
                WeeklyDistanceView graph = (WeeklyDistanceView) findType(root, WeeklyDistanceView.class);
                assertNotNull(graph);
                assertTrue(graph.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
                assertNotNull(findText(root, "5.00 km"));
                assertTrue(graph.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
                assertNotNull(findText(root, "38.00 km"));
                capture(root, CoachTheme.MODES[mode] + "-weekly-progress");
                for (String label : new String[]{"Today", "Plan", "Progress", "Insights"}) {
                    TextView nav = (TextView) findText(findDescription(root, label), label);
                    assertTrue(label, nav.getLayout().getWidth() <= nav.getWidth());
                }
            }
        }
    }

    @Test public void weeklyGraphLabelsIncompleteImportsWithoutZeroFillingEarlierWeeks() {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); clickDescription(root, "Progress");
            assertNotNull(findText(root, "Imported totals only · earlier history may be incomplete"));
            WeeklyDistanceView graph = (WeeklyDistanceView) findType(root, WeeklyDistanceView.class);
            assertTrue(graph.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
            assertTrue(graph.getContentDescription().toString().contains("not imported"));
        }
    }

    @Test public void followPhoneRespondsToNightAndDayConfiguration() {
        CoachTheme.save(context, 2);
        for (boolean night : new boolean[]{true, false}) {
            RuntimeEnvironment.setQualifiers(night ? "+night" : "+notnight");
            try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
                android.util.TypedValue value = new android.util.TypedValue();
                assertTrue(c.get().getTheme().resolveAttribute(android.R.attr.windowLightStatusBar, value, true));
                assertEquals(!night, value.data != 0);
            }
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    public void narrowScreenWithLargeTextKeepsNavigationAndCheckInReachable() throws Exception {
        RuntimeEnvironment.setFontScale(1.5f);
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); capture(root, "narrow-large-text", 640, 1280);
            for (String tab : new String[]{"Today", "Plan", "Progress", "Insights"}) {
                View navigation = findDescription(root, tab); assertNotNull(navigation);
                assertTrue(tab, navigation.getWidth() >= 96 && navigation.getHeight() >= 96);
            }
            assertEquals(1, ((TextView) findText(findDescription(root, "Progress"), "Progress")).getLineCount());
            clickText(root, "Daily check-in");
            View dialog = ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();
            assertNotNull(findText(dialog, "Fatigue · 0"));
            assertTrue(containsScroll(dialog));
            assertTrue(ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        }
    }

    @Test public void healthMealFavouriteQuickAddAndWeightPersist() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root=c.get().getWindow().getDecorView(); clickDescription(root,"Health");
            clickText(root,"Add meal"); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); View form=dialog.getWindow().getDecorView();
            ((android.widget.EditText)findDescription(form,"Meal name")).setText("Porridge");
            ((android.widget.EditText)findDescription(form,"Calories per portion (kcal)")).setText("400");
            ((android.widget.EditText)findDescription(form,"Portions")).setText("1.5");
            ((android.widget.CheckBox)findText(form,"Save as favourite")).setChecked(true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertFalse(dialog.isShowing());
            assertNotNull(findText(root,"600 kcal")); clickText(root,"Quick add");
            clickText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"Add 1 portion · Porridge");assertNotNull(findText(root,"1000 kcal"));
            clickText(root,"Weight");
            clickText(root,"Log weight"); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); dialog=ShadowAlertDialog.getLatestAlertDialog();form=dialog.getWindow().getDecorView();
            ((android.widget.EditText)findDescription(form,"Weight (kg)")).setText("98.5");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertNotNull(findText(root,"98.5 kg"));
        }
        try(CoachRepository repo=new CoachRepository(context)){assertEquals(4,repo.healthRecords().length());assertEquals(4,repo.store.pending("health").length());}
    }

    @Test public void removingWeightClosesTheStaleEditFormAndGoalRemainsReadOnly() throws Exception {
        try(CoachRepository repo=new CoachRepository(context)){long now=System.currentTimeMillis();repo.saveHealth(new JSONArray().put(au.com.endurancecoach.core.HealthRecords.weight(today,98,null,now)).put(au.com.endurancecoach.core.HealthRecords.goal(85,null,now)));}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create()){
            View root=c.get().getWindow().getDecorView();clickDescription(root,"Health");clickText(root,"Weight");
            assertNotNull(findText(root,"Weight goal · 85.0 kg"));assertNull(findText(root,"Set goal weight"));
            clickText(root,"Edit weight");org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog edit=ShadowAlertDialog.getLatestAlertDialog();clickText(edit.getWindow().getDecorView(),"Remove weight");
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(edit.isShowing());
            try(CoachRepository repo=new CoachRepository(context)){JSONArray current=repo.healthRecords();for(int i=0;i<current.length();i++)if(("weight:"+today).equals(current.getJSONObject(i).getString("record_id")))assertTrue(current.getJSONObject(i).getBoolean("deleted"));}
        }
    }

    @Test public void healthInvalidMealStaysOpenAndDoesNotSave() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root=c.get().getWindow().getDecorView();clickDescription(root,"Health");clickText(root,"Add meal"); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(dialog.isShowing());
            try(CoachRepository repo=new CoachRepository(context)){assertEquals(0,repo.healthRecords().length());}
        }
    }

    @Test @Config(qualifiers="w320dp-h640dp-xhdpi")
    public void healthHasReadableNavigationAndNativeChartsInBothThemes() throws Exception {
        try(CoachRepository repo=new CoachRepository(context)){
            long now=System.currentTimeMillis(); JSONArray records=new JSONArray();
            for(int i=13;i>=0;i--){LocalDate date=today.minusDays(i);if(i%3!=0)records.put(au.com.endurancecoach.core.HealthRecords.weight(date,100-i*.1,null,now));
                if(i%4!=0)records.put(au.com.endurancecoach.core.HealthRecords.meal(date,"Sample meal",400+i*20,1,"",null,now));}
            records.put(au.com.endurancecoach.core.HealthRecords.meal(today,"Synthetic preview meal",650,1,"",null,now));
            repo.saveHealth(records);
            LocalDate effective=today.minusDays(13);
            JSONObject target=new JSONObject().put("target_id","baseline-"+effective).put("revision",1).put("scope","baseline").put("date",effective.toString())
                    .put("calories_kcal",2300).put("protein_g",JSONObject.NULL).put("carbohydrate_g",JSONObject.NULL).put("fat_g",JSONObject.NULL)
                    .put("weight_goal_kg",JSONObject.NULL).put("weight_goal_date","").put("weight_change_kg_per_week",JSONObject.NULL)
                    .put("training_type","").put("exercise_included",true).put("source","Synthetic preview Coach")
                    .put("created_at",effective+"T00:00:00.000Z").put("reason","Synthetic preview target").put("status","approved");
            repo.store.acceptSnapshot(repo.snapshot().put("nutrition_targets",new JSONArray().put(target)),new JSONArray(),new JSONArray());
        }
        for(int mode:new int[]{0,1}){CoachTheme.save(context,mode);
            try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create()){
                View root=c.get().getWindow().getDecorView();clickDescription(root,"Health");clickText(root,"Diary");capture(root,CoachTheme.MODES[mode]+"-health",640,1280);
                assertNotNull(findText(root,"Logged calories"));assertNotNull(findText(root,"Breakfast · —"));assertNotNull(findText(root,"Target · 2300 kcal"));assertNotNull(findText(root,"Remaining · 1650 kcal"));
                clickText(root,"Weight");capture(root,CoachTheme.MODES[mode]+"-health-weight",640,1280);assertNotNull(findType(root,TrendView.class));
                android.widget.ScrollView scroll=(android.widget.ScrollView)findType(root,android.widget.ScrollView.class);
                View graph=findType(root,TrendView.class);android.graphics.Rect region=new android.graphics.Rect();graph.getDrawingRect(region);scroll.offsetDescendantRectToMyCoords(graph,region);
                scroll.scrollTo(0,Math.max(0,region.top-(int)(80*context.getResources().getDisplayMetrics().density)));
                capture(root,CoachTheme.MODES[mode]+"-health-weight-chart",640,1280);
                for(String label:new String[]{"Today","Plan","Progress","Insights","Health"}){
                    View nav=findDescription(root,label);assertTrue(label,nav.getWidth()>=96&&nav.getHeight()>=96);
                }
            }
        }
    }

    private void capture(View root, String name) throws Exception {
        capture(root, name, 786, 1704);
    }
    private void capture(View root, String name, int width, int height) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); root.draw(new Canvas(image));
        File folder = new File("build/ui-previews"); assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }
    private static boolean containsScroll(View v) {
        if (v instanceof android.widget.ScrollView) return true;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
            if (containsScroll(((ViewGroup) v).getChildAt(i))) return true;
        return false;
    }
    private static View findType(View v, Class<?> type) {
        if (type.isInstance(v)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View found = findType(((ViewGroup) v).getChildAt(i), type); if (found != null) return found;
        }
        return null;
    }
    private static void clickText(View root, String label) { View v = findText(root, label); assertNotNull(label, v); v.performClick(); }
    private static void clickDescription(View root, String label) {
        View v = findDescription(root, label); assertNotNull(label, v); v.performClick();
    }
    private static View findDescription(View v, String text) {
        if (text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) v).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static View findText(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View found = findText(((ViewGroup) v).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static String allText(View v) {
        StringBuilder text = new StringBuilder(); if (v instanceof TextView) text.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) text.append(allText(((ViewGroup) v).getChildAt(i)));
        return text.toString();
    }
}
