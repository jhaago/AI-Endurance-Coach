package au.com.endurancecoach.core;

import org.json.*;
import java.util.*;

/** Cache only successful, unchanged activity details; all expiry times are in epoch seconds. */
public final class ActivityCache {
    private ActivityCache() {}
    public static boolean reusable(JSONObject currentList, JSONObject original, long fetchedAt, long now) {
        if (fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= 86400 || original == null) return false;
        JSONObject detail = original.optJSONObject("detail"), previousList = original.optJSONObject("list");
        return detail != null && detail.length() > 0 && previousList != null
                && canonical(currentList).equals(canonical(previousList));
    }
    private static String canonical(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value; List<String> keys = new ArrayList<>();
            for (Iterator<String> i = object.keys(); i.hasNext();) keys.add(i.next()); Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) result.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key))).append(',');
            return result.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value; StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) result.append(canonical(array.opt(i))).append(',');
            return result.append(']').toString();
        }
        if (value == null || value == JSONObject.NULL) return "null";
        return value instanceof Number || value instanceof Boolean ? value.toString() : JSONObject.quote(value.toString());
    }
}
