package au.com.endurancecoach.core;

/** Main-thread sync requests coalesce into one follow-up, with Garmin taking priority. */
public final class SyncQueue {
 private boolean running;
 private Boolean pending;
 public boolean request(boolean garmin){
  if(!running){running=true;return true;}
  pending=garmin||Boolean.TRUE.equals(pending);return false;
 }
 public Boolean complete(){Boolean next=pending;pending=null;running=next!=null;return next;}
 public boolean isRunning(){return running;}
}
