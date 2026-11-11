package au.com.endurancecoach;
import android.content.Context;import org.json.*;import au.com.endurancecoach.core.*;import java.util.*;

/** SQLite owns durable drafts, provider cache and offline immutable route events. */
final class RouteStore implements MapServices.Cache {
 private final LocalStore store;private final Context context;
 RouteStore(Context context,LocalStore store){this.store=store;this.context=context.getApplicationContext();}
 JSONObject attachment(String workout)throws Exception{return RoutePlan.payload(RoutePlan.find(store.routeRecords(),"attachment:"+workout));}
 JSONObject preference(String type)throws Exception{return RoutePlan.payload(RoutePlan.find(store.routeRecords(),"preference:"+type));}
 JSONArray routes()throws Exception{JSONArray out=new JSONArray(),rows=store.routeRecords();for(int i=0;i<rows.length();i++){JSONObject c=rows.getJSONObject(i);if(c.optString("kind").equals("route")&&!c.optBoolean("deleted")){JSONObject p=RoutePlan.payload(c);p.put("record_id",c.getString("record_id"));out.put(p);}}return out;}
 JSONObject save(JSONObject route,String id)throws Exception{if(id==null||id.isEmpty())id="route:"+UUID.randomUUID();JSONObject copy=new JSONObject(route.toString());copy.remove("record_id");JSONObject c=RoutePlan.change(id,"route",copy,false,RoutePlan.find(store.routeRecords(),id),System.currentTimeMillis());store.saveRouteChanges(new JSONArray().put(c));route.put("record_id",id);return c;}
 void delete(JSONObject route)throws Exception{String id=route.getString("record_id");store.saveRouteChanges(new JSONArray().put(RoutePlan.change(id,"route",route,true,RoutePlan.find(store.routeRecords(),id),System.currentTimeMillis())));}
 void attach(JSONObject session,JSONObject route,JSONObject requirements)throws Exception{
  JSONArray rows=store.routeRecords();String workout=session.getString("workout_id"),key=RouteMatcher.sessionKey(session);JSONObject payload=new JSONObject().put("workout_id",workout).put("session_revision",session.optInt("revision",1)).put("route",new JSONObject(route.toString())).put("requirements",new JSONObject(requirements.toString()));
  JSONObject attachment=RoutePlan.change("attachment:"+workout,"attachment",payload,false,RoutePlan.find(rows,"attachment:"+workout),System.currentTimeMillis()),preference=RoutePlan.change("preference:"+key,"preference",requirements,false,RoutePlan.find(rows,"preference:"+key),System.currentTimeMillis());
  store.saveRouteChanges(new JSONArray().put(attachment).put(preference));
 }
 void detach(String workout)throws Exception{JSONObject old=RoutePlan.find(store.routeRecords(),"attachment:"+workout);if(old!=null)store.saveRouteChanges(new JSONArray().put(RoutePlan.change(old.getString("record_id"),"attachment",new JSONObject(old.getString("payload_json")),true,old,System.currentTimeMillis())));}
 void draft(String workout,JSONObject draft){store.put("route-draft:"+workout,draft);}
 JSONObject draft(String workout)throws Exception{return store.get("route-draft:"+workout);}
 @Override public JSONObject get(String key)throws Exception{return store.get("map-cache:"+key);}
 @Override public void put(String key,JSONObject value)throws Exception{store.put("map-cache:"+key,value);}
 boolean enabled(){return context.getSharedPreferences("route-services",0).getBoolean("enabled",true);}
 MapServices services(){android.content.SharedPreferences p=context.getSharedPreferences("route-services",0);return new MapServices(new PublicMapTransport(),this,p.getString("routing","https://valhalla1.openstreetmap.de"),p.getString("search","https://nominatim.openstreetmap.org"),p.getString("discovery","https://overpass-api.de/api/interpreter"));}
}
