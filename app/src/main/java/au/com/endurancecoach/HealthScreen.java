package au.com.endurancecoach;

import android.app.*;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.widget.*;
import au.com.endurancecoach.core.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Manual actuals with read-only, Coach-approved nutrition targets. */
final class HealthScreen {
 private static final String[] TYPES={"breakfast","lunch","dinner","snacks","other"};
 private static final String[] GROUPS={"Breakfast","Lunch","Dinner","Snacks","Other"};
 private final Activity activity;private final CoachRepository repo;private final CoachTheme theme;
 private final LocalDate day;private final Consumer<LocalDate> selectDay;private final Runnable saved;
 private JSONArray records;private NutritionTargets.Target target;
 HealthScreen(Activity activity,CoachRepository repo,CoachTheme theme,LocalDate day,Consumer<LocalDate> selectDay,Runnable saved){this.activity=activity;this.repo=repo;this.theme=theme;this.day=day;this.selectDay=selectDay;this.saved=saved;}
 private SharedPreferences preferences(){return activity.getSharedPreferences("health-ui",0);}
 private boolean future(){return day.isAfter(LocalDate.now(PlanEngine.ZONE));}
 private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private TextView text(String value,int size,boolean bold){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(bold?theme.ink:theme.muted);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setPadding(0,dp(5),0,dp(5));return v;}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
 private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(theme.accent);b.setBackground(shape(theme.raised));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(4));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
 private void buttons(LinearLayout parent,Button... buttons){LinearLayout row=new LinearLayout(activity);for(int i=0;i<buttons.length;i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(i==0?0:dp(4),dp(5),i==buttons.length-1?0:dp(4),dp(4));row.addView(buttons[i],p);}parent.addView(row);}
 private Button actualButton(String label,Runnable action){Button b=button(label,action);b.setEnabled(!future());if(future())b.setAlpha(.45f);return b;}
 private Button choice(String label,boolean selected,Runnable action){Button b=button(label,action);b.setSelected(selected);b.setBackground(shape(selected?theme.accentSurface:theme.raised));return b;}
 private LinearLayout card(LinearLayout parent){LinearLayout box=column();box.setPadding(dp(16),dp(10),dp(16),dp(12));box.setBackground(shape(theme.surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(8));parent.addView(box,p);return box;}
 private static String amount(Double n,String unit){return n==null?"—":String.format(Locale.UK,unit.equals("kg")?"%.1f %s":"%.0f %s",n,unit);}
 void render(LinearLayout parent) throws Exception {
  records=HealthRecords.current(repo.healthRecords());JSONArray plan=repo.snapshot().optJSONArray("plan");if(plan==null)plan=new JSONArray();
  target=NutritionTargets.forDay(repo.nutritionTargets(),day,plan);HealthRecords.Summary summary=HealthRecords.summary(records,day);
  boolean weight="weight".equals(preferences().getString("section","diary"));
  buttons(parent,choice("Diary",!weight,()->section("diary")),choice("Weight",weight,()->section("weight")));
  parent.addView(button(day.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy",Locale.UK))+" · Change day",this::pickDay));
  String error=repo.nutritionError();if(!error.isEmpty())parent.addView(text(target==null?"Nutrition targets unavailable. Sync again when connected.":"Nutrition targets may be out of date. Sync again when connected.",14,false));
  if(future())parent.addView(text("Future day · targets only. Log food and weight on today or an earlier date.",14,false));
  if(weight)weight(parent,summary);else diary(parent,summary,plan);
 }
 private void section(String section){preferences().edit().putString("section",section).apply();selectDay.accept(day);}
 private void diary(LinearLayout parent,HealthRecords.Summary summary,JSONArray plan){
  buttons(parent,actualButton("Add meal",()->mealForm(null,null,"other")),actualButton("Quick add",this::quickAdd));
  LinearLayout totals=card(parent);totals.addView(text("Logged calories",14,false));totals.addView(text(amount(summary.calories,"kcal"),30,true));
  totals.addView(text(summary.mealCount+" meal"+(summary.mealCount==1?"":"s")+" logged",13,false));
  if(target==null)totals.addView(text("No approved nutrition target",18,true));
  else {
   totals.addView(text("Target · "+amount(target.calories,"kcal"),18,true));totals.addView(text("Remaining · "+amount(summary.calories==null?null:target.calories-summary.calories,"kcal"),16,false));
   if(summary.calories!=null){ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.min(1000,Math.max(0,summary.calories/target.calories*1000)));progress.setProgressTintList(ColorStateList.valueOf(theme.accent));progress.setProgressBackgroundTintList(ColorStateList.valueOf(theme.outline));progress.setContentDescription("Logged food: "+amount(summary.calories,"kcal")+" of "+amount(target.calories,"kcal"));totals.addView(progress,new LinearLayout.LayoutParams(-1,dp(14)));}
   List<String> macros=new ArrayList<>();if(target.protein!=null)macros.add("P "+amount(target.protein,"g"));if(target.carbohydrate!=null)macros.add("C "+amount(target.carbohydrate,"g"));if(target.fat!=null)macros.add("F "+amount(target.fat,"g"));
   if(!macros.isEmpty()){TextView line=text("Macro targets · "+String.join(" · ",macros),14,false);List<String> spoken=new ArrayList<>();if(target.protein!=null)spoken.add("Protein "+amount(target.protein,"g"));if(target.carbohydrate!=null)spoken.add("Carbohydrate "+amount(target.carbohydrate,"g"));if(target.fat!=null)spoken.add("Fat "+amount(target.fat,"g"));line.setContentDescription("Macro targets: "+String.join(", ",spoken));totals.addView(line);}
   if(target.trainingChanged)totals.addView(text("Coach review needed · training changed",14,true));
  }
  totals.addView(text("Training · "+(target==null?NutritionTargets.trainingType(plan,day):target.trainingType),14,false));
  totals.addView(button("Target details",this::targetDetails));
  List<JSONObject> meals=kind("meal");meals.removeIf(r->!day.toString().equals(r.optString("date")));meals.sort(Comparator.comparing(r->r.optString("updated_at")));
  for(int i=0;i<TYPES.length;i++){final String type=TYPES[i];List<JSONObject> group=new ArrayList<>();double total=0;for(JSONObject meal:meals)if(type.equals(meal.optString("meal_type","other"))){group.add(meal);total+=meal.optDouble("calories_kcal")*meal.optDouble("portions");}if(i==4&&group.isEmpty())continue;
   LinearLayout box=card(parent);box.addView(text(GROUPS[i]+" · "+amount(group.isEmpty()?null:total,"kcal"),18,true));
   for(JSONObject meal:group){Button item=actualButton(meal.optString("name")+" · "+amount(meal.optDouble("calories_kcal")*meal.optDouble("portions"),"kcal"),()->mealOptions(meal));item.setContentDescription(item.getText()+", "+meal.optDouble("portions")+" portions at "+amount(meal.optDouble("calories_kcal"),"kcal")+" each. Edit or remove");box.addView(item);}
   box.addView(actualButton("+ "+GROUPS[i],()->mealForm(null,null,type)));
  }
 }
 private void targetDetails(){
  LinearLayout box=form();
  if(target==null){box.addView(text("No approved nutrition target",18,true));box.addView(text("Ask Coach to set or change your targets in Sheets.",14,false));}
  else {
   box.addView(text(target.scope.equals("daily")?"Approved daily target · "+day:"Approved baseline · effective "+target.date,16,true));
   box.addView(text("Source · "+target.source,14,false));if(!target.reason.isEmpty())box.addView(text(target.reason,14,false));
   if(target.trainingChanged)box.addView(text("Training changed after this target was approved. Ask Coach to review it.",14,false));
   box.addView(text(target.exerciseIncluded?"Exercise is included in this target.":"Exercise is not marked as included in this target.",14,false));
   box.addView(text("Ask Coach to change your targets in Sheets.",14,false));
  }
  box.addView(text("Exercise calories are not added.",14,false));box.addView(text("Logged food may be incomplete. Missing days stay blank.",14,false));
  ScrollView scroll=new ScrollView(activity);scroll.addView(box);new AlertDialog.Builder(activity).setTitle("Target details").setView(scroll).setPositiveButton("Close",null).show();
 }
 private void weight(LinearLayout parent,HealthRecords.Summary summary){
  LinearLayout box=card(parent);box.addView(text("Latest weight",14,false));box.addView(text(amount(summary.latestWeight,"kg"),30,true));if(summary.latestWeight!=null)box.addView(text(summary.latestWeightDate,13,false));
  box.addView(actualButton(summary.weight==null?"Log weight":"Edit weight",()->weightForm(day)));
  box.addView(text("7-day measured average · "+amount(summary.averageWeight,"kg"),16,true));box.addView(text(summary.weighIns+" weigh-in"+(summary.weighIns==1?"":"s")+" in 7 days",13,false));
  Double goal=target!=null&&target.weightGoalKg!=null?target.weightGoalKg:summary.goal;
  box.addView(text(goal==null?"No weight goal":"Weight goal · "+amount(goal,"kg"),16,true));
  if(goal!=null){box.addView(text(target!=null&&target.weightGoalKg!=null?"Coach-managed weight goal":"Previously saved weight goal",13,false));if(target!=null&&target.weightGoalKg!=null&&!target.weightGoalDate.isEmpty())box.addView(text("Goal date · "+target.weightGoalDate,13,false));}
  if(target!=null&&target.weightChangeKgPerWeek!=null)box.addView(text(String.format(Locale.UK,"Coach target change · %+.2f kg/week",target.weightChangeKgPerWeek),14,false));
  LinearLayout graph=card(parent);int stored=preferences().getInt("weight-range",30);int range=stored==90?90:30;
  buttons(graph,choice("30 days",range==30,()->range(30)),choice("90 days",range==90,()->range(90)));chart(graph,range);
  LinearLayout history=card(parent);history.addView(text("Weight history",20,true));List<JSONObject> weights=kind("weight");weights.removeIf(r->LocalDate.parse(r.optString("date")).isAfter(day));weights.sort((a,b)->b.optString("date").compareTo(a.optString("date")));
  if(weights.isEmpty())history.addView(text("No weight records yet.",14,false));for(JSONObject row:weights){LocalDate date=LocalDate.parse(row.optString("date"));history.addView(button(date+" · "+amount(row.optDouble("weight_kg"),"kg"),()->weightForm(date)));}
 }
 private void range(int days){preferences().edit().putInt("weight-range",days).apply();selectDay.accept(day);}
 private void chart(LinearLayout box,int count){String title="Weight · past "+count+" days (kg)";box.addView(text(title,14,false));Map<String,Double> weights=new HashMap<>();for(JSONObject r:kind("weight"))weights.put(r.optString("date"),r.optDouble("weight_kg"));List<Double> points=new ArrayList<>();for(int i=count-1;i>=0;i--)points.add(weights.get(day.minusDays(i).toString()));TrendView graph=new TrendView(activity,points,theme.accent,theme.muted,theme.outline);graph.setContentDescription(title+". "+graph.getContentDescription());box.addView(graph,new LinearLayout.LayoutParams(-1,dp(140)));box.addView(text(day.minusDays(count-1).format(DateTimeFormatter.ofPattern("d MMM"))+" – "+day.format(DateTimeFormatter.ofPattern("d MMM")),12,false));}
 private List<JSONObject> kind(String kind){List<JSONObject> list=new ArrayList<>();for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(!r.optBoolean("deleted")&&kind.equals(r.optString("kind")))list.add(r);}return list;}
 private JSONObject latest(String id) throws Exception {JSONArray current=repo.healthRecords();for(int i=0;i<current.length();i++){JSONObject r=current.getJSONObject(i);if(id.equals(r.optString("record_id")))return r;}return null;}
 private JSONObject latest(JSONObject row) throws Exception {return row==null?null:latest(row.optString("record_id"));}
 private void pickDay(){new DatePickerDialog(activity,(v,y,m,d)->selectDay.accept(LocalDate.of(y,m+1,d)),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show();}
 private interface Changes {JSONArray make() throws Exception;}
 private void save(Changes action){save(action,()->{});}
 private void save(Changes action,Runnable beforeRedraw){try{repo.saveHealth(action.make());beforeRedraw.run();saved.run();}catch(Exception e){new AlertDialog.Builder(activity).setTitle("Could not save").setMessage(e.getMessage()).setPositiveButton("Close",null).show();}}
 private void remove(JSONObject row){remove(row,()->{});}
 private void remove(JSONObject row,Runnable removed){new AlertDialog.Builder(activity).setTitle("Remove entry?").setMessage("Remove "+(row.optString("name").isEmpty()?"this weight entry":row.optString("name"))+"?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->save(()->new JSONArray().put(HealthRecords.deleted(latest(row),System.currentTimeMillis())),removed)).show();}
 private JSONObject copyGrams(JSONObject result,JSONObject source) throws JSONException {if(source!=null)for(String key:new String[]{"protein_g","carbohydrate_g","fat_g"})result.put(key,source.isNull(key)?JSONObject.NULL:source.get(key));return result;}
 private void mealOptions(JSONObject row){new AlertDialog.Builder(activity).setTitle(row.optString("name")).setItems(new String[]{"Edit meal","Save as favourite","Remove meal"},(d,w)->{if(w==0)mealForm(row,null,row.optString("meal_type","other"));else if(w==1)save(()->new JSONArray().put(copyGrams(HealthRecords.favourite(row.optString("name"),row.optDouble("calories_kcal"),row.optString("note"),null,System.currentTimeMillis()),row)));else remove(row);}).show();}
 private void favouriteOptions(JSONObject row){new AlertDialog.Builder(activity).setTitle(row.optString("name")).setItems(new String[]{"Add with portions","Edit favourite","Remove favourite"},(d,w)->{if(w==0)mealForm(null,row,"other");else if(w==1)favouriteForm(row);else remove(row);}).show();}
 private Spinner mealGroup(LinearLayout box,String type){box.addView(text("Meal group",14,false));Spinner spinner=new Spinner(activity);spinner.setContentDescription("Meal group");ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,GROUPS);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);int index=Arrays.asList(TYPES).indexOf(type);spinner.setSelection(index<0?4:index);box.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));return spinner;}
 private void quickAdd(){
  LinearLayout box=form();Spinner group=mealGroup(box,"other");ScrollView scroll=new ScrollView(activity);scroll.addView(box);AlertDialog picker=new AlertDialog.Builder(activity).setTitle("Quick add").setView(scroll).setNegativeButton("Close",null).create();
  box.addView(text("Favourites",20,true));List<JSONObject> favourites=kind("favourite");favourites.sort(Comparator.comparing(r->r.optString("name").toLowerCase(Locale.UK)));
  if(favourites.isEmpty())box.addView(text("Save meals you eat often for a quick add.",14,false));
  for(JSONObject favourite:favourites){box.addView(button(favourite.optString("name")+" · "+amount(favourite.optDouble("calories_kcal"),"kcal"),()->{picker.dismiss();mealForm(null,favourite,TYPES[group.getSelectedItemPosition()]);}));box.addView(button("Add 1 portion · "+favourite.optString("name"),()->save(()->new JSONArray().put(HealthRecords.fromFavourite(favourite,day,1,System.currentTimeMillis(),TYPES[group.getSelectedItemPosition()])),picker::dismiss)));box.addView(button("Options · "+favourite.optString("name"),()->{picker.dismiss();favouriteOptions(favourite);}));}
  box.addView(button("New favourite",()->{picker.dismiss();favouriteForm(null);}));box.addView(text("Recent",20,true));List<JSONObject> recent=kind("meal");recent.sort((a,b)->b.optString("updated_at").compareTo(a.optString("updated_at")));Set<String> seen=new HashSet<>();int count=0;
  for(JSONObject meal:recent){String key=meal.optString("name")+"\n"+meal.optDouble("calories_kcal")+"\n"+meal.optString("note");if(!seen.add(key))continue;box.addView(button(meal.optString("name")+" · "+amount(meal.optDouble("calories_kcal"),"kcal"),()->{picker.dismiss();mealForm(null,meal,TYPES[group.getSelectedItemPosition()]);}));if(++count==8)break;}
  if(count==0)box.addView(text("Your logged meals will appear here.",14,false));picker.show();
 }
 private EditText field(LinearLayout box,String label,String value,boolean numeric){box.addView(text(label,14,false));EditText input=new EditText(activity);input.setContentDescription(label);input.setText(value);input.setTextColor(theme.ink);input.setTextSize(16);input.setInputType(numeric?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setSingleLine(true);box.addView(input,new LinearLayout.LayoutParams(-1,-2));return input;}
 private LinearLayout form(){LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(16));return box;}
 private AlertDialog dialog(String title,LinearLayout box,Changes action){TextView error=text("",14,false);error.setTextColor(theme.accent);box.addView(error);ScrollView scroll=new ScrollView(activity);scroll.addView(box);AlertDialog d=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{repo.saveHealth(action.make());d.dismiss();saved.run();}catch(Exception e){error.setText(e instanceof NumberFormatException?"Enter a valid number":e.getMessage());}}));d.show();return d;}
 private void mealForm(JSONObject previous,JSONObject source,String type){
  JSONObject seed=previous==null?source:previous;LinearLayout box=form();box.addView(text(day.format(DateTimeFormatter.ofPattern("EEE d MMM")),14,false));Spinner group=mealGroup(box,type);
  EditText name=field(box,"Meal name",seed==null?"":seed.optString("name"),false),kcal=field(box,"Calories per portion (kcal)",seed==null?"":String.valueOf(seed.optDouble("calories_kcal")),true),portions=field(box,"Portions",previous==null?"1":String.valueOf(previous.optDouble("portions")),true),note=field(box,"Note (optional)",seed==null?"":seed.optString("note"),false);
  CheckBox fav=new CheckBox(activity);fav.setText("Save as favourite");fav.setTextColor(theme.ink);box.addView(fav);
  dialog(previous==null?"Add meal":"Edit meal",box,()->{long now=System.currentTimeMillis();JSONObject old=latest(previous),origin=previous==null?source:old;JSONObject meal=copyGrams(HealthRecords.meal(day,name.getText().toString(),Double.parseDouble(kcal.getText().toString()),Double.parseDouble(portions.getText().toString()),note.getText().toString(),old,now,TYPES[group.getSelectedItemPosition()]),origin);JSONArray changes=new JSONArray().put(meal);if(fav.isChecked())changes.put(copyGrams(HealthRecords.favourite(name.getText().toString(),Double.parseDouble(kcal.getText().toString()),note.getText().toString(),null,now),origin));return changes;});
 }
 private void favouriteForm(JSONObject previous){LinearLayout box=form();EditText name=field(box,"Meal name",previous==null?"":previous.optString("name"),false),kcal=field(box,"Calories per portion (kcal)",previous==null?"":String.valueOf(previous.optDouble("calories_kcal")),true),note=field(box,"Note (optional)",previous==null?"":previous.optString("note"),false);dialog(previous==null?"New favourite":"Edit favourite",box,()->new JSONArray().put(HealthRecords.favourite(name.getText().toString(),Double.parseDouble(kcal.getText().toString()),note.getText().toString(),latest(previous),System.currentTimeMillis())));}
 private void weightForm(LocalDate date){try{JSONObject previous=latest("weight:"+date);if(previous!=null&&previous.optBoolean("deleted"))previous=null;final JSONObject initial=previous;LinearLayout box=form();box.addView(text(date.toString(),14,false));EditText kg=field(box,"Weight (kg)",initial==null?"":String.valueOf(initial.optDouble("weight_kg")),true);AlertDialog[] edit=new AlertDialog[1];if(initial!=null)box.addView(button("Remove weight",()->remove(initial,()->edit[0].dismiss())));edit[0]=dialog(initial==null?"Log weight":"Edit weight",box,()->new JSONArray().put(HealthRecords.weight(date,Double.parseDouble(kg.getText().toString()),latest("weight:"+date),System.currentTimeMillis())));}catch(Exception e){new AlertDialog.Builder(activity).setMessage("Could not load weight").setPositiveButton("Close",null).show();}}
}
