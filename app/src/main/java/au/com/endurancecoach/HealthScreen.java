package au.com.endurancecoach;

import android.app.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import au.com.endurancecoach.core.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Small manual food and weight diary, backed by the same offline outbox as training. */
final class HealthScreen {
 private final Activity activity;private final CoachRepository repo;private final CoachTheme theme;
 private final LocalDate day;private final Consumer<LocalDate> selectDay;private final Runnable saved;
 private JSONArray records;
 HealthScreen(Activity activity,CoachRepository repo,CoachTheme theme,LocalDate day,Consumer<LocalDate> selectDay,Runnable saved){this.activity=activity;this.repo=repo;this.theme=theme;this.day=day;this.selectDay=selectDay;this.saved=saved;}
 private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private TextView text(String value,int size,boolean bold){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(bold?theme.ink:theme.muted);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setPadding(0,dp(5),0,dp(5));return v;}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));return d;}
 private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(theme.accent);b.setBackground(shape(theme.raised));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(4));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
 private LinearLayout card(LinearLayout parent){LinearLayout box=column();box.setPadding(dp(18),dp(12),dp(18),dp(14));box.setBackground(shape(theme.surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(8));parent.addView(box,p);return box;}
 private static String amount(Double n,String unit){return n==null?"—":String.format(Locale.UK,unit.equals("kg")?"%.1f %s":"%.0f %s",n,unit);}
 void render(LinearLayout parent) throws Exception {
  records=repo.healthRecords();HealthRecords.Summary summary=HealthRecords.summary(records,day);
  parent.addView(button(day.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy",Locale.UK))+" · Change day",this::pickDay));
  LinearLayout meals=card(parent);meals.addView(text("Logged calories",14,false));meals.addView(text(amount(summary.calories,"kcal"),32,true));
  meals.addView(text(summary.mealCount+" meal"+(summary.mealCount==1?"":"s")+" logged",14,false));meals.addView(button("Add meal",()->mealForm(null,null)));
  List<JSONObject> entries=kind("meal");entries.sort(Comparator.comparing(r->r.optString("updated_at")));
  for(JSONObject meal:entries)if(day.toString().equals(meal.optString("date"))){
   meals.addView(button(meal.optString("name")+" · "+amount(meal.optDouble("calories_kcal")*meal.optDouble("portions"),"kcal"),()->mealOptions(meal)));
  }
  LinearLayout favourites=card(parent);favourites.addView(text("Favourites",20,true));
  List<JSONObject> favouritesList=kind("favourite");favouritesList.sort(Comparator.comparing(r->r.optString("name").toLowerCase(Locale.UK)));
  if(favouritesList.isEmpty())favourites.addView(text("Save meals you eat often for a quick add.",14,false));
  for(JSONObject favourite:favouritesList){
   favourites.addView(button("+ "+favourite.optString("name")+" · "+amount(favourite.optDouble("calories_kcal"),"kcal"),()->save(()->new JSONArray().put(HealthRecords.fromFavourite(favourite,day,1,System.currentTimeMillis())))));
   favourites.addView(button("Options · "+favourite.optString("name"),()->favouriteOptions(favourite)));
  }
  favourites.addView(button("New favourite",()->favouriteForm(null)));
  LinearLayout weight=card(parent);weight.addView(text("Weight",20,true));weight.addView(text(amount(summary.weight,"kg"),30,true));
  if(summary.weight==null&&summary.latestWeight!=null)weight.addView(text("Last recorded "+amount(summary.latestWeight,"kg")+" · "+summary.latestWeightDate,14,false));
  weight.addView(button(summary.weight==null?"Log weight":"Edit weight",()->weightForm(false)));
  weight.addView(text("7-day average: "+amount(summary.averageWeight,"kg")+" · "+summary.weighIns+" weigh-in(s)",14,false));
  weight.addView(button(summary.goal==null?"Set goal weight":"Goal · "+amount(summary.goal,"kg"),()->weightForm(true)));
  chart(weight,"Weight · past 30 days (kg)",30,true);
  LinearLayout calories=card(parent);calories.addView(text("Calorie history",20,true));
  calories.addView(text("7-day average: "+amount(summary.averageCalories,"kcal")+" · "+summary.loggedDays+" logged day(s)",14,false));
  chart(calories,"Logged calories · past 14 days (kcal)",14,false);
  calories.addView(text("Totals include only the meals you log. Missing days stay blank.",13,false));
 }
 private void chart(LinearLayout box,String title,int count,boolean weight){
  box.addView(text(title,14,false));List<Double> points=new ArrayList<>();for(int i=count-1;i>=0;i--){HealthRecords.Summary s=HealthRecords.summary(records,day.minusDays(i));points.add(weight?s.weight:s.calories);}
  TrendView graph=new TrendView(activity,points,theme.accent,theme.muted,theme.outline);graph.setContentDescription(title+". "+graph.getContentDescription());box.addView(graph,new LinearLayout.LayoutParams(-1,dp(140)));
  box.addView(text(day.minusDays(count-1).format(DateTimeFormatter.ofPattern("d MMM"))+" – "+day.format(DateTimeFormatter.ofPattern("d MMM")),12,false));
 }
 private List<JSONObject> kind(String kind){List<JSONObject> list=new ArrayList<>();for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(!r.optBoolean("deleted")&&kind.equals(r.optString("kind")))list.add(r);}return list;}
 private JSONObject latest(String id) throws Exception {JSONArray current=repo.healthRecords();for(int i=0;i<current.length();i++){JSONObject r=current.getJSONObject(i);if(id.equals(r.optString("record_id")))return r;}return null;}
 private JSONObject latest(JSONObject row) throws Exception {return row==null?null:latest(row.optString("record_id"));}
 private void pickDay(){DatePickerDialog picker=new DatePickerDialog(activity,(v,y,m,d)->selectDay.accept(LocalDate.of(y,m+1,d)),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();}
 private interface Changes {JSONArray make() throws Exception;}
 private void save(Changes action){try{repo.saveHealth(action.make());saved.run();}catch(Exception e){new AlertDialog.Builder(activity).setTitle("Could not save").setMessage(e.getMessage()).setPositiveButton("Close",null).show();}}
 private void remove(JSONObject row){remove(row,()->{});}
 private void remove(JSONObject row,Runnable removed){new AlertDialog.Builder(activity).setTitle("Remove entry?").setMessage("Remove "+(row.optString("name").isEmpty()?"this weight entry":row.optString("name"))+"?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{try{repo.saveHealth(new JSONArray().put(HealthRecords.deleted(latest(row),System.currentTimeMillis())));removed.run();saved.run();}catch(Exception e){new AlertDialog.Builder(activity).setMessage("Could not remove entry; please retry").setPositiveButton("Close",null).show();}}).show();}
 private void mealOptions(JSONObject row){new AlertDialog.Builder(activity).setTitle(row.optString("name")).setItems(new String[]{"Edit meal","Save as favourite","Remove meal"},(d,w)->{if(w==0)mealForm(row,null);else if(w==1)save(()->new JSONArray().put(HealthRecords.favourite(row.optString("name"),row.optDouble("calories_kcal"),row.optString("note"),null,System.currentTimeMillis())));else remove(row);}).show();}
 private void favouriteOptions(JSONObject row){new AlertDialog.Builder(activity).setTitle(row.optString("name")).setItems(new String[]{"Add with portions","Edit favourite","Remove favourite"},(d,w)->{if(w==0)mealForm(null,row);else if(w==1)favouriteForm(row);else remove(row);}).show();}
 private EditText field(LinearLayout box,String label,String value,boolean numeric){box.addView(text(label,14,false));EditText input=new EditText(activity);input.setContentDescription(label);input.setText(value);input.setTextColor(theme.ink);input.setTextSize(16);input.setInputType(numeric?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setSingleLine(true);box.addView(input,new LinearLayout.LayoutParams(-1,-2));return input;}
 private LinearLayout form(){LinearLayout box=column();box.setPadding(dp(22),dp(8),dp(22),dp(16));return box;}
 private AlertDialog dialog(String title,LinearLayout box,Changes action){
  TextView error=text("",14,false);error.setTextColor(theme.accent);box.addView(error);ScrollView scroll=new ScrollView(activity);scroll.addView(box);
  AlertDialog d=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{repo.saveHealth(action.make());d.dismiss();saved.run();}catch(Exception e){error.setText(e instanceof NumberFormatException?"Enter a valid number":e.getMessage());}}));d.show();return d;
 }
 private void mealForm(JSONObject previous,JSONObject favourite){
  JSONObject seed=previous==null?favourite:previous;LinearLayout box=form();
  box.addView(text(day.format(DateTimeFormatter.ofPattern("EEE d MMM")),14,false));
  EditText name=field(box,"Meal name",seed==null?"":seed.optString("name"),false),kcal=field(box,"Calories per portion (kcal)",seed==null?"":String.valueOf(seed.optDouble("calories_kcal")),true),portions=field(box,"Portions",previous==null?"1":String.valueOf(previous.optDouble("portions")),true),note=field(box,"Note (optional)",seed==null?"":seed.optString("note"),false);
  CheckBox fav=new CheckBox(activity);fav.setText("Save as favourite");fav.setTextColor(theme.ink);box.addView(fav);
  dialog(previous==null?"Add meal":"Edit meal",box,()->{
   long now=System.currentTimeMillis();JSONArray changes=new JSONArray().put(HealthRecords.meal(day,name.getText().toString(),Double.parseDouble(kcal.getText().toString()),Double.parseDouble(portions.getText().toString()),note.getText().toString(),latest(previous),now));
   if(fav.isChecked())changes.put(HealthRecords.favourite(name.getText().toString(),Double.parseDouble(kcal.getText().toString()),note.getText().toString(),null,now));return changes;
  });
 }
 private void favouriteForm(JSONObject previous){LinearLayout box=form();EditText name=field(box,"Meal name",previous==null?"":previous.optString("name"),false),kcal=field(box,"Calories per portion (kcal)",previous==null?"":String.valueOf(previous.optDouble("calories_kcal")),true),note=field(box,"Note (optional)",previous==null?"":previous.optString("note"),false);dialog(previous==null?"New favourite":"Edit favourite",box,()->new JSONArray().put(HealthRecords.favourite(name.getText().toString(),Double.parseDouble(kcal.getText().toString()),note.getText().toString(),latest(previous),System.currentTimeMillis())));}
 private void weightForm(boolean goal){
  try{JSONObject previous=latest(goal?"goal-weight":"weight:"+day);if(previous!=null&&previous.optBoolean("deleted"))previous=null;
   final JSONObject initial=previous;LinearLayout box=form();EditText kg=field(box,goal?"Goal weight (kg)":"Weight (kg)",initial==null?"":String.valueOf(initial.optDouble("weight_kg")),true);
   AlertDialog[] edit=new AlertDialog[1];
   if(initial!=null)box.addView(button(goal?"Remove goal":"Remove weight",()->remove(initial,()->edit[0].dismiss())));
   edit[0]=dialog(goal?"Goal weight":"Log weight",box,()->{JSONObject old=latest(goal?"goal-weight":"weight:"+day);double value=Double.parseDouble(kg.getText().toString());return new JSONArray().put(goal?HealthRecords.goal(value,old,System.currentTimeMillis()):HealthRecords.weight(day,value,old,System.currentTimeMillis()));});
  }catch(Exception e){new AlertDialog.Builder(activity).setMessage("Could not load weight").setPositiveButton("Close",null).show();}
 }
}
