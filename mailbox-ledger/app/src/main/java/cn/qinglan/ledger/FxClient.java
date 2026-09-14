package cn.qinglan.ledger;
import android.content.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.io.*;
import java.net.*;
import java.math.BigDecimal;
import java.util.*;
import java.time.LocalDate;
import org.json.*;
final class FxClient {
    final Context context;final SharedPreferences prefs;
    FxClient(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);}
    String today(){return LocalDate.now().toString();}
    String loadKey() throws Exception {
        String saved=prefs.getString("key","");if(saved.isEmpty())return "";
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        String[] parts=saved.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,(SecretKey)ks.getKey("qinglan_api",null),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");
    }
    String readAll(InputStream stream) throws Exception {
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("响应过大");out.write(b,0,n);}return out.toString("UTF-8");}
    }
    JSONObject http(String endpoint, Map<String,String> params) throws Exception {
        StringBuilder form = new StringBuilder(); params.put("appkey", loadKey());
        for (Map.Entry<String,String> e : params.entrySet()) { if (form.length()>0) form.append('&'); form.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8")); }
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.jisuapi.com/exchange/" + endpoint).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        try {
            try (OutputStream out = c.getOutputStream()) { out.write(form.toString().getBytes("UTF-8")); }
            int status = c.getResponseCode(); if (status != 200) throw new Exception("汇率服务返回 HTTP " + status);
            JSONObject json = new JSONObject(readAll(c.getInputStream()));
            if (json.optInt("status", -1) != 0) throw new Exception("汇率接口：" + json.optString("msg", "查询失败，请检查AppKey及额度"));
            return json.getJSONObject("result");
        } finally { c.disconnect(); }
    }
    JSONObject quote(JSONObject q) throws Exception {
                    String from = q.getString("from"), to = q.getString("to"), date = q.getString("date");
                    if (!from.matches("[A-Z]{3}") || !to.matches("[A-Z]{3}") || !date.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("币种或日期无效");
                    if (date.compareTo(today()) > 0) throw new Exception("未来日期尚无汇率，请手动填写");
                    if (from.equals(to)) { return new JSONObject().put("rate","1").put("updated",date).put("source","同币种").put("cached",false); }
                    String cacheId = from + "_" + to + "_" + date;
                    SharedPreferences cache = context.getSharedPreferences("rates",Context.MODE_PRIVATE);
                    if (prefs.getBoolean("dailyCache",true) && !q.optBoolean("force",false)) {
                        String saved = cache.getString(cacheId, "");
                        if (!saved.isEmpty()) { JSONObject hit = new JSONObject(saved); hit.put("cached",true); return hit; }
                    }
                    if (loadKey().isEmpty()) throw new Exception("请先在设置中填写极速数据 AppKey，或手动输入汇率");
                    Map<String,String> p = new LinkedHashMap<>(); p.put("from",from); p.put("to",to);
                    JSONObject r;
                    if (date.equals(today())) { p.put("amount","1"); r = http("convert",p);
                        if (!from.equals(r.optString("from")) || !to.equals(r.optString("to"))) throw new Exception("接口返回的币种不匹配");
                    } else {
                        p.put("startdate",date); p.put("enddate",date); JSONObject all = http("history",p);
                        if (!from.equals(all.optString("from")) || !to.equals(all.optString("to"))) throw new Exception("接口返回的币种不匹配");
                        JSONArray list = all.optJSONArray("list"); r = null;
                        if (list != null) for (int i=0;i<list.length();i++) { JSONObject row=list.getJSONObject(i); if (date.equals(row.optString("date"))) r=row; }
                        if (r == null) throw new Exception("该日暂无历史汇率，请手动填写实际汇率");
                    }
                    String rate = r.getString("rate");
                    if (!rate.matches("[0-9]{1,12}(\\.[0-9]{1,18})?") || new BigDecimal(rate).signum() <= 0) throw new Exception("接口返回了无效汇率");
                    String updated = r.optString("updatetime",r.optString("date",""));
                    if (updated.length()<10) throw new Exception("接口未返回汇率日期，请稍后重试");
                    JSONObject result = new JSONObject().put("rate",rate).put("updated",updated).put("source","极速数据").put("cached",false);
                    if (updated.substring(0,10).equals(date)) cache.edit().putString(cacheId,result.toString()).apply();

return result;
}
}
