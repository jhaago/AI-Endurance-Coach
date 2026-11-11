package au.com.endurancecoach;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import au.com.endurancecoach.core.*;
import org.json.JSONArray;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Coach-owned assessment display inside Diary; every control is read-only. */
final class DietQualitySection {
 private final Activity activity;private final CoachRepository repo;private final CoachTheme theme;private final LocalDate day;private final Runnable redraw;
 DietQualitySection(Activity activity,CoachRepository repo,CoachTheme theme,LocalDate day,Runnable redraw){this.activity=activity;this.repo=repo;this.theme=theme;this.day=day;this.redraw=redraw;}
 private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size,boolean bold){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(bold?theme.ink:theme.muted);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setPadding(0,dp(4),0,dp(4));return v;}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
 private LinearLayout column(){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);return box;}
 private LinearLayout card(LinearLayout parent){LinearLayout box=column();box.setPadding(dp(16),dp(12),dp(16),dp(14));box.setBackground(shape(theme.surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(8));parent.addView(box,p);return box;}
 private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(theme.accent);b.setBackground(shape(theme.raised));b.setMinHeight(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->action.run());return b;}
 private static String title(String s){return s.substring(0,1).toUpperCase(Locale.UK)+s.substring(1);}
 void render(LinearLayout parent,JSONArray health) throws Exception {
  LocalDate today=LocalDate.now(PlanEngine.ZONE);if(day.isAfter(today))return;
  JSONArray ledger=repo.dietQuality();DietQuality.Assessment a=DietQuality.forDay(ledger,health,day);String error=repo.dietQualityError();
  LinearLayout score=card(parent);score.addView(text("Diet Quality",20,true));
  if(!error.isEmpty())score.addView(text(a==null?"Diet quality unavailable. Ask Coach to check setup, then sync again.":"Saved assessment may be out of date. Sync again when connected.",14,false));
  if(a==null){score.addView(text("No diet quality assessment yet",18,true));score.addView(text("Log food with Coach, then sync after the assessment is saved.",14,false));}
  else {
   score.addView(text(a.score+" / 100",32,true));score.addView(text(a.stale?"Needs review · food log changed":"provisional".equals(a.status)?"Provisional · food logged so far":"Complete · day reviewed",14,true));
   if(a.stale)score.addView(text("Ask Coach to reassess your updated food log. This score is excluded from the trend and average.",14,false));
   score.addView(text("Confidence · "+title(a.confidence),13,false));score.addView(text(a.summary,15,false));score.addView(text("Next step · "+a.improvement,14,true));
   String assessed=Instant.parse(a.assessedAt).atZone(PlanEngine.ZONE).format(DateTimeFormatter.ofPattern("d MMM, h:mm a",Locale.UK));score.addView(text("Assessed "+assessed+" AWST",12,false));
   LinearLayout details=column();details.setVisibility(View.GONE);
   for(int i=0;i<5;i++){details.addView(text(DietQuality.LABELS[i]+" · "+a.points[i]+" / "+DietQuality.MAXIMUMS[i],15,true));details.addView(text(a.reasons[i],14,false));}
   details.addView(text("Strength · "+a.strength,14,true));details.addView(text("Source · "+a.source+" · revision "+a.revision,12,false));details.addView(text("Coach estimate · dq-v1. Food quality is separate from calorie targets. Photos may not reveal ingredients or exact portions. Running fuel is considered in context.",13,false));
   Button expand=button("Show breakdown",()->{});expand.setContentDescription("Show diet quality breakdown");expand.setOnClickListener(v->{boolean show=details.getVisibility()!=View.VISIBLE;details.setVisibility(show?View.VISIBLE:View.GONE);expand.setText(show?"Hide breakdown":"Show breakdown");expand.setContentDescription(show?"Hide diet quality breakdown":"Show diet quality breakdown");});
   score.addView(expand,new LinearLayout.LayoutParams(-1,-2));score.addView(details);
  }
  LinearLayout graph=card(parent);graph.addView(text("Diet Quality trend",18,true));
  int stored=activity.getSharedPreferences("health-ui",0).getInt("diet-quality-range",7),range=stored==30?30:7;
  LinearLayout choices=new LinearLayout(activity);for(int count:new int[]{7,30}){Button b=button(count+" days",()->{activity.getSharedPreferences("health-ui",0).edit().putInt("diet-quality-range",count).apply();redraw.run();});b.setSelected(range==count);b.setBackground(shape(range==count?theme.accentSurface:theme.raised));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,dp(6),dp(4),dp(8));choices.addView(b,p);}graph.addView(choices);
  DietQuality.Trend trend=DietQuality.trend(ledger,health,day,today,range);
  graph.addView(text("7-day average · "+(trend.average==null?"—":String.format(Locale.UK,"%.0f / 100",trend.average)),18,true));graph.addView(text(trend.completedDays+" completed day"+(trend.completedDays==1?"":"s")+" in the past 7 days",13,false));
  graph.addView(new DietQualityTrendView(activity,trend,day,theme),new LinearLayout.LayoutParams(-1,dp(170)));
  graph.addView(text("Solid points: completed days · hollow point: provisional today. Missing and unreviewed days stay blank.",12,false));
 }
}
