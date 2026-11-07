package au.com.endurancecoach.core;
import org.json.*;

/** Preserve same-day known measurements when a new response is partial or unavailable. */
public final class RecoveryRecord {
 private RecoveryRecord(){}
 public static JSONObject merge(JSONObject fresh,JSONObject known){
  JSONObject result=new JSONObject(fresh.toString());JSONArray retained=new JSONArray();
  if(known!=null&&fresh.has("date")&&fresh.optString("date").equals(known.optString("date"))){
   for(String key:new String[]{"sleep_hours","sleep_score","hrv_ms","resting_hr"}){
    Object previous=known.opt(key);boolean valid=previous instanceof Number&&Double.isFinite(((Number)previous).doubleValue())&&((Number)previous).doubleValue()>=(key.equals("sleep_score")?0:0.00001);
    if(result.isNull(key)&&valid){result.put(key,previous);retained.put(key);}
   }
  }
  if(retained.length()>0)result.put("retained_fields",retained);return result;
 }
}
