package cn.qinglan.ledger;
import org.json.*;
import java.time.*;
public class ScheduleTest {
 static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args)throws Exception{
  JSONObject book=new JSONObject().put("id","daily").put("currency","CNY");
  JSONObject r=new JSONObject("{\"id\":\"rent\",\"generation\":\"g1\",\"enabled\":true,\"name\":\"房租\",\"bookId\":\"daily\",\"type\":\"expense\",\"currency\":\"USD\",\"amount\":\"10.01\",\"startDate\":\"2024-02-01\",\"nextDate\":\"2024-02-29\",\"frequency\":\"monthly\",\"day\":31,\"time\":\"09:00\",\"note\":\"固定支出\",\"category\":\"住房\",\"rateMode\":\"auto\",\"fixedRate\":\"7.2\"}");
  JSONArray books=new JSONArray().put(book);ScheduleEngine.validateRule(r,books);
  JSONObject entry=ScheduleEngine.entry(r,book,"2024-02-29",(f,t,d)->new JSONObject().put("rate","7.12345").put("updated",d).put("source","test"));
  eq(entry.getString("converted"),"71.31");eq(entry.getString("amount"),"10.01");eq(entry.getString("note"),"固定支出");
  boolean rejected=false;try{ScheduleEngine.entry(r,book,"2024-02-29",(f,t,d)->new JSONObject().put("rate","7").put("updated","2024-02-28").put("source","test"));}catch(Exception e){rejected=true;}eq(rejected,true);
  JSONObject db=new JSONObject().put("books",books).put("entries",new JSONArray()).put("schedules",new JSONArray().put(r)).put("revision",0);
  eq(ScheduleEngine.commit(db,"rent","old","2024-02-29",entry,"",0),-1);eq(db.getJSONArray("entries").length(),0);
  eq(ScheduleEngine.commit(db,"rent","g1","2024-02-29",null,"offline",100),2);eq(r.getString("nextDate"),"2024-02-29");eq(db.getJSONArray("entries").length(),0);eq(r.getLong("retryAfter"),1800100L);
  eq(ScheduleEngine.commit(db,"rent","g1","2024-02-29",entry,"",200),1);eq(r.getString("nextDate"),"2024-03-31");eq(r.getString("lastError"),"");
  eq(ScheduleEngine.commit(db,"rent","g1","2024-02-29",entry,"",200),-1);eq(db.getJSONArray("entries").length(),1);
  r.put("nextDate","2024-02-29");eq(ScheduleEngine.commit(db,"rent","g1","2024-02-29",entry,"",200),0);eq(db.getJSONArray("entries").length(),1);
  r.put("enabled",false);eq(ScheduleEngine.commit(db,"rent","g1","2024-03-31",entry,"",200),-1);
  r.put("currency","CNY");eq(ScheduleEngine.entry(r,book,"2024-03-31",(f,t,d)->{throw new Exception("must not call");}).getString("converted"),"10.01");
  r.put("currency","USD").put("rateMode","fixed");eq(ScheduleEngine.entry(r,book,"2024-03-31",(f,t,d)->{throw new Exception("must not call");}).getString("converted"),"72.07");
  eq(ScheduleDates.first("monthly",31,LocalDate.parse("2025-02-01")).toString(),"2025-02-28");eq(ScheduleDates.after("monthly",31,LocalDate.parse("2024-02-29")).toString(),"2024-03-31");eq(ScheduleDates.after("weekly",7,LocalDate.parse("2026-09-13")).toString(),"2026-09-20");
  System.out.println("PASS native schedule validation, FX rounding/date, fixed/same currency, atomic commit planner, retry cursor, idempotency, edited/paused rule protection, month end and weekly dates");
 }
}
