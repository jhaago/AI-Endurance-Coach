package au.com.endurancecoach.core;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class SyncWindowTest {
 @Test public void catchesUpFourteenDayGapWithOverlap(){assertEquals(LocalDate.parse("2026-10-22"),SyncWindow.start(LocalDate.parse("2026-11-07"),"2026-10-24T00:00:00Z",2,7));}
 @Test public void initialWindowAndFutureClockRemainBounded(){assertEquals(LocalDate.parse("2026-11-01"),SyncWindow.start(LocalDate.parse("2026-11-07"),"",2,7));assertEquals(LocalDate.parse("2026-11-05"),SyncWindow.start(LocalDate.parse("2026-11-07"),"2027-01-01T00:00:00Z",2,7));}
}
