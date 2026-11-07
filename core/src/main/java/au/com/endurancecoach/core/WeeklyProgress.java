package au.com.endurancecoach.core;

import org.json.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Actual running only. A zero requires verified import coverage; a missing metric stays missing. */
public final class WeeklyProgress {
    private WeeklyProgress() {}
    public static final class Week {
        public final LocalDate start;
        public final int runs;
        public final Double km, seconds, elevation;
        public final boolean covered;
        private Week(LocalDate start, Bucket b, boolean covered) {
            this.start = start; this.runs = b.runs; this.covered = covered;
            boolean available = covered || runs > 0;
            km = available && b.distanceKnown ? b.metres / 1000 : null;
            seconds = available && b.timeKnown ? b.seconds : null;
            elevation = available && b.elevationKnown ? b.elevation : null;
        }
    }
    private static final class Bucket {
        int runs;
        double metres, seconds, elevation;
        boolean distanceKnown = true, timeKnown = true, elevationKnown = true;
    }
    public static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public static LocalDate historyStart(LocalDate today) { return monday(today).minusWeeks(11); }
    /** Preserve an older offline catch-up start; otherwise backfill the visible horizon once. */
    public static LocalDate importStart(LocalDate today, String lastSync, String coverageStart) {
        LocalDate normal = SyncWindow.start(today, lastSync, 6, 30), target = historyStart(today);
        LocalDate known = date(coverageStart);
        return (known == null || known.isAfter(target)) && normal.isAfter(target) ? target : normal;
    }
    public static List<Week> weeks(JSONArray activities, LocalDate today, String coverageStart, String coverageEnd) {
        LocalDate start = historyStart(today), knownStart = date(coverageStart), knownEnd = date(coverageEnd);
        Bucket[] buckets = new Bucket[12]; for (int i = 0; i < 12; i++) buckets[i] = new Bucket();
        Map<String, JSONObject> distinct = new LinkedHashMap<>();
        for (int i = 0; i < activities.length(); i++) {
            JSONObject a = activities.getJSONObject(i); distinct.put(a.getString("activity_id"), a);
        }
        for (JSONObject a : distinct.values()) {
            String type = a.optString("type").toLowerCase(Locale.ROOT);
            if (!type.contains("running") && !type.equals("run") && !type.endsWith("_run")) continue;
            LocalDate day = LocalDate.parse(PlanEngine.day(a));
            if (day.isBefore(start) || day.isAfter(today)) continue;
            int index = (int) java.time.temporal.ChronoUnit.WEEKS.between(start, monday(day));
            Bucket b = buckets[index]; b.runs++;
            Double distance = measurement(a, "distance_m"), duration = measurement(a, "duration_s"), elevation = measurement(a, "elevation_m");
            if (distance == null) b.distanceKnown = false; else b.metres += distance;
            if (duration == null) b.timeKnown = false; else b.seconds += duration;
            if (elevation == null) b.elevationKnown = false; else b.elevation += elevation;
        }
        List<Week> result = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate day = start.plusWeeks(i), end = day.plusDays(6); if (end.isAfter(today)) end = today;
            boolean covered = knownStart != null && knownEnd != null && !knownStart.isAfter(day) && !knownEnd.isBefore(end);
            result.add(new Week(day, buckets[i], covered));
        }
        return Collections.unmodifiableList(result);
    }
    private static Double measurement(JSONObject a, String key) {
        Object value = a.opt(key); if (!(value instanceof Number)) return null;
        double n = ((Number) value).doubleValue(); return Double.isFinite(n) && n >= 0 ? n : null;
    }
    private static LocalDate date(String iso) {
        try { return LocalDate.parse(iso); } catch (Exception e) { return null; }
    }
}
