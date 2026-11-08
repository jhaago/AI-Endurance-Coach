package au.com.endurancecoach;

import android.app.*;
import android.os.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
    private static final String[] TABS = {"Today", "Plan", "Progress", "Insights", "Health"};
    private static final int[] ICONS = {R.drawable.ic_today, R.drawable.ic_plan,
            R.drawable.ic_progress, R.drawable.ic_insights, R.drawable.ic_health};
    private CoachTheme theme;
    private CoachRepository repo;
    private LinearLayout root, content;
    private String currentSyncMessage = "";
    private TextView syncDetailsStatus, syncDetailsTimes, syncDetailsPending;
    private ScrollView screenScroll;
    private int tab;
    private int progressWeekOffset;
    private LocalDate week;
    private LocalDate healthDay;
    private boolean active;
    private final SyncQueue syncQueue = new SyncQueue();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            if (active) { sync(false); handler.postDelayed(this, 60000); }
        }
    };

    @Override public void onCreate(Bundle state) {
        theme = CoachTheme.apply(this);
        super.onCreate(state);
        repo = new CoachRepository(this);
        week = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        healthDay = today();
        if (state != null) {
            tab = Math.max(0, Math.min(TABS.length - 1, state.getInt("tab", 0)));
            progressWeekOffset = Math.max(0, Math.min(11, state.getInt("progress_week", 0)));
            week = LocalDate.parse(state.getString("week", week.toString()));
            healthDay = LocalDate.parse(state.getString("health_day", healthDay.toString()));
        }
        getWindow().setStatusBarColor(theme.background);
        getWindow().setNavigationBarColor(!theme.dark && Build.VERSION.SDK_INT < 27 ? theme.ink : theme.background);
        int systemUi = theme.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!theme.dark && Build.VERSION.SDK_INT >= 27) systemUi |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        SyncJob.schedule(this);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", tab);
        state.putInt("progress_week", progressWeekOffset);
        state.putString("week", week.toString());
        state.putString("health_day", healthDay.toString());
        state.putInt("scroll", screenScroll == null ? 0 : screenScroll.getScrollY());
        super.onSaveInstanceState(state);
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (screenScroll != null) screenScroll.post(() -> screenScroll.scrollTo(0, state.getInt("scroll", 0)));
    }

    @Override protected void onResume() {
        super.onResume(); active = true; sync(true); handler.postDelayed(refresh, 60000);
    }
    @Override protected void onPause() { active = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() {
        // Keep already requested writes alive after Back/rotation; the final callback releases ownership.
        if (!syncQueue.isRunning()) { network.shutdown(); repo.close(); }
        super.onDestroy();
    }

    private LocalDate today() { return LocalDate.now(PlanEngine.ZONE); }
    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("tnum");
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setPadding(0, dp(4), 0, dp(4)); v.setLineSpacing(dp(2), 1);
        return v;
    }
    private GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (border) d.setStroke(dp(1), theme.outline);
        return d;
    }
    private RippleDrawable ripple(int color, int radius, boolean border) {
        return new RippleDrawable(ColorStateList.valueOf(theme.dark ? 0x2582DBC4 : 0x18116D5C),
                shape(color, radius, border), shape(Color.WHITE, radius, false));
    }
    private Button button(String label, Runnable action) { return button(label, action, false); }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? theme.onAccent : theme.accent);
        b.setBackground(ripple(primary ? theme.accent : theme.raised, 14, false));
        b.setStateListAnimator(null); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(2)); b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout card() { return card(theme.surface); }
    private LinearLayout card(int color) {
        LinearLayout v = column(); v.setPadding(dp(20), dp(16), dp(20), dp(16));
        v.setBackground(shape(color, 22, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(8)); v.setLayoutParams(p); content.addView(v); return v;
    }
    private void section(String label) {
        TextView heading = text(label, 13, theme.muted, true);
        heading.setPadding(dp(2), dp(18), 0, dp(6)); content.addView(heading);
    }
    private void notice(String value) { card().addView(text(value, 14, theme.muted, false)); }
    private ScrollView scroll(LinearLayout box) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        scroll.addView(box); return scroll;
    }
    private LinearLayout dialogBox() {
        LinearLayout box = column(); box.setPadding(dp(24), dp(8), dp(24), dp(16)); return box;
    }
    private void showDetails(String title, LinearLayout box) {
        new AlertDialog.Builder(this).setTitle(title).setView(scroll(box)).setPositiveButton("Close", null).show();
    }
    private void render() { render(false); }
    private void render(boolean keepScroll) {
        int previousScroll = keepScroll && screenScroll != null ? screenScroll.getScrollY() : 0;
        try {
            JSONObject s = repo.snapshot(); root = column(); root.setBackgroundColor(theme.background);
            root.setPadding(dp(20), dp(10), dp(20), dp(4));
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = i.top; bottom = i.bottom; left = i.left; right = i.right;
                } else {
                    top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
                }
                view.setPadding(left + dp(20), top + dp(8), right + dp(20), bottom + dp(4)); return insets;
            });
            LinearLayout head = row(); TextView brand = text("ENDURANCE COACH", 12, theme.accent, true);
            brand.setLetterSpacing(.10f); head.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
            Button settings = button("Settings", this::settings);
            settings.setTextSize(12); settings.setBackground(ripple(theme.background, 14, false));
            head.addView(settings, new LinearLayout.LayoutParams(-2, -2)); root.addView(head);
            root.addView(text(TABS[tab], 30, theme.ink, true));
            updateStatus(syncQueue.isRunning() ? "Syncing…" : currentSyncMessage.isEmpty()
                    ? repo.store.get("status").optString("message", "") : currentSyncMessage);
            screenScroll = new ScrollView(this); screenScroll.setFillViewport(true);
            screenScroll.setClipToPadding(false); screenScroll.setPadding(0, dp(6), 0, dp(12));
            content = column(); screenScroll.addView(content);
            root.addView(screenScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            if (tab == 0) todayScreen(s); else if (tab == 1) planScreen(s);
            else if (tab == 2) progressScreen(s); else if (tab == 3) insightsScreen(s);
            else new HealthScreen(this, repo, theme, healthDay, day -> { healthDay = day; render(); }, () -> { render(true); sync(false); }).render(content);
            root.addView(navigation()); setContentView(root); root.requestApplyInsets();
            if (previousScroll > 0) screenScroll.post(() -> screenScroll.scrollTo(0, previousScroll));
        } catch (Exception e) {
            LinearLayout error = dialogBox(); error.setBackgroundColor(theme.background);
            error.setPadding(dp(24), dp(48), dp(24), dp(24));
            error.addView(text("Training data needs attention", 24, theme.ink, true));
            error.addView(text("Check workout dates and revisions in the training database, then retry.", 16, theme.muted, false));
            error.addView(button("Settings", this::settings));
            setContentView(error);
        }
    }
    private void updateStatus(String message) {
        if (message.isEmpty()) {
            try { message = repo.vault.get("bridge").isEmpty() ? "Connect in Settings to get started" : "Ready to sync"; }
            catch (Exception e) { message = "Check connections in Settings"; }
        }
        message = message.replace("see Insights import details", "see Settings → Sync & import details");
        currentSyncMessage = message;
        if (syncDetailsStatus != null) syncDetailsStatus.setText(message);
        if (syncDetailsTimes != null) {
            try { syncDetailsTimes.setText(syncTimes(repo.store.get("status"))); }
            catch (Exception ignored) { /* Keep the last successfully read timestamps. */ }
        }
        if (syncDetailsPending != null) {
            try {
                int count = repo.store.pending("feedback").length() + repo.store.pending("health").length();
                syncDetailsPending.setText(count + " entry change(s) waiting to sync");
                syncDetailsPending.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            } catch (Exception ignored) { /* Keep the last known queue count. */ }
        }
    }
    private LinearLayout navigation() {
        LinearLayout nav = row(); nav.setPadding(0, dp(8), 0, dp(2));
        for (int i = 0; i < TABS.length; i++) {
            final int selected = i; boolean current = i == tab;
            LinearLayout item = column(); item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(8), dp(2), dp(6));
            item.setBackground(ripple(current ? theme.accentSurface : theme.background, 18, false));
            ImageView icon = new ImageView(this); icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(current ? theme.accent : theme.muted));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView label = text(TABS[i], 11, current ? theme.accent : theme.muted, current);
            label.setMaxLines(1); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER); label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(label); item.setContentDescription(TABS[i]); item.setSelected(current);
            item.setFocusable(true); item.setClickable(true);
            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName());
                }
            });
            item.setOnClickListener(v -> { if (tab != selected) { tab = selected; render(); } });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.setMargins(dp(2), 0, dp(2), 0); nav.addView(item, p);
        }
        return nav;
    }
    private JSONArray plan(JSONObject s) throws Exception {
        return PlanEngine.forEvent(CoachRepository.array(s, "plan"), event(s).optString("event_id"));
    }
    private JSONObject matches(JSONObject s) throws Exception {
        return PlanEngine.matches(CoachRepository.array(s, "plan"), CoachRepository.array(s, "activities"), repo.overrides(s));
    }
    private JSONObject event(JSONObject s) throws Exception {
        JSONArray events = CoachRepository.array(s, "events");
        for (int i = 0; i < events.length(); i++) if (events.getJSONObject(i).optBoolean("active")) return events.getJSONObject(i);
        return new JSONObject();
    }
    private Set<String> completed(JSONObject s) throws Exception {
        JSONObject m = matches(s); Set<String> ids = new HashSet<>();
        for (Iterator<String> keys = m.keys(); keys.hasNext();) ids.add(m.getString(keys.next()));
        return ids;
    }
    private void todayScreen(JSONObject s) throws Exception {
        JSONArray plan = plan(s); Set<String> completed = completed(s); JSONObject next = null;
        for (int i = 0; i < plan.length(); i++) {
            JSONObject w = plan.getJSONObject(i);
            if (!LocalDate.parse(w.getString("date")).isBefore(today()) && !completed.contains(w.getString("workout_id"))) { next = w; break; }
        }
        section("NEXT UP");
        if (next == null) notice("No upcoming workout. Your next session will appear when the plan is updated.");
        else workout(next, false, true);
        content.addView(button("Daily check-in", () -> feedback("", "checkin-" + today()), true));
        JSONObject event = event(s);
        if (event.has("event_id")) {
            section("YOUR EVENT"); LinearLayout target = card();
            target.addView(text(event.optString("name", "Next event"), 17, theme.ink, true));
            if (event.has("date")) {
                LocalDate date = LocalDate.parse(event.getString("date"));
                long days = java.time.temporal.ChronoUnit.DAYS.between(today(), date);
                target.addView(text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))
                        + (days > 0 ? " · " + days + " days to go" : days == 0 ? " · Today" : " · Past event"), 13, theme.muted, false));
            }
            target.addView(text(event.optInt("primary_yards", 10) + " yards", 28, theme.accent, true));
            target.addView(text(String.format(Locale.UK, "%.2f km target · Stretch %d yards / %.2f km",
                    event.optDouble("primary_km", 67.06), event.optInt("stretch_yards", 12), event.optDouble("stretch_km", 80.47)), 13, theme.muted, false));
        }
    }
    private void workout(JSONObject w, boolean completed, boolean featured) throws Exception {
        LinearLayout c = card(featured ? theme.accentSurface : theme.surface);
        String meta = dayLabel(w.getString("date")) + " · " + typeLabel(w.getString("type"))
                + (w.optBoolean("optional") ? " · Optional" : "") + (completed ? " · Completed" : "");
        c.addView(text(meta, 12, theme.accent, true));
        c.addView(text(w.optString("title", "Workout"), featured ? 25 : 19, theme.ink, true));
        if (w.optInt("duration_min") > 0) c.addView(text(w.optInt("duration_min") + " min", 24, theme.ink, true));
        String intensity = w.optString("intensity", "");
        if (!intensity.isEmpty()) c.addView(text(intensity, 14, theme.muted, false));
        c.addView(button("Workout details", () -> workoutDetail(w)));
    }
    private void workoutDetail(JSONObject workout) {
        try {
            LinearLayout box = dialogBox();
            box.addView(text(dayLabel(workout.getString("date")) + " · " + typeLabel(workout.optString("type")), 14, theme.accent, true));
            if (workout.optInt("duration_min") > 0) box.addView(text(workout.optInt("duration_min") + " min", 24, theme.ink, true));
            box.addView(text(workout.optString("intensity", ""), 16, theme.ink, false));
            box.addView(button("Add feedback", () -> feedback("", workout.optString("workout_id")), true));
            showDetails(workout.optString("title", "Workout"), box);
        } catch (Exception e) { toast("Workout details unavailable"); }
    }
    private void planScreen(JSONObject s) throws Exception {
        LinearLayout controls = row(); Button prev = button("‹", () -> { week = week.minusWeeks(1); render(); });
        prev.setContentDescription("Previous week"); controls.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView range = text(week.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + " – "
                + week.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)), 16, theme.ink, true);
        range.setGravity(Gravity.CENTER); controls.addView(range, new LinearLayout.LayoutParams(0, -2, 1));
        Button next = button("›", () -> { week = week.plusWeeks(1); render(); }); next.setContentDescription("Next week");
        controls.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(controls);
        JSONArray p = plan(s); Set<String> done = completed(s);
        for (int d = 0; d < 7; d++) {
            LocalDate date = week.plusDays(d); boolean any = false;
            section(date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) + (date.equals(today()) ? " · Today" : ""));
            for (int i = 0; i < p.length(); i++) {
                JSONObject w = p.getJSONObject(i);
                if (w.getString("date").equals(date.toString())) { workout(w, done.contains(w.getString("workout_id")), false); any = true; }
            }
            if (!any) content.addView(text("Nothing scheduled", 13, theme.muted, false));
        }
    }
    private List<JSONObject> activities(JSONObject s) throws Exception {
        JSONArray a = CoachRepository.array(s, "activities"); List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) list.add(a.getJSONObject(i));
        list.sort(Comparator.comparing((JSONObject o) -> o.optString("start_utc")).reversed()); return list;
    }
    private void progressScreen(JSONObject s) throws Exception {
        List<JSONObject> rows = activities(s); JSONObject matched = matches(s);
        JSONObject syncStatus = repo.store.get("status");
        List<WeeklyProgress.Week> weeks = WeeklyProgress.weeks(CoachRepository.array(s, "activities"), today(),
                syncStatus.optString("activity_start", ""), syncStatus.optString("activity_end", ""));
        LinearLayout totals = card();
        TextView period = text("", 13, theme.accent, true); totals.addView(period);
        TextView dates = text("", 12, theme.muted, false); totals.addView(dates);
        LinearLayout metrics = row(); metrics.setGravity(Gravity.TOP); TextView[] values = new TextView[3];
        String[] labels = {"Distance", "Time", "Elev gain"};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout cell = column(); cell.addView(text(labels[i], 12, theme.muted, false));
            values[i] = text("—", 20, theme.ink, true); cell.addView(values[i]);
            metrics.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        totals.addView(metrics);
        TextView count = text("", 12, theme.muted, false); totals.addView(count);
        totals.addView(text("PAST 12 WEEKS · RUNNING", 12, theme.muted, true));
        TextView coverage = text("", 12, theme.muted, false);
        java.util.function.IntConsumer select = index -> {
            progressWeekOffset = weeks.size() - 1 - index; WeeklyProgress.Week w = weeks.get(index);
            period.setText(index == weeks.size() - 1 ? "THIS WEEK" : "WEEKLY TOTAL");
            dates.setText(w.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + " – "
                    + w.start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
                    + (index == weeks.size() - 1 ? " · So far" : ""));
            values[0].setText(w.km == null ? "—" : String.format(Locale.UK, "%.2f km", w.km));
            values[1].setText(w.seconds == null ? "—" : durationSeconds(w.seconds));
            values[2].setText(w.elevation == null ? "—" : String.format(Locale.UK, "%.0f m", w.elevation));
            count.setText(w.runs + (w.runs == 1 ? " recorded run" : " recorded runs"));
            coverage.setText(!w.covered ? "Imported totals only · earlier history may be incomplete"
                    : w.km == null || w.seconds == null || w.elevation == null ? "— means a measurement is unavailable"
                    : "Tap a week to view its totals · Mon–Sun");
        };
        int selected = weeks.size() - 1 - progressWeekOffset; select.accept(selected);
        totals.addView(new WeeklyDistanceView(this, weeks, selected, theme, select), new LinearLayout.LayoutParams(-1, dp(200)));
        totals.addView(coverage);
        section("ACTIVITY HISTORY");
        if (rows.isEmpty()) notice("Your completed activities will appear here.");
        for (int i = 0; i < Math.min(100, rows.size()); i++) {
            JSONObject a = rows.get(i); LinearLayout c = card();
            c.addView(text(dayLabel(PlanEngine.day(a)) + " · " + typeLabel(a.optString("type")), 12, theme.accent, true));
            c.addView(text(a.optString("name", "Activity"), 19, theme.ink, true));
            c.addView(text(value(a, "distance_m", .001, " km") + " · " + duration(a), 17, theme.ink, true));
            c.addView(text(matched.has(a.getString("activity_id")) ? "Workout matched" : "No workout match", 12, theme.muted, false));
            c.addView(button("View activity", () -> activityDetail(a)));
        }
    }
    private List<JSONObject> wellness(JSONObject s) throws Exception {
        JSONArray a = CoachRepository.array(s, "wellness"); List<JSONObject> rows = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) rows.add(a.getJSONObject(i));
        rows.sort(Comparator.comparing(o -> o.optString("date"))); return rows;
    }
    private LinearLayout metric(String name, String value, String extra) {
        LinearLayout tile = column(); tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(shape(theme.surface, 20, false)); tile.addView(text(name, 12, theme.muted, true));
        tile.addView(text(value, 23, theme.ink, true));
        if (!extra.isEmpty()) tile.addView(text(extra, 12, theme.muted, false));
        return tile;
    }
    private void metricPair(LinearLayout a, LinearLayout b) {
        LinearLayout pair = row(); pair.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -1, 1); first.setMargins(0, dp(4), dp(5), dp(6));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -1, 1); second.setMargins(dp(5), dp(4), 0, dp(6));
        pair.addView(a, first); pair.addView(b, second); content.addView(pair);
    }
    private void insightsScreen(JSONObject s) throws Exception {
        List<JSONObject> rows = wellness(s);
        if (rows.isEmpty()) notice("Your recorded sleep, overnight HRV and resting heart rate will appear here.");
        else {
            JSONObject latest = rows.get(rows.size() - 1);
            section("RECOVERY · " + dayLabel(latest.optString("date")));
            if (!latest.optString("date").equals(today().toString()))
                content.addView(text("Latest available day · earlier than today", 13, theme.muted, false));
            metricPair(metric("Sleep", sleep(latest), ""), metric("Sleep score", value(latest, "sleep_score", 1, ""), "out of 100"));
            metricPair(metric("Overnight HRV", value(latest, "hrv_ms", 1, " ms"), ""),
                    metric("Resting HR", value(latest, "resting_hr", 1, " bpm"), ""));
            content.addView(text("— means no measurement was available.", 12, theme.muted, false));
            section("14-DAY TRENDS");
            Map<String, JSONObject> days = new HashMap<>(); for (JSONObject w : rows) days.put(w.optString("date"), w);
            LocalDate end = today(), start = end.minusDays(13);
            for (String metric : new String[]{"sleep_hours", "hrv_ms", "resting_hr"}) {
                List<Double> points = new ArrayList<>();
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    JSONObject w = days.get(d.toString()); points.add(w == null || w.isNull(metric) ? null : w.optDouble(metric));
                }
                LinearLayout chart = card();
                chart.addView(text(metric.equals("sleep_hours") ? "Sleep · hours" : metric.equals("hrv_ms") ? "Overnight HRV · ms" : "Resting HR · bpm", 15, theme.ink, true));
                chart.addView(new TrendView(this, points, theme.accent, theme.muted, theme.outline), new LinearLayout.LayoutParams(-1, dp(112)));
                chart.addView(text(start.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + " – "
                        + end.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)), 12, theme.muted, false));
            }
        }
        content.addView(button("Daily check-in", () -> feedback("", "checkin-" + today()), true));
        JSONArray f = CoachRepository.merge(CoachRepository.array(s, "feedback"), repo.store.pending("feedback"), "feedback_id");
        if (f.length() > 0) {
            section("RECENT CHECK-INS"); LinearLayout c = card();
            for (int i = Math.max(0, f.length() - 7); i < f.length(); i++) {
                JSONObject v = f.getJSONObject(i);
                c.addView(text(stamp(v.optString("created_at")), 12, theme.accent, true));
                c.addView(text("Effort " + v.optInt("rpe") + " · Soreness " + v.optInt("soreness") + " · Fatigue " + v.optInt("fatigue"), 13, theme.ink, false));
                if (!v.optString("note", "").isEmpty()) c.addView(text(v.optString("note"), 14, theme.muted, false));
            }
        }
    }
    private void activityDetail(JSONObject a) {
        try {
            String id = a.getString("activity_id"); LinearLayout box = dialogBox();
            box.addView(text(dayLabel(PlanEngine.day(a)), 13, theme.accent, true));
            box.addView(text(value(a, "distance_m", .001, " km") + " · " + duration(a), 24, theme.ink, true));
            box.addView(text("Pace " + pace(a.optDouble("pace_s_km", Double.NaN)) + "\nHeart rate " + value(a, "average_hr", 1, " bpm")
                    + "\nElevation gain " + value(a, "elevation_m", 1, " m") + "\nCadence " + value(a, "cadence_spm", 1, " steps/min"), 16, theme.ink, false));
            JSONArray samples = CoachRepository.array(repo.store.get("samples:" + id), "records");
            String[] metrics = {"hr", "pace_s_km", "elevation_m", "cadence_spm"};
            String[] titles = {"Heart rate · bpm", "Pace · seconds/km", "Elevation · m", "Cadence · steps/min"};
            if (samples.length() > 0) for (int k = 0; k < metrics.length; k++) {
                List<Double> points = new ArrayList<>(); int stride = Math.max(1, samples.length() / 250);
                for (int i = 0; i < samples.length(); i += stride) {
                    JSONObject r = samples.getJSONObject(i); points.add(r.isNull(metrics[k]) ? null : r.optDouble(metrics[k]));
                }
                box.addView(text(titles[k], 13, theme.accent, true));
                box.addView(new TrendView(this, points, theme.accent, theme.muted, theme.outline), new LinearLayout.LayoutParams(-1, dp(100)));
            }
            JSONObject matched = matches(repo.snapshot()); String matchedId = matched.optString(id);
            String matchName = "No workout match"; JSONArray p = plan(repo.snapshot());
            for (int i = 0; i < p.length(); i++) if (p.getJSONObject(i).optString("workout_id").equals(matchedId))
                matchName = "Matched: " + p.getJSONObject(i).optString("title");
            box.addView(text(matchName, 14, theme.muted, false));
            box.addView(button("Add session feedback", () -> feedback(id, ""), true));
            box.addView(button("Change workout match", () -> chooseMatch(a)));
            showDetails(a.optString("name", "Activity"), box);
        } catch (Exception e) { toast("Activity details unavailable"); }
    }
    private void chooseMatch(JSONObject a) {
        try {
            JSONArray p = plan(repo.snapshot()); List<String> labels = new ArrayList<>(), ids = new ArrayList<>();
            labels.add("Leave unmatched"); ids.add("");
            for (int i = 0; i < p.length(); i++) {
                JSONObject w = p.getJSONObject(i);
                if (w.getString("date").equals(PlanEngine.day(a))) { labels.add(w.optString("title") + " · " + typeLabel(w.optString("type"))); ids.add(w.getString("workout_id")); }
            }
            new AlertDialog.Builder(this).setTitle("Match this activity").setItems(labels.toArray(new String[0]), (d, index) -> {
                try { repo.match(a.getString("activity_id"), ids.get(index)); render(true); sync(false); }
                catch (Exception e) { toast("Could not save match"); }
            }).show();
        } catch (Exception e) { toast("Could not read workouts"); }
    }
    private void feedback(String activity, String workout) {
        LinearLayout box = dialogBox(); String[] names = {"Effort", "Soreness", "Fatigue"};
        String[] scales = {"0 rest · 10 maximum", "0 none · 10 extreme", "0 fresh · 10 exhausted"}; SeekBar[] sliders = new SeekBar[3];
        for (int i = 0; i < names.length; i++) {
            TextView label = text(names[i] + " · 0", 16, theme.ink, true); box.addView(label);
            box.addView(text(scales[i], 12, theme.muted, false));
            SeekBar bar = new SeekBar(this); bar.setMax(10); bar.setMinimumHeight(dp(48));
            bar.setContentDescription(names[i]); final String name = names[i];
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar b, int value, boolean user) { label.setText(name + " · " + value); }
                public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
            }); sliders[i] = bar; box.addView(bar);
        }
        EditText note = new EditText(this); note.setHint("Pain, energy or fuelling notes (optional)");
        note.setTextColor(theme.ink); note.setHintTextColor(theme.muted); note.setTextSize(14);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)}); box.addView(note);
        new AlertDialog.Builder(this).setTitle("Quick check-in").setView(scroll(box)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        repo.feedback(activity, workout, sliders[0].getProgress(), sliders[1].getProgress(), sliders[2].getProgress(), note.getText().toString());
                        toast("Check-in saved"); render(true); sync(false);
                    } catch (Exception e) { toast("Could not save feedback. Please retry."); }
                }).show();
    }
    private void settings() {
        LinearLayout box = dialogBox(); int selected = Arrays.asList(CoachTheme.MODES).indexOf(CoachTheme.preference(this));
        box.addView(text("Appearance", 13, theme.muted, true));
        box.addView(button(CoachTheme.LABELS[selected] + "  ›", this::appearance));
        box.addView(text("Connections & data", 13, theme.muted, true));
        box.addView(button("Garmin & training database", this::connections));
        box.addView(button("Sync & import details", this::syncDetails));
        box.addView(button("About your data", this::aboutData));
        box.addView(button("Third-party licences", this::licences));
        box.addView(text("Endurance Coach · " + BuildConfig.VERSION_NAME, 12, theme.muted, false));
        showDetails("Settings", box);
    }
    private void appearance() {
        int selected = Arrays.asList(CoachTheme.MODES).indexOf(CoachTheme.preference(this));
        new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(CoachTheme.LABELS, selected, (dialog, index) -> {
            if (index == selected) { dialog.dismiss(); return; }
            CoachTheme.save(this, index); dialog.dismiss(); recreate();
        }).setNegativeButton("Cancel", null).show();
    }
    private void syncDetails() {
        try {
            LinearLayout box = dialogBox(); JSONObject st = repo.store.get("status");
            TextView message = text(currentSyncMessage.isEmpty() ? st.optString("message", "Not yet synced") : currentSyncMessage, 15, theme.ink, true);
            TextView times = text(syncTimes(st), 14, theme.muted, false);
            syncDetailsStatus = message; syncDetailsTimes = times; box.addView(message); box.addView(times);
            TextView pending = text("", 14, theme.muted, false); syncDetailsPending = pending; box.addView(pending);
            updateStatus(message.getText().toString());
            box.addView(text("Automatic sync", 16, theme.ink, true));
            box.addView(text("Garmin syncs when you open or return to the app, with a background check about every 6 hours. The plan and your notes also refresh each minute while open. Your watch must sync to Garmin Connect first. Activity names come from Garmin, including when you also use Strava.", 14, theme.muted, false));
            box.addView(button("Sync Garmin + plan", () -> sync(true), true));
            box.addView(button("Refresh plan", () -> sync(false)));
            List<JSONObject> days = wellness(repo.snapshot());
            for (int i = days.size() - 1; i >= Math.max(0, days.size() - 7); i--) {
                JSONObject day = days.get(i); box.addView(text(day.optString("date"), 13, theme.accent, true));
                box.addView(text(day.optString("coverage_note", "No import details recorded"), 13, theme.muted, false));
            }
            if (days.isEmpty()) box.addView(text("No recovery import details yet.", 14, theme.muted, false));
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Sync & import details")
                    .setView(scroll(box)).setPositiveButton("Close", null).create();
            dialog.setOnDismissListener(d -> {
                if (syncDetailsStatus == message) { syncDetailsStatus = null; syncDetailsTimes = null; syncDetailsPending = null; }
            }); dialog.show();
        } catch (Exception e) { toast("Import details unavailable"); }
    }
    private String syncTimes(JSONObject status) {
        return "Garmin: " + stamp(status.optString("garmin_sync")) + "\nPlan: " + stamp(status.optString("cloud_sync"));
    }
    private void aboutData() {
        LinearLayout box = dialogBox();
        box.addView(text("Recovery measurements", 17, theme.ink, true));
        box.addView(text("Missing measurements stay blank. Your watch is off at work, so daytime records may be incomplete; gaps do not indicate recovery or inactivity.", 14, theme.muted, false));
        box.addView(text("Your training plan", 17, theme.ink, true));
        box.addView(text("ChatGPT reviews training history and your check-ins, then updates future workouts in the shared database. Plan history is retained in the database. Planned sessions and actual Garmin activities remain separate.", 14, theme.muted, false));
        box.addView(text("Available Garmin data", 17, theme.ink, true));
        box.addView(text("Sleep, overnight HRV and resting heart rate are shown when supplied by Garmin. No training-readiness or recovery-time score is inferred. Original activity records are retained.", 14, theme.muted, false));
        showDetails("About your data", box);
    }
    private EditText field(LinearLayout box, String hint, boolean secret) {
        EditText v = new EditText(this); v.setHint(hint); v.setTextSize(14);
        v.setTextColor(theme.ink); v.setHintTextColor(theme.muted); v.setSingleLine(true); v.setMinHeight(dp(56));
        v.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); box.addView(v); return v;
    }
    private void connections() {
        try {
            LinearLayout box = dialogBox();
            box.addView(text("Keys stay encrypted on this phone. Leave a stored key or secret blank to keep it.", 14, theme.muted, false));
            box.addView(text("Garmin", 13, theme.accent, true));
            EditText garmin = field(box, repo.vault.get("garmin").isEmpty() ? "Personal API key" : "Key stored · blank keeps it", true);
            box.addView(text("Training database", 13, theme.accent, true));
            EditText bridge = field(box, "Apps Script /exec URL", false); bridge.setText(repo.vault.get("bridge"));
            EditText secret = field(box, repo.vault.get("secret").isEmpty() ? "Database bridge secret" : "Secret stored · blank keeps it", true);
            box.addView(text("Get a Garmin key in Garmin Connect web → Account Settings → Personal API Key.", 13, theme.muted, false));
            box.addView(button("Forget saved connections", () -> new AlertDialog.Builder(this)
                    .setMessage("Remove the Garmin key and database connection from this phone?").setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> { repo.vault.clear(); toast("Connections removed"); render(true); }).show()));
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connections").setView(scroll(box))
                    .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String url = bridge.getText().toString().trim(), key = garmin.getText().toString().trim(), token = secret.getText().toString().trim();
                    if (!url.isEmpty()) HttpsTransport.validateBridge(url);
                    if (!url.isEmpty() && (token.isEmpty() ? repo.vault.get("secret") : token).length() < 32)
                        throw new IllegalArgumentException("Enter a bridge secret of at least 32 characters.");
                    if (!key.isEmpty()) repo.vault.set("garmin", key);
                    repo.vault.set("bridge", url); if (!token.isEmpty()) repo.vault.set("secret", token);
                    dialog.dismiss(); render(true); sync(true);
                } catch (Exception e) { toast(e instanceof IllegalArgumentException ? e.getMessage() : "Could not encrypt settings. Please retry."); }
            })); dialog.show();
        } catch (Exception e) { toast("Encrypted settings unavailable. Reconfigure connections."); }
    }
    private void licences() {
        try (java.io.InputStream input = getAssets().open("third_party/FIT-LICENSE.txt")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
            LinearLayout box = dialogBox(); box.addView(text(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), 13, theme.ink, false));
            showDetails("Garmin FIT SDK licence", box);
        } catch (Exception e) { toast("See project third-party notices"); }
    }
    private void sync(boolean garmin) {
        if (isDestroyed() || !syncQueue.request(garmin)) return;
        startSync(garmin);
    }
    private void startSync(boolean garmin) {
        if (!isDestroyed()) updateStatus("Syncing…");
        network.submit(() -> {
            String message;
            try { message = repo.sync(garmin); }
            catch (Exception e) { message = e instanceof java.io.IOException ? e.getMessage() : "Sync incomplete. Your local records are retained; retry when connected."; }
            final String result = message;
            handler.post(() -> {
                Boolean next = syncQueue.complete();
                if (!isDestroyed()) {
                    render(true); updateStatus(result);
                }
                if (next != null) startSync(next);
                else if (isDestroyed()) { network.shutdown(); repo.close(); }
            });
        });
    }
    private String value(JSONObject row, String key, double scale, String unit) {
        return row.isNull(key) || !row.has(key) ? "—" : String.format(Locale.UK, "%.1f", row.optDouble(key) * scale) + unit;
    }
    private String sleep(JSONObject row) {
        if (row.isNull("sleep_hours")) return "—";
        long minutes = Math.round(row.optDouble("sleep_hours") * 60); return minutes / 60 + "h " + minutes % 60 + "m";
    }
    private String duration(JSONObject activity) {
        double seconds = activity.optDouble("duration_s", Double.NaN);
        if (!Double.isFinite(seconds) || seconds < 0) return "—";
        return durationSeconds(seconds);
    }
    private String durationSeconds(double seconds) {
        long minutes = Math.round(seconds / 60); return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + " min";
    }
    private String dayLabel(String iso) {
        try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)); }
        catch (Exception e) { return iso; }
    }
    private String typeLabel(String type) {
        if (type.contains("running")) return "Run"; if (type.contains("walking")) return "Walk";
        return type.isEmpty() ? "Activity" : type.substring(0, 1).toUpperCase(Locale.UK) + type.substring(1).replace('_', ' ');
    }
    private String pace(double value) {
        if (!Double.isFinite(value) || value <= 0) return "—";
        long secs = Math.round(value); return secs / 60 + ":" + String.format(Locale.UK, "%02d", secs % 60) + " /km";
    }
    private String stamp(String utc) {
        try { return Instant.parse(utc).atZone(PlanEngine.ZONE).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)); }
        catch (Exception e) { return "not yet synced"; }
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
