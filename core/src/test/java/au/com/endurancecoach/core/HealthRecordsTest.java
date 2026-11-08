package au.com.endurancecoach.core;
import org.json.*;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class HealthRecordsTest {
 private final LocalDate day=LocalDate.parse("2026-11-08");
 private final long now=Instant.parse("2026-11-08T12:00:00Z").toEpochMilli();
 @Test public void favouriteCopiesCaloriesAndPortionWithoutLinkingPastMeals(){
  JSONObject favourite=HealthRecords.favourite("Porridge",400,"",null,now);
  JSONObject meal=HealthRecords.fromFavourite(favourite,day,1.5,now);
  JSONObject edited=HealthRecords.favourite("Porridge",500,"",favourite,now);
  assertEquals(600,HealthRecords.summary(new JSONArray().put(meal).put(edited),day).calories,0);
  assertEquals(400,meal.getDouble("calories_kcal"),0);assertEquals(1.5,meal.getDouble("portions"),0);
 }
 @Test public void latestChangeWinsRegardlessOfDeliveryOrderAndDeleteDoesNotResurrect(){
  JSONObject first=HealthRecords.meal(day,"Lunch",500,1,"",null,now);
  JSONObject edit=HealthRecords.meal(day,"Lunch",600,1,"",first,now);
  JSONObject deleted=HealthRecords.deleted(edit,now);
  JSONArray current=HealthRecords.current(new JSONArray().put(deleted).put(first).put(edit));
  assertEquals(1,current.length());assertTrue(current.getJSONObject(0).getBoolean("deleted"));
  assertNull(HealthRecords.summary(current,day).calories);
  assertTrue(Instant.parse(deleted.getString("updated_at")).isAfter(Instant.parse(edit.getString("updated_at"))));
 }
 @Test public void missingDaysStayMissingAndAveragesUseMeasuredDays(){
  JSONArray records=new JSONArray().put(HealthRecords.weight(day.minusDays(6),100,null,now))
   .put(HealthRecords.weight(day,98,null,now)).put(HealthRecords.meal(day.minusDays(1),"Dinner",800,1,"",null,now));
  HealthRecords.Summary s=HealthRecords.summary(records,day);
  assertEquals(99,s.averageWeight,0);assertEquals(2,s.weighIns);assertNull(s.calories);
  assertEquals(800,s.averageCalories,0);assertEquals(1,s.loggedDays);assertEquals(98,s.latestWeight,0);
 }
 @Test public void explicitZeroCalorieMealIsDifferentFromAnUnloggedDay(){
  JSONObject meal=HealthRecords.meal(day,"Water",0,1,"",null,now);
  assertEquals(0,HealthRecords.summary(new JSONArray().put(meal),day).calories,0);
  assertNull(HealthRecords.summary(new JSONArray(),day).calories);
 }
 @Test public void weightsUseOneStableIdPerDateAndGoalIsOptional(){
  JSONObject first=HealthRecords.weight(day,100,null,now),edit=HealthRecords.weight(day,99,first,now);
  JSONArray records=HealthRecords.current(new JSONArray().put(first).put(edit).put(HealthRecords.goal(85,null,now)));
  assertEquals(2,records.length());assertEquals(99,HealthRecords.summary(records,day).weight,0);
  assertEquals(85,HealthRecords.summary(records,day).goal,0);assertNull(HealthRecords.summary(new JSONArray(),day).goal);
 }
 @Test public void invalidInputAndFutureActualDatesAreRejected(){
  for(double p:new double[]{0,-1,Double.NaN,101})assertThrows(IllegalArgumentException.class,()->HealthRecords.meal(day,"Meal",500,p,"",null,now));
  assertThrows(IllegalArgumentException.class,()->HealthRecords.meal(day,"",500,1,"",null,now));
  assertThrows(IllegalArgumentException.class,()->HealthRecords.weight(day,0,null,now));
  assertThrows(IllegalArgumentException.class,()->HealthRecords.meal(day.plusDays(1),"Future",500,1,"",null,now));
 }
 @Test public void equalTimesResolveByChangeIdConsistently(){
  JSONObject a=HealthRecords.meal(day,"A",100,1,"",null,now);
  JSONObject b=new JSONObject(a.toString()).put("change_id","zzzz").put("calories_kcal",200);
  a.put("change_id","aaaa");
  assertEquals(200,HealthRecords.current(new JSONArray().put(b).put(a)).getJSONObject(0).getDouble("calories_kcal"),0);
 }
 @Test public void mealsUseExplicitGroupsAndLegacyRowsBecomeOtherWithoutMutation(){
  JSONObject meal=HealthRecords.meal(day,"Toast",200,2,"",null,now,"breakfast");assertEquals("breakfast",meal.getString("meal_type"));
  JSONObject legacy=HealthRecords.meal(day,"Soup",300,1,"",null,now);legacy.remove("meal_type");
  JSONObject current=HealthRecords.current(new JSONArray().put(legacy)).getJSONObject(0);assertEquals("other",current.getString("meal_type"));assertFalse(legacy.has("meal_type"));
  assertEquals("",HealthRecords.favourite("Toast",200,"",null,now).getString("meal_type"));assertTrue(current.isNull("protein_g"));
  assertThrows(IllegalArgumentException.class,()->HealthRecords.meal(day,"Toast",1,1,"",null,now,"brunch"));
 }
 @Test public void foodEditsAndIndependentFavouriteCopiesPreserveOptionalPerPortionGrams(){
  JSONObject favourite=HealthRecords.favourite("Toast",200,"",null,now).put("protein_g",8).put("carbohydrate_g",30).put("fat_g",0);
  JSONObject meal=HealthRecords.fromFavourite(favourite,day,2,now,"snacks");
  assertEquals("snacks",meal.getString("meal_type"));assertEquals(8,meal.getDouble("protein_g"),0);assertEquals(0,meal.getDouble("fat_g"),0);assertNotEquals(favourite.getString("record_id"),meal.getString("record_id"));
  JSONObject edit=HealthRecords.meal(day,"Toast",210,1.5,"",meal,now,"lunch");assertEquals(30,edit.getDouble("carbohydrate_g"),0);
  JSONObject editedFavourite=HealthRecords.favourite("Toast",250,"",favourite,now);assertEquals(8,editedFavourite.getDouble("protein_g"),0);assertEquals(200,meal.getDouble("calories_kcal"),0);
  assertEquals(315,HealthRecords.summary(new JSONArray().put(edit),day).calories,0);
 }
 @Test public void canonicalLegacyRowsSupplyNullOptionalGramsForAllKinds(){
  JSONObject old=HealthRecords.weight(day,98,null,now);old.remove("protein_g");old.remove("carbohydrate_g");old.remove("fat_g");old.remove("meal_type");JSONObject row=HealthRecords.current(new JSONArray().put(old)).getJSONObject(0);
  assertEquals("",row.getString("meal_type"));for(String key:new String[]{"protein_g","carbohydrate_g","fat_g"})assertTrue(row.has(key)&&row.isNull(key));
 }
}
