package au.com.endurancecoach;
import android.content.Context;import android.graphics.*;import android.view.View;import org.json.*;

/** Elevation gaps remain gaps; axes use metres and route distance. */
final class RouteProfileView extends View {
 private final CoachTheme theme;private final JSONArray points;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 RouteProfileView(Context c,CoachTheme theme,JSONObject route){super(c);this.theme=theme;JSONArray profile=route.optJSONArray("profile");points=profile==null?route.optJSONArray("points"):profile;setContentDescription(route.isNull("ascent_m")?"Elevation unavailable or incomplete":"Elevation profile. Approximate total ascent "+route.optDouble("ascent_m")+" metres");setFocusable(true);}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(theme.surface);double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;int count=0;
  if(points!=null)for(int i=0;i<points.length();i++){JSONArray v=points.optJSONArray(i);if(v!=null&&v.length()>2&&!v.isNull(2)){double h=v.optDouble(2);min=Math.min(min,h);max=Math.max(max,h);count++;}}
  p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);p.setColor(theme.muted);if(count<2){canvas.drawText("Elevation unavailable",10,getHeight()/2f,p);return;}
  double[] distance=new double[points.length()];for(int i=1;i<points.length();i++){JSONArray a=points.optJSONArray(i-1),b=points.optJSONArray(i);distance[i]=distance[i-1]+au.com.endurancecoach.core.RoutePlan.distance(a.optDouble(0),a.optDouble(1),b.optDouble(0),b.optDouble(1));}double total=distance[distance.length-1],range=Math.max(10,max-min);Path path=new Path();boolean open=false;
  for(int i=0;i<points.length();i++){JSONArray v=points.optJSONArray(i);if(v==null||v.length()<3||v.isNull(2)){open=false;continue;}float x=48+(float)(distance[i]/Math.max(1,total)*(getWidth()-60)),y=20+(float)((max-v.optDouble(2))/range*(getHeight()-50));if(open)path.lineTo(x,y);else path.moveTo(x,y);open=true;}
  p.setColor(theme.accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);p.setColor(theme.muted);canvas.drawText(Math.round(max)+" m",4,20,p);canvas.drawText(Math.round(min)+" m",4,getHeight()-30,p);canvas.drawText("0",48,getHeight()-5,p);canvas.drawText(String.format(java.util.Locale.UK,"%.1f km",total/1000),Math.max(48,getWidth()-70),getHeight()-5,p);
 }
}
