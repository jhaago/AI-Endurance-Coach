package au.com.endurancecoach.core;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PlanEngineTest {
    JSONObject w(String id, int revision, String date, String type) {
        return new JSONObject().put("workout_id",id).put("revision",revision).put("date",date).put("type",type).put("status","scheduled");
    }
    JSONObject a(String id, String instant) {
        return new JSONObject().put("activity_id",id).put("start_utc",instant).put("type","running").put("duration_s",1800);
    }
    @Test public void latestRevisionWinsAndCancellationIsRetainedInLedger() {
        JSONArray rows = new JSONArray().put(w("w",1,"2026-11-10","run")).put(w("w",2,"2026-11-11","run"));
        assertEquals("2026-11-11",PlanEngine.current(rows).getJSONObject(0).getString("date"));
        rows.put(w("w",3,"2026-11-11","run").put("status","cancelled"));
        assertEquals(0,PlanEngine.current(rows).length()); assertEquals(3,rows.length());
    }
    @Test public void identicalRevisionWithDifferentContentIsRejected() {
        JSONArray rows = new JSONArray().put(w("w",1,"2026-11-10","run")).put(w("w",1,"2026-11-11","run"));
        assertThrows(IllegalArgumentException.class,()->PlanEngine.current(rows));
    }
    @Test public void matchesUsingPerthDateAndNeverMutatesOriginalActivity() {
        JSONArray activities=new JSONArray().put(a("1","2026-11-09T22:00:00Z"));
        JSONObject matches=PlanEngine.matches(new JSONArray().put(w("w",1,"2026-11-10","run")),activities,new JSONObject());
        assertEquals("w",matches.getString("1")); assertFalse(activities.getJSONObject(0).has("workout_id"));
    }
    @Test public void ambiguousActivitiesAndWorkoutsRemainUnmatched() {
        JSONArray plan=new JSONArray().put(w("w",1,"2026-11-10","run"));
        JSONArray activities=new JSONArray().put(a("1","2026-11-10T00:00:00Z")).put(a("2","2026-11-10T02:00:00Z"));
        assertEquals(0,PlanEngine.matches(plan,activities,new JSONObject()).length());
        plan.put(w("w2",1,"2026-11-10","run"));
        assertEquals(0,PlanEngine.matches(plan,new JSONArray().put(activities.get(0)),new JSONObject()).length());
    }
    @Test public void explicitUnmatchSurvivesAutomaticMatching() {
        assertEquals(0,PlanEngine.matches(new JSONArray().put(w("w",1,"2026-11-10","run")),new JSONArray().put(a("1","2026-11-10T00:00:00Z")),new JSONObject().put("1", "")).length());
    }
    @Test public void restIsNeverMatchedToRunning() {
        assertEquals(0,PlanEngine.matches(new JSONArray().put(w("w",1,"2026-11-10","recovery")),new JSONArray().put(a("1","2026-11-10T00:00:00Z")),new JSONObject()).length());
    }
    @Test public void futureEventPlanDoesNotMixPreviousEventWorkouts() {
        JSONArray ledger=new JSONArray().put(w("old",1,"2027-03-15","run").put("event_id","herdys"))
            .put(w("new",1,"2027-03-16","run").put("event_id","future"));
        JSONArray result=PlanEngine.forEvent(ledger,"future");assertEquals(1,result.length());assertEquals("new",result.getJSONObject(0).getString("workout_id"));
    }
    @Test public void conflictingManualMatchesReserveWorkoutAgainstThirdAutomaticMatch() {
        JSONArray p=new JSONArray().put(w("w",1,"2026-11-10","run"));
        JSONArray a=new JSONArray().put(a("1","2026-11-10T00:00:00Z")).put(a("2","2026-11-10T01:00:00Z")).put(a("3","2026-11-10T02:00:00Z"));
        assertEquals(0,PlanEngine.matches(p,a,new JSONObject().put("1","w").put("2","w")).length());
    }
}
