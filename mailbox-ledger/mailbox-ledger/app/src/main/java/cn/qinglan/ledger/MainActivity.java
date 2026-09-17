package cn.qinglan.ledger;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.net.Uri;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import android.util.AtomicFile;
import android.util.Base64;
import android.security.keystore.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.io.*;
import java.net.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    WebView web;
    FrameLayout container;
    AtomicFile ledger;
    SharedPreferences prefs;
    ExecutorService pool = Executors.newSingleThreadExecutor();
    String exportData;
    final Handler refreshHandler=new Handler(Looper.getMainLooper());
    final Runnable refreshTick=new Runnable(){public void run(){checkSchedules();refreshHandler.postDelayed(this,60000);}};
    void checkSchedules(){ScheduleEngine.EXECUTOR.execute(()->{ScheduleEngine.run(MainActivity.this,new java.util.concurrent.atomic.AtomicBoolean(false),false);ScheduleEngine.arm(MainActivity.this);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())web.evaluateJavascript("window.onLedgerUpdated && window.onLedgerUpdated()",null);});});}
    @Override protected void onResume(){super.onResume();refreshHandler.removeCallbacks(refreshTick);refreshHandler.post(refreshTick);}
    @Override protected void onPause(){refreshHandler.removeCallbacks(refreshTick);super.onPause();}
    static final int LIMIT = 12 * 1024 * 1024;
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ledger = new AtomicFile(new File(getFilesDir(), "ledger.json"));
        web = new WebView(this);
        container = new FrameLayout(this);
        container.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setContentView(container);
        container.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        applySystemTheme();
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient() {
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this,dialogTheme()).setMessage(message).setPositiveButton("放弃",(d,w)->result.confirm()).setNegativeButton("继续编辑",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) { applySystemTheme(); }
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("https".equals(u.getScheme()) && "app.qinglan.local".equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.equals("/")) path = "/index.html";
                    if (path.contains("..")) return empty();
                    try { String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".png") ? "image/png" : "text/html";
                        return new WebResourceResponse(mime, "UTF-8", getAssets().open(path.substring(1)));
                    } catch (Exception e) { return empty(); }
                }
                return empty();
            }
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) { return true; }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("https://app.qinglan.local/index.html");
    }
    boolean isSystemNight() { return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    boolean isNight(){String mode=prefs.getString("theme","system");return mode.equals("dark")||(mode.equals("system")&&isSystemNight());}
    int dialogTheme() { return isNight() ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert; }
    void applySystemTheme() {
        boolean dark=isNight(); int bg=dark?0xff090d14:0xfff2f6ff;
        container.setBackgroundColor(bg); web.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(dark?0xff141a25:0xffffffff);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web.evaluateJavascript("window.syncTheme && window.syncTheme()",null);
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); applySystemTheme(); }
    WebResourceResponse empty() { return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0])); }
    String readAll(InputStream stream) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) { if (out.size() + n > LIMIT) throw new Exception("文件过大，请控制在12MB内"); out.write(b, 0, n); }
            return out.toString("UTF-8");
        }
    }
    void reply(String id, JSONObject result) {
        runOnUiThread(() -> { if (!isFinishing()) web.evaluateJavascript("window.nativeReply(" + JSONObject.quote(id) + "," + result.toString() + ")", null); });
    }
    JSONObject error(String message) { JSONObject o = new JSONObject(); try { o.put("error", message); } catch (Exception ignored) {} return o; }
    String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    SecretKey secret() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("qinglan_api")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("qinglan_api", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey();
        }
        return (SecretKey) ks.getKey("qinglan_api", null);
    }
    String loadKey() throws Exception {
        String saved = prefs.getString("key", ""); if (saved.isEmpty()) return "";
        String[] parts = saved.split(":"); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "UTF-8");
    }
    public class Bridge {
        @JavascriptInterface public String systemTheme() { return isSystemNight()?"dark":"light"; }
        @JavascriptInterface public String load() {
            try { JSONObject d=LedgerStore.read(MainActivity.this);return d==null?"":d.toString(); } catch(Exception e){return "LOAD_ERROR";}
        }
        @JavascriptInterface public String save(String value) {String result=LedgerStore.saveUI(MainActivity.this,value,false);if("ok".equals(result))ScheduleEngine.arm(MainActivity.this);return result;}
        @JavascriptInterface public String restore(String value) {String result=LedgerStore.saveUI(MainActivity.this,value,true);if("ok".equals(result))ScheduleEngine.arm(MainActivity.this);return result;}
        @JavascriptInterface public void runSchedules(String id,boolean force) {
            ScheduleEngine.EXECUTOR.execute(()->{JSONObject result=ScheduleEngine.run(MainActivity.this,new java.util.concurrent.atomic.AtomicBoolean(false),force);ScheduleEngine.arm(MainActivity.this);reply(id,result);});
        }
        @JavascriptInterface public synchronized boolean consumeReleaseNotice() {
            if(prefs.getBoolean("releaseNoticeShown",false))return false;
            // Persist before display, including when the process is killed without dismissing.
            return prefs.edit().putBoolean("releaseNoticeShown",true).commit();
        }
        @JavascriptInterface public String themeMode(){return prefs.getString("theme","system");}
        @JavascriptInterface public String setThemeMode(String mode){
            if(!mode.equals("light")&&!mode.equals("dark")&&!mode.equals("system"))return "主题无效";
            if(!prefs.edit().putString("theme",mode).commit())return "主题保存失败";
            runOnUiThread(()->applySystemTheme());return "ok";
        }
        @JavascriptInterface public String settings() {
            try { return new JSONObject().put("key", loadKey()).put("provider",prefs.getString("provider","jisu")).put("dailyCache", prefs.getBoolean("dailyCache", true)).toString(); }
            catch (Exception e) { return "{\"key\":\"\",\"dailyCache\":true,\"warning\":\"密钥读取失败，请重新填写AppKey\"}"; }
        }
        @JavascriptInterface public String saveSettings(String json) {
            try {
                JSONObject o = new JSONObject(json); String key = o.getString("key").trim();
                String provider=o.optString("provider","jisu");if(!provider.equals("jisu")&&!provider.equals("frankfurter"))return "汇率来源无效";
                String saved = "";
                if (!key.isEmpty()) { Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, secret()); saved = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(key.getBytes("UTF-8")), Base64.NO_WRAP); }
                boolean ok = prefs.edit().putString("provider",provider).putString("key", saved).putBoolean("dailyCache", o.optBoolean("dailyCache", true)).commit();
                return ok ? "ok" : "设置保存失败";
            } catch (Exception e) { return "设置保存失败，请重试"; }
        }
        @JavascriptInterface public void rate(String raw) {
            pool.execute(() -> {
                String id = "";
                try {
                    JSONObject q = new JSONObject(raw); id = q.getString("id");
                    reply(id,new FxClient(MainActivity.this).quote(q));
                } catch (Exception e) { String msg=e.getMessage(); if (e instanceof IOException) msg="网络连接失败，请检查网络后重试，也可手动填写汇率"; reply(id,error(msg == null ? "汇率查询失败" : msg)); }
            });
        }
        @JavascriptInterface public void exportBackup(String value) {
            exportData=value;
            runOnUiThread(() -> { try { Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,"邮箱记账-备份-"+today()+".json"); startActivityForResult(i,41); } catch (Exception e) { Toast.makeText(MainActivity.this,"无法打开文件选择器",Toast.LENGTH_LONG).show(); } });
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> { try { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,42); } catch (Exception e) { Toast.makeText(MainActivity.this,"无法打开文件选择器",Toast.LENGTH_LONG).show(); } });
        }
        @JavascriptInterface public void openProvider() { runOnUiThread(() -> { try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.jisuapi.com/api/exchange/"))); } catch (Exception e) { Toast.makeText(MainActivity.this,"请使用浏览器打开极速数据官网",Toast.LENGTH_LONG).show(); } }); }
    }
    protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result!=RESULT_OK || data==null || data.getData()==null) return;
        try {
            if (request==41 && exportData!=null) {
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())) { out.write(exportData.getBytes("UTF-8")); }
                exportData=null; Toast.makeText(this,"备份已导出",Toast.LENGTH_LONG).show();
            } else if (request==42) {
                String s=readAll(getContentResolver().openInputStream(data.getData()));
                web.evaluateJavascript("window.receiveBackup("+JSONObject.quote(s)+")",null);
            }
        } catch (Exception e) { Toast.makeText(this,"文件读写失败，请重试",Toast.LENGTH_LONG).show(); }
    }
    @Override public void onBackPressed() { web.evaluateJavascript("window.handleBack()",v -> { if ("false".equals(v)) new AlertDialog.Builder(this,dialogTheme()).setMessage("退出邮箱记账？已保存的账目会保留。").setPositiveButton("退出",(d,w)->finish()).setNegativeButton("取消",null).show(); }); }
    protected void onDestroy() { refreshHandler.removeCallbacks(refreshTick); pool.shutdownNow(); web.removeJavascriptInterface("Android"); web.destroy(); super.onDestroy(); }
}
