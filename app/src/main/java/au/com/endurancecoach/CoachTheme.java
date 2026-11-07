package au.com.endurancecoach;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Appearance preferences contain no credentials and never touch the connection vault. */
final class CoachTheme {
    static final String[] MODES = {"dark", "light", "system"};
    static final String[] LABELS = {"Dark", "Light", "Follow phone"};
    final boolean dark;
    final int background, surface, raised, ink, muted, accent, accentSurface, outline, onAccent;

    private CoachTheme(boolean dark) {
        this.dark = dark;
        background = color(dark ? "#101617" : "#F3F6F4");
        surface = color(dark ? "#1A2325" : "#FFFFFF");
        raised = color(dark ? "#253133" : "#E8EFEC");
        ink = color(dark ? "#F1F6F3" : "#162C2A");
        muted = color(dark ? "#ACBCB8" : "#566B65");
        accent = color(dark ? "#82DBC4" : "#116D5C");
        accentSurface = color(dark ? "#203E36" : "#DAF1E7");
        outline = color(dark ? "#344543" : "#D5E2DB");
        onAccent = color(dark ? "#102E25" : "#FFFFFF");
    }

    static String preference(Context context) {
        String mode = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                .getString("mode", "dark");
        for (String option : MODES) if (option.equals(mode)) return mode;
        return "dark";
    }

    static void save(Context context, int index) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putString("mode", MODES[index]).apply();
    }

    static CoachTheme apply(Activity activity) {
        String mode = preference(activity);
        boolean systemDark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = mode.equals("dark") || (mode.equals("system") && systemDark);
        activity.setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        return new CoachTheme(dark);
    }

    private static int color(String hex) { return Color.parseColor(hex); }
}
