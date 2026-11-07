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
}
