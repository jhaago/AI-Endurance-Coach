package au.com.endurancecoach;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import au.com.endurancecoach.core.DietQuality;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fixed 0–100 scale. Null dates break lines; today's provisional value is hollow. */
final class DietQualityTrendView extends View {
 private final DietQuality.Trend trend;private final LocalDate end;private final CoachTheme theme;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 DietQualityTrendView(Context context,DietQuality.Trend trend,LocalDate end,CoachTheme theme){
  super(context);this.trend=trend;this.end=end;this.theme=theme;int count=0;for(Double point:trend.values)if(point!=null)count++;
  setContentDescription("Diet quality, scale 0 to 100. "+count+" completed days."+(trend.provisionalScore==null?"":" Provisional today "+String.format(Locale.UK,"%.0f",trend.provisionalScore)+".")+" Missing values shown as gaps. Provisional scores excluded from the average.");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
 }
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
  float left=paint.measureText("100")+10*d,right=getWidth()-8*d,top=16*d,bottom=getHeight()-26*d;if(right<=left||bottom<=top)return;
  for(int level:new int[]{0,50,100}){float y=bottom-level/100f*(bottom-top);paint.setColor(theme.outline);paint.setStrokeWidth(d);canvas.drawLine(left,y,right,y,paint);paint.setColor(theme.muted);canvas.drawText(String.valueOf(level),0,y+4*d,paint);}
  DateTimeFormatter format=DateTimeFormatter.ofPattern("d MMM",Locale.UK);paint.setColor(theme.muted);String first=end.minusDays(trend.values.size()-1L).format(format),last=end.format(format);canvas.drawText(first,left,getHeight()-4*d,paint);canvas.drawText(last,right-paint.measureText(last),getHeight()-4*d,paint);
  float previousX=0,previousY=0;boolean previous=false,any=false;paint.setColor(theme.accent);paint.setStrokeWidth(2*d);
  for(int i=0;i<trend.values.size();i++){Double point=trend.values.get(i);if(point==null){previous=false;continue;}any=true;float x=left+(right-left)*i/(trend.values.size()-1f),y=bottom-point.floatValue()/100*(bottom-top);if(previous)canvas.drawLine(previousX,previousY,x,y,paint);canvas.drawCircle(x,y,3*d,paint);previous=true;previousX=x;previousY=y;}
  if(trend.provisionalScore!=null){any=true;float x=left+(right-left)*trend.provisionalIndex/(trend.values.size()-1f),y=bottom-trend.provisionalScore.floatValue()/100*(bottom-top);paint.setStyle(Paint.Style.STROKE);canvas.drawCircle(x,y,6*d,paint);paint.setStyle(Paint.Style.FILL);}
  if(!any){paint.setColor(theme.muted);String message="No completed assessments yet";canvas.drawText(message,left,(top+bottom)/2,paint);}
 }
}
