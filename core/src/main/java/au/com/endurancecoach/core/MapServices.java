package au.com.endurancecoach.core;
import org.json.*;import java.io.*;import java.net.URLEncoder;import java.nio.charset.StandardCharsets;import java.util.*;import java.security.MessageDigest;

/** Public providers are replaceable. Matching and saved route format do not depend on them. */
public final class MapServices {
 public interface Cache {JSONObject get(String key)throws Exception;void put(String key,JSONObject value)throws Exception;}
 private final Transport transport;private final Cache cache;private final String routing,geocoding,overpass;
 public MapServices(Transport transport,Cache cache){this(transport,cache,"https://valhalla1.openstreetmap.de","https://nominatim.openstreetmap.org","https://overpass-api.de/api/interpreter");}
 public MapServices(Transport transport,Cache cache,String routing,String geocoding,String overpass){PublicMapTransport.validateEndpoint(routing);PublicMapTransport.validateEndpoint(geocoding);PublicMapTransport.validateEndpoint(overpass);this.transport=transport;this.cache=cache;this.routing=routing.replaceAll("/$","");this.geocoding=geocoding.replaceAll("/$","");this.overpass=overpass;}
 public JSONArray search(String query)throws Exception{
  query=query.trim();if(query.length()<2||query.length()>200)throw new IllegalArgumentException("Enter a place name (2–200 characters)");
  String text=call("GET",geocoding+"/search?format=jsonv2&limit=5&q="+URLEncoder.encode(query,"UTF-8"),null,86400000).getString("text");JSONArray source=new JSONArray(text),out=new JSONArray();
  for(int i=0;i<Math.min(5,source.length());i++){JSONObject r=source.getJSONObject(i);JSONArray point=new JSONArray().put(Double.parseDouble(r.getString("lat"))).put(Double.parseDouble(r.getString("lon")));RoutePlan.point(point);out.put(new JSONObject().put("name",r.optString("display_name","Place")).put("point",point));}return out;
 }
 public JSONObject route(JSONArray waypoints,String terrain)throws Exception{
  if(waypoints==null||waypoints.length()<2||waypoints.length()>24)throw new IllegalArgumentException("Choose 2–24 waypoints");
  JSONArray locations=new JSONArray();for(int i=0;i<waypoints.length();i++){JSONArray p=waypoints.getJSONArray(i);RoutePlan.point(p);locations.put(new JSONObject().put("lat",p.getDouble(0)).put("lon",p.getDouble(1)).put("type","break"));}
  JSONObject options=new JSONObject().put("type","foot").put("use_ferry",0).put("use_living_streets",1).put("use_tracks",terrain.equals("road")?0:terrain.equals("trail")?1:.5).put("use_hills",.5);
  JSONObject request=new JSONObject().put("locations",locations).put("costing","pedestrian").put("costing_options",new JSONObject().put("pedestrian",options)).put("units","kilometers").put("shape_format","geojson");
  JSONObject response=new JSONObject(call("POST",routing+"/route",request.toString(),86400000).getString("text"));JSONArray legs=response.getJSONObject("trip").getJSONArray("legs"),points=new JSONArray();
  for(int i=0;i<legs.length();i++){
   Object shape=legs.getJSONObject(i).get("shape");JSONArray leg=new JSONArray();if(shape instanceof JSONObject){JSONArray coords=((JSONObject)shape).getJSONArray("coordinates");for(int j=0;j<coords.length();j++){JSONArray c=coords.getJSONArray(j);leg.put(new JSONArray().put(c.getDouble(1)).put(c.getDouble(0)));}}else leg=decodePolyline(String.valueOf(shape));
   if(leg.length()<2)throw new IOException("Routing service returned no usable path");
   if(points.length()>0){JSONArray a=points.getJSONArray(points.length()-1),b=leg.getJSONArray(0);if(RoutePlan.distance(a.getDouble(0),a.getDouble(1),b.getDouble(0),b.getDouble(1))>25)throw new IOException("Routing returned disconnected legs");}
   for(int j=i==0?0:1;j<leg.length();j++){points.put(leg.getJSONArray(j));if(points.length()>20000)throw new IOException("Route too complex");}
  }
  // Detect unreasonable source/end snaps instead of silently relocating the chosen start.
  if(points.length()<2)throw new IOException("No mapped pedestrian route found");JSONArray first=points.getJSONArray(0),last=points.getJSONArray(points.length()-1),start=waypoints.getJSONArray(0),end=waypoints.getJSONArray(waypoints.length()-1);
  if(RoutePlan.distance(first.getDouble(0),first.getDouble(1),start.getDouble(0),start.getDouble(1))>500||RoutePlan.distance(last.getDouble(0),last.getDouble(1),end.getDouble(0),end.getDouble(1))>500)throw new IOException("Chosen point is over 500 m from the mapped pedestrian network");
  JSONObject r=RoutePlan.create("Mapped route","drawn",points,waypoints,"OpenStreetMap contributors · routing: FOSSGIS Valhalla","");return elevation(r);
 }
 public JSONObject elevation(JSONObject route)throws Exception{
  JSONArray points=route.getJSONArray("points"),sample=new JSONArray();int count=Math.min(200,points.length());
  for(int i=0;i<count;i++){JSONArray p=points.getJSONArray((int)Math.round((points.length()-1)*i/(double)(count-1)));sample.put(new JSONObject().put("lat",p.getDouble(0)).put("lon",p.getDouble(1)));}
  try{
   JSONObject reply=new JSONObject(call("POST",routing+"/height",new JSONObject().put("shape",sample).toString(),604800000).getString("text"));JSONArray heights=reply.getJSONArray("height");if(heights.length()!=count)throw new IOException("Incomplete elevation");
   JSONArray profile=new JSONArray();boolean complete=true;double ascent=0,anchor=Double.NaN;
   for(int i=0;i<count;i++){Object value=heights.get(i);double h=value instanceof Number?((Number)value).doubleValue():Double.NaN;if(!Double.isFinite(h)||h<-500||h>10000){complete=false;h=Double.NaN;}
    JSONObject pos=sample.getJSONObject(i);profile.put(new JSONArray().put(pos.getDouble("lat")).put(pos.getDouble("lon")).put(Double.isFinite(h)?Math.round(h*10)/10.0:JSONObject.NULL));
    if(Double.isNaN(anchor))anchor=h;else if(Double.isFinite(h)){double delta=h-anchor;if(delta>=3){ascent+=delta;anchor=h;}else if(delta<=-3)anchor=h;}
   }
   route.put("profile",profile).put("ascent_m",complete?Math.round(ascent):JSONObject.NULL).put("elevation_note","Sampled terrain elevation; ascent is approximate");
  }catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;route.put("ascent_m",JSONObject.NULL).put("elevation_note","Elevation unavailable; retry from route details");}
  return route;
 }
 public JSONArray generate(JSONArray start,JSONObject requirements)throws Exception{
  RoutePlan.point(start);RoutePlan.validateRequirements(requirements);double target=requirements.getDouble("target_km");String shape=requirements.getString("shape"),terrain=requirements.getString("terrain");JSONArray out=new JSONArray();
  for(int direction=0;direction<3;direction++){
   if(Thread.currentThread().isInterrupted())throw new IOException("Generation cancelled");
   JSONArray waypoints=new JSONArray().put(start);double angle=direction*120;
   if(shape.equals("point_to_point"))waypoints.put(offset(start,target*.8,angle));
   else if(shape.equals("out_back")){waypoints.put(offset(start,target*.4,angle));waypoints.put(start);}
   else{double radius=target/(2+Math.sqrt(3))*.8;waypoints.put(offset(start,radius,angle)).put(offset(start,radius,angle+60)).put(start);}
   try{JSONObject r=route(waypoints,terrain).put("name","Suggested "+(direction+1)).put("source","generated");out.put(r);}catch(Exception error){if(Thread.currentThread().isInterrupted())throw error;}
  }
  if(out.length()==0)throw new IOException("No mapped routes found near this start. Try another point or draw waypoints.");return out;
 }
 public JSONArray discover(JSONArray start,double radiusKm)throws Exception{
  RoutePlan.point(start);if(!Double.isFinite(radiusKm)||radiusKm<.1||radiusKm>100)throw new IllegalArgumentException("Invalid search radius");
  String query=String.format(Locale.ROOT,"[out:json][timeout:20];rel(around:%.0f,%.6f,%.6f)[type=route][route~\"^(hiking|foot|running)$\"];out tags geom 12;",Math.min(25000,radiusKm*1000),start.getDouble(0),start.getDouble(1));
  JSONObject reply=new JSONObject(call("FORM",overpass,"data="+URLEncoder.encode(query,"UTF-8"),86400000).getString("text"));JSONArray elements=reply.getJSONArray("elements"),out=new JSONArray();
  for(int i=0;i<Math.min(12,elements.length());i++){JSONObject r=established(elements.getJSONObject(i));if(r!=null)out.put(r);}return out;
 }
 public static JSONObject established(JSONObject relation)throws Exception{
  JSONArray members=relation.optJSONArray("members");if(members==null)return null;List<JSONArray> ways=new ArrayList<>();
  for(int i=0;i<members.length();i++){JSONObject member=members.getJSONObject(i);if(!member.optString("type").equals("way"))continue;JSONArray g=member.optJSONArray("geometry");if(g==null||g.length()<2)return null;JSONArray w=new JSONArray();for(int j=0;j<g.length();j++){JSONObject p=g.getJSONObject(j);w.put(new JSONArray().put(p.getDouble("lat")).put(p.getDouble("lon")));}ways.add(w);}
  if(ways.isEmpty()||ways.size()>1000)return null;JSONArray joined=ways.remove(0);int total=joined.length();
  while(!ways.isEmpty()){
   boolean found=false;for(int i=0;i<ways.size();i++){JSONArray way=ways.get(i);for(int mode=0;mode<4;mode++){
    JSONArray end=joined.getJSONArray(mode<2?joined.length()-1:0),candidate=way.getJSONArray(mode%2==0?0:way.length()-1);
    if(RoutePlan.distance(end.getDouble(0),end.getDouble(1),candidate.getDouble(0),candidate.getDouble(1))>3)continue;
    JSONArray ordered=new JSONArray();boolean reverse=mode%2==1;if(mode<2){for(int j=0;j<joined.length();j++)ordered.put(joined.get(j));for(int j=1;j<way.length();j++)ordered.put(way.get(reverse?way.length()-1-j:j));}
    else{for(int j=way.length()-1;j>0;j--)ordered.put(way.get(reverse?way.length()-1-j:j));for(int j=0;j<joined.length();j++)ordered.put(joined.get(j));}
    joined=ordered;ways.remove(i);found=true;break;
   }if(found)break;}if(!found)return null;total=joined.length();if(total>20000)return null;
  }
  JSONObject tags=relation.optJSONObject("tags");String name=tags==null?"Mapped walking route":tags.optString("name","Mapped walking route");name=name.substring(0,Math.min(120,name.length()));
  try{return RoutePlan.create(name,"established",joined,new JSONArray(),"OpenStreetMap contributors · mapped route relation; current access unverified","https://www.openstreetmap.org/relation/"+relation.getLong("id"));}catch(IllegalArgumentException bad){return null;}
 }
 private JSONObject call(String method,String url,String body,long ttl)throws Exception{
  String key=hash(method+url+body);if(cache!=null){JSONObject entry=cache.get(key);if(entry!=null&&System.currentTimeMillis()-entry.optLong("saved_at",0)<ttl&&entry.has("text"))return entry;}
  Map<String,String> headers=new HashMap<>();headers.put("User-Agent",PublicMapTransport.CLIENT);headers.put("X-Client-Id","au.com.endurancecoach");headers.put("Accept","application/json");headers.put("Content-Type",method.equals("FORM")?"application/x-www-form-urlencoded":"application/json");
  Transport.Response response=transport.request(method.equals("FORM")?"POST":method,url,headers,body==null?null:body.getBytes(StandardCharsets.UTF_8));if(response.code==429)throw new IOException("Map service is busy. Wait a minute before retrying.");response.requireSuccess();
  String result=response.text();if(result.length()>6000000)throw new IOException("Map response too large");JSONObject entry=new JSONObject().put("saved_at",System.currentTimeMillis()).put("text",result);if(cache!=null)cache.put(key,entry);return entry;
 }
 private static String hash(String s)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
 public static JSONArray offset(JSONArray start,double km,double bearing)throws Exception{double a=km/6371.0088,b=Math.toRadians(bearing),lat=Math.toRadians(start.getDouble(0)),lon=Math.toRadians(start.getDouble(1));double y=Math.asin(Math.sin(lat)*Math.cos(a)+Math.cos(lat)*Math.sin(a)*Math.cos(b)),x=lon+Math.atan2(Math.sin(b)*Math.sin(a)*Math.cos(lat),Math.cos(a)-Math.sin(lat)*Math.sin(y));return new JSONArray().put(Math.toDegrees(y)).put(((Math.toDegrees(x)+540)%360)-180);}
 private static JSONArray decodePolyline(String shape)throws Exception{JSONArray out=new JSONArray();int[] cursor={0};long lat=0,lon=0;while(cursor[0]<shape.length()){lat+=decode(shape,cursor);lon+=decode(shape,cursor);JSONArray p=new JSONArray().put(lat/1e6).put(lon/1e6);RoutePlan.point(p);out.put(p);if(out.length()>20000)throw new IOException("Route too complex");}return out;}
 private static long decode(String text,int[] cursor)throws IOException{long value=0;int shift=0,b;do{if(cursor[0]>=text.length()||shift>35)throw new IOException("Invalid route shape");b=text.charAt(cursor[0]++)-63;if(b<0||b>63)throw new IOException("Invalid route shape");value|=(long)(b&31)<<shift;shift+=5;}while(b>=32);return (value&1)!=0?~(value>>1):(value>>1);}
}
