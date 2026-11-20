package com.xboxlauncher.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF080D09);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidLauncher");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (host.equals("play.google.com") || host.endsWith(".google.com")) {
                    return false;
                }
                if ("file".equals(uri.getScheme()) || "about".equals(uri.getScheme())) {
                    return false;
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                              FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No se encontró un selector de archivos.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray result = new JSONArray();
            try {
                Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
                launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager pm = getPackageManager();
                List<ResolveInfo> apps;
                if (Build.VERSION.SDK_INT >= 33) {
                    apps = pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0));
                } else {
                    apps = pm.queryIntentActivities(launcherIntent, 0);
                }
                Collections.sort(apps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));

                for (ResolveInfo info : apps) {
                    String packageName = info.activityInfo.packageName;
                    // Excluir la propia aplicación del launcher.
                    if (packageName.equals(getPackageName())) continue;
                    JSONObject item = new JSONObject();
                    item.put("name", info.loadLabel(pm).toString());
                    item.put("packageName", packageName);
                    item.put("icon", "🎮");
                    item.put("color", "");
                    result.put(item);
                }
            } catch (Exception ignored) { }
            return result.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName) {
            runOnUiThread(() -> {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launch);
                    } else {
                        Toast.makeText(MainActivity.this, "No se encontró una actividad para abrir.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo abrir esta aplicación.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public int getBatteryPercent() {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm == null) return -1;
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        @JavascriptInterface
        public void openPlayStore() {
            runOnUiThread(() -> {
                // Se intenta mostrar la web de Play Store dentro del mismo WebView.
                webView.loadUrl("https://play.google.com/store/games");
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) results = new Uri[]{uri};
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidLauncher");
            webView.destroy();
        }
        super.onDestroy();
    }
}
