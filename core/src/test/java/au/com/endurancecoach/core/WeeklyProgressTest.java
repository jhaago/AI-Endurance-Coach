package au.com.endurancecoach.core;

import org.json.*;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class WeeklyProgressTest {
    private final LocalDate today = LocalDate.parse("2026-11-07");
    private JSONObject activity(String id, String instant, String type, Object metres, Object seconds, Object elevation) {
        return new JSONObject().put("activity_id", id).put("start_utc", instant).put("type", type)
                .put("distance_m", metres).put("duration_s", seconds).put("elevation_m", elevation);
    }
    private List<WeeklyProgress.Week> weeks(JSONArray a, String start, String end) {
        return WeeklyProgress.weeks(a, today, start, end);
    }
    @Test public void groupsAtMondayMidnightInPerth() {
        JSONArray a = new JSONArray().put(activity("1", "2026-11-01T15:59:59Z", "running", 2000, 900, 10))
                .put(activity("2", "2026-11-01T16:00:00Z", "running", 3000, 1200, 20));
        List<WeeklyProgress.Week> w = weeks(a, "2026-08-17", today.toString());
        assertEquals(12, w.size()); assertEquals(LocalDate.parse("2026-11-02"), w.get(11).start);
        assertEquals(3, w.get(11).km, 0); assertEquals(2, w.get(10).km, 0);
    }
    @Test public void countsRunningTypesOnceAndExcludesWalksAndFutureActivities() {
        JSONObject run = activity("1", "2026-11-03T00:00:00Z", "running", 10000, 3600, 100);
        JSONArray a = new JSONArray().put(run).put(new JSONObject(run.toString()))
                .put(activity("2", "2026-11-04T00:00:00Z", "trail_running", 5000, 2000, 250))
                .put(activity("3", "2026-11-05T00:00:00Z", "treadmill_running", 2000, 900, 0))
                .put(activity("4", "2026-11-05T00:00:00Z", "walking", 40000, 18000, 600))
                .put(activity("5", "2026-11-08T00:00:00Z", "running", 40000, 18000, 600));
        String before = a.toString(); WeeklyProgress.Week w = weeks(a, "2026-08-17", today.toString()).get(11);
        assertEquals(3, w.runs); assertEquals(17, w.km, 0); assertEquals(6500, w.seconds, 0);
        assertEquals(350, w.elevation, 0); assertEquals(before, a.toString());
    }
    @Test public void distinguishesVerifiedZeroWeeksFromUnknownHistory() {
        List<WeeklyProgress.Week> known = weeks(new JSONArray(), "2026-08-17", today.toString());
        List<WeeklyProgress.Week> unknown = weeks(new JSONArray(), "", "");
        assertTrue(known.get(0).covered); assertEquals(0, known.get(0).km, 0);
        assertFalse(unknown.get(0).covered); assertNull(unknown.get(0).km); assertNull(unknown.get(11).seconds);
    }
    @Test public void partialImportedWeekRetainsMeasuredTotalsAndIsFlagged() {
        JSONArray a = new JSONArray().put(activity("1", "2026-11-04T00:00:00Z", "running", 5000, 2000, 10));
        WeeklyProgress.Week w = weeks(a, "2026-11-04", today.toString()).get(11);
        assertFalse(w.covered); assertEquals(5, w.km, 0); assertEquals(1, w.runs);
        assertNull(weeks(new JSONArray(), "2026-11-04", today.toString()).get(11).km);
    }
    @Test public void missingMetricsDoNotBecomeZeroOrAnUndercountedTotal() {
        JSONArray a = new JSONArray().put(activity("1", "2026-11-04T00:00:00Z", "running", 5000, JSONObject.NULL, JSONObject.NULL))
                .put(activity("2", "2026-11-05T00:00:00Z", "running", JSONObject.NULL, 2000, 10));
        WeeklyProgress.Week w = weeks(a, "2026-08-17", today.toString()).get(11);
        assertNull(w.km); assertNull(w.seconds); assertNull(w.elevation); assertEquals(2, w.runs);
    }
    @Test public void currentWeekIsCoveredThroughTodayButStaleCoverageIsPartial() {
        assertTrue(weeks(new JSONArray(), "2026-08-17", today.toString()).get(11).covered);
        assertFalse(weeks(new JSONArray(), "2026-08-17", "2026-11-06").get(11).covered);
    }
    @Test public void backfillsTwelveWeeksOnceAndThenUsesNormalOverlap() {
        assertEquals(LocalDate.parse("2026-08-17"), WeeklyProgress.importStart(today, "2026-11-07T00:00:00Z", ""));
        assertEquals(LocalDate.parse("2026-11-01"), WeeklyProgress.importStart(today, "2026-11-07T00:00:00Z", "2026-08-17"));
        assertEquals(LocalDate.parse("2026-08-17"), WeeklyProgress.importStart(today, "2026-11-07T00:00:00Z", "2026-10-01"));
    }
    @Test public void preservesLongOfflineCatchupBeyondTheDisplayHorizon() {
        assertEquals(LocalDate.parse("2026-07-26"), WeeklyProgress.importStart(today, "2026-08-01T00:00:00Z", "2026-06-01"));
    }
}
