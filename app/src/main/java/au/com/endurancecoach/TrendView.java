package au.com.endurancecoach;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Measured points only; gaps break the line rather than inventing missing values. */
final class TrendView extends View {
 private final List<Double> points;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 TrendView(Context c,List<Double> points){super(c);this.points=points;setContentDescription("Trend of measured values, missing values shown as gaps");}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(Double v:points)if(v!=null&&Double.isFinite(v)){min=Math.min(min,v);max=Math.max(max,v);}if(!Double.isFinite(min))return;
  float pad=16,span=Math.max(1,getWidth()-2*pad),height=Math.max(1,getHeight()-2*pad);p.setColor(Color.rgb(20,125,114));p.setStrokeWidth(3);Float lastX=null,lastY=null;
  for(int i=0;i<points.size();i++){Double v=points.get(i);if(v==null||!Double.isFinite(v)){lastX=null;lastY=null;continue;}float x=pad+span*i/Math.max(1,points.size()-1),y=pad+height-(float)((v-min)/Math.max(1,max-min))*height;if(lastX!=null)canvas.drawLine(lastX,lastY,x,y,p);canvas.drawCircle(x,y,3,p);lastX=x;lastY=y;}
 }
}
