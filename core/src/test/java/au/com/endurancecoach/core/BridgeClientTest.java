package au.com.endurancecoach.core;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class BridgeClientTest {
 @Test public void secondSnapshotChangesPlanWithoutChangingActivityRecord() throws Exception {
  String first="{\"ok\":true,\"snapshot\":{\"plan\":[{\"workout_id\":\"w\",\"revision\":1,\"date\":\"2026-11-10\",\"type\":\"run\"}],\"activities\":[{\"activity_id\":\"1\",\"duration_s\":1800}]}}";
  String second="{\"ok\":true,\"snapshot\":{\"plan\":[{\"workout_id\":\"w\",\"revision\":1,\"date\":\"2026-11-10\",\"type\":\"run\"},{\"workout_id\":\"w\",\"revision\":2,\"date\":\"2026-11-11\",\"type\":\"run\"}],\"activities\":[{\"activity_id\":\"1\",\"duration_s\":1800}]}}";
  GarminClientTest.Fake fake=new GarminClientTest.Fake().add(200,first).add(200,second);
  BridgeClient c=new BridgeClient(fake,"https://script.google.com/macros/s/test/exec","test-secret-123456789012345678901234");
  JSONObject request=new JSONObject().put("action","snapshot");JSONObject a=c.call(request).getJSONObject("snapshot"),b=c.call(request).getJSONObject("snapshot");
  assertEquals("2026-11-10",PlanEngine.current(a.getJSONArray("plan")).getJSONObject(0).getString("date"));
  assertEquals("2026-11-11",PlanEngine.current(b.getJSONArray("plan")).getJSONObject(0).getString("date"));
  assertEquals(a.getJSONArray("activities").toString(),b.getJSONArray("activities").toString());assertFalse(request.has("secret"));
 }
 @Test public void refusesNonGoogleAndQueryCredentialUrls() {
  for(String url:new String[]{"http://script.google.com/macros/s/x/exec","https://evil.example/macros/s/x/exec","https://script.google.com/macros/s/x/exec?secret=bad","https://user@script.google.com/macros/s/x/exec"})assertThrows(IllegalArgumentException.class,()->HttpsTransport.validateBridge(url));
 }
}
