package cn.qinglan.ledger;
import android.app.job.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class ScheduleJob extends JobService {
    private final java.util.concurrent.ConcurrentHashMap<Integer,AtomicBoolean> tokens=new java.util.concurrent.ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters params){
        final AtomicBoolean token=new AtomicBoolean(false);tokens.put(params.getJobId(),token);
        ScheduleEngine.EXECUTOR.execute(()->{ScheduleEngine.run(this,token,false);if(!token.get()){tokens.remove(params.getJobId(),token);jobFinished(params,false);ScheduleEngine.arm(this);}});return true;
    }
    @Override public boolean onStopJob(JobParameters params){AtomicBoolean token=tokens.remove(params.getJobId());if(token!=null)token.set(true);return true;}
}
