package cn.qinglan.ledger;
import org.json.*;
public class ScheduledSavingsTest {
 static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 interface Checked{void run()throws Exception;}
 static void fails(Checked c)throws Exception{boolean caught=false;try{c.run();}catch(Exception e){caught=true;}eq(caught,true);}
 public static void main(String[] args)throws Exception{
  JSONArray books=new JSONArray("[{\"id\":\"daily\",\"currency\":\"CNY\"},{\"id\":\"saving\",\"currency\":\"CNY\"},{\"id\":\"usd\",\"currency\":\"USD\"}]");
  JSONObject r=new JSONObject("{\"id\":\"subscription\",\"generation\":\"g1\",\"enabled\":true,\"name\":\"会员\",\"bookId\":\"daily\",\"type\":\"expense\",\"currency\":\"CNY\",\"amount\":\"88.01\",\"startDate\":\"2026-10-01\",\"nextDate\":\"2026-10-10\",\"frequency\":\"monthly\",\"day\":10,\"time\":\"09:00\",\"note\":\"支付宝扣款\",\"category\":\"其他\",\"rateMode\":\"auto\",\"fixedRate\":\"1\"}");
  JSONObject saving=new JSONObject("{\"scope\":\"books\",\"mode\":\"percent\",\"percent\":1,\"fixed\":\"1.00\",\"from\":\"daily\",\"to\":\"saving\"}");
  ScheduleEngine.RateProvider offline=(f,t,d)->{throw new Exception("offline");};
  eq(SavingsEngine.build(r,books,"2026-10-10",offline).has("saving"),false);
  r.put("savingRule",saving);ScheduleEngine.validateRule(r,books);
  JSONObject e=SavingsEngine.build(r,books,"2026-10-10",offline),s=e.getJSONObject("saving");
  eq(s.getString("amount"),"1.88");eq(s.getJSONObject("fromQuote").getString("amount"),"1.88");eq(s.getJSONObject("toQuote").getString("amount"),"1.88");
  for(int percent:new int[]{1,5,10,20,30,50,80,100}){saving.put("percent",percent);for(String amount:new String[]{"0.01","0.88","88.00","88.01","188.00","188.01","10000.00"})System.out.println(percent+":"+amount+":"+SavingsEngine.calculate(amount,saving));}
  saving.put("percent",1);eq(SavingsEngine.calculate("88.00",saving),"0.88");eq(SavingsEngine.calculate("188.01",saving),"2.88");eq(SavingsEngine.calculate("10000.00",saving),"99.88");
  saving.put("mode","fixed").put("fixed","123.45");eq(SavingsEngine.build(r,books,"2026-10-10",offline).getJSONObject("saving").getString("amount"),"123.45");
  // Rule snapshots cannot mutate an already generated entry.
  eq(e.getJSONObject("saving").getJSONObject("rule").getString("mode"),"percent");
  saving.put("to","usd");fails(()->SavingsEngine.build(r,books,"2026-10-10",offline));
  ScheduleEngine.RateProvider fx=(f,t,d)->new JSONObject().put("rate",t.equals("USD")?"0.14":"7.2").put("updated",d).put("source","test");
  JSONObject foreign=SavingsEngine.build(r,books,"2026-10-10",fx);eq(foreign.getJSONObject("saving").getJSONObject("toQuote").getString("amount"),"17.28");
  fails(()->SavingsEngine.build(r,books,"2026-10-10",(f,t,d)->new JSONObject().put("rate","0.14").put("updated","2026-10-09").put("source","test")));
  r.put("bookId","usd").put("currency","USD").put("amount","10.00");saving.put("mode","percent").put("to","saving");JSONObject usd=SavingsEngine.build(r,books,"2026-10-10",fx);eq(usd.getJSONObject("saving").getString("base"),"72.00");eq(usd.getJSONObject("saving").getString("amount"),"0.88");
  r.put("bookId","daily").put("rateMode","fixed").put("fixedRate","9.0");eq(SavingsEngine.build(r,books,"2026-10-10",offline).getJSONObject("saving").getString("base"),"90.00");
  r.put("type","income");fails(()->ScheduleEngine.validateRule(r,books));r.put("type","expense");saving.put("to","daily");fails(()->ScheduleEngine.validateRule(r,books));saving.put("to","missing");fails(()->ScheduleEngine.validateRule(r,books));saving.put("to","saving");
  JSONObject db=new JSONObject().put("books",books).put("entries",new JSONArray()).put("schedules",new JSONArray().put(r)).put("revision",0);
  eq(ScheduleEngine.commit(db,r.getString("id"),"g1","2026-10-10",null,"offline",0),2);eq(db.getJSONArray("entries").length(),0);eq(r.getString("nextDate"),"2026-10-10");
  eq(ScheduleEngine.commit(db,r.getString("id"),"g1","2026-10-10",e,"",1),1);eq(db.getJSONArray("entries").getJSONObject(0).getJSONObject("saving").getString("amount"),"1.88");
  eq(ScheduleEngine.commit(db,r.getString("id"),"g1","2026-10-10",e,"",1),-1);eq(db.getJSONArray("entries").length(),1);
  System.out.println("PASS scheduled savings opt-in, native rounding, snapshots, foreign base/transfer, network/stale errors, income/missing/same-book rejection, atomic retry and deduplication");
 }
}
