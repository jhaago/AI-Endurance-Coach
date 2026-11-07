package au.com.endurancecoach.core;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BridgeClient {
 private final Transport transport;private final String url,secret;
 public BridgeClient(Transport transport,String url,String secret){HttpsTransport.validateBridge(url);if(secret.length()<32)throw new IllegalArgumentException("Bridge secret must have at least 32 characters.");this.transport=transport;this.url=url;this.secret=secret;}
 public JSONObject call(JSONObject request) throws IOException {
  try{
   JSONObject copy=new JSONObject(request.toString()).put("secret",secret);
   Map<String,String> headers=new HashMap<>();headers.put("Content-Type","application/json; charset=utf-8");headers.put("Accept","application/json");
   Transport.Response r=transport.request("POST",url,headers,copy.toString().getBytes(StandardCharsets.UTF_8));r.requireSuccess();JSONObject result=new JSONObject(r.text());
   if(!result.optBoolean("ok"))throw new IOException("Training database: "+result.optString("error","sync failed"));
   JSONObject snapshot=result.getJSONObject("snapshot");PlanEngine.current(snapshot.getJSONArray("plan"));return result;
  }catch(JSONException e){throw new IOException("Training database returned an invalid response. Check the deployment.");}
 }
}
