package au.com.endurancecoach;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import au.com.endurancecoach.core.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private static final int INK=Color.rgb(18,47,53),MUTED=Color.rgb(89,107,107),GREEN=Color.rgb(20,125,114),BG=Color.rgb(244,246,243);
 private CoachRepository repo;private LinearLayout root,content;private TextView status;private int tab;private LocalDate week;
 private final ExecutorService network=Executors.newSingleThreadExecutor();private final Handler handler=new Handler(Looper.getMainLooper());private boolean active,busy;
 private final Runnable refresh=new Runnable(){public void run(){if(active){sync(false);handler.postDelayed(this,60000);}}};
 @Override public void onCreate(Bundle state){super.onCreate(state);repo=new CoachRepository(this);week=today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));if(state!=null){tab=state.getInt("tab",0);week=LocalDate.parse(state.getString("week",week.toString()));}SyncJob.schedule(this);render();}
 @Override protected void onSaveInstanceState(Bundle state){state.putInt("tab",tab);state.putString("week",week.toString());super.onSaveInstanceState(state);}
 @Override protected void onResume(){super.onResume();active=true;sync(false);handler.postDelayed(refresh,60000);}
 @Override protected void onPause(){active=false;handler.removeCallbacks(refresh);super.onPause();}
 @Override protected void onDestroy(){network.shutdownNow();if(!busy)repo.close();super.onDestroy();}
 private LocalDate today(){return LocalDate.now(PlanEngine.ZONE);}
 private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(4),0,dp(4));return v;}
 private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(GREEN);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;}
 private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(18),dp(14),dp(18),dp(14));GradientDrawable shape=new GradientDrawable();shape.setColor(Color.WHITE);shape.setCornerRadius(dp(18));v.setBackground(shape);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));v.setLayoutParams(p);content.addView(v);return v;}
 private void notice(String value){LinearLayout v=card();v.addView(text(value,15,MUTED,false));}
 private void render(){
  try{
   JSONObject s=repo.snapshot();root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),dp(12),dp(20),dp(8));
   root.setOnApplyWindowInsetsListener((view,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());top=i.top;bottom=i.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}view.setPadding(dp(20),top+dp(12),dp(20),bottom+dp(8));return insets;});
   LinearLayout head=row();TextView brand=text("ENDURANCE",16,INK,true);brand.setLetterSpacing(.13f);head.addView(brand,new LinearLayout.LayoutParams(0,-2,1));head.addView(button("Settings",this::settings));root.addView(head);
   String[] labels={"Today","Plan","Progress","Insights"};root.addView(text(labels[tab],34,INK,true));
   status=text(busy?"Syncing…":repo.store.get("status").optString("message","Connect Garmin and your training database in Settings"),12,MUTED,false);root.addView(status);
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   if(tab==0)todayScreen(s);else if(tab==1)planScreen(s);else if(tab==2)progressScreen(s);else insightsScreen(s);
   LinearLayout nav=row();for(int i=0;i<labels.length;i++){final int selected=i;Button b=button(labels[i],()->{tab=selected;render();});if(tab==i){b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(INK);}nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(nav);setContentView(root);
  }catch(Exception e){LinearLayout error=column();error.setPadding(dp(24),dp(48),dp(24),dp(24));error.addView(text("Training data needs attention",24,INK,true));error.addView(text("The latest plan could not be read. Check revision numbers and dates in the training database, then retry.",16,MUTED,false));error.addView(button("Settings",this::settings));error.addView(button("Retry sync",()->sync(false)));setContentView(error);}
 }
 private JSONArray plan(JSONObject s) throws Exception {return PlanEngine.forEvent(CoachRepository.array(s,"plan"),event(s).optString("event_id"));}
 private JSONObject matches(JSONObject s) throws Exception {return PlanEngine.matches(CoachRepository.array(s,"plan"),CoachRepository.array(s,"activities"),repo.overrides(s));}
 private JSONObject event(JSONObject s) throws Exception {JSONArray events=CoachRepository.array(s,"events");for(int i=0;i<events.length();i++)if(events.getJSONObject(i).optBoolean("active"))return events.getJSONObject(i);return new JSONObject();}
 private void todayScreen(JSONObject s) throws Exception {
  JSONObject event=event(s);LinearLayout target=card();target.addView(text(event.optString("name","Choose your next event"),17,INK,true));
  if(event.has("date"))target.addView(text(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.UK).format(LocalDate.parse(event.getString("date")))+" · "+java.time.temporal.ChronoUnit.DAYS.between(today(),LocalDate.parse(event.getString("date")))+" days",14,MUTED,false));
  target.addView(text(event.optInt("primary_yards",10)+" yards · "+event.optDouble("primary_km",67.06)+" km",24,GREEN,true));target.addView(text("Stretch "+event.optInt("stretch_yards",12)+" yards · "+event.optDouble("stretch_km",80.47)+" km",14,MUTED,false));
  JSONArray plan=plan(s);JSONObject match=matches(s);Set<String> completed=new HashSet<>();for(Iterator<String> keys=match.keys();keys.hasNext();)completed.add(match.getString(keys.next()));
  JSONObject next=null;for(int i=0;i<plan.length();i++){JSONObject w=plan.getJSONObject(i);if(!LocalDate.parse(w.getString("date")).isBefore(today())&&!completed.contains(w.getString("workout_id"))){next=w;break;}}
  if(next==null)notice("Your next workout will appear here after the plan is reviewed in ChatGPT.");else workout(next,false);
  LinearLayout actions=card();actions.addView(button("Sync Garmin + plan",()->sync(true)));actions.addView(button("Daily recovery check-in",()->feedback("","checkin-"+today())));
  actions.addView(text("3 runs a week, with an optional fourth. Strength and recovery belong in the same plan.",14,MUTED,false));
  JSONObject st=repo.store.get("status");actions.addView(text("Garmin: "+stamp(st.optString("garmin_sync"))+"\nPlan: "+stamp(st.optString("cloud_sync")),12,MUTED,false));
 }
 private void workout(JSONObject w,boolean completed) throws Exception {
  LinearLayout c=card();c.addView(text(w.getString("date")+" · "+w.getString("type")+(w.optBoolean("optional")?" · optional":"")+(completed?" · completed":""),12,GREEN,true));c.addView(text(w.optString("title","Workout"),22,INK,true));
  if(w.optInt("duration_min")>0)c.addView(text(w.optInt("duration_min")+" min",24,INK,true));c.addView(text(w.optString("intensity",""),15,MUTED,false));c.addView(text("Revision "+w.getInt("revision")+" · "+w.optString("reason",""),12,MUTED,false));
  if(!w.getString("type").equals("run"))c.addView(button("Add feedback",()->feedback("",w.optString("workout_id"))));
 }
 private void planScreen(JSONObject s) throws Exception {
  LinearLayout controls=row();controls.addView(button("‹",()->{week=week.minusWeeks(1);render();}));TextView range=text(week.format(DateTimeFormatter.ofPattern("d MMM"))+" – "+week.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM")),15,INK,true);range.setGravity(Gravity.CENTER);controls.addView(range,new LinearLayout.LayoutParams(0,-2,1));controls.addView(button("›",()->{week=week.plusWeeks(1);render();}));content.addView(controls);
  JSONArray p=plan(s);JSONObject m=matches(s);Set<String> done=new HashSet<>();for(Iterator<String> keys=m.keys();keys.hasNext();)done.add(m.getString(keys.next()));
  for(int d=0;d<7;d++){LocalDate date=week.plusDays(d);content.addView(text(date.format(DateTimeFormatter.ofPattern("EEEE d MMM",Locale.UK)),14,INK,true));boolean any=false;for(int i=0;i<p.length();i++){JSONObject w=p.getJSONObject(i);if(w.getString("date").equals(date.toString())){workout(w,done.contains(w.getString("workout_id")));any=true;}}if(!any)content.addView(text("No workout scheduled",14,MUTED,false));}
  notice("ChatGPT appends a new revision when a workout changes. Refresh to load it; the app also checks the plan each minute while open.");content.addView(button("Refresh plan",()->sync(false)));
 }
 private List<JSONObject> activities(JSONObject s) throws Exception {JSONArray a=CoachRepository.array(s,"activities");List<JSONObject> list=new ArrayList<>();for(int i=0;i<a.length();i++)list.add(a.getJSONObject(i));list.sort(Comparator.comparing((JSONObject o)->o.optString("start_utc")).reversed());return list;}
 private void progressScreen(JSONObject s) throws Exception {
  List<JSONObject> rows=activities(s);JSONObject matched=matches(s);double km=0,minutes=0;int runs=0;LocalDate from=today().minusDays(6);
  for(JSONObject a:rows)if(!LocalDate.parse(PlanEngine.day(a)).isBefore(from)&&a.optString("type").contains("running")){km+=a.optDouble("distance_m",0)/1000;minutes+=a.optDouble("duration_s",0)/60;runs++;}
  LinearLayout totals=card();totals.addView(text("LAST 7 DAYS",12,GREEN,true));totals.addView(text(String.format(Locale.UK,"%.1f km",km),32,INK,true));totals.addView(text(runs+" runs · "+Math.round(minutes)+" minutes",15,MUTED,false));
  if(rows.isEmpty())notice("Completed Garmin activities will appear here after your first sync.");
  for(int i=0;i<Math.min(100,rows.size());i++){JSONObject a=rows.get(i);LinearLayout c=card();c.addView(text(PlanEngine.day(a)+" · "+a.optString("type"),12,GREEN,true));c.addView(text(a.optString("name","Activity"),19,INK,true));c.addView(text(value(a,"distance_m",.001," km")+" · "+value(a,"duration_s",1d/60," min"),16,INK,false));c.addView(text(matched.has(a.getString("activity_id"))?"Matched to "+matched.getString(a.getString("activity_id")):"Unmatched · available for review",12,MUTED,false));c.addView(button("Details + feedback",()->activityDetail(a)));}
  int pending=repo.store.pending("feedback").length();if(pending>0)notice(pending+" feedback record(s) saved on this phone, awaiting database sync.");
 }
 private void insightsScreen(JSONObject s) throws Exception {
  JSONArray array=CoachRepository.array(s,"wellness");List<JSONObject> rows=new ArrayList<>();for(int i=0;i<array.length();i++)rows.add(array.getJSONObject(i));rows.sort(Comparator.comparing(o->o.optString("date")));
  if(rows.isEmpty())notice("Sync Garmin to see sleep, overnight HRV and resting heart rate. Missing measurements remain blank.");
  else{JSONObject latest=rows.get(rows.size()-1);LinearLayout c=card();c.addView(text(latest.optString("date")+" · LATEST MEASUREMENTS",12,GREEN,true));c.addView(text("Sleep "+value(latest,"sleep_hours",1," h"),24,INK,true));c.addView(text("Sleep score "+value(latest,"sleep_score",1," / 100"),16,MUTED,false));c.addView(text("Overnight HRV "+value(latest,"hrv_ms",1," ms")+"\nResting HR "+value(latest,"resting_hr",1," bpm"),19,INK,false));if(!latest.optString("date").equals(today().toString()))c.addView(text("These measurements are from an earlier day.",14,MUTED,true));
   for(String metric:new String[]{"sleep_hours","hrv_ms","resting_hr"}){List<Double> points=new ArrayList<>();LocalDate end=today(),start=end.minusDays(13);Map<String,JSONObject> days=new HashMap<>();for(JSONObject w:rows)days.put(w.optString("date"),w);for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1)){JSONObject w=days.get(d.toString());points.add(w==null||w.isNull(metric)?null:w.optDouble(metric));}LinearLayout chart=card();chart.addView(text(metric.equals("sleep_hours")?"Sleep duration · hours":metric.equals("hrv_ms")?"Overnight HRV · ms":"Resting heart rate · bpm",15,INK,true));chart.addView(new TrendView(this,points),new LinearLayout.LayoutParams(-1,dp(100)));chart.addView(text(start+" → "+end,12,MUTED,false));}
  }
  notice("Your watch is off during work. Daytime data may be incomplete. Missing data is not interpreted as recovery or inactivity.");
  notice("Fatigue monitoring combines your check-ins with sleep and HRV trends. Training readiness and recovery-time scores are not assumed available through Garmin's Personal API. Plan changes are reviewed in ChatGPT.");
  JSONArray f=CoachRepository.merge(CoachRepository.array(s,"feedback"),repo.store.pending("feedback"),"feedback_id");if(f.length()>0){LinearLayout c=card();c.addView(text("RECENT CHECK-INS",12,GREEN,true));for(int i=Math.max(0,f.length()-7);i<f.length();i++){JSONObject v=f.getJSONObject(i);c.addView(text(stamp(v.optString("created_at"))+"\nEffort "+v.optInt("rpe")+" · soreness "+v.optInt("soreness")+" · fatigue "+v.optInt("fatigue")+"\n"+v.optString("note",""),14,MUTED,false));}}
 }
 private void activityDetail(JSONObject a){try{String id=a.getString("activity_id");LinearLayout box=column();box.setPadding(dp(18),dp(8),dp(18),dp(8));box.addView(text("Pace "+pace(a.optDouble("pace_s_km",Double.NaN))+"\nHeart rate "+value(a,"average_hr",1," bpm")+"\nElevation gained "+value(a,"elevation_m",1," m")+"\nCadence "+value(a,"cadence_spm",1," steps/min"),17,INK,false));
   JSONArray samples=CoachRepository.array(repo.store.get("samples:"+id),"records");box.addView(text(samples.length()+" original FIT samples retained on this phone",13,MUTED,false));
   if(samples.length()>0)for(String key:new String[]{"hr","pace_s_km","elevation_m","cadence_spm"}){List<Double> points=new ArrayList<>();int stride=Math.max(1,samples.length()/250);for(int i=0;i<samples.length();i+=stride){JSONObject r=samples.getJSONObject(i);points.add(r.has(key)?r.optDouble(key):null);}box.addView(text(key.replace('_',' '),13,GREEN,true));box.addView(new TrendView(this,points),new LinearLayout.LayoutParams(-1,dp(80)));}
   box.addView(button("Add session feedback",()->feedback(id,"")));box.addView(button("Change workout match",()->chooseMatch(a)));ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle(a.optString("name","Activity")).setView(scroll).setPositiveButton("Close",null).show();
  }catch(Exception e){toast("Activity details unavailable");}}
 private void chooseMatch(JSONObject a){try{JSONArray p=plan(repo.snapshot());List<String> labels=new ArrayList<>(),ids=new ArrayList<>();labels.add("Leave unmatched");ids.add("");for(int i=0;i<p.length();i++){JSONObject w=p.getJSONObject(i);if(w.getString("date").equals(PlanEngine.day(a))){labels.add(w.optString("title")+" · "+w.optString("type"));ids.add(w.getString("workout_id"));}}new AlertDialog.Builder(this).setTitle("Match this activity").setItems(labels.toArray(new String[0]),(d,index)->{try{repo.match(a.getString("activity_id"),ids.get(index));render();sync(false);}catch(Exception e){toast("Could not save match");}}).show();}catch(Exception e){toast("Could not read workouts");}}
 private void feedback(String activity,String workout){LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(8));String[] names={"Effort (0 rest, 10 maximum)","Soreness (0 none, 10 extreme)","Fatigue (0 fresh, 10 exhausted)"};SeekBar[] sliders=new SeekBar[3];for(int i=0;i<3;i++){TextView label=text(names[i]+" · 0",14,INK,false);box.addView(label);SeekBar bar=new SeekBar(this);bar.setMax(10);final String name=names[i];bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(name+" · "+value);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});sliders[i]=bar;box.addView(bar);}EditText note=new EditText(this);note.setHint("Any pain, energy or fuelling notes?");note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});box.addView(note);
  new AlertDialog.Builder(this).setTitle("Quick check-in").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{try{repo.feedback(activity,workout,sliders[0].getProgress(),sliders[1].getProgress(),sliders[2].getProgress(),note.getText().toString());toast("Feedback saved");render();sync(false);}catch(Exception e){toast("Could not save feedback. Please retry.");}}).show();
 }
 private EditText field(LinearLayout box,String hint,boolean secret){EditText v=new EditText(this);v.setHint(hint);v.setTextSize(14);v.setSingleLine(true);v.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);box.addView(v);return v;}
 private void settings(){try{LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(8));box.addView(text("Garmin Personal API key stays encrypted on your phone. The database uses its own separate secret.",14,MUTED,false));EditText garmin=field(box,repo.vault.get("garmin").isEmpty()?"Garmin Personal API key":"Garmin key stored · blank keeps it",true);EditText bridge=field(box,"Apps Script web app /exec URL",false);bridge.setText(repo.vault.get("bridge"));EditText secret=field(box,repo.vault.get("secret").isEmpty()?"Database bridge secret":"Bridge secret stored · blank keeps it",true);
   box.addView(text("Create your key in Garmin Connect web → Profile & Account → Account Settings → Personal API Key.\n\nSync runs manually, at app launch for the plan, and about every 6 hours in the background. Android may delay background work.",13,MUTED,false));box.addView(button("Third-party licences",()->{try(java.io.InputStream input=getAssets().open("third_party/FIT-LICENSE.txt")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);TextView licence=text(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),13,INK,false);ScrollView scroll=new ScrollView(this);scroll.addView(licence);new AlertDialog.Builder(this).setTitle("Garmin FIT SDK licence").setView(scroll).setPositiveButton("Close",null).show();}catch(Exception error){toast("See project third-party notices");}}));box.addView(button("Forget saved connections",()->new AlertDialog.Builder(this).setMessage("Remove the Garmin key and database connection from this phone?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{repo.vault.clear();toast("Connections removed");render();}).show()));
   AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connections").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String url=bridge.getText().toString().trim(),key=garmin.getText().toString().trim(),token=secret.getText().toString().trim();if(!url.isEmpty())HttpsTransport.validateBridge(url);if(!url.isEmpty()&&(token.isEmpty()?repo.vault.get("secret"):token).length()<32)throw new IllegalArgumentException("Enter a bridge secret of at least 32 characters.");if(!key.isEmpty())repo.vault.set("garmin",key);repo.vault.set("bridge",url);if(!token.isEmpty())repo.vault.set("secret",token);dialog.dismiss();render();sync(true);}catch(Exception e){toast(e instanceof IllegalArgumentException?e.getMessage():"Could not encrypt settings. Please retry.");}}));dialog.show();
  }catch(Exception e){toast("Encrypted settings unavailable. Reconfigure connections.");}}
 private void sync(boolean garmin){if(busy)return;busy=true;if(status!=null)status.setText("Syncing…");network.submit(()->{String message;try{message=repo.sync(garmin);}catch(Exception e){message=e instanceof java.io.IOException?e.getMessage():"Sync incomplete. Your local records are retained; retry when connected.";}final String result=message;handler.post(()->{busy=false;if(!isDestroyed()){render();if(status!=null)status.setText(result);}else repo.close();});});}
 private String value(JSONObject row,String key,double scale,String unit){return row.isNull(key)||!row.has(key)?"—":String.format(Locale.UK,"%.1f",row.optDouble(key)*scale)+unit;}
 private String pace(double value){if(!Double.isFinite(value)||value<=0)return "—";long secs=Math.round(value);return secs/60+":"+String.format(Locale.UK,"%02d",secs%60)+" /km";}
 private String stamp(String utc){try{return Instant.parse(utc).atZone(PlanEngine.ZONE).format(DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.UK));}catch(Exception e){return "not yet synced";}}
 private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
