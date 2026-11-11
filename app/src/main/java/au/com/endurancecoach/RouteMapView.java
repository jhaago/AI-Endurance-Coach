package au.com.endurancecoach;
import android.content.*;import android.graphics.*;import android.util.LruCache;import android.view.*;import org.json.*;import au.com.endurancecoach.core.*;import java.io.*;import java.util.*;import java.util.concurrent.*;

/** Interactive viewport-only OSM raster map. No location permission or activity tracking. */
final class RouteMapView extends View {
 interface Listener {void tap(double lat,double lon);void move(int index,double lat,double lon);}
 private final CoachTheme theme;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Listener listener;private final boolean online;
 private final LruCache<String,Bitmap> memory=new LruCache<>(48);private final Set<String> pending=ConcurrentHashMap.newKeySet();private final Map<String,Long> failed=new ConcurrentHashMap<>();
 private ExecutorService tiles=Executors.newFixedThreadPool(2);private final ScaleGestureDetector scale;
 private JSONArray route=new JSONArray(),waypoints=new JSONArray();private double latitude=-31.95,longitude=115.86;private int zoom=13;private float downX,downY,lastX,lastY;private boolean moved,editing=false,closed=false;private int dragging=-1;
 RouteMapView(Context context,CoachTheme theme,boolean online,Listener listener){super(context);this.theme=theme;this.online=online;this.listener=listener;setFocusable(true);setContentDescription("Route planning map. Drag to pan, pinch to zoom. Use coordinate controls for accessible point selection.");scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){if(d.getScaleFactor()>1.15){zoom(1);return true;}if(d.getScaleFactor()<.87){zoom(-1);return true;}return false;}});}
 void editing(boolean value){editing=value;}
 void points(JSONArray points){waypoints=points;invalidate();}
 void route(JSONArray points){route=points;invalidate();}
 void center(JSONArray point){latitude=point.optDouble(0,latitude);longitude=point.optDouble(1,longitude);invalidate();}
 void fit(JSONArray points){if(points.length()==0)return;double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;for(int i=0;i<points.length();i++){JSONArray p=points.optJSONArray(i);if(p==null)continue;double x=worldX(p.optDouble(1),18),y=worldY(p.optDouble(0),18);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}if(!Double.isFinite(minX))return;longitude=lon((minX+maxX)/2,18);latitude=lat((minY+maxY)/2,18);int width=Math.max(240,getWidth())-80,height=Math.max(220,getHeight())-80;zoom=18;while(zoom>3&&((maxX-minX)/Math.pow(2,18-zoom)>width||(maxY-minY)/Math.pow(2,18-zoom)>height))zoom--;invalidate();}
 void zoom(int delta){zoom=Math.max(3,Math.min(18,zoom+delta));invalidate();}
 private double worldX(double lon,int z){return (lon+180)/360*(256L<<z);}
 private double worldY(double lat,int z){double l=Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)));return (1-Math.log(Math.tan(l)+1/Math.cos(l))/Math.PI)/2*(256L<<z);}
 private double lon(double x,int z){return ((x/(256L<<z)*360+180)%360+360)%360-180;}
 private double lat(double y,int z){return Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*y/(256L<<z)))));}
 private double left(){return worldX(longitude,zoom)-getWidth()/2.0;}private double top(){return worldY(latitude,zoom)-getHeight()/2.0;}
 private float px(JSONArray p){double d=worldX(p.optDouble(1),zoom)-worldX(longitude,zoom),size=256L<<zoom;if(d>size/2)d-=size;if(d<-size/2)d+=size;return (float)(d+getWidth()/2.0);}
 private float py(JSONArray p){return (float)(worldY(p.optDouble(0),zoom)-top());}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(theme.raised);double left=left(),top=top();int size=1<<zoom;
  for(int x=(int)Math.floor(left/256);x<=(int)Math.floor((left+getWidth())/256);x++)for(int y=(int)Math.floor(top/256);y<=(int)Math.floor((top+getHeight())/256);y++){
   if(y<0||y>=size)continue;int wrapped=((x%size)+size)%size;String key=zoom+"_"+wrapped+"_"+y;Bitmap bitmap=memory.get(key);float sx=(float)(x*256-left),sy=(float)(y*256-top);
   if(bitmap!=null)c.drawBitmap(bitmap,sx,sy,null);else{paint.setColor(theme.outline);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);c.drawRect(sx,sy,sx+256,sy+256,paint);if(online)tile(key,zoom,wrapped,y);}
  }
  if(route.length()>1){Path p=new Path();for(int i=0;i<route.length();i++){JSONArray v=route.optJSONArray(i);if(v==null)continue;if(i==0)p.moveTo(px(v),py(v));else p.lineTo(px(v),py(v));}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(6);paint.setColor(0xff163d37);c.drawPath(p,paint);paint.setStrokeWidth(3);paint.setColor(0xff20aa8a);c.drawPath(p,paint);}
  for(int i=0;i<waypoints.length();i++){JSONArray p=waypoints.optJSONArray(i);if(p==null)continue;paint.setStyle(Paint.Style.FILL);paint.setColor(0xff116d5c);c.drawCircle(px(p),py(p),14,paint);paint.setColor(Color.WHITE);paint.setTextSize(15);paint.setTextAlign(Paint.Align.CENTER);c.drawText(String.valueOf(i+1),px(p),py(p)+5,paint);}
  paint.setStyle(Paint.Style.FILL);paint.setColor(0xeef3f6f4);c.drawRect(0,getHeight()-25,getWidth(),getHeight(),paint);paint.setColor(0xff162c2a);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.LEFT);c.drawText("© OpenStreetMap contributors",8,getHeight()-8,paint);
  if(!online){paint.setColor(theme.ink);paint.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);c.drawText("Map services off · saved route outline",12,24,paint);}
 }
 private void tile(String key,int z,int x,int y){if(closed||tiles.isShutdown()||pending.contains(key)||System.currentTimeMillis()-failed.getOrDefault(key,0L)<60000)return;pending.add(key);
  tiles.execute(()->{try{File dir=new File(getContext().getFilesDir(),"route-tiles");dir.mkdirs();File file=new File(dir,key+".png");byte[] bytes;
   if(file.exists()&&System.currentTimeMillis()-file.lastModified()<604800000L)bytes=java.nio.file.Files.readAllBytes(file.toPath());
   else{Transport.Response reply=new PublicMapTransport().request("GET","https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png",Collections.singletonMap("Accept","image/png"),null);reply.requireSuccess();bytes=reply.body;Bitmap check=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(check==null)throw new IOException();check.recycle();java.nio.file.Files.write(file.toPath(),bytes);}
   Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null&&!closed)memory.put(key,bitmap);
  }catch(Exception error){failed.put(key,System.currentTimeMillis());}finally{pending.remove(key);if(!closed)postInvalidate();}});
 }
 @Override public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);float x=e.getX(),y=e.getY();
  if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=lastX=x;downY=lastY=y;moved=false;dragging=-1;if(editing)for(int i=0;i<waypoints.length();i++){JSONArray p=waypoints.optJSONArray(i);if(p!=null&&Math.hypot(x-px(p),y-py(p))<24*getResources().getDisplayMetrics().density){dragging=i;break;}}getParent().requestDisallowInterceptTouchEvent(true);return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!scale.isInProgress()){if(Math.hypot(x-downX,y-downY)>8)moved=true;if(dragging>=0&&moved){try{waypoints.put(dragging,new JSONArray().put(lat(top()+y,zoom)).put(lon(left()+x,zoom)));}catch(JSONException ignored){}}else{longitude=lon(worldX(longitude,zoom)-(x-lastX),zoom);latitude=lat(worldY(latitude,zoom)-(y-lastY),zoom);}lastX=x;lastY=y;invalidate();return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){moved=true;dragging=-1;}
  if(e.getActionMasked()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);if(dragging>=0&&moved){JSONArray p=waypoints.optJSONArray(dragging);listener.move(dragging,p.optDouble(0),p.optDouble(1));}else if(!moved&&!scale.isInProgress()){performClick();listener.tap(lat(top()+y,zoom),lon(left()+x,zoom));}dragging=-1;return true;}
  if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=-1;getParent().requestDisallowInterceptTouchEvent(false);}return true;
 }
 @Override public boolean performClick(){super.performClick();return true;}
 void close(){closed=true;tiles.shutdownNow();memory.evictAll();}
}
