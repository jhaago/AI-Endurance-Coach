package au.com.endurancecoach.core;
import org.junit.Test;import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;import static org.junit.Assert.*;
public class MapServicesTest {
 @Test public void requestsPedestrianRoutingAndNeverUsesProviderWalkingTime() throws Exception {
  List<String> calls=new ArrayList<>();Transport t=(method,url,headers,body)->{calls.add(url);try{JSONObject q=new JSONObject(new String(body,StandardCharsets.UTF_8));if(url.endsWith("/route")){assertEquals("pedestrian",q.getString("costing"));assertEquals("foot",q.getJSONObject("costing_options").getJSONObject("pedestrian").getString("type"));return response("{\"trip\":{\"legs\":[{\"shape\":{\"coordinates\":[[115.85,-31.95],[115.85,-31.949]]}}]}}");}return response("{\"height\":[10,20]}");}catch(Exception e){throw new java.io.IOException(e);}};
  JSONObject r=new MapServices(t,null).route(new JSONArray("[[-31.95,115.85],[-31.949,115.85]]"),"path");assertEquals(111,r.getDouble("distance_m"),2);assertEquals(10,r.getDouble("ascent_m"),.1);assertFalse(r.has("estimated_minutes"));assertEquals(2,calls.size());
 }
 @Test public void elevationFailureLeavesUnknownNotZeroAndBadRouteDoesNotBecomeStraightLine() throws Exception {
  Transport t=(method,url,headers,body)->url.endsWith("/route")?response("{\"trip\":{\"legs\":[{\"shape\":{\"coordinates\":[[115.85,-31.95],[115.85,-31.949]]}}]}}"):new Transport.Response(503,new byte[0]);
  JSONObject r=new MapServices(t,null).route(new JSONArray("[[-31.95,115.85],[-31.949,115.85]]"),"any");assertTrue(r.isNull("ascent_m"));
  assertThrows(Exception.class,()->new MapServices((m,u,h,b)->response("{}"),null).route(new JSONArray("[[0,0],[0,0.01]]"),"any"));
 }
 @Test public void rejectsUnjoinedEstablishedGeometryInsteadOfInventingConnectors() throws Exception {
  JSONObject relation=new JSONObject("{\"id\":123,\"tags\":{\"name\":\"Track\"},\"members\":[{\"type\":\"way\",\"geometry\":[{\"lat\":0,\"lon\":0},{\"lat\":0,\"lon\":0.01}]},{\"type\":\"way\",\"geometry\":[{\"lat\":1,\"lon\":1},{\"lat\":1,\"lon\":1.01}]}]}");
  assertNull(MapServices.established(relation));
 }
 @Test public void cachesManualPlaceSearchAndIdentifiesClient() throws Exception {
  final int[] count={0};Map<String,JSONObject> entries=new HashMap<>();MapServices.Cache cache=new MapServices.Cache(){public JSONObject get(String key){return entries.get(key);}public void put(String key,JSONObject value){entries.put(key,value);}};
  MapServices service=new MapServices((m,u,h,b)->{count[0]++;assertTrue(h.get("User-Agent").contains("EnduranceCoach"));return response("[{\"lat\":\"-31.95\",\"lon\":\"115.85\",\"display_name\":\"Perth\"}]");},cache);
  assertEquals(1,service.search("Perth").length());service.search("Perth");assertEquals(1,count[0]);
 }
 private static Transport.Response response(String text){return new Transport.Response(200,text.getBytes(StandardCharsets.UTF_8));}
}
