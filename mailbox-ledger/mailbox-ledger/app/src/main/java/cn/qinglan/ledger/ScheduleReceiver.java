package cn.qinglan.ledger;
import android.content.*;
public class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){final PendingResult result=goAsync();final Context app=context.getApplicationContext();new Thread(()->{try{ScheduleEngine.arm(app);}finally{result.finish();}}).start();}
}
