package au.com.endurancecoach;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Measured points only: missing dates break the line, and empty series stay empty. */
final class TrendView extends View {
    private final List<Double> points;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, muted, grid;
    TrendView(Context context, List<Double> points, int accent, int muted, int grid) {
        super(context); this.points = points; this.accent = accent; this.muted = muted; this.grid = grid;
        int measured = 0; Double latest = null;
        for (Double v : points) if (v != null && Double.isFinite(v)) { measured++; latest = v; }
        setContentDescription(measured == 0 ? "No measurements available"
                : measured + " measured points. Latest measured value " + String.format(Locale.UK, "%.1f", latest)
                + ". Missing values shown as gaps.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        paint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Double v : points) if (v != null && Double.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); }
        if (!Double.isFinite(min)) {
            paint.setColor(muted); canvas.drawText("No measurements", 0, getHeight() / 2f, paint); return;
        }
        if (max - min < 1) { min -= .5; max += .5; }
        String low = String.format(Locale.UK, "%.1f", min), high = String.format(Locale.UK, "%.1f", max);
        float left = Math.max(paint.measureText(low), paint.measureText(high)) + 12 * density;
        float top = 14 * density, bottom = getHeight() - 12 * density;
        float width = Math.max(1, getWidth() - left - 6 * density), height = Math.max(1, bottom - top);
        paint.setColor(grid); paint.setStrokeWidth(density);
        for (int i = 0; i < 3; i++) canvas.drawLine(left, top + height * i / 2, left + width, top + height * i / 2, paint);
        paint.setColor(muted); canvas.drawText(high, 0, top + 4 * density, paint); canvas.drawText(low, 0, bottom + 4 * density, paint);
        paint.setColor(accent); paint.setStrokeWidth(2 * density); paint.setStrokeCap(Paint.Cap.ROUND);
        Float lastX = null, lastY = null;
        for (int i = 0; i < points.size(); i++) {
            Double v = points.get(i);
            if (v == null || !Double.isFinite(v)) { lastX = null; lastY = null; continue; }
            float x = left + width * i / Math.max(1, points.size() - 1);
            float y = bottom - (float) ((v - min) / (max - min)) * height;
            if (lastX != null) canvas.drawLine(lastX, lastY, x, y, paint);
            canvas.drawCircle(x, y, 2.5f * density, paint); lastX = x; lastY = y;
        }
    }
}
