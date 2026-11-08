package au.com.endurancecoach.core;
import org.json.*;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;
public class WorkoutCompletionTest {
 private final long now=Instant.parse("2026-11-08T01:00:00Z").toEpochMilli();
 private JSONObject workout(){return new JSONObject().put("workout_id","run-1").put("duration_min",60);}
 @Test public void shorterAndLongerRunsRemainCompleted(){WorkoutCompletion.Result r=WorkoutCompletion.evaluate(workout(),new JSONObject().put("duration_s",2700),null);assertTrue(r.completed);assertEquals(45,r.actualMinutes,0);assertEquals("Completed · shortened",r.label);assertEquals("Completed · longer",WorkoutCompletion.evaluate(workout(),new JSONObject().put("duration_s",4500),null).label);}
 @Test public void tinyTimingDifferencesUseRoundedMinutes(){assertEquals("Completed",WorkoutCompletion.evaluate(workout(),new JSONObject().put("duration_s",3598),null).label);}
 @Test public void unknownActualIsNotZero(){WorkoutCompletion.Result r=WorkoutCompletion.evaluate(workout(),new JSONObject(),null);assertTrue(r.completed);assertNull(r.actualMinutes);}
 @Test public void manualSkipWinsAndResetUsesActivityAgain(){JSONObject done=WorkoutCompletion.change("run-1","completed",40d,"",null,now),skip=WorkoutCompletion.change("run-1","skipped",null,"",done,now),reset=WorkoutCompletion.change("run-1","scheduled",null,"",skip,now);assertTrue(WorkoutCompletion.evaluate(workout(),new JSONObject(),skip).skipped);assertEquals("Completed · shortened",WorkoutCompletion.evaluate(workout(),new JSONObject().put("duration_s",3000),reset).label);assertEquals("scheduled",WorkoutCompletion.current(new JSONArray().put(reset).put(done)).getJSONObject(0).getString("status"));assertTrue(Instant.parse(reset.getString("updated_at")).isAfter(Instant.parse(done.getString("updated_at"))));}
 @Test public void invalidManualStatusIsRejected(){assertThrows(IllegalArgumentException.class,()->WorkoutCompletion.change("run-1","skipped",20d,"",null,now));assertThrows(IllegalArgumentException.class,()->WorkoutCompletion.change("run-1","completed",Double.NaN,"",null,now));}
}
