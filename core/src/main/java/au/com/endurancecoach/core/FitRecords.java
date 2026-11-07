package au.com.endurancecoach.core;

import com.garmin.fit.*;
import org.json.*;
import java.io.*;
import java.util.zip.*;

/** Decode Garmin's original activity ZIP with the official FIT SDK. ZIP entries never touch the filesystem. */
public final class FitRecords {
 private FitRecords() {}
 public static JSONArray decodeZip(byte[] bytes,boolean running) throws IOException {
  JSONArray records=new JSONArray();boolean found=false;long expanded=0;
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;
   while((entry=zip.getNextEntry())!=null){if(entry.isDirectory()||!entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".fit"))continue;
    found=true;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
    while((count=zip.read(buffer))!=-1){expanded+=count;if(expanded>64*1024*1024)throw new IOException("Expanded FIT exceeds limit.");out.write(buffer,0,count);}
    byte[] fit=out.toByteArray();Decode decode=new Decode();MesgBroadcaster broadcaster=new MesgBroadcaster(decode);
    broadcaster.addListener((RecordMesgListener)r->{
     if(records.length()>=100000)throw new FitRuntimeException("FIT record limit exceeded");
     JSONObject sample=new JSONObject();
     if(r.getTimestamp()!=null)sample.put("timestamp",r.getTimestamp().getDate().toInstant().toString());
     if(r.getHeartRate()!=null)sample.put("hr",r.getHeartRate());
     Float speed=r.getEnhancedSpeed()!=null?r.getEnhancedSpeed():r.getSpeed();
     if(speed!=null&&speed>0)sample.put("pace_s_km",1000d/speed);
     Float elevation=r.getEnhancedAltitude()!=null?r.getEnhancedAltitude():r.getAltitude();
     if(elevation!=null)sample.put("elevation_m",elevation);
     if(r.getCadence()!=null){double cadence=r.getCadence();if(r.getFractionalCadence()!=null)cadence+=r.getFractionalCadence();sample.put(running?"cadence_spm":"cadence_rpm",cadence*(running?2:1));}
     records.put(sample);
    });
    try{if(!decode.checkFileIntegrity(new ByteArrayInputStream(fit)))throw new IOException("Invalid FIT integrity.");if(!decode.read(new ByteArrayInputStream(fit),broadcaster,broadcaster))throw new IOException("FIT decoding incomplete.");}
    catch(RuntimeException e){throw new IOException("Invalid FIT activity file.");}
   }
  }
  if(!found)throw new IOException("Original activity ZIP contains no FIT file.");return records;
 }
}
