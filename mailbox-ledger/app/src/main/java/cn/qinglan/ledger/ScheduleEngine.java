package cn.qinglan.ledger;
import android.app.job.*;
import android.content.*;
import java.time.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

final class ScheduleEngine {
    static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    static final int PERIODIC=7021, NEXT=7022;
    interface RateProvider {JSONObject get(String from,String to,String date)throws Exception;}
    static void validateRule(JSONObject r,JSONArray books)throws Exception {
        r.getBoolean("enabled");if(r.getString("generation").isEmpty())throw new Exception("规则版本无效");
        if(!r.getString("id").matches("[a-zA-Z0-9_-]{1,80}"))throw new Exception("规则编号无效");
        if(LedgerStore.find(books,r.getString("bookId"))==null)throw new Exception("账本不存在");
        if(!r.getString("name").matches("(?s).{1,40}"))throw new Exception("请填写规则名称");
        if(!Arrays.asList("income","expense").contains(r.getString("type")))throw new Exception("收支类型无效");
        if(!r.getString("currency").matches("[A-Z]{3}"))throw new Exception("币种无效");
        String a=r.getString("amount");if(!a.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")||new BigDecimal(a).signum()<=0)throw new Exception("金额无效");
        LocalDate start=LocalDate.parse(r.getString("startDate")),next=LocalDate.parse(r.getString("nextDate"));
        if(start.getYear()<1900||start.getYear()>2199||next.isBefore(start))throw new Exception("日期无效");
        LocalTime.parse(r.getString("time"));
        ScheduleDates.first(r.getString("frequency"),r.getInt("day"),start);
        if(!ScheduleDates.first(r.getString("frequency"),r.getInt("day"),next).equals(next))throw new Exception("下次日期与周期不符");
        if(r.getString("note").length()>2000||r.getString("category").length()>30)throw new Exception("备注或分类过长");
        if(!Arrays.asList("auto","fixed").contains(r.getString("rateMode")))throw new Exception("汇率方式无效");
        if("fixed".equals(r.getString("rateMode"))){String rate=r.getString("fixedRate");if(!rate.matches("[0-9]{1,12}(\\.[0-9]{1,18})?")||new BigDecimal(rate).signum()<=0)throw new Exception("固定汇率无效");}
    }
    static JSONObject entry(JSONObject rule,JSONObject book,String date,RateProvider provider)throws Exception {
        String from=rule.getString("currency"),to=book.getString("currency");JSONObject q;
        if(from.equals(to))q=new JSONObject().put("rate","1").put("updated",date).put("source","同币种");
        else if(rule.optString("rateMode").equals("fixed"))q=new JSONObject().put("rate",rule.getString("fixedRate")).put("updated",date).put("source","手动汇率");
        else {q=provider.get(from,to,date);if(!q.getString("updated").startsWith(date))throw new Exception("返回汇率日期不符，请重试或改用固定汇率");}
        BigDecimal amount=new BigDecimal(rule.getString("amount")),rate=new BigDecimal(q.getString("rate"));
        if(rate.signum()<=0)throw new Exception("汇率无效");
        BigDecimal converted=amount.multiply(rate).setScale(2,RoundingMode.HALF_UP);
        if(converted.compareTo(new BigDecimal("999999999999.99"))>0)throw new Exception("换算金额过大");
        return new JSONObject().put("id","auto_"+rule.getString("id")+"_"+date).put("bookId",rule.getString("bookId")).put("type",rule.getString("type")).put("date",date).put("currency",from).put("amount",amount.setScale(2).toPlainString()).put("converted",converted.toPlainString()).put("rate",q.getString("rate")).put("rateUpdated",q.getString("updated")).put("rateSource",q.getString("source")).put("category",rule.getString("category")).put("note",rule.getString("note")).put("created",Instant.now().toString()).put("modified",Instant.now().toString()).put("scheduleId",rule.getString("id")).put("scheduleName",rule.getString("name"));
    }
    // Called inside the store lock; entry and recurrence cursor are persisted together.
    static int commit(JSONObject d,String id,String generation,String date,JSONObject entry,String failure,long now)throws Exception {
        JSONObject r=LedgerStore.find(d.getJSONArray("schedules"),id);
        if(r==null||!r.optBoolean("enabled")||!generation.equals(r.optString("generation"))||!date.equals(r.optString("nextDate")))return -1;
        int result=0;
        if(entry!=null){
            JSONArray all=d.getJSONArray("entries");if(LedgerStore.find(all,entry.getString("id"))==null){all.put(entry);result=1;}
            r.put("lastDate",date).put("nextDate",ScheduleDates.after(r.getString("frequency"),r.getInt("day"),LocalDate.parse(date)).toString()).put("lastError","").put("retryAfter",0);
        }else{r.put("lastError",failure).put("retryAfter",now+30*60*1000L);result=2;}
        d.put("revision",d.optLong("revision",0)+1);return result;
    }
    static void arm(Context context){
        Context c=context.getApplicationContext();
        try {
            JSONObject d=LedgerStore.read(c);JSONArray rules=d==null?new JSONArray():d.getJSONArray("schedules");
            long earliest=Long.MAX_VALUE,now=System.currentTimeMillis();
            for(int i=0;i<rules.length();i++){JSONObject r=rules.getJSONObject(i);if(!r.optBoolean("enabled"))continue;long due=Math.max(ScheduleDates.dueMillis(r.getString("nextDate"),r.getString("time")),r.optLong("retryAfter",0));earliest=Math.min(earliest,due);}
            JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(earliest==Long.MAX_VALUE){scheduler.cancel(PERIODIC);scheduler.cancel(NEXT);return;}
            ComponentName service=new ComponentName(c,ScheduleJob.class);
            if(scheduler.getPendingJob(PERIODIC)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC,service).setPeriodic(15*60*1000L).setPersisted(true).build());
            scheduler.schedule(new JobInfo.Builder(NEXT,service).setMinimumLatency(Math.max(1000,earliest-now)).setPersisted(true).build());
        }catch(Exception ignored){}
    }
    static JSONObject run(Context context,AtomicBoolean stopped,boolean force){
        JSONObject report=new JSONObject();if(!RUNNING.compareAndSet(false,true))return report;
        int added=0,failed=0,processed=0;Context c=context.getApplicationContext();long started=System.currentTimeMillis();
        try {
            JSONObject initial=LedgerStore.read(c);if(initial==null)return report;
            JSONArray rules=initial.getJSONArray("schedules");
            FxClient fx=new FxClient(c);
            for(int i=0;i<rules.length()&&processed<40&&!stopped.get();i++){
                String ruleId=rules.getJSONObject(i).getString("id");
                while(processed<40&&!stopped.get()&&System.currentTimeMillis()-started<90000){
                    JSONObject snapshot,book;String date,generation;
                    synchronized(LedgerStore.LOCK){
                        JSONObject d=LedgerStore.read(c),r=LedgerStore.find(d.getJSONArray("schedules"),ruleId);
                        if(r==null||!r.optBoolean("enabled"))break;
                        if(!force&&r.optLong("retryAfter",0)>System.currentTimeMillis())break;
                        if(ScheduleDates.dueMillis(r.getString("nextDate"),r.getString("time"))>System.currentTimeMillis())break;
                        snapshot=new JSONObject(r.toString());date=r.getString("nextDate");generation=r.getString("generation");
                        book=LedgerStore.find(d.getJSONArray("books"),r.getString("bookId"));if(book==null)break;
                    }
                    JSONObject newEntry=null;String failure="";
                    try{validateRule(snapshot,initial.getJSONArray("books"));newEntry=entry(snapshot,book,date,(from,to,day)->fx.quote(new JSONObject().put("from",from).put("to",to).put("date",day).put("force",false)));}
                    catch(Exception e){failure=e instanceof java.io.IOException?"网络不可用，等待重试":e.getMessage();if(failure==null)failure="自动记账失败";}
                    processed++;
                    if(stopped.get())break;
                    synchronized(LedgerStore.LOCK){
                        JSONObject d=LedgerStore.read(c);
                        int result=commit(d,ruleId,generation,date,newEntry,failure,System.currentTimeMillis());
                        if(result<0)break;
                        LedgerStore.write(c,d);
                        if(result==1)added++;if(result==2)failed++;
                    }
                    if(newEntry==null)break;
                }
            }
            report.put("added",added).put("failed",failed);
        }catch(Exception e){try{report.put("error",e.getMessage()==null?"自动记账失败":e.getMessage());}catch(Exception ignored){}}
        finally{RUNNING.set(false);}
        return report;
    }
}
