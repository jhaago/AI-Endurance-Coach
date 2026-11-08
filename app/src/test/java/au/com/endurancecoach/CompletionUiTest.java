package au.com.endurancecoach;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.LocalDate;
import static org.junit.Assert.*;

/** Synthetic offline completion UI checks: actual outcomes never rewrite planned targets. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35}, qualifiers = "w320dp-h640dp-xhdpi")
public class CompletionUiTest {
    private Context context;
    private LocalDate today;

    @Before public void seed() throws Exception {
        context = RuntimeEnvironment.getApplication(); today = LocalDate.now(PlanEngine.ZONE);
        context.getSharedPreferences("appearance", 0).edit().clear().commit();
        save(new JSONArray().put(workout("session", today, "Synthetic session", "run", 30)), new JSONArray(), new JSONArray());
    }
    private JSONObject workout(String id, LocalDate day, String title, String type, int minutes) throws Exception {
        return new JSONObject().put("workout_id", id).put("event_id", "synthetic-event").put("revision", 1)
                .put("date", day.toString()).put("type", type).put("title", title).put("status", "scheduled")
                .put("duration_min", minutes).put("intensity", "Synthetic instructions").put("optional", true);
    }
    private JSONObject activity(Object seconds) throws Exception {
        return new JSONObject().put("activity_id", "synthetic-activity").put("type", "running")
                .put("name", "Synthetic Garmin activity").put("start_utc", today + "T02:00:00Z")
                .put("duration_s", seconds).put("distance_m", 5000);
    }
    private JSONObject manual(String status, Object minutes) throws Exception {
        return new JSONObject().put("change_id", "synthetic-completion").put("workout_id", "session").put("status", status)
                .put("actual_duration_min", minutes).put("note", "Synthetic completion note").put("updated_at", "2026-01-01T12:00:00.000Z");
    }
    private void save(JSONArray plan, JSONArray activities, JSONArray statuses) throws Exception {
        try (LocalStore store = new LocalStore(context)) {
            store.put("snapshot", new JSONObject().put("events", new JSONArray().put(new JSONObject()
                    .put("event_id", "synthetic-event").put("name", "Synthetic event").put("active", true)))
                    .put("plan", plan).put("activities", activities).put("workout_status", statuses));
        }
    }
    @Test public void matchedActivityShowsShortenedLongerAndUnknownActualSeparatelyFromPlan() throws Exception {
        Object[] seconds = {1200, 2400, JSONObject.NULL};
        String[] labels = {"Completed · shortened", "Completed · longer", "Completed"};
        String[] actuals = {"Actual · 20 min", "Actual · 40 min", "Actual · —"};
        for (int i = 0; i < seconds.length; i++) {
            save(new JSONArray().put(workout("session", today, "Synthetic session", "run", 30)), new JSONArray().put(activity(seconds[i])), new JSONArray());
            try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
                View root = c.get().getWindow().getDecorView();
                assertNotNull(findText(root, "Synthetic session"));
                assertNotNull(findText(root, labels[i]));
                assertNotNull(findText(root, actuals[i]));
                clickDescription(root, "Plan");
                assertNotNull(findText(root, labels[i])); assertNotNull(findText(root, "Planned · 30 min")); assertNotNull(findText(root, actuals[i]));
                assertNull(findText(root, "Actual · 0 min"));
                clickText(root, "Workout details"); View details = dialog().getWindow().getDecorView();
                assertNotNull(findText(details, "Matched activity · Synthetic Garmin activity")); assertNotNull(findText(details, "View matched activity"));
            }
        }
    }
    @Test public void manualSkipWinsOverGarminAndTodayOffersTheNextScheduledSession() throws Exception {
        save(new JSONArray().put(workout("session", today, "Synthetic session", "run", 30))
                .put(workout("next", today.plusDays(1), "Synthetic next session", "run", 45)), new JSONArray().put(activity(1200)), new JSONArray().put(manual("skipped", JSONObject.NULL)));
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); assertNotNull(findText(root, "Synthetic next session")); assertNull(findText(root, "Synthetic session"));
            clickDescription(root, "Plan"); assertNotNull(findText(root, "Skipped")); clickText(root, "Workout details");
            View details = dialog().getWindow().getDecorView(); assertNotNull(findText(details, "Matched activity · Synthetic Garmin activity")); assertNotNull(findText(details, "Reset completion"));
        }
    }
    @Test public void completingARecoveryCheckInKeepsActualUnknownAndClosesParentDetails() throws Exception {
        save(new JSONArray().put(workout("session", today, "Synthetic recovery", "recovery", 0)), new JSONArray(), new JSONArray());
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); clickText(root, "Workout details"); AlertDialog parent = dialog();
            clickText(parent.getWindow().getDecorView(), "Mark completed"); idle(); AlertDialog form = dialog();
            assertNotNull(findDescription(form.getWindow().getDecorView(), "Actual minutes (optional)"));
            form.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); assertFalse(form.isShowing()); assertFalse(parent.isShowing());
            clickDescription(root, "Plan"); assertNotNull(findText(root, "Completed")); assertNotNull(findText(root, "Actual · —"));
            try (CoachRepository repo = new CoachRepository(context)) {
                JSONArray pending = repo.store.pending("workout_status"); assertEquals(1, pending.length()); assertTrue(pending.getJSONObject(0).isNull("actual_duration_min"));
                assertEquals(0, repo.store.pending("feedback").length()); assertEquals(1, repo.store.get("snapshot").getJSONArray("plan").length());
            }
        }
    }
    @Test public void invalidActualDurationKeepsTheFormOpenWithoutSaving() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); clickText(root, "Workout details"); clickText(dialog().getWindow().getDecorView(), "Mark completed"); idle();
            AlertDialog form = dialog(); ((EditText)findDescription(form.getWindow().getDecorView(), "Actual minutes (optional)")).setText("10081");
            form.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertTrue(form.isShowing());
            try (CoachRepository repo = new CoachRepository(context)) { assertEquals(0, repo.store.pending("workout_status").length()); }
        }
    }
    @Test public void skipThenResetRestoresTheScheduledSessionWithoutDeletingStatusHistory() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); clickText(root, "Workout details"); AlertDialog parent = dialog();
            clickText(parent.getWindow().getDecorView(), "Skip session"); idle(); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); assertFalse(parent.isShowing());
            assertNull(findText(root, "Synthetic session")); clickDescription(root, "Plan"); assertNotNull(findText(root, "Skipped")); clickText(root, "Workout details");
            parent = dialog(); clickText(parent.getWindow().getDecorView(), "Reset completion"); idle(); assertFalse(parent.isShowing());
            clickDescription(root, "Today"); assertNotNull(findText(root, "Synthetic session"));
            try (CoachRepository repo = new CoachRepository(context)) { JSONArray queued = repo.store.pending("workout_status"); assertEquals(2, queued.length()); assertEquals("scheduled", queued.getJSONObject(1).getString("status")); assertTrue(queued.getJSONObject(1).isNull("actual_duration_min")); }
        }
    }
    @Test public void futureWorkoutHasNoCompletionControlsOrCompletionCheckbox() throws Exception {
        save(new JSONArray().put(workout("session", today.plusDays(1), "Synthetic future session", "run", 30)), new JSONArray(), new JSONArray());
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            clickText(c.get().getWindow().getDecorView(), "Workout details"); View details = dialog().getWindow().getDecorView();
            assertNull(findText(details, "Mark completed")); assertNull(findText(details, "Skip session")); assertNull(findText(details, "Reset completion"));
            clickText(details, "Add feedback"); assertNull(findText(dialog().getWindow().getDecorView(), "Mark session completed"));
        }
    }
    @Test public void feedbackCompletionIsUncheckedByDefaultAndOnlyExplicitChoiceQueuesCompletion() throws Exception {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create()) {
            View root = c.get().getWindow().getDecorView(); clickText(root, "Workout details"); AlertDialog parent = dialog(); clickText(parent.getWindow().getDecorView(), "Add feedback"); idle();
            CheckBox complete = (CheckBox)findText(dialog().getWindow().getDecorView(), "Mark session completed"); assertNotNull(complete); assertFalse(complete.isChecked());
            dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); assertFalse(parent.isShowing());
            try (CoachRepository repo = new CoachRepository(context)) { assertEquals(1, repo.store.pending("feedback").length()); assertEquals(0, repo.store.pending("workout_status").length()); }
            clickText(root, "Workout details"); parent = dialog(); clickText(parent.getWindow().getDecorView(), "Add feedback"); idle();
            ((CheckBox)findText(dialog().getWindow().getDecorView(), "Mark session completed")).setChecked(true); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); assertFalse(parent.isShowing());
            try (CoachRepository repo = new CoachRepository(context)) { assertEquals(2, repo.store.pending("feedback").length()); assertEquals(1, repo.store.pending("workout_status").length()); }
        }
    }
    private static void idle() { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }
    private static AlertDialog dialog() { return ShadowAlertDialog.getLatestAlertDialog(); }
    private static void clickText(View root, String text) { View found = findText(root, text); assertNotNull(text, found); found.performClick(); }
    private static void clickDescription(View root, String text) { View found = findDescription(root, text); assertNotNull(text, found); found.performClick(); }
    private static View findDescription(View root, String text) {
        if (text.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=findDescription(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found; } return null;
    }
    private static View findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView)root).getText())) return root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=findText(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found; } return null;
    }
}
