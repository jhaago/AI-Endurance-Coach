package au.com.endurancecoach.core;

import org.json.*;
import java.time.Instant;
import java.util.*;

/** Compact route snapshots and immutable changes shared by phone and Coach bridge. */
public final class RoutePlan {
 private RoutePlan() {}
 public static final int MAX_POINTS=1000, MAX_PAYLOAD=45000;
 public static double distance(double lat1,double lon1,double lat2,double lon2) {
  double a=Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);
  return 6371008.8*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
 }
 public static void point(JSONArray p) {
  if(p==null||p.length()<2||p.length()>3||!(p.opt(0) instanceof Number)||!(p.opt(1) instanceof Number)||!Double.isFinite(p.optDouble(0))||!Double.isFinite(p.optDouble(1))||Math.abs(p.optDouble(0))>85.05112878||Math.abs(p.optDouble(1))>180)throw new IllegalArgumentException("Invalid map coordinates");
  if(p.length()==3&&!p.isNull(2)&&(!(p.opt(2) instanceof Number)||!Double.isFinite(p.optDouble(2))||p.optDouble(2)<-500||p.optDouble(2)>10000))throw new IllegalArgumentException("Invalid elevation");
 }
 public static JSONObject geometry(JSONArray points) throws Exception {
  if(points==null||points.length()<2||points.length()>20000)throw new IllegalArgumentException("A route needs 2–20000 points");
  double length=0,ascent=0;boolean heights=true;double anchor=points.getJSONArray(0).optDouble(2,Double.NaN);
  for(int i=0;i<points.length();i++){
   JSONArray p=points.getJSONArray(i);point(p);if(p.length()<3||p.isNull(2))heights=false;
   if(i>0){JSONArray q=points.getJSONArray(i-1);length+=distance(q.getDouble(0),q.getDouble(1),p.getDouble(0),p.getDouble(1));}
   if(heights){double change=p.getDouble(2)-anchor;if(change>=3){ascent+=change;anchor=p.getDouble(2);}else if(change<=-3)anchor=p.getDouble(2);}
  }
  if(length<=0||length>1000000)throw new IllegalArgumentException("Route distance is outside 0–1000 km");
  return new JSONObject().put("distance_m",Math.round(length*10)/10.0).put("ascent_m",heights?Math.round(ascent*10)/10.0:JSONObject.NULL);
 }
 public static JSONArray compact(JSONArray points) throws Exception {
  // Retain points at a bounded distance-error tolerance, increasing only if the cell limit needs it.
  if(points.length()<=MAX_POINTS)return new JSONArray(points.toString());
  List<Integer> kept=new ArrayList<>();
  for(double tolerance=2;tolerance<=2048;tolerance*=2){
   boolean[] keep=new boolean[points.length()];keep[0]=keep[keep.length-1]=true;
   ArrayDeque<int[]> ranges=new ArrayDeque<>();ranges.push(new int[]{0,keep.length-1});
   while(!ranges.isEmpty()){
    int[] r=ranges.pop();JSONArray a=points.getJSONArray(r[0]),b=points.getJSONArray(r[1]);double largest=tolerance;int index=-1;
    double scale=Math.cos(Math.toRadians((a.getDouble(0)+b.getDouble(0))/2)),bx=(b.getDouble(1)-a.getDouble(1))*scale*111195,by=(b.getDouble(0)-a.getDouble(0))*111195,den=bx*bx+by*by;
    for(int i=r[0]+1;i<r[1];i++){JSONArray p=points.getJSONArray(i);double x=(p.getDouble(1)-a.getDouble(1))*scale*111195,y=(p.getDouble(0)-a.getDouble(0))*111195,t=den==0?0:Math.max(0,Math.min(1,(x*bx+y*by)/den)),d=Math.hypot(x-t*bx,y-t*by);if(d>largest){largest=d;index=i;}}
    if(index>=0){keep[index]=true;ranges.push(new int[]{r[0],index});ranges.push(new int[]{index,r[1]});}
   }
   kept.clear();for(int i=0;i<keep.length;i++)if(keep[i])kept.add(i);if(kept.size()<=MAX_POINTS)break;
  }
  if(kept.size()>MAX_POINTS)throw new IllegalArgumentException("Route is too complex to save");
  JSONArray out=new JSONArray();for(int i:kept)out.put(points.getJSONArray(i));return out;
 }
 public static JSONObject create(String name,String source,JSONArray points,JSONArray waypoints,String attribution,String url) throws Exception {
  JSONObject m=geometry(points);JSONArray compact=compact(points);
  JSONObject r=new JSONObject().put("name",name).put("source",source).put("points",compact).put("waypoints",waypoints).put("distance_m",m.get("distance_m")).put("ascent_m",m.get("ascent_m")).put("attribution",attribution).put("source_url",url).put("terrain","unknown").put("favourite",false).put("simplified",compact.length()<points.length());
  validateRoute(r);return r;
 }
 public static void validateRoute(JSONObject r) throws Exception {
  if(r==null||!text(r,"name",1,120)||!Arrays.asList("imported","established","generated","drawn").contains(r.optString("source"))||!text(r,"attribution",1,500)||!text(r,"source_url",0,1000)||!(r.opt("favourite") instanceof Boolean)||!(r.opt("simplified") instanceof Boolean)||!Arrays.asList("unknown","road","path","trail","mixed").contains(r.optString("terrain")))throw new IllegalArgumentException("Invalid route details");
  JSONArray p=r.optJSONArray("points"),w=r.optJSONArray("waypoints");if(p==null||p.length()>MAX_POINTS||w==null||w.length()>24)throw new IllegalArgumentException("Invalid route geometry");geometry(p);for(int i=0;i<w.length();i++)point(w.getJSONArray(i));
  if(!number(r,"distance_m",.01,1000000)||(!r.isNull("ascent_m")&&!number(r,"ascent_m",0,100000)))throw new IllegalArgumentException("Invalid route measurements");
  String url=r.optString("source_url");if(!url.isEmpty()&&!url.matches("https://www\\.openstreetmap\\.org/relation/[0-9]+"))throw new IllegalArgumentException("Invalid source link");
 }
 public static boolean number(JSONObject r,String k,double min,double max){Object v=r.opt(k);return v instanceof Number&&Double.isFinite(((Number)v).doubleValue())&&((Number)v).doubleValue()>=min&&((Number)v).doubleValue()<=max;}
 private static boolean text(JSONObject r,String k,int min,int max){Object v=r.opt(k);return v instanceof String&&((String)v).length()>=min&&((String)v).length()<=max;}
 public static void validateRequirements(JSONObject r) throws Exception {
  if(r==null||!number(r,"target_km",.1,200)||!number(r,"radius_km",.1,100)||!Arrays.asList("any","road","path","trail").contains(r.optString("terrain"))||!Arrays.asList("any","flat","rolling","hilly").contains(r.optString("climbing"))||!Arrays.asList("any","loop","out_back","point_to_point").contains(r.optString("shape")))throw new IllegalArgumentException("Invalid route filters");
  JSONArray start=r.optJSONArray("start");if(start!=null)point(start);
  if(r.has("duration_min")&&!number(r,"duration_min",1,1440))throw new IllegalArgumentException("Invalid target duration");
 }
 public static void validateChange(JSONObject c) throws Exception {
  if(c==null||!c.optString("change_id").matches("[a-zA-Z0-9_:.-]{1,100}")||!c.optString("record_id").matches("[a-zA-Z0-9_:.-]{1,100}")||!(c.opt("deleted") instanceof Boolean)||!text(c,"payload_json",2,MAX_PAYLOAD))throw new IllegalArgumentException("Invalid route change");
  try {if(!Instant.parse(c.getString("updated_at")).toString().equals(Instant.ofEpochMilli(Instant.parse(c.getString("updated_at")).toEpochMilli()).toString()))throw new Exception();}catch(Exception e){throw new IllegalArgumentException("Invalid route timestamp");}
  JSONObject p=new JSONObject(c.getString("payload_json"));String kind=c.optString("kind"),id=c.getString("record_id");
  if(kind.equals("route")){if(!id.startsWith("route:"))throw new IllegalArgumentException("Invalid route ID");validateRoute(p);}
  else if(kind.equals("attachment")){if(!id.startsWith("attachment:")||!text(p,"workout_id",1,100)||!id.equals("attachment:"+p.getString("workout_id")))throw new IllegalArgumentException("Invalid session attachment");validateRoute(p.getJSONObject("route"));validateRequirements(p.getJSONObject("requirements"));}
  else if(kind.equals("preference")){if(!id.startsWith("preference:"))throw new IllegalArgumentException("Invalid session preference");validateRequirements(p);}
  else throw new IllegalArgumentException("Invalid route change kind");
 }
 public static JSONObject change(String id,String kind,JSONObject payload,boolean deleted,JSONObject previous,long now) throws Exception {
  long time=previous==null?now:Math.max(now,Instant.parse(previous.getString("updated_at")).toEpochMilli()+1);
  JSONObject c=new JSONObject().put("change_id",UUID.randomUUID().toString()).put("record_id",id).put("kind",kind).put("payload_json",payload.toString()).put("deleted",deleted).put("updated_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.ROOT){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date(time)));
  validateChange(c);return c;
 }
 public static JSONArray current(JSONArray... ledgers) throws Exception {
  Map<String,JSONObject> latest=new LinkedHashMap<>();Map<String,String> ids=new HashMap<>();
  for(JSONArray ledger:ledgers)for(int i=0;i<ledger.length();i++){
   JSONObject c=ledger.getJSONObject(i);validateChange(c);String id=c.getString("change_id"),fp=fingerprint(c);String known=ids.put(id,fp);if(known!=null&&!known.equals(fp))throw new IllegalArgumentException("Conflicting route change");
   String key=c.getString("record_id");JSONObject old=latest.get(key);if(old==null||compare(c,old)>0)latest.put(key,new JSONObject(c.toString()));
  }
  return new JSONArray(new ArrayList<>(latest.values()));
 }
 private static int compare(JSONObject a,JSONObject b)throws Exception{int t=Instant.parse(a.getString("updated_at")).compareTo(Instant.parse(b.getString("updated_at")));return t!=0?t:a.getString("change_id").compareTo(b.getString("change_id"));}
 private static String fingerprint(JSONObject c)throws Exception{return new JSONArray().put(c.get("record_id")).put(c.get("kind")).put(c.get("payload_json")).put(c.get("deleted")).put(c.get("updated_at")).toString();}
 public static JSONObject find(JSONArray rows,String id)throws Exception{for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("record_id")))return rows.getJSONObject(i);return null;}
 public static JSONObject payload(JSONObject change)throws Exception{return change==null||change.optBoolean("deleted")?null:new JSONObject(change.getString("payload_json"));}
}
