package au.com.endurancecoach.core;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RecoveryReaderTest {
 GarminClient client(GarminClientTest.Fake f){return new GarminClient(f,"never-display-this-key",()->1000L);}
 GarminClientTest.Fake fake(){return new GarminClientTest.Fake().add(200,GarminClientTest.jwt(9000));}
 @Test public void noDailyDataUsesDocumentedSameDateSummaries() throws Exception {
  GarminClientTest.Fake f=fake().add(204,"").add(200,"[{\"calendarDate\":\"2026-11-06\",\"sleepTimeSeconds\":500},{\"calendarDate\":\"2026-11-07\",\"sleepTimeSeconds\":25200}]");
  RecoveryReader.Result r=new RecoveryReader(client(f)).read("sleep","2026-11-07");
  assertEquals(25200,r.data.getInt("sleepTimeSeconds"));assertTrue(f.urls.get(2).contains("dailySleepSummariesByDate?startDate=2026-11-07&endDate=2026-11-07"));assertTrue(r.note.contains("HTTP 204"));assertTrue(r.note.contains("summary HTTP 200"));
 }
 @Test public void hrvSummaryWorksButDailyMinimumIsNotInventedAsRestingHr() throws Exception {
  GarminClientTest.Fake f=fake().add(404,"private-body").add(200,"{\"hrvSummaries\":[{\"calendarDate\":\"2026-11-07\",\"lastNightAvg\":44}]}")
    .add(204,"").add(200,"{\"data\":[{\"calendarDate\":\"2026-11-07\",\"heartRate\":{\"restingValue\":52}}]}");
  RecoveryReader reader=new RecoveryReader(client(f));assertEquals(44,reader.read("hrv","2026-11-07").data.getJSONObject("hrvSummary").getInt("lastNightAvg"));
  RecoveryReader.Result heart=reader.read("heart","2026-11-07");assertTrue(heart.data.isNull("restingHeartRate"));assertTrue(heart.note.contains("minimum"));
 }
 @Test public void wrongDateNeverBecomesCurrentRecovery() throws Exception {
  GarminClientTest.Fake f=fake().add(204,"").add(200,"[{\"calendarDate\":\"2026-11-06\",\"sleepTimeSeconds\":25200}]");
  assertEquals(0,new RecoveryReader(client(f)).read("sleep","2026-11-07").data.length());
 }
 @Test public void missingAndUnexpectedDataHaveSafeDiagnosticsWithoutInventingMetrics() throws Exception {
  GarminClientTest.Fake f=fake().add(200,"[]").add(403,"key=never-display-this-key; bearer-secret");
  RecoveryReader.Result r=new RecoveryReader(client(f)).read("sleep","2026-11-07");
  assertEquals(0,r.data.length());assertTrue(r.note.contains("array"));assertTrue(r.note.contains("HTTP 403"));assertFalse(r.note.contains("never-display"));assertFalse(r.note.contains("bearer-secret"));
 }
 @Test public void validDailyObjectDoesNotUseFallback() throws Exception {
  GarminClientTest.Fake f=fake().add(200,"{\"hrvSummary\":{\"lastNightAvg\":41}}");
  assertEquals(41,new RecoveryReader(client(f)).read("hrv","2026-11-07").data.getJSONObject("hrvSummary").getInt("lastNightAvg"));assertEquals(2,f.urls.size());
 }
 @Test public void rateLimitDoesNotMultiplyRequests() throws Exception {
  GarminClientTest.Fake f=fake().add(429,"private-response");RecoveryReader.Result r=new RecoveryReader(client(f)).read("sleep","2026-11-07");assertTrue(r.retry);assertEquals(2,f.urls.size());
 }
 @Test public void planPollingCannotEraseRecoveryWarning(){assertEquals("Recovery data unavailable",SyncStatus.message("Recovery data unavailable",Collections.emptyList()));}
 @Test public void emptySleepScoresDoNotSuppressSummaryFallback() throws Exception {
  GarminClientTest.Fake f=fake().add(200,"{\"sleepScores\":{}}").add(200,"[{\"calendarDate\":\"2026-11-07\",\"sleepTimeSeconds\":25200}]");
  assertEquals(25200,new RecoveryReader(client(f)).read("sleep","2026-11-07").data.getInt("sleepTimeSeconds"));assertEquals(3,f.urls.size());
 }
 @Test public void partialSleepAndNullDiagnosticsRetainKnownCloudMeasurementsOnlyForSameDate(){
  JSONObject old=new JSONObject().put("date","2026-11-07").put("sleep_hours",7).put("sleep_score",80).put("hrv_ms",44).put("resting_hr",52);
  JSONObject fresh=Metrics.wellness("2026-11-07",new JSONObject("{\"sleepScores\":{\"overall\":{\"value\":75}}}"),new JSONObject(),new JSONObject());
  JSONObject result=RecoveryRecord.merge(fresh,old);assertEquals(7,result.getInt("sleep_hours"));assertEquals(75,result.getInt("sleep_score"));assertEquals(44,result.getInt("hrv_ms"));
  assertTrue(RecoveryRecord.merge(fresh,old.put("date","2026-11-06")).isNull("sleep_hours"));
 }
}
