package au.com.endurancecoach.core;
import org.json.*;
import java.time.*;
import java.util.*;

public final class RouteMatcher {
 private RouteMatcher(){}
 public static String sessionKey(JSONObject session){String t=session.optString("type","run").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");String text=(session.optString("title")+" "+session.optString("intensity")).toLowerCase(Locale.ROOT);return t+(text.contains("hill")?"_hills":text.contains("trail")?"_trail":text.contains("long")?"_long":text.contains("interval")||text.contains("tempo")?"_quality":"_easy");}
 public static JSONObject defaults(JSONObject session,JSONArray activities)throws Exception{
  String text=(session.optString("title")+" "+session.optString("intensity")).toLowerCase(Locale.ROOT);double minutes=Math.max(10,session.optDouble("duration_min",60)),pace=pace(activities,text.contains("trail"))[0];
  double km=session.optDouble("distance_km",session.optDouble("distance_m",0)/1000);if(km<=0)km=Math.max(.1,Math.min(200,minutes/(pace/60)));
  return new JSONObject().put("target_km",Math.round(km*10)/10.0).put("duration_min",Math.min(1440,minutes)).put("target_mode",session.optDouble("distance_km",0)>0?"distance":"duration").put("terrain",text.contains("trail")?"trail":"any").put("climbing",text.contains("hill")?"hilly":"any").put("shape","any").put("radius_km",15);
 }
 public static double[] pace(JSONArray activities,boolean trail){
  List<Double> values=new ArrayList<>();Instant cutoff=Instant.now().minus(Duration.ofDays(90));
  for(int i=0;i<activities.length();i++)try{JSONObject a=activities.getJSONObject(i);String type=a.optString("type").toLowerCase(Locale.ROOT);if(!type.contains("run")||trail&&!type.contains("trail")||!trail&&type.contains("trail")||Instant.parse(a.getString("start_utc")).isBefore(cutoff))continue;double d=a.getDouble("distance_m"),s=a.getDouble("duration_s"),p=s/d*1000;if(d>=1000&&s>=300&&p>=180&&p<=1200)values.add(p);}catch(Exception ignored){}
  Collections.sort(values);double p=values.isEmpty()?420:values.size()%2==1?values.get(values.size()/2):(values.get(values.size()/2-1)+values.get(values.size()/2))/2;return new double[]{p,values.size()};
 }
 public static JSONObject assess(JSONObject route,JSONObject requirements,JSONArray activities)throws Exception{
  boolean trail=route.optString("terrain").equals("trail")||requirements.optString("terrain").equals("trail");double[] history=pace(activities,trail);double km=route.getDouble("distance_m")/1000,ascent=route.optDouble("ascent_m",Double.NaN);double estimate=km*history[0]/60+(Double.isFinite(ascent)?ascent/100*2:0);if(trail&&history[1]==0)estimate*=1.15;
  List<String> notes=new ArrayList<>();boolean matches=true;double target=requirements.optDouble("target_km",km),error=Math.abs(km-target)/target;
  if(error>.2){matches=false;notes.add(String.format(Locale.UK,"Distance is %.0f%% %s than the %.1f km target",error*100,km>target?"longer":"shorter",target));}else notes.add(String.format(Locale.UK,"%.1f km is within 20%% of the %.1f km target",km,target));
  if(requirements.optString("target_mode").equals("duration")){double minutes=requirements.optDouble("duration_min",estimate);if(Math.abs(estimate-minutes)/minutes>.2){matches=false;notes.add(String.format(Locale.UK,"Estimated time differs from the %.0f min session",minutes));}}
  String terrain=requirements.optString("terrain","any");if(!terrain.equals("any")&&!terrain.equals(route.optString("terrain"))){matches=false;notes.add("Surface preference unverified; mapped foot access does not confirm surface");}
  String climbing=requirements.optString("climbing","any");if(!climbing.equals("any")){if(!Double.isFinite(ascent)){matches=false;notes.add("Climbing unverified: elevation unavailable");}else{double density=ascent/km;boolean ok=climbing.equals("flat")?density<=10:climbing.equals("rolling")?density>10&&density<=30:density>30;if(!ok){matches=false;notes.add("Climbing differs from your "+climbing+" preference");}}}
  JSONArray points=route.getJSONArray("points"),a=points.getJSONArray(0),b=points.getJSONArray(points.length()-1);boolean closed=RoutePlan.distance(a.getDouble(0),a.getDouble(1),b.getDouble(0),b.getDouble(1))<=80;String shape=actualShape(points,closed);
  String wanted=requirements.optString("shape","any");if(!wanted.equals("any")&&!wanted.equals(shape)){matches=false;notes.add("Actual shape: "+shape.replace('_',' ') +"; differs from your preference");}
  JSONArray start=requirements.optJSONArray("start");double offset=start==null?0:RoutePlan.distance(start.getDouble(0),start.getDouble(1),a.getDouble(0),a.getDouble(1))/1000;
  if(start!=null){notes.add(String.format(Locale.UK,"Starts %.1f km from your chosen point",offset));if(offset>requirements.optDouble("radius_km",15))matches=false;}
  if(!Double.isFinite(ascent))notes.add("Elevation unavailable; time may be underestimated");
  String basis=history[1]>0?((int)history[1])+" recent comparable run(s); median pace":"7:00 min/km fallback; no comparable recent runs";
  notes.add("Estimate uses "+basis+"; effort, stops and trail conditions can change it");
  return new JSONObject().put("matches",matches).put("score",error+offset/100+(matches?0:1)).put("estimated_minutes",Math.round(estimate)).put("estimate_basis",basis).put("shape",shape).put("explanation",String.join(". ",notes)+".");
 }
 private static String actualShape(JSONArray p,boolean closed)throws Exception{
  if(!closed)return "point_to_point";
  // Compare corresponding distance samples in opposite directions; a repeated path is not a loop.
  double total=RoutePlan.geometry(p).getDouble("distance_m");int n=p.length();double[] cumulative=new double[n];for(int i=1;i<n;i++){JSONArray a=p.getJSONArray(i-1),b=p.getJSONArray(i);cumulative[i]=cumulative[i-1]+RoutePlan.distance(a.getDouble(0),a.getDouble(1),b.getDouble(0),b.getDouble(1));}
  int same=0;for(int k=1;k<=8;k++){double d=total*k/20;JSONArray a=at(p,cumulative,d),b=at(p,cumulative,total-d);if(RoutePlan.distance(a.getDouble(0),a.getDouble(1),b.getDouble(0),b.getDouble(1))<Math.max(30,total*.015))same++;}return same>=6?"out_back":"loop";
 }
 private static JSONArray at(JSONArray p,double[] d,double target)throws Exception{int i=1;while(i<d.length-1&&d[i]<target)i++;JSONArray a=p.getJSONArray(i-1),b=p.getJSONArray(i);double f=d[i]==d[i-1]?0:(target-d[i-1])/(d[i]-d[i-1]);return new JSONArray().put(a.getDouble(0)+(b.getDouble(0)-a.getDouble(0))*f).put(a.getDouble(1)+(b.getDouble(1)-a.getDouble(1))*f);}
}
