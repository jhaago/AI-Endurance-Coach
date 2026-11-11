package au.com.endurancecoach.core;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class RoutePlanTest {
 static JSONArray line() throws Exception {return new JSONArray("[[-31.95,115.85,10],[-31.949,115.85,30],[-31.948,115.85,20]]");}
 static JSONObject route() throws Exception {return RoutePlan.create("Park run","imported",line(),new JSONArray(),"GPX import","");}
 @Test public void measuresDistanceAndAscentWithoutInventingMissingHeights() throws Exception {
  JSONObject r=route();assertEquals(222,r.getDouble("distance_m"),2);assertEquals(20,r.getDouble("ascent_m"),.1);
  JSONObject missing=RoutePlan.create("x","drawn",new JSONArray("[[0,0],[0,0.01]]"),new JSONArray(),"OSM","");assertTrue(missing.isNull("ascent_m"));
 }
 @Test public void immutableIdsRejectConflictsAndCurrentNeverRollsBack() throws Exception {
  JSONObject first=RoutePlan.change("route:1","route",route(),false,null,1000),next=RoutePlan.change("route:1","route",route().put("name","new"),false,first,1000);
  assertEquals("new",new JSONObject(RoutePlan.current(new JSONArray().put(next),new JSONArray().put(first)).getJSONObject(0).getString("payload_json")).getString("name"));
  JSONObject conflict=new JSONObject(first.toString()).put("payload_json",next.getString("payload_json"));
  assertThrows(IllegalArgumentException.class,()->RoutePlan.current(new JSONArray().put(first).put(conflict)));
 }
 @Test public void rejectsInvalidCoordinatesAndOversizedCell() throws Exception {
  assertThrows(IllegalArgumentException.class,()->RoutePlan.create("x","imported",new JSONArray("[[91,0],[0,0]]"),new JSONArray(),"gpx",""));
  JSONObject r=route().put("name","x".repeat(121));assertThrows(IllegalArgumentException.class,()->RoutePlan.change("route:1","route",r,false,null,1000));
 }
 @Test public void gpxImportsTrackAndRejectsDoctypeAndDisconnectedSegments() throws Exception {
  JSONObject r=GpxRoutes.parse("<gpx><trk><name>Park</name><trkseg><trkpt lat='-31.95' lon='115.85'><ele>10</ele></trkpt><trkpt lat='-31.949' lon='115.85'><ele>20</ele></trkpt></trkseg></trk></gpx>");assertEquals("Park",r.getString("name"));assertEquals(10,r.getDouble("ascent_m"),.1);
  assertThrows(IllegalArgumentException.class,()->GpxRoutes.parse("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx>&x;</gpx>"));
  assertThrows(IllegalArgumentException.class,()->GpxRoutes.parse("<gpx><trk><trkseg><trkpt lat='0' lon='0'/><trkpt lat='0' lon='0.01'/></trkseg><trkseg><trkpt lat='1' lon='1'/><trkpt lat='1' lon='1.01'/></trkseg></trk></gpx>"));
 }
 @Test public void strictUnknownTerrainAndAscentAreNotClaimedAsMatch() throws Exception {
  JSONObject requirements=new JSONObject().put("target_km",.222).put("terrain","trail").put("climbing","hilly").put("shape","any").put("radius_km",10);
  JSONObject fit=RouteMatcher.assess(route(),requirements,new JSONArray());assertFalse(fit.getBoolean("matches"));assertTrue(fit.getString("explanation").contains("unverified"));assertTrue(fit.getString("estimate_basis").contains("fallback"));
 }
 @Test public void estimateUsesRecentRunningHistoryNotWalkingProviderTime() throws Exception {
  JSONObject r=route(),req=new JSONObject().put("terrain","any").put("climbing","any").put("shape","any").put("target_km",.222);
  JSONArray a=new JSONArray().put(new JSONObject().put("type","running").put("distance_m",5000).put("duration_s",1800).put("start_utc",java.time.Instant.now().toString()));
  JSONObject fit=RouteMatcher.assess(r,req,a);assertTrue(fit.getString("estimate_basis").contains("1 recent"));assertTrue(fit.getDouble("estimated_minutes")<3);
 }
}
