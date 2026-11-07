package au.com.endurancecoach.core;

import org.json.*;
import java.io.IOException;
import java.time.LocalDate;

/** Documented daily endpoints, then documented same-date summary endpoints; no private Connect API. */
public final class RecoveryReader {
 private final GarminClient client;
 public RecoveryReader(GarminClient client){this.client=client;}
 public static final class Result {
  public final JSONObject data,raw;public final String note;public final boolean retry;
  Result(JSONObject data,JSONObject raw,String note,boolean retry){this.data=data;this.raw=raw;this.note=note;this.retry=retry;}
 }
 private static final class Attempt {
  final int status;final Object value;final String note;final boolean retry;
  Attempt(int status,Object value,String note,boolean retry){this.status=status;this.value=value;this.note=note;this.retry=retry;}
 }
 private Attempt fetch(String path) {
  try{GarminClient.JsonResult r=client.inspectJson(path);return new Attempt(r.status,r.value,"HTTP "+r.status+" "+r.shape(),false);}
  catch(Transport.HttpFailure e){return new Attempt(e.status,JSONObject.NULL,"HTTP "+e.status,e.status==401||e.status==408||e.status==429||e.status>=500);}
  catch(IOException e){return new Attempt(0,JSONObject.NULL,"network or invalid JSON response",true);}
 }
 public Result read(String kind,String date) {
  LocalDate.parse(date);String daily,summary;
  switch(kind){
   case "sleep":daily="/sleep-service/sleep/dailySleep?date="+date;summary="/sleep-service/sleep/dailySleepSummariesByDate?startDate="+date+"&endDate="+date;break;
   case "hrv":daily="/hrv-service/hrv/"+date;summary="/hrv-service/hrv/daily/"+date+"/"+date;break;
   case "heart":daily="/wellness-service/wellness/dailyHeartRate?date="+date;summary="/usersummary-service/v2/dailysummaries?startDate="+date+"&endDate="+date;break;
   default:throw new IllegalArgumentException("Unknown recovery dataset");
  }
  Attempt first=fetch(daily);JSONObject raw=new JSONObject().put("daily",first.value);JSONObject data=selectDaily(kind,date,first.value);String note=label(kind)+": daily "+first.note;
  if(measured(kind,data))return new Result(data,raw,note+"; available",false);
  // Do not multiply rate-limited, offline, authentication or server-failure requests.
  if(first.status==0||first.status==401||first.status==429||first.status>=500)return new Result(new JSONObject(),raw,note+"; unavailable",first.retry);
  Attempt fallback=fetch(summary);raw.put("summary",fallback.value);data=selectSummary(kind,date,fallback.value);
  if(kind.equals("heart"))note+="; summary resting/minimum values are not used as confirmed resting HR";
  return new Result(data,raw,note+"; summary "+fallback.note+(measured(kind,data)?"; available":"; unavailable"),first.retry||fallback.retry);
 }
 private static String label(String kind){return kind.equals("heart")?"Resting HR":kind.equals("hrv")?"HRV":"Sleep";}
 private JSONObject selectDaily(String kind,String date,Object value){
  if(!(value instanceof JSONObject))return new JSONObject();JSONObject obj=(JSONObject)value;
  if(kind.equals("sleep")&&obj.optJSONObject("dailySleepDTO")!=null)obj=obj.optJSONObject("dailySleepDTO");
  JSONObject dated=kind.equals("hrv")?obj.optJSONObject("hrvSummary"):obj;
  if(dated!=null&&dated.has("calendarDate")&&!date.equals(dated.optString("calendarDate")))return new JSONObject();
  return measured(kind,obj)?obj:new JSONObject();
 }
 private JSONObject selectSummary(String kind,String date,Object value){
  JSONArray rows=null;
  if(kind.equals("sleep")&&value instanceof JSONArray)rows=(JSONArray)value;
  if(value instanceof JSONObject)rows=((JSONObject)value).optJSONArray(kind.equals("hrv")?"hrvSummaries":"data");
  if(rows==null)return new JSONObject();
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.optJSONObject(i);if(row==null||!date.equals(row.optString("calendarDate")))continue;
   JSONObject data=row;
   if(kind.equals("hrv"))data=new JSONObject().put("hrvSummary",row);
   // Garmin's summary restingValue may be an absolute minimum; retain raw only, never label it measured resting HR.
   if(kind.equals("heart"))data=new JSONObject();
   if(measured(kind,data))return data;
  }
  return new JSONObject();
 }
 public static boolean measured(String kind,JSONObject obj){
  if(kind.equals("sleep")){JSONObject scores=obj.optJSONObject("sleepScores"),overall=scores==null?null:scores.optJSONObject("overall");return obj.optDouble("sleepTimeSeconds",0)>0||(overall!=null&&overall.optDouble("value",-1)>=0&&overall.optDouble("value",101)<=100);}
  if(kind.equals("hrv")){JSONObject h=obj.optJSONObject("hrvSummary");return h!=null&&h.optDouble("lastNightAvg",0)>0;}
  return obj.optDouble("restingHeartRate",0)>0;
 }
}
