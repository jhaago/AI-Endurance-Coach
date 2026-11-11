package au.com.endurancecoach.core;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/** Read-only Coach estimates. Food and targets are never changed by this model. */
public final class DietQuality {
 private DietQuality(){}
 public static final String[] HEADERS={"assessment_id","revision","date","status","score","fruit_veg_points","fibre_points","protein_calcium_points","fat_quality_points","discretionary_points","confidence","summary","strength","improvement","fruit_veg_reason","fibre_reason","protein_calcium_reason","fat_quality_reason","discretionary_reason","food_change_ids","source","assessed_at","rubric_version"};
 public static final String[] COMPONENTS={"fruit_veg","fibre","protein_calcium","fat_quality","discretionary"};
 public static final String[] LABELS={"Fruit and vegetables","Fibre-rich foods","Protein and calcium sources","Fat quality","Discretionary-food balance"};
 public static final int[] MAXIMUMS={30,20,20,15,15};
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Diet quality assessments need attention in Sheets");}
 private static String text(JSONObject r,String key,int max){Object v=r.opt(key);if(!(v instanceof String)||((String)v).trim().isEmpty()||((String)v).length()>max)throw invalid();return (String)v;}
 private static int integer(JSONObject r,String key,int min,int max){Object v=r.opt(key);if(!(v instanceof Number))throw invalid();double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<min||n>max)throw invalid();return (int)n;}
 private static JSONObject normalise(JSONObject r){
  try{
   JSONObject out=new JSONObject();String date=text(r,"date",10);if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!LocalDate.parse(date).toString().equals(date))throw invalid();
   String id=text(r,"assessment_id",100);if(!id.equals("diet-quality-"+date))throw invalid();
   String status=text(r,"status",16),confidence=text(r,"confidence",8);
   if(!Arrays.asList("provisional","complete","withdrawn").contains(status)||!Arrays.asList("low","medium","high").contains(confidence)||!text(r,"rubric_version",16).equals("dq-v1"))throw invalid();
   String stamp=text(r,"assessed_at",24);if(!stamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")||!new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.parse(stamp)).equals(stamp))throw invalid();
   String ids=text(r,"food_change_ids",10100),previous="";String[] parts=ids.split("\\|",-1);if(parts.length>100)throw invalid();
   for(String part:parts){if(!part.matches("[a-zA-Z0-9_:.\\-]{1,100}")||part.compareTo(previous)<=0)throw invalid();previous=part;}
   out.put("assessment_id",id).put("date",date).put("revision",integer(r,"revision",1,Integer.MAX_VALUE)).put("status",status).put("confidence",confidence).put("assessed_at",stamp).put("food_change_ids",ids).put("rubric_version","dq-v1");
   int sum=0;for(int i=0;i<COMPONENTS.length;i++){int points=integer(r,COMPONENTS[i]+"_points",0,MAXIMUMS[i]);sum+=points;out.put(COMPONENTS[i]+"_points",points).put(COMPONENTS[i]+"_reason",text(r,COMPONENTS[i]+"_reason",500));}
   int score=integer(r,"score",0,100);if(score!=sum)throw invalid();out.put("score",score);
   for(String key:new String[]{"summary","strength","improvement","source"})out.put(key,text(r,key,key.equals("source")?120:key.equals("summary")?1000:500));
   return out;
  }catch(Exception e){throw invalid();}
 }
 public static JSONArray validate(JSONArray ledger){
  if(ledger==null)throw invalid();TreeMap<String,JSONObject> unique=new TreeMap<>();HashMap<String,String> fingerprints=new HashMap<>();
  try{for(int i=0;i<ledger.length();i++){JSONObject row=normalise(ledger.getJSONObject(i));String key=row.getString("assessment_id")+":"+row.getInt("revision");JSONArray values=new JSONArray();for(String field:HEADERS)values.put(row.get(field));String fingerprint=values.toString();if(fingerprints.containsKey(key)&&!fingerprints.get(key).equals(fingerprint))throw invalid();fingerprints.put(key,fingerprint);unique.put(key,row);}return new JSONArray(new ArrayList<>(unique.values()));}catch(Exception e){throw invalid();}
 }
 public static String foodChangeIds(JSONArray health,LocalDate day){
  JSONArray current=HealthRecords.current(health);TreeSet<String> ids=new TreeSet<>();
  for(int i=0;i<current.length();i++){JSONObject row=current.optJSONObject(i);if(row!=null&&"meal".equals(row.optString("kind"))&&!row.optBoolean("deleted")&&day.toString().equals(row.optString("date")))ids.add(row.optString("change_id"));}
  return String.join("|",ids);
 }
 /** Non-empty snapshots must extend immutable history, never silently roll it back. */
 public static JSONArray importLedger(JSONArray previous,JSONArray incoming){
  JSONArray fresh=validate(incoming);if(fresh.length()==0)return fresh;
  JSONArray combined=new JSONArray();for(JSONArray rows:new JSONArray[]{validate(previous),fresh})for(int i=0;i<rows.length();i++)combined.put(rows.optJSONObject(i));
  if(validate(combined).length()!=fresh.length())throw invalid();return fresh;
 }
 public static final class Assessment {
  public final String date,status,confidence,summary,strength,improvement,source,assessedAt;
  public final int score,revision;public final int[] points=new int[5];public final String[] reasons=new String[5];public final boolean stale;
  private Assessment(JSONObject r,JSONArray health,LocalDate day){date=r.optString("date");status=r.optString("status");confidence=r.optString("confidence");summary=r.optString("summary");strength=r.optString("strength");improvement=r.optString("improvement");source=r.optString("source");assessedAt=r.optString("assessed_at");score=r.optInt("score");revision=r.optInt("revision");stale=!r.optString("food_change_ids").equals(foodChangeIds(health,day));for(int i=0;i<5;i++){points[i]=r.optInt(COMPONENTS[i]+"_points");reasons[i]=r.optString(COMPONENTS[i]+"_reason");}}
 }
 private static Map<String,JSONObject> latest(JSONArray ledger){Map<String,JSONObject> latest=new HashMap<>();JSONArray rows=validate(ledger);for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i),old=latest.get(r.optString("date"));if(old==null||r.optInt("revision")>old.optInt("revision"))latest.put(r.optString("date"),r);}return latest;}
 private static Assessment assessment(Map<String,JSONObject> latest,JSONArray health,LocalDate day){JSONObject r=latest.get(day.toString());return r==null||"withdrawn".equals(r.optString("status"))?null:new Assessment(r,health,day);}
 public static Assessment forDay(JSONArray ledger,JSONArray health,LocalDate day){return assessment(latest(ledger),health,day);}
 public static final class Trend {
  public final List<Double> values=new ArrayList<>();public Double provisionalScore,average;public int provisionalIndex=-1,completedDays;
 }
 public static Trend trend(JSONArray ledger,JSONArray health,LocalDate end,LocalDate today,int days){
  if(days!=7&&days!=30)throw new IllegalArgumentException("Choose 7 or 30 days");
  Map<String,JSONObject> latest=latest(ledger);Trend t=new Trend();double sum=0;
  for(int i=0;i<days;i++){LocalDate day=end.minusDays(days-1L-i);Assessment a=day.isAfter(today)?null:assessment(latest,health,day);Double score=null;
   if(a!=null&&!a.stale){if("complete".equals(a.status)){score=(double)a.score;if(!day.isBefore(end.minusDays(6))){sum+=a.score;t.completedDays++;}}else if(day.equals(today)){t.provisionalScore=(double)a.score;t.provisionalIndex=i;}}
   t.values.add(score);
  }
  if(t.completedDays>0)t.average=sum/t.completedDays;return t;
 }
}
