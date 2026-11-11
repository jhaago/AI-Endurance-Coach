package au.com.endurancecoach.core;
import org.json.*;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class DietQualityTest {
 private final LocalDate day=LocalDate.parse("2026-11-11");
 private JSONObject food(String id,String date) throws Exception {return new JSONObject().put("record_id",id).put("change_id",id).put("kind","meal").put("date",date).put("name","Synthetic food").put("calories_kcal",500).put("portions",1).put("deleted",false).put("updated_at","2026-11-11T01:00:00.000Z");}
 private JSONObject row(String date,int revision,String status,String ids) throws Exception {
  return new JSONObject().put("assessment_id","diet-quality-"+date).put("revision",revision).put("date",date).put("status",status).put("score",70)
   .put("fruit_veg_points",20).put("fibre_points",15).put("protein_calcium_points",15).put("fat_quality_points",10).put("discretionary_points",10)
   .put("confidence","medium").put("summary","Synthetic assessment").put("strength","Variety").put("improvement","Add vegetables")
   .put("fruit_veg_reason","Synthetic produce").put("fibre_reason","Synthetic grains").put("protein_calcium_reason","Synthetic protein").put("fat_quality_reason","Ingredients uncertain").put("discretionary_reason","Synthetic context")
   .put("food_change_ids",ids).put("source","Synthetic Coach").put("assessed_at","2026-11-11T02:00:00.000Z").put("rubric_version","dq-v1");
 }
 @Test public void latestRevisionWinsRegardlessOfTimestampAndWithdrawalHidesIt() throws Exception {
  JSONObject old=row(day.toString(),1,"provisional","a"),current=row(day.toString(),2,"complete","a").put("assessed_at","2026-11-11T01:00:00.000Z");
  JSONArray ledger=new JSONArray().put(current).put(old).put(current),health=new JSONArray().put(food("a",day.toString()));
  DietQuality.Assessment a=DietQuality.forDay(ledger,health,day);assertEquals(2,a.revision);assertEquals(70,a.score);assertEquals("complete",a.status);assertFalse(a.stale);
  ledger.put(row(day.toString(),3,"withdrawn","a"));assertNull(DietQuality.forDay(ledger,health,day));
 }
 @Test public void conflictingDuplicateRejectsLedgerRatherThanChoosingArbitrarily() throws Exception {
  JSONObject a=row(day.toString(),1,"complete","a"),b=new JSONObject(a.toString()).put("summary","Different judgement");
  assertThrows(IllegalArgumentException.class,()->DietQuality.validate(new JSONArray().put(a).put(b)));
 }
 @Test public void invalidTotalsTypesComponentsSignaturesAndVersionsReject() throws Exception {
  Object[][] variants={{"score",71},{"fruit_veg_points",31},{"fibre_points",15.5},{"revision",0},{"revision","1"},{"confidence","certain"},{"status","approved"},{"date","2026-02-30"},{"assessment_id","wrong"},{"assessed_at","2026-11-11T02:00:00Z"},{"rubric_version","dq-v2"},{"food_change_ids","b|a"},{"food_change_ids","a|a"},{"food_change_ids",""},{"summary",JSONObject.NULL}};
  for(Object[] v:variants){JSONObject r=row(day.toString(),1,"provisional","a").put((String)v[0],v[1]);assertThrows(v[0].toString(),IllegalArgumentException.class,()->DietQuality.validate(new JSONArray().put(r)));}
 }
 @Test public void signatureTracksOnlyCurrentActiveMealsOnTheSelectedDate() throws Exception {
  JSONObject old=food("old",day.toString()).put("record_id","a"),edit=food("new",day.toString()).put("record_id","a").put("updated_at","2026-11-11T01:00:00.001Z");
  JSONArray health=new JSONArray().put(food("z",day.toString())).put(old).put(edit).put(food("fav",day.toString()).put("kind","favourite")).put(food("gone",day.toString()).put("deleted",true)).put(food("other",day.minusDays(1).toString()));
  assertEquals("new|z",DietQuality.foodChangeIds(health,day));assertTrue(DietQuality.forDay(new JSONArray().put(row(day.toString(),1,"provisional","old|z")),health,day).stale);
  assertFalse(DietQuality.forDay(new JSONArray().put(row(day.toString(),2,"provisional","new|z")),health,day).stale);
  edit.put("deleted",true);assertEquals("z",DietQuality.foodChangeIds(health,day));
 }
 @Test public void trendPreservesGapsAndZeroWhileExcludingStaleProvisionalAndWithdrawnDays() throws Exception {
  JSONArray ledger=new JSONArray(),health=new JSONArray();
  for(int i=0;i<7;i++){String d=day.minusDays(i).toString();health.put(food("f"+i,d));if(i!=3)ledger.put(row(d,1,i==0||i==4?"provisional":i==5?"withdrawn":"complete","f"+i));}
  JSONObject zero=ledger.getJSONObject(1);zero.put("score",0);for(String k:new String[]{"fruit_veg_points","fibre_points","protein_calcium_points","fat_quality_points","discretionary_points"})zero.put(k,0);
  ledger.getJSONObject(2).put("food_change_ids","outdated");DietQuality.Trend t=DietQuality.trend(ledger,health,day,day,7);
  assertEquals(7,t.values.size());assertEquals(70,t.values.get(0),0);assertNull(t.values.get(1));assertNull(t.values.get(2));assertNull(t.values.get(3));assertNull(t.values.get(4));assertEquals(0,t.values.get(5),0);assertNull(t.values.get(6));
  assertEquals(2,t.completedDays);assertEquals(35,t.average,0);assertEquals(6,t.provisionalIndex);assertEquals(70,t.provisionalScore,0);
 }
 @Test public void historicalProvisionalAndFutureScoresDoNotBecomeTodaysPoint() throws Exception {
  JSONArray h=new JSONArray().put(food("a",day.toString())).put(food("b",day.plusDays(1).toString())),l=new JSONArray().put(row(day.toString(),1,"provisional","a")).put(row(day.plusDays(1).toString(),1,"complete","b"));
  DietQuality.Trend historical=DietQuality.trend(l,h,day,day.plusDays(1),7);assertNull(historical.provisionalScore);assertNull(historical.average);assertEquals(0,historical.completedDays);
  DietQuality.Trend future=DietQuality.trend(l,h,day.plusDays(1),day,7);assertEquals(0,future.completedDays);assertNull(future.values.get(6));
 }
 @Test public void cachedLedgerRejectsOlderNonemptyHistoryAndCrossSnapshotConflicts() throws Exception {
  JSONObject one=row(day.toString(),1,"provisional","a"),two=row(day.toString(),2,"withdrawn","a");JSONArray previous=new JSONArray().put(one).put(two);
  assertThrows(IllegalArgumentException.class,()->DietQuality.importLedger(previous,new JSONArray().put(one)));
  assertThrows(IllegalArgumentException.class,()->DietQuality.importLedger(previous,new JSONArray().put(one).put(new JSONObject(two.toString()).put("summary","Changed same revision"))));
  assertEquals(3,DietQuality.importLedger(previous,new JSONArray().put(one).put(two).put(row(day.toString(),3,"complete","a"))).length());
  assertEquals(0,DietQuality.importLedger(previous,new JSONArray()).length());
 }
}
