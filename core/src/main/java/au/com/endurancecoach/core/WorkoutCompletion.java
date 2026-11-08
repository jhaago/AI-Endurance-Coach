package au.com.endurancecoach.core;
import org.json.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
/** Completion records actual outcomes without rewriting planned targets. */
public final class WorkoutCompletion {
 private WorkoutCompletion(){}
 private static final DateTimeFormatter TIME=new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
 public static JSONObject change(String id,String status,Double minutes,String note,JSONObject previous,long now){
  if(id==null||!id.matches("[a-zA-Z0-9_:.\\-]{1,100}")||!Arrays.asList("completed","skipped","scheduled").contains(status))throw new IllegalArgumentException("Invalid workout status");
  if(note==null||note.length()>1000)throw new IllegalArgumentException("Keep completion notes under 1,000 characters");
  if(minutes!=null&&(!Double.isFinite(minutes)||minutes<0||minutes>10080||!status.equals("completed")))throw new IllegalArgumentException("Enter a valid actual duration");
  try{long stamp=previous==null?now:Math.max(now,Instant.parse(previous.getString("updated_at")).toEpochMilli()+1);return new JSONObject().put("change_id",UUID.randomUUID().toString()).put("workout_id",id).put("status",status).put("actual_duration_min",minutes==null?JSONObject.NULL:minutes).put("note",note).put("updated_at",TIME.format(Instant.ofEpochMilli(stamp)));}catch(Exception e){throw new IllegalArgumentException("Invalid previous completion",e);}
 }
 public static JSONArray current(JSONArray... sources){TreeMap<String,JSONObject> rows=new TreeMap<>();try{for(JSONArray source:sources)for(int i=0;i<source.length();i++){JSONObject r=source.getJSONObject(i);String id=r.getString("workout_id");JSONObject old=rows.get(id);Instant time=Instant.parse(r.getString("updated_at"));if(old==null||time.isAfter(Instant.parse(old.getString("updated_at")))||(time.equals(Instant.parse(old.getString("updated_at")))&&r.getString("change_id").compareTo(old.getString("change_id"))>0))rows.put(id,new JSONObject(r.toString()));}return new JSONArray(new ArrayList<>(rows.values()));}catch(Exception e){throw new IllegalArgumentException("Invalid completion history",e);}}
 public static final class Result {public final String status,label,source;public final Double actualMinutes;public final boolean completed,skipped;private Result(String status,String label,Double minutes,String source){this.status=status;this.label=label;actualMinutes=minutes;this.source=source;completed=status.equals("completed");skipped=status.equals("skipped");}}
 public static Result evaluate(JSONObject workout,JSONObject activity,JSONObject manual){String status=manual==null?"scheduled":manual.optString("status","scheduled");if(status.equals("skipped"))return new Result(status,"Skipped",null,"Manual");boolean explicit=status.equals("completed");Double minutes=null;String source=explicit?"Manual":"Garmin";if(explicit&&!manual.isNull("actual_duration_min")){double n=manual.optDouble("actual_duration_min",Double.NaN);if(Double.isFinite(n)&&n>=0)minutes=n;}if(minutes==null&&activity!=null){double n=activity.optDouble("duration_s",Double.NaN);if(Double.isFinite(n)&&n>=0){minutes=n/60;source="Garmin";}}if(!explicit&&activity==null)return new Result("scheduled","",null,"");String label="Completed";int planned=workout.optInt("duration_min");if(minutes!=null&&planned>0){long rounded=Math.round(minutes);if(rounded<planned)label+=" · shortened";else if(rounded>planned)label+=" · longer";}return new Result("completed",label,minutes,source);}
}
