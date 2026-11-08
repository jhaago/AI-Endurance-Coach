package au.com.endurancecoach.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class SyncQueueTest {
 @Test public void idleRequestStartsAndCompletes(){SyncQueue q=new SyncQueue();assertTrue(q.request(true));assertTrue(q.isRunning());assertNull(q.complete());assertFalse(q.isRunning());}
 @Test public void appOpenBehindCloudRefreshGetsGarminFollowUp(){SyncQueue q=new SyncQueue();assertTrue(q.request(false));assertFalse(q.request(true));assertEquals(Boolean.TRUE,q.complete());assertTrue(q.isRunning());assertNull(q.complete());}
 @Test public void busyRequestsCoalesceWithGarminPriority(){SyncQueue q=new SyncQueue();q.request(false);q.request(false);q.request(true);q.request(false);q.request(true);assertEquals(Boolean.TRUE,q.complete());assertNull(q.complete());}
 @Test public void notesSavedDuringGarminGetCloudFollowUp(){SyncQueue q=new SyncQueue();q.request(true);assertFalse(q.request(false));assertEquals(Boolean.FALSE,q.complete());assertNull(q.complete());}
 @Test public void newOpenAfterCompletionStartsAgain(){SyncQueue q=new SyncQueue();q.request(true);q.complete();assertTrue(q.request(true));assertNull(q.complete());}
}
