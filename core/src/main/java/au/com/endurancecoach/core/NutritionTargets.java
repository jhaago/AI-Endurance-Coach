package au.com.endurancecoach.core;

import org.json.*;
import java.time.*;
import java.util.*;

/** Read-only Coach-approved targets. Actual food, weight and exercise never change them. */
public final class NutritionTargets {
 private NutritionTargets(){}
 public static final String[] HEADERS={"target_id","revision","scope","date","calories_kcal","protein_g","carbohydrate_g","fat_g","weight_goal_kg","weight_goal_date","weight_change_kg_per_week","training_type","exercise_included","source","created_at","reason","status"};
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Nutrition targets need attention in the training database");}
 private static String string(JSONObject row,String key,int maximum,boolean empty){Object v=row.opt(key);if(!(v instanceof String)||((String)v).length()>maximum||(!empty&&((String)v).trim().isEmpty()))throw invalid();return (String)v;}
 private static Double number(JSONObject row,String key,double low,double high,boolean optional,boolean inclusive){Object v=row.opt(key);if(v==null||v==JSONObject.NULL){if(optional)return null;throw invalid();}if(!(v instanceof Number))throw invalid();double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n>high||(inclusive?n<low:n<=low))throw invalid();return n;}
 private static String day(String value){try{if(!value.matches("\\d{4}-\\d{2}-\\d{2}")||!LocalDate.parse(value).toString().equals(value))throw invalid();return value;}catch(Exception e){throw invalid();}}
 private static JSONObject normalise(JSONObject source){
  try{
   String scope=string(source,"scope",16,false),date=day(string(source,"date",10,false));if(!scope.equals("baseline")&&!scope.equals("daily"))throw invalid();
   String id=string(source,"target_id",100,false);if(!id.equals((scope.equals("daily")?"nutrition-":"baseline-")+date))throw invalid();
   double revision=number(source,"revision",1,Integer.MAX_VALUE,false,true);if(revision!=Math.rint(revision))throw invalid();
   String status=string(source,"status",16,false);if(!Arrays.asList("approved","draft","cancelled").contains(status))throw invalid();
   String stamp=string(source,"created_at",24,false);if(!stamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")||!new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.parse(stamp)).equals(stamp))throw invalid();
   String goalDate=source.isNull("weight_goal_date")?"":string(source,"weight_goal_date",10,true);if(!goalDate.isEmpty())day(goalDate);
   if(!(source.opt("exercise_included") instanceof Boolean))throw invalid();
   JSONObject out=new JSONObject().put("target_id",id).put("revision",(int)revision).put("scope",scope).put("date",date).put("status",status).put("created_at",stamp)
    .put("calories_kcal",number(source,"calories_kcal",0,20000,false,false)).put("exercise_included",source.getBoolean("exercise_included"))
    .put("source",string(source,"source",120,false)).put("reason",string(source,"reason",2000,true)).put("training_type",string(source,"training_type",120,true)).put("weight_goal_date",goalDate);
   for(String key:new String[]{"protein_g","carbohydrate_g","fat_g"}){Double v=number(source,key,0,5000,true,true);out.put(key,v==null?JSONObject.NULL:v);}
   Double weight=number(source,"weight_goal_kg",0,500,true,false),rate=number(source,"weight_change_kg_per_week",-5,5,true,true);
   out.put("weight_goal_kg",weight==null?JSONObject.NULL:weight).put("weight_change_kg_per_week",rate==null?JSONObject.NULL:rate);return out;
  }catch(Exception e){throw invalid();}
 }
 public static JSONArray validate(JSONArray ledger){
  if(ledger==null)throw invalid();TreeMap<String,JSONObject> unique=new TreeMap<>();HashMap<String,String> canonical=new HashMap<>();
  try{for(int i=0;i<ledger.length();i++){JSONObject row=normalise(ledger.getJSONObject(i));String key=row.getString("target_id")+":"+row.getInt("revision");JSONArray values=new JSONArray();for(String field:HEADERS)values.put(row.get(field));String fingerprint=values.toString();if(canonical.containsKey(key)&&!canonical.get(key).equals(fingerprint))throw invalid();canonical.put(key,fingerprint);unique.put(key,row);}return new JSONArray(new ArrayList<>(unique.values()));}catch(Exception e){throw invalid();}
 }
 public static final class Target {
  public String targetId,scope,date,source,reason,trainingType,weightGoalDate;
  public int revision;public double calories;public Double protein,carbohydrate,fat,weightGoalKg,weightChangeKgPerWeek;
  public boolean exerciseIncluded,trainingChanged;
 }
 private static Double optional(JSONObject row,String key){return row==null||row.isNull(key)?null:row.optDouble(key);}
 public static Target forDay(JSONArray ledger,LocalDate day,JSONArray plan){
  JSONArray rows=validate(ledger);HashMap<String,JSONObject> latest=new HashMap<>();
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r.optString("status").equals("draft"))continue;String id=r.optString("target_id");JSONObject old=latest.get(id);if(old==null||r.optInt("revision")>old.optInt("revision"))latest.put(id,r);}
  JSONObject baseline=null,daily=null;for(JSONObject row:latest.values()){
   if(!row.optString("status").equals("approved"))continue;LocalDate date=LocalDate.parse(row.optString("date"));
   if(row.optString("scope").equals("daily")&&date.equals(day))daily=row;
   if(row.optString("scope").equals("baseline")&&!date.isAfter(day)&&(baseline==null||date.isAfter(LocalDate.parse(baseline.optString("date")))))baseline=row;
  }
  JSONObject row=daily==null?baseline:daily;if(row==null)return null;Target t=new Target();
  t.targetId=row.optString("target_id");t.revision=row.optInt("revision");t.scope=row.optString("scope");t.date=row.optString("date");t.calories=row.optDouble("calories_kcal");
  t.protein=optional(row,"protein_g");t.carbohydrate=optional(row,"carbohydrate_g");t.fat=optional(row,"fat_g");t.weightGoalKg=optional(row,"weight_goal_kg");t.weightChangeKgPerWeek=optional(row,"weight_change_kg_per_week");t.weightGoalDate=row.optString("weight_goal_date");
  if(t.weightGoalKg==null)t.weightGoalKg=optional(baseline,"weight_goal_kg");if(t.weightChangeKgPerWeek==null)t.weightChangeKgPerWeek=optional(baseline,"weight_change_kg_per_week");if(t.weightGoalDate.isEmpty()&&baseline!=null)t.weightGoalDate=baseline.optString("weight_goal_date");
  t.source=row.optString("source");t.reason=row.optString("reason");t.exerciseIncluded=row.optBoolean("exercise_included");t.trainingType=row.optString("training_type");if(t.trainingType.isEmpty())t.trainingType=trainingType(plan,day);
  if(t.scope.equals("daily"))for(int i=0;i<plan.length();i++){JSONObject w=plan.optJSONObject(i);if(w!=null&&day.toString().equals(w.optString("date")))try{if(Instant.parse(w.optString("created_at")).isAfter(Instant.parse(row.optString("created_at"))))t.trainingChanged=true;}catch(Exception ignored){/* No inference from an absent plan timestamp. */}}
  return t;
 }
 public static String trainingType(JSONArray plan,LocalDate day){
  LinkedHashSet<String> labels=new LinkedHashSet<>();JSONArray current=PlanEngine.current(plan);for(int i=0;i<current.length();i++){JSONObject w=current.optJSONObject(i);if(day.toString().equals(w.optString("date"))){String type=w.optString("type");if(!type.isEmpty())labels.add(type.substring(0,1).toUpperCase(Locale.UK)+type.substring(1));}}
  return labels.isEmpty()?"No training scheduled":String.join(" + ",labels);
 }
}
