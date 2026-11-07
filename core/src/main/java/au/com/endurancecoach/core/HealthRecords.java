package au.com.endurancecoach.core;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/** Immutable health changes. Missing measurements are never converted to zero. */
public final class HealthRecords {
 private HealthRecords() {}
 private static final DateTimeFormatter TIME=new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
 private static JSONObject base(String kind,String id,String date,JSONObject previous,long now) {
  try {
   long time=previous==null?now:Math.max(now,Instant.parse(previous.getString("updated_at")).toEpochMilli()+1);
   return new JSONObject().put("change_id",UUID.randomUUID().toString()).put("record_id",id)
    .put("kind",kind).put("date",date).put("name","").put("calories_kcal",JSONObject.NULL)
    .put("portions",JSONObject.NULL).put("weight_kg",JSONObject.NULL).put("note","")
    .put("deleted",false).put("updated_at",TIME.format(Instant.ofEpochMilli(time)));
  } catch(Exception e){throw new IllegalArgumentException("Invalid previous health entry",e);}
 }
 private static String id(JSONObject previous){return previous==null?UUID.randomUUID().toString():previous.optString("record_id");}
 private static void number(double n,double low,double high,boolean zero,String label){if(!Double.isFinite(n)||n>high||(zero?n<low:n<=low))throw new IllegalArgumentException(label+" is outside the allowed range");}
 private static void day(LocalDate d,long now){if(d==null||d.isAfter(Instant.ofEpochMilli(now).atZone(PlanEngine.ZONE).toLocalDate()))throw new IllegalArgumentException("Choose today or an earlier date");}
 private static JSONObject food(String kind,String date,String name,double calories,double portions,String note,JSONObject previous,long now){
  if(name==null||name.trim().isEmpty()||name.trim().length()>120)throw new IllegalArgumentException("Enter a meal name (up to 120 characters)");
  if(note==null||note.length()>1000)throw new IllegalArgumentException("Keep notes under 1,000 characters");
  number(calories,0,20000,true,"Calories");number(portions,0,100,false,"Portions");
  try{return base(kind,id(previous),date,previous,now).put("name",name.trim()).put("calories_kcal",calories).put("portions",portions).put("note",note);}
  catch(JSONException e){throw new IllegalArgumentException(e);}
 }
 public static JSONObject meal(LocalDate d,String name,double kcal,double portions,String note,JSONObject previous,long now){day(d,now);return food("meal",d.toString(),name,kcal,portions,note,previous,now);}
 public static JSONObject favourite(String name,double kcal,String note,JSONObject previous,long now){return food("favourite","",name,kcal,1,note,previous,now);}
 public static JSONObject fromFavourite(JSONObject favourite,LocalDate d,double portions,long now){return meal(d,favourite.optString("name"),favourite.optDouble("calories_kcal"),portions,favourite.optString("note"),null,now);}
 public static JSONObject weight(LocalDate d,double kg,JSONObject previous,long now){day(d,now);return kilos("weight","weight:"+d,d.toString(),kg,previous,now);}
 public static JSONObject goal(double kg,JSONObject previous,long now){return kilos("goal","goal-weight","",kg,previous,now);}
 private static JSONObject kilos(String kind,String id,String date,double kg,JSONObject previous,long now){number(kg,0,500,false,"Weight");try{return base(kind,id,date,previous,now).put("weight_kg",kg);}catch(JSONException e){throw new IllegalArgumentException(e);}}
 public static JSONObject deleted(JSONObject previous,long now){try{JSONObject out=new JSONObject(previous.toString());out.put("change_id",UUID.randomUUID().toString()).put("deleted",true).put("updated_at",TIME.format(Instant.ofEpochMilli(Math.max(now,Instant.parse(previous.getString("updated_at")).toEpochMilli()+1))));return out;}catch(Exception e){throw new IllegalArgumentException("Invalid health entry",e);}}
 public static JSONArray current(JSONArray... sources){
  TreeMap<String,JSONObject> rows=new TreeMap<>();
  try{for(JSONArray source:sources)for(int i=0;i<source.length();i++){
   JSONObject row=source.getJSONObject(i);String id=row.getString("record_id");JSONObject old=rows.get(id);
   Instant stamp=Instant.parse(row.getString("updated_at"));
   if(old==null||stamp.isAfter(Instant.parse(old.getString("updated_at")))||(stamp.equals(Instant.parse(old.getString("updated_at")))&&row.getString("change_id").compareTo(old.getString("change_id"))>0))rows.put(id,new JSONObject(row.toString()));
  }return new JSONArray(new ArrayList<>(rows.values()));}catch(Exception e){throw new IllegalArgumentException("Invalid health history",e);}
 }
 public static final class Summary {
  public Double calories,weight,latestWeight,averageWeight,averageCalories,goal;
  public int weighIns,loggedDays,mealCount;
  public String latestWeightDate="";
 }
 public static Summary summary(JSONArray records,LocalDate day){
  Summary s=new Summary();TreeMap<LocalDate,Double> weights=new TreeMap<>(),calories=new TreeMap<>();
  JSONArray rows=current(records);
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r.optBoolean("deleted"))continue;
   String kind=r.optString("kind");if(kind.equals("goal")){s.goal=r.optDouble("weight_kg");continue;}
   if(!kind.equals("weight")&&!kind.equals("meal"))continue;
   LocalDate d=LocalDate.parse(r.optString("date"));if(d.isAfter(day))continue;
   if(kind.equals("weight"))weights.put(d,r.optDouble("weight_kg"));
   else {calories.put(d,calories.getOrDefault(d,0d)+r.optDouble("calories_kcal")*r.optDouble("portions"));if(d.equals(day))s.mealCount++;}
  }
  s.weight=weights.get(day);s.calories=calories.get(day);
  if(!weights.isEmpty()){s.latestWeight=weights.lastEntry().getValue();s.latestWeightDate=weights.lastKey().toString();}
  double w=0,c=0;LocalDate start=day.minusDays(6);
  for(Map.Entry<LocalDate,Double> e:weights.entrySet())if(!e.getKey().isBefore(start)){w+=e.getValue();s.weighIns++;}
  for(Map.Entry<LocalDate,Double> e:calories.entrySet())if(!e.getKey().isBefore(start)){c+=e.getValue();s.loggedDays++;}
  s.averageWeight=s.weighIns==0?null:w/s.weighIns;s.averageCalories=s.loggedDays==0?null:c/s.loggedDays;return s;
 }
}
