package au.com.endurancecoach.core;
import java.util.*;
/** A successful plan refresh cannot erase a warning from the separate Garmin import. */
public final class SyncStatus {
 private SyncStatus(){}
 public static String message(String garminMessage,List<String> currentWarnings){
  LinkedHashSet<String> parts=new LinkedHashSet<>();if(garminMessage!=null&&!garminMessage.isEmpty()&&!garminMessage.equals("Up to date"))parts.add(garminMessage);parts.addAll(currentWarnings);
  return parts.isEmpty()?"Up to date":String.join(" · ",parts);
 }
}
