package au.com.endurancecoach.core;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class GarminClientTest {
    static String jwt(long exp) { return "header."+Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":"+exp+"}").getBytes(StandardCharsets.UTF_8))+".signature"; }
    static class Fake implements Transport {
        final List<String> urls=new ArrayList<>(); final Queue<Response> responses=new ArrayDeque<>(); final List<Map<String,String>> headers=new ArrayList<>();
        Fake add(int code,String body){responses.add(new Response(code,body.getBytes(StandardCharsets.UTF_8)));return this;}
        public Response request(String method,String url,Map<String,String> h,byte[] body){urls.add(url);headers.add(h);return responses.remove();}
    }
    @Test public void officialTokenIsRawJwtAndCachedUntilExpiry() throws Exception {
        Fake f=new Fake().add(200,jwt(9000)).add(200,"[]").add(200,"[]");
        GarminClient c=new GarminClient(f,"key",()->1000L); c.json("/hrv-service/hrv/2026-11-10"); c.json("/hrv-service/hrv/2026-11-11");
        assertEquals(3,f.urls.size()); assertTrue(f.urls.get(0).endsWith("/personalapitoken-service/personal-api/personal-api-key/token"));
        assertEquals("ApiKey key",f.headers.get(0).get("Authorization")); assertTrue(f.headers.get(1).get("Authorization").startsWith("Bearer "));
    }
    @Test public void unauthorizedRenewsExactlyOnce() throws Exception {
        Fake f=new Fake().add(200,jwt(9000)).add(401,"private response").add(200,jwt(9001)).add(401,"secret");
        GarminClient c=new GarminClient(f,"key",()->1000L);
        Exception e=assertThrows(Exception.class,()->c.json("/hrv-service/hrv/2026-11-10"));
        assertEquals(4,f.urls.size()); assertFalse(e.getMessage().contains("secret"));
    }
    @Test public void paginationReadsEveryActivityAndStopsAtEmptyPage() throws Exception {
        Fake f=new Fake().add(200,jwt(9000)).add(200,"[{\"activityId\":1},{\"activityId\":2}]").add(200,"[{\"activityId\":3}]");
        GarminClient c=new GarminClient(f,"key",()->1000L);
        assertEquals(3,c.activities("2026-11-01","2026-11-10",2).length()); assertTrue(f.urls.get(2).contains("start=2"));
    }
    @Test public void absoluteUrlCannotExfiltrateBearer() {
        GarminClient c=new GarminClient(new Fake(),"key",()->1000L);
        assertThrows(IllegalArgumentException.class,()->c.json("https://evil.example/"));
    }
    @Test public void absentWellnessRemainsNull() {
        JSONObject m=Metrics.wellness("2026-11-10",new JSONObject(),new JSONObject(),new JSONObject());
        assertTrue(m.isNull("sleep_hours")); assertTrue(m.isNull("hrv_ms")); assertTrue(m.isNull("resting_hr"));
    }
    @Test public void extractsDocumentedGarminWellnessContainers() {
        JSONObject sleep=new JSONObject("{\"dailySleepDTO\":{\"sleepTimeSeconds\":25200,\"sleepScores\":{\"overall\":{\"value\":79}}}}");
        JSONObject hrv=new JSONObject("{\"hrvSummary\":{\"lastNightAvg\":44}}");
        JSONObject m=Metrics.wellness("2026-11-10",sleep,hrv,new JSONObject().put("restingHeartRate",52));
        assertEquals(7,m.getDouble("sleep_hours"),0.001); assertEquals(44,m.getInt("hrv_ms")); assertEquals(79,m.getInt("sleep_score"));
    }
    @Test public void activitySummaryNormalizesSiUnitsAndUtc() {
        JSONObject m=Metrics.activity(new JSONObject("{\"activityId\":123,\"startTimeGMT\":\"2026-11-09 22:00:00\",\"activityType\":{\"typeKey\":\"running\"},\"distance\":5000,\"duration\":1800}"));
        assertEquals("123",m.getString("activity_id")); assertEquals("2026-11-09T22:00:00Z",m.getString("start_utc")); assertEquals(360, m.getDouble("pace_s_km"),.001);
    }
    @Test public void officialPersonalSleepIsTopLevel() {
        JSONObject m=Metrics.wellness("2026-11-10",new JSONObject("{\"sleepTimeSeconds\":28800,\"sleepScores\":{\"overall\":{\"value\":85}}}"),new JSONObject(),new JSONObject());
        assertEquals(8,m.getDouble("sleep_hours"),.001);assertEquals(85,m.getInt("sleep_score"));
    }
    @Test public void officialDetailUsesActivityTypeDtoAndSummaryCadence() {
        JSONObject m=Metrics.activity(new JSONObject("{\"activityId\":1,\"activityTypeDTO\":{\"typeKey\":\"running\"},\"summaryDTO\":{\"startTimeGMT\":\"2026-11-10T00:00:00.000\",\"averageRunCadence\":164}}"));
        assertEquals("running",m.getString("type"));assertEquals(164,m.getInt("cadence_spm"));
    }
}
