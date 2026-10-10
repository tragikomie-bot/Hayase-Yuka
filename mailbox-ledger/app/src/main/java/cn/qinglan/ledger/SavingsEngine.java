package cn.qinglan.ledger;
import java.math.*;
import java.util.*;
import org.json.*;

/** Builds the saving inside its expense; both are committed in one store write. */
final class SavingsEngine {
    static final BigDecimal MAX=new BigDecimal("999999999999.99");
    static BigDecimal positive(String value)throws Exception {
        if(!value.matches("[0-9]{1,12}(\\.[0-9]{1,2})?"))throw new Exception("笔笔攒金额无效");
        BigDecimal n=new BigDecimal(value);if(n.signum()<=0||n.compareTo(MAX)>0)throw new Exception("笔笔攒金额无效");return n;
    }
    static String calculate(String base,JSONObject r)throws Exception {
        if("fixed".equals(r.getString("mode")))return positive(r.getString("fixed")).setScale(2).toPlainString();
        BigDecimal n=positive(base).multiply(new BigDecimal(r.getInt("percent"))).movePointLeft(2);
        BigDecimal steps=n.subtract(new BigDecimal("0.88")).max(BigDecimal.ZERO).setScale(0,RoundingMode.CEILING);
        return steps.add(new BigDecimal("0.88")).min(new BigDecimal("99.88")).setScale(2).toPlainString();
    }
    static void validate(JSONObject schedule,JSONArray books)throws Exception {
        if(!schedule.has("savingRule")||schedule.isNull("savingRule"))return;
        JSONObject r=schedule.getJSONObject("savingRule");
        if(!"expense".equals(schedule.getString("type")))throw new Exception("仅支出可启用笔笔攒");
        if(!"books".equals(r.optString("scope"))||!Arrays.asList("percent","fixed").contains(r.optString("mode"))||!Arrays.asList(1,5,10,20,30,50,80,100).contains(r.getInt("percent")))throw new Exception("请设置有效的账本笔笔攒规则");
        positive(r.getString("fixed"));
        if(r.getString("from").equals(r.getString("to"))||LedgerStore.find(books,r.getString("from"))==null||LedgerStore.find(books,r.getString("to"))==null)throw new Exception("请选择两个不同的转出、转入账本");
    }
    static JSONObject quote(String from,String to,String date,ScheduleEngine.RateProvider provider)throws Exception {
        JSONObject q=from.equals(to)?new JSONObject().put("rate","1").put("updated",date).put("source","同币种"):provider.get(from,to,date);
        if(!q.getString("updated").startsWith(date))throw new Exception("笔笔攒汇率日期不符，等待有效汇率后重试");
        if(new BigDecimal(q.getString("rate")).signum()<=0)throw new Exception("笔笔攒汇率无效");return q;
    }
    static String convert(String amount,String rate)throws Exception {
        BigDecimal n=new BigDecimal(amount).multiply(new BigDecimal(rate)).setScale(2,RoundingMode.HALF_UP);
        if(n.signum()<0||n.compareTo(MAX)>0)throw new Exception("笔笔攒换算金额过大");return n.toPlainString();
    }
    static JSONObject build(JSONObject schedule,JSONArray books,String date,ScheduleEngine.RateProvider provider)throws Exception {
        validate(schedule,books);
        JSONObject book=LedgerStore.find(books,schedule.getString("bookId"));
        JSONObject e=ScheduleEngine.entry(schedule,book,date,provider);
        if(!schedule.has("savingRule")||schedule.isNull("savingRule"))return e;
        JSONObject r=new JSONObject(schedule.getJSONObject("savingRule").toString());
        String base;
        if("CNY".equals(e.getString("currency")))base=e.getString("amount");
        else if("CNY".equals(book.getString("currency")))base=e.getString("converted");
        else base=convert(e.getString("amount"),quote(e.getString("currency"),"CNY",date,provider).getString("rate"));
        positive(base);
        String amount=calculate(base,r);
        JSONObject saving=new JSONObject().put("currency","CNY").put("base",base).put("amount",amount).put("from",r.getString("from")).put("to",r.getString("to")).put("rule",r);
        Map<String,JSONObject> quotes=new HashMap<>();
        for(String side:new String[]{"from","to"}){
            String currency=LedgerStore.find(books,r.getString(side)).getString("currency");
            JSONObject q=quotes.get(currency);if(q==null){q=quote("CNY",currency,date,provider);quotes.put(currency,q);}
            saving.put(side+"Quote",new JSONObject(q.toString()).put("currency",currency).put("amount",convert(amount,q.getString("rate"))));
        }
        return e.put("saving",saving);
    }
}
