package au.com.endurancecoach.core;

import java.io.*;
import java.net.*;
import java.util.*;

/** Explicit redirect handling prevents Garmin credentials or bridge secrets following a redirect. */
public final class HttpsTransport implements Transport {
 public static void validateBridge(String value) {
  try{URI uri=new URI(value);if(!"https".equals(uri.getScheme())||!"script.google.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().matches("/macros/s/[a-zA-Z0-9_\\-]+/exec"))throw new Exception();}
  catch(Exception e){throw new IllegalArgumentException("Use the deployed https://script.google.com/macros/s/…/exec address.");}
 }
 @Override public Response request(String method,String url,Map<String,String> headers,byte[] body) throws IOException {
  URL target=new URL(url);String host=target.getHost();boolean bridge=host.equals("script.google.com");
  if(!target.getProtocol().equals("https")||(!host.equals("personal.connectapi.garmin.com")&&!bridge))throw new IOException("Untrusted service address.");
  if(bridge)validateBridge(url);
  HttpURLConnection c=open(target,method,headers,body);
  try{
   int status=c.getResponseCode();
   if(bridge&&(status==302||status==303)){
    String location=c.getHeaderField("Location");c.disconnect();URL next=new URL(location);
    if(!next.getProtocol().equals("https")||!next.getHost().equals("script.googleusercontent.com")||next.getUserInfo()!=null||(next.getPort()!=-1&&next.getPort()!=443))throw new IOException("Untrusted bridge redirect.");
    c=open(next,"GET",Collections.singletonMap("Accept","application/json"),null);status=c.getResponseCode();
   }
   InputStream stream=status>=200&&status<300?c.getInputStream():c.getErrorStream();
   if(stream==null)return new Response(status,new byte[0]);
   try(InputStream input=stream){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>32*1024*1024)throw new IOException("Service response exceeds 32 MB limit.");out.write(buffer,0,count);}return new Response(status,out.toByteArray());}
  }finally{c.disconnect();}
 }
 private HttpURLConnection open(URL url,String method,Map<String,String> headers,byte[] body) throws IOException {
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
  if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream output=c.getOutputStream()){output.write(body);}}
  return c;
 }
}
