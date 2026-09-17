package cn.qinglan.ledger;
import org.json.*;
public class ForeignRateTest {
 public static void main(String[] a)throws Exception{
  JSONObject r=new JSONObject("{\"base\":\"USD\",\"quote\":\"CNY\",\"rate\":6.7077,\"date\":\"2026-09-17\"}");
  JSONObject q=FxClient.parseForeign(r,"USD","CNY","2026-09-17");
  if(!q.getString("rate").equals("6.7077")||!q.getString("source").equals("Frankfurter"))throw new AssertionError();
  if(!FxClient.parseForeign(r,"USD","CNY","2026-09-19").getString("updated").equals("2026-09-17"))throw new AssertionError();
  for(int i=0;i<4;i++){
   JSONObject bad=new JSONObject(r.toString());if(i==0)bad.put("base","EUR");if(i==1)bad.put("rate",0);if(i==2)bad.put("date","2026-09-20");if(i==3)bad.put("date","bad");
   boolean rejected=false;try{FxClient.parseForeign(bad,"USD","CNY","2026-09-17");}catch(Exception e){rejected=true;}if(!rejected)throw new AssertionError("Invalid response accepted");
  }
  System.out.println("PASS foreign rate parsing, currency and date validation, nonpositive rates, actual date preservation");
 }
}
