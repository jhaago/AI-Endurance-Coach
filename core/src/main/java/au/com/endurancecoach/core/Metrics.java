package au.com.endurancecoach.core;

import org.json.*;
import java.time.*;

public final class Metrics {
    private Metrics() {}
    private static JSONObject object(JSONObject parent,String key) { JSONObject o=parent.optJSONObject(key); return o==null?new JSONObject():o; }
    private static Object positive(JSONObject source,String key){Object v=source.opt(key); return v instanceof Number && ((Number)v).doubleValue()>0?v:JSONObject.NULL;}
    public static JSONObject wellness(String date,JSONObject sleep,JSONObject hrv,JSONObject hr) {
        JSONObject s=sleep.optJSONObject("dailySleepDTO");if(s==null)s=sleep;
        JSONObject h=object(hrv,"hrvSummary");
        Object seconds=positive(s,"sleepTimeSeconds");
        return new JSONObject().put("date",date).put("sleep_hours",seconds==JSONObject.NULL?JSONObject.NULL:((Number)seconds).doubleValue()/3600)
            .put("sleep_score",positive(object(object(s,"sleepScores"),"overall"),"value"))
            .put("hrv_ms",positive(h,"lastNightAvg")).put("resting_hr",positive(hr,"restingHeartRate"))
            .put("coverage_note","Watch removed during work; daytime metrics may be incomplete.");
    }
    public static JSONObject activity(JSONObject source) {
        JSONObject s=source.optJSONObject("summaryDTO"); if(s==null)s=source;
        String utc=s.optString("startTimeGMT",source.optString("startTimeGMT"));
        Object rawTime=s.opt("startTimeGMT");
        if(rawTime instanceof Number)utc=Instant.ofEpochMilli(((Number)rawTime).longValue()).toString();
        else {utc=utc.replace(' ','T');if(!utc.endsWith("Z") && !utc.contains("+"))utc+="Z"; utc=Instant.parse(utc).toString();}
        double distance=s.optDouble("distance",Double.NaN), duration=s.optDouble("duration",Double.NaN);
        JSONObject type=source.optJSONObject("activityTypeDTO");if(type==null)type=object(source,"activityType");
        return new JSONObject().put("activity_id",String.valueOf(source.get("activityId"))).put("name",source.optString("activityName","Garmin activity"))
            .put("start_utc",utc).put("type",type.optString("typeKey","unknown"))
            .put("distance_m",finite(distance)).put("duration_s",finite(duration)).put("pace_s_km",finite(distance>0?duration*1000/distance:Double.NaN))
            .put("average_hr",positive(s,"averageHR")).put("elevation_m",s.has("elevationGain")?s.opt("elevationGain"):JSONObject.NULL)
            .put("cadence_spm",s.has("averageRunCadence")?positive(s,"averageRunCadence"):positive(s,"averageRunningCadenceInStepsPerMinute"));
    }
    private static Object finite(double value){return Double.isFinite(value) && value>=0?value:JSONObject.NULL;}
}
