package au.com.endurancecoach.core;
import java.time.*;
public final class SyncWindow {
 private SyncWindow(){}
 public static LocalDate start(LocalDate today,String lastSync,int overlapDays,int initialDays){if(lastSync==null||lastSync.isEmpty())return today.minusDays(initialDays-1L);LocalDate previous=Instant.parse(lastSync).atZone(PlanEngine.ZONE).toLocalDate();if(previous.isAfter(today))previous=today;return previous.minusDays(overlapDays);}
}
