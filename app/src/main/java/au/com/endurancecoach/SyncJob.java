package au.com.endurancecoach;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

public final class SyncJob extends JobService {
 private final ExecutorService executor=Executors.newSingleThreadExecutor();private Future<?> running;
 static void schedule(Context context){JobScheduler s=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(s!=null)s.schedule(new JobInfo.Builder(2701,new ComponentName(context,SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(6*60*60*1000L).build());}
 @Override public boolean onStartJob(JobParameters params){running=executor.submit(()->{boolean retry=false;try(CoachRepository repo=new CoachRepository(this)){repo.sync(true);}catch(Exception e){retry=true;}jobFinished(params,retry);});return true;}
 @Override public boolean onStopJob(JobParameters params){if(running!=null)running.cancel(true);return true;}
 @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
