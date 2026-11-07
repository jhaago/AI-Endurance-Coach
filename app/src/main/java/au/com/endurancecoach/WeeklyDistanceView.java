package au.com.endurancecoach;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import au.com.endurancecoach.core.WeeklyProgress;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntConsumer;

/** Native chart of actual weekly kilometres. Unknown weeks break the line. */
final class WeeklyDistanceView extends View {
    private final List<WeeklyProgress.Week> weeks;
    private final CoachTheme theme;
    private final IntConsumer onSelection;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density, scaledDensity;
    private int selected;
    private float downX, downY;

    WeeklyDistanceView(Context context, List<WeeklyProgress.Week> weeks, int selected, CoachTheme theme, IntConsumer onSelection) {
        super(context); this.weeks = weeks; this.selected = selected; this.theme = theme; this.onSelection = onSelection;
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        setFocusable(true); setClickable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); describe();
    }
    private float left() { return 5 * density; }
    private float right() {
        paint.setTextSize(11 * scaledDensity);
        return Math.max(left() + 1, getWidth() - paint.measureText(axis(maximum()) + " km") - 10 * density);
    }
    private float x(int index) { return left() + (right() - left()) * index / Math.max(1, weeks.size() - 1); }
    private double maximum() {
        double largest = 0; for (WeeklyProgress.Week w : weeks) if (w.km != null) largest = Math.max(largest, w.km);
        double step = largest <= 20 ? 5 : largest <= 100 ? 10 : largest <= 250 ? 50 : 100;
        return Math.max(10, Math.ceil(largest / step) * step);
    }
    private String axis(double value) { return String.format(Locale.UK, "%.0f", value); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float top = 16 * density, bottom = getHeight() - 30 * density;
        float height = Math.max(1, bottom - top), right = right(); double max = maximum();
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density); paint.setColor(theme.outline);
        for (int i = 0; i < 3; i++) canvas.drawLine(left(), top + height * i / 2, right, top + height * i / 2, paint);
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(11 * scaledDensity); paint.setColor(theme.muted);
        for (int i = 0; i < 3; i++) canvas.drawText(axis(max * (2 - i) / 2) + " km", right + 8 * density, top + height * i / 2 + 4 * density, paint);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
        for (int i : new int[]{0, weeks.size() / 2, weeks.size() - 1}) {
            String label = weeks.get(i).start.format(format); float width = paint.measureText(label);
            float labelX = Math.max(0, Math.min(right - width, x(i) - width / 2));
            canvas.drawText(label, labelX, getHeight() - 4 * density, paint);
        }
        paint.setColor(theme.outline); paint.setStrokeWidth(density);
        canvas.drawLine(x(selected), top, x(selected), bottom, paint);
        List<PointF> segment = new ArrayList<>(); Path line = new Path(); boolean continuing = false, any = false;
        for (int i = 0; i < weeks.size(); i++) {
            Double km = weeks.get(i).km;
            if (km == null) { area(canvas, segment, top, bottom); segment.clear(); continuing = false; continue; }
            any = true; float px = x(i), py = bottom - (float) (km / max) * height;
            segment.add(new PointF(px, py)); if (continuing) line.lineTo(px, py); else line.moveTo(px, py); continuing = true;
        }
        area(canvas, segment, top, bottom);
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.2f * density);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setColor(theme.accent); canvas.drawPath(line, paint);
        for (int i = 0; i < weeks.size(); i++) if (weeks.get(i).km != null) {
            float px = x(i), py = bottom - (float) (weeks.get(i).km / max) * height;
            paint.setStyle(Paint.Style.FILL); paint.setColor(i == selected ? theme.accent : theme.surface);
            canvas.drawCircle(px, py, (i == selected ? 5 : 3) * density, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(theme.accent); paint.setStrokeWidth(1.8f * density);
            canvas.drawCircle(px, py, (i == selected ? 5 : 3) * density, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (!any) {
            paint.setColor(theme.muted); paint.setTextSize(12 * scaledDensity);
            canvas.drawText("No running history imported", left() + 6 * density, top + height / 2, paint);
        }
    }
    private void area(Canvas canvas, List<PointF> points, float top, float bottom) {
        if (points.size() < 2) return;
        Path p = new Path(); p.moveTo(points.get(0).x, bottom);
        for (PointF point : points) p.lineTo(point.x, point.y);
        p.lineTo(points.get(points.size() - 1).x, bottom); p.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, bottom, (theme.accent & 0x00ffffff) | 0x44000000,
                theme.accent & 0x00ffffff, Shader.TileMode.CLAMP));
        canvas.drawPath(p, paint); paint.setShader(null);
    }
    private boolean select(int index) {
        if (index < 0 || index >= weeks.size() || index == selected) return false;
        selected = index; describe(); onSelection.accept(index); invalidate(); return true;
    }
    private void describe() {
        WeeklyProgress.Week w = weeks.get(selected);
        setContentDescription("Weekly running distance. Week of " + w.start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))
                + (w.km == null ? ": not imported or distance unavailable" : String.format(Locale.UK, ": %.2f kilometres", w.km))
                + (w.covered ? "" : ". Partial import") + ". Earlier and later week actions available.");
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(event.getX() - downX) <= slop && Math.abs(event.getY() - downY) <= slop) {
                int i = Math.round((event.getX() - left()) / Math.max(1, right() - left()) * (weeks.size() - 1));
                select(Math.max(0, Math.min(weeks.size() - 1, i))); performClick();
            }
            return true;
        }
        return event.getActionMasked() != MotionEvent.ACTION_CANCEL;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int key, android.view.KeyEvent event) {
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) return select(selected - 1);
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT) return select(selected + 1);
        return super.onKeyDown(key, event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setScrollable(true);
        if (selected > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (selected < weeks.size() - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) return select(selected - 1);
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) return select(selected + 1);
        return super.performAccessibilityAction(action, args);
    }
}
