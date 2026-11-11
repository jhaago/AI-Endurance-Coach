package au.com.endurancecoach.core;
import java.io.*;import java.net.*;import java.util.*;

/** No Garmin/bridge credentials; no redirects; bounded public mapping requests. */
public final class PublicMapTransport implements Transport {
 private static final Map<String,Long> LAST=new HashMap<>();
 public static final String CLIENT="EnduranceCoach/0.8 (https://github.com/jhaago/AI-Endurance-Coach)";
 public static void validateEndpoint(String value){try{URI u=new URI(value);if(!"https".equals(u.getScheme())||u.getHost()==null||!u.getHost().contains(".")||u.getUserInfo()!=null||u.getPort()!=-1||u.getQuery()!=null||u.getFragment()!=null)throw new Exception();}catch(Exception e){throw new IllegalArgumentException("Use an HTTPS service address without a query, port or credentials");}}
 @Override public Response request(String method,String url,Map<String,String> headers,byte[] body)throws IOException{
  URL target=new URL(url);if(!"https".equals(target.getProtocol())||target.getUserInfo()!=null||target.getPort()!=-1)throw new IOException("Invalid map service address");
  if(!target.getHost().equals("tile.openstreetmap.org"))pace(target.getHost());
  HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("User-Agent",CLIENT);c.setRequestProperty("X-Client-Id","au.com.endurancecoach");
  for(Map.Entry<String,String> h:headers.entrySet())if(Arrays.asList("Accept","Content-Type","If-None-Match","If-Modified-Since").contains(h.getKey()))c.setRequestProperty(h.getKey(),h.getValue());
  try{if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
   int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(stream!=null)try(InputStream in=stream){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8000000)throw new IOException("Map response too large");out.write(buffer,0,n);}}return new Response(code,out.toByteArray());
  }finally{c.disconnect();}
 }
 private static synchronized void pace(String host)throws IOException{long elapsed=System.nanoTime()/1000000,delay=1100-(elapsed-LAST.getOrDefault(host,0L));if(delay>0)try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Map request cancelled");}LAST.put(host,System.nanoTime()/1000000);}
}
