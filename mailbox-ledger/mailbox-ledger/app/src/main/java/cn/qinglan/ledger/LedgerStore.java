package cn.qinglan.ledger;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import org.json.*;

final class LedgerStore {
    static final Object LOCK=new Object();
    static final int LIMIT=12*1024*1024;
    static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"ledger.json"));}
    static JSONObject read(Context c) throws Exception {
        synchronized(LOCK){
            try(InputStream in=file(c).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("账本过大");out.write(b,0,n);}
                return normalize(new JSONObject(out.toString("UTF-8")));
            }catch(FileNotFoundException e){return null;}
        }
    }
    static JSONObject normalize(JSONObject d) throws Exception {
        if(d.getInt("version")!=1)throw new Exception("账本版本不兼容");
        if(!d.has("revision"))d.put("revision",0);
        if(!d.has("schedules"))d.put("schedules",new JSONArray());
        return d;
    }
    static void write(Context c,JSONObject d) throws Exception {
        synchronized(LOCK){
            byte[] bytes=d.toString().getBytes("UTF-8");if(bytes.length>LIMIT||d.getJSONArray("entries").length()>50000)throw new Exception("账本容量已满，请备份后整理");
            AtomicFile f=file(c);FileOutputStream out=null;
            try{out=f.startWrite();out.write(bytes);f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}
        }
    }
    static String saveUI(Context c,String raw,boolean restore){
        synchronized(LOCK){try{
            JSONObject d=normalize(new JSONObject(raw)), old=read(c);long rev=old==null?0:old.optLong("revision",0);
            if(!restore&&d.optLong("revision",0)!=rev)return "CONFLICT";
            if(d.getJSONArray("books").length()==0)throw new Exception("至少保留一个账本");
            if(restore){JSONArray rules=d.getJSONArray("schedules");for(int i=0;i<rules.length();i++){JSONObject r=rules.getJSONObject(i);r.put("enabled",false);r.put("generation",java.util.UUID.randomUUID().toString());r.put("retryAfter",0);}}
            JSONArray rules=d.getJSONArray("schedules");
            if(rules.length()>100)throw new Exception("定时规则过多");
            java.util.HashSet<String> ids=new java.util.HashSet<>();
            for(int i=0;i<rules.length();i++){JSONObject r=rules.getJSONObject(i);ScheduleEngine.validateRule(r,d.getJSONArray("books"));if(!ids.add(r.getString("id")))throw new Exception("规则编号重复");}
            d.put("revision",rev+1);write(c,d);return "ok";
        }catch(Exception e){return "保存失败："+(e.getMessage()==null?"请检查存储空间":e.getMessage());}}
    }
    static JSONObject find(JSONArray a,String id) throws Exception {for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id")))return o;}return null;}
}
