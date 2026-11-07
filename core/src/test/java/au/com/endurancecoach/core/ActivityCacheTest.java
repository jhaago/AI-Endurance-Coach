package au.com.endurancecoach.core;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ActivityCacheTest {
 private JSONObject list(){return new JSONObject().put("activityId",1).put("summary",new JSONObject().put("distance",5000).put("duration",1800));}
 private JSONObject saved(){return new JSONObject().put("list",list()).put("detail",new JSONObject().put("summaryDTO",new JSONObject().put("averageHR",140)));}
 @Test public void unchangedFreshDetailsAreReusableRegardlessOfObjectKeyOrder(){JSONObject reordered=new JSONObject("{\"summary\":{\"duration\":1800,\"distance\":5000},\"activityId\":1}");assertTrue(ActivityCache.reusable(reordered,saved(),1000,1100));}
 @Test public void summaryEditsRefetchImmediately(){assertFalse(ActivityCache.reusable(list().put("activityName","Edited"),saved(),1000,1100));}
 @Test public void missingAndEmptyDetailsNeverBecomeCachedSuccess(){assertFalse(ActivityCache.reusable(list(),new JSONObject(),1000,1100));assertFalse(ActivityCache.reusable(list(),saved().put("detail",new JSONObject()),1000,1100));}
 @Test public void expiredMissingAndFutureCacheTimesRefetch(){for(long stamp:new long[]{0,1000,90000})assertFalse(ActivityCache.reusable(list(),saved(),stamp,87400));}
}
