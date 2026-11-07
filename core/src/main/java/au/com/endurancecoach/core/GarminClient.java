package au.com.endurancecoach.core;

import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Only Garmin's documented, personal read API. No password or private Connect endpoints. */
public final class GarminClient {
    public static final String HOST="https://personal.connectapi.garmin.com";
    public static final String TOKEN_PATH="/personalapitoken-service/personal-api/personal-api-key/token";
    private final Transport transport; private final String key; private final LongSupplier clock;
    private String token=""; private long expiresAt;
    public GarminClient(Transport transport,String key,LongSupplier clock) {
        if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("Enter your Garmin Personal API key in Settings.");
        this.transport=transport; this.key=key.trim(); this.clock=clock;
    }
    private void authenticate() throws IOException {
        Transport.Response r=transport.request("POST",HOST+TOKEN_PATH,Collections.singletonMap("Authorization","ApiKey "+key),null);
        r.requireSuccess();
        String value=r.text().trim();
        try {
            String[] parts=value.split("\\.");
            expiresAt=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8)).getLong("exp");
            if(expiresAt<=clock.getAsLong()) throw new IllegalArgumentException();
            token=value;
        } catch(RuntimeException e) { throw new IOException("Garmin returned an invalid access token."); }
    }
    public synchronized byte[] get(String path) throws IOException {
        if(!path.startsWith("/") || path.startsWith("//") || path.contains(":") || path.contains("..")) throw new IllegalArgumentException("Invalid Garmin path");
        if(expiresAt-clock.getAsLong()<3600) authenticate();
        for(int attempt=0;attempt<2;attempt++) {
            Map<String,String> h=new HashMap<>(); h.put("Authorization","Bearer "+token); h.put("Accept","*/*");
            Transport.Response r=transport.request("GET",HOST+path,h,null);
            if(r.code==401 && attempt==0) { authenticate(); continue; }
            r.requireSuccess(); return r.body;
        }
        throw new IOException("Garmin authorization failed.");
    }
    public Object json(String path) throws IOException {
        String text=new String(get(path),StandardCharsets.UTF_8);
        try { return text.trim().isEmpty()?JSONObject.NULL:new JSONTokener(text).nextValue(); }
        catch(RuntimeException e){throw new IOException("Garmin returned invalid JSON.");}
    }
    public JSONArray activities(String startDate,String endDate,int pageSize) throws IOException {
        LocalDate.parse(startDate); LocalDate.parse(endDate);
        if(pageSize<1 || pageSize>100) throw new IllegalArgumentException("Invalid page size");
        JSONArray all=new JSONArray(); Set<String> seen=new HashSet<>();
        for(int start=0;start<10000;start+=pageSize) {
            Object value=json("/activitylist-service/activities/search/activities?start="+start+"&limit="+pageSize+"&startDate="+startDate+"&endDate="+endDate);
            if(!(value instanceof JSONArray)) throw new IOException("Unexpected Garmin activity list.");
            JSONArray page=(JSONArray)value; int added=0;
            for(int i=0;i<page.length();i++) { JSONObject a=page.getJSONObject(i); String id=a.optString("activityId"); if(seen.add(id)){all.put(a);added++;} }
            if(page.length()<pageSize) return all;
            if(added==0) throw new IOException("Garmin activity pagination repeated. Retry later.");
        }
        throw new IOException("Activity import exceeded 10,000 records. Choose a shorter date range.");
    }
}
