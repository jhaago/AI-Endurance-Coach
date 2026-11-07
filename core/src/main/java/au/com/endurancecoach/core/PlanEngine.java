package au.com.endurancecoach.core;

import org.json.*;
import java.time.*;
import java.util.*;

public final class PlanEngine {
    public static final ZoneId ZONE=ZoneId.of("Australia/Perth");
    private PlanEngine() {}
    public static JSONArray current(JSONArray ledger) {
        Map<String,JSONObject> latest=new HashMap<>(); Map<String,String> versions=new HashMap<>();
        for(int i=0;i<ledger.length();i++) {
            JSONObject row=ledger.getJSONObject(i); String id=row.getString("workout_id"); int rev=row.getInt("revision");
            if(id.isEmpty() || rev<1) throw new IllegalArgumentException("Invalid plan revision");
            LocalDate.parse(row.getString("date"));
            String version=id+":"+rev; String canonical=canonical(row);
            if(versions.containsKey(version) && !versions.get(version).equals(canonical)) throw new IllegalArgumentException("Conflicting plan revision for "+id);
            versions.put(version,canonical);
            if(!latest.containsKey(id) || latest.get(id).getInt("revision")<rev) latest.put(id,row);
        }
        List<JSONObject> rows=new ArrayList<>();
        for(JSONObject row:latest.values()) if(!row.optString("status").equals("cancelled")) rows.add(row);
        rows.sort(Comparator.comparing((JSONObject r)->r.getString("date")).thenComparing(r->r.getString("workout_id")));
        return new JSONArray(rows);
    }
    private static String canonical(JSONObject row) {
        List<String> keys=keys(row); Collections.sort(keys); StringBuilder b=new StringBuilder();
        for(String k:keys)b.append(k).append('=').append(row.get(k)).append('\n'); return b.toString();
    }
    public static String day(JSONObject activity) { return Instant.parse(activity.getString("start_utc")).atZone(ZONE).toLocalDate().toString(); }
    public static boolean compatible(String workout,String activity) {
        String a=activity.toLowerCase(Locale.ROOT);
        return (workout.equals("run") && (a.contains("running") || a.equals("run"))) || (workout.equals("strength") && a.contains("strength"));
    }
    /** Conservatively infer only unique one-to-one, same-day/type matches. Explicit empty overrides mean unmatched. */
    public static JSONObject matches(JSONArray ledger,JSONArray activities,JSONObject overrides) {
        JSONArray plan=current(ledger); JSONObject result=new JSONObject(); Set<String> used=new HashSet<>();
        Set<String> existingActivities=new HashSet<>(); for(int i=0;i<activities.length();i++)existingActivities.add(activities.getJSONObject(i).getString("activity_id"));
        Map<String,Integer> overrideCounts=new HashMap<>();
        for(String id:keys(overrides)) { String w=overrides.optString(id); if(existingActivities.contains(id) && !w.isEmpty())overrideCounts.put(w,overrideCounts.getOrDefault(w,0)+1); }
        used.addAll(overrideCounts.keySet());
        for(int i=0;i<activities.length();i++) {
            String id=activities.getJSONObject(i).getString("activity_id"); String w=overrides.optString(id);
            if(!w.isEmpty() && overrideCounts.get(w)==1) { for(int p=0;p<plan.length();p++) if(plan.getJSONObject(p).getString("workout_id").equals(w)){result.put(id,w);used.add(w);} }
        }
        for(int i=0;i<activities.length();i++) {
            JSONObject a=activities.getJSONObject(i); String id=a.getString("activity_id"); if(overrides.has(id))continue;
            List<String> candidates=new ArrayList<>();
            for(int p=0;p<plan.length();p++) {
                JSONObject w=plan.getJSONObject(p); String wid=w.getString("workout_id");
                if(used.contains(wid) || !w.getString("date").equals(day(a)) || !compatible(w.getString("type"),a.optString("type")))continue;
                int count=0;
                for(int j=0;j<activities.length();j++){JSONObject other=activities.getJSONObject(j); if(!overrides.has(other.getString("activity_id")) && day(other).equals(w.getString("date")) && compatible(w.getString("type"),other.optString("type")))count++;}
                if(count==1)candidates.add(wid);
            }
            if(candidates.size()==1){result.put(id,candidates.get(0));used.add(candidates.get(0));}
        }
        return result;
    }
    public static JSONArray forEvent(JSONArray ledger,String eventId) {
        JSONArray all=current(ledger),filtered=new JSONArray();
        for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optString("event_id").equals(eventId))filtered.put(all.getJSONObject(i));
        return filtered;
    }
    private static List<String> keys(JSONObject value){List<String> result=new ArrayList<>();for(Iterator<String> i=value.keys();i.hasNext();)result.add(i.next());return result;}
}
