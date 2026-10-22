package com.liugong.machineproductioncycletime;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://script.google.com/macros/s/AKfycbyK9UPFDk9SlmQs90OlhMM6yxauOSzJnUiLIXPZ19CNYyk14qZ7_VqOFwo-b92K0GAGJA/exec";
    private FrameLayout root;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 4102;
    private WebView popupWebView;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF173650);
        getWindow().setNavigationBarColor(0xFF173650);
        root = new FrameLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF79C6ED));
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3));
        root.addView(progress, pp);
        setContentView(root);
        configureWebView(webView, false);
        webView.loadUrl(APP_URL);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void configureWebView(WebView view, boolean popup) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.addJavascriptInterface(new NativeBridge(this, view), "NativeBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, Bitmap favicon) {
                if (!popup && progress != null) progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (!popup && progress != null) progress.setVisibility(View.GONE);
                injectDownloadSupport(v);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return routeUrl(v, request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return routeUrl(v, url);
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int newProgress) {
                if (!popup && progress != null) {
                    progress.setProgress(newProgress);
                    progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try { startActivityForResult(Intent.createChooser(intent, "Select Excel or JSON file"), FILE_CHOOSER_REQUEST); }
                catch (ActivityNotFoundException e) { filePathCallback = null; Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_LONG).show(); return false; }
                return true;
            }
            @Override public boolean onCreateWindow(WebView parent, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView child = new WebView(MainActivity.this);
                popupWebView = child;
                configureWebView(child, true);
                child.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
                root.addView(child);
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView window) {
                if (window != webView) {
                    root.removeView(window);
                    window.destroy();
                    if (popupWebView == window) popupWebView = null;
                }
            }
        });
        view.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (url != null && url.startsWith("blob:")) {
                    Toast.makeText(MainActivity.this, "Preparing report download…", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setTitle("Machine Production Cycle Time report");
                    request.setDescription("Downloading report");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName(contentDisposition, url));
                    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                    Toast.makeText(MainActivity.this, "Download started. Check Downloads.", Toast.LENGTH_LONG).show();
                } catch (Exception e) { Toast.makeText(MainActivity.this, "Could not download: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            }
        });
    }

    private boolean routeUrl(WebView view, String rawUrl) {
        if (rawUrl == null) return false;
        Uri uri = Uri.parse(rawUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (host.equals("script.google.com") || host.endsWith(".googleusercontent.com") || host.equals("googleusercontent.com") || host.equals("accounts.google.com") || host.equals("drive.google.com")) return false;
        if (rawUrl.startsWith("blob:") || rawUrl.startsWith("data:") || rawUrl.startsWith("about:")) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
        return true;
    }

    private String fileName(String disposition, String url) {
        if (disposition != null) {
            int i = disposition.toLowerCase().indexOf("filename=");
            if (i >= 0) return disposition.substring(i + 9).replace("\"", "").trim();
        }
        String last = Uri.parse(url).getLastPathSegment();
        return (last == null || last.isEmpty()) ? "Machine_Production_Cycle_Time_Report" : last;
    }

    private void injectDownloadSupport(WebView view) {
        String js = "(function(){if(window.__nativeDownloadHook)return;window.__nativeDownloadHook=true;document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;if(!a||!a.href||a.href.indexOf('blob:')!==0||!window.NativeBridge)return;e.preventDefault();e.stopPropagation();fetch(a.href).then(function(r){return r.blob()}).then(function(b){var fr=new FileReader();fr.onloadend=function(){var data=String(fr.result).split(',')[1]||'';NativeBridge.saveBase64File(a.download||'Machine_Report.xlsx',b.type||'application/octet-stream',data)};fr.readAsDataURL(b)}).catch(function(){alert('Could not prepare report download. Please use the web tracker in Chrome.');});},true);})();";
        view.evaluateJavascript(js + ";try{if(!window.__nativePrintHook){window.__nativePrintHook=true;window.print=function(){if(window.NativeBridge&&NativeBridge.printCurrentPage)NativeBridge.printCurrentPage();};}}catch(e){}", null);
    }

    public class NativeBridge {
        private final Context context;
        private final WebView targetView;
        NativeBridge(Context c, WebView v) { context = c; targetView = v; }
        @JavascriptInterface public void saveBase64File(String name, String mime, String base64) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    String safeName = (name == null || name.trim().isEmpty()) ? "Machine_Report.xlsx" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (!safeName.contains(".")) safeName += ".xlsx";
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                    values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime == null ? "application/octet-stream" : mime);
                    values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri target = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (target == null) throw new Exception("Download destination unavailable");
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(target)) { if (out == null) throw new Exception("Could not open download file"); out.write(bytes); }
                    Toast.makeText(context, "Saved to Downloads: " + safeName, Toast.LENGTH_LONG).show();
                } catch (Exception ex) { Toast.makeText(context, "Report save failed: " + ex.getMessage(), Toast.LENGTH_LONG).show(); }
            });
        }
        @JavascriptInterface public void printCurrentPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                    String jobName = "Machine Production Cycle Time Report";
                    pm.print(jobName, targetView.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(new android.print.PrintAttributes.Resolution("default", "Default", 300, 300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
                } catch (Exception ex) { Toast.makeText(context, "Print service unavailable", Toast.LENGTH_LONG).show(); }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) results = new Uri[]{uri};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (popupWebView != null) {
            root.removeView(popupWebView); popupWebView.destroy(); popupWebView = null; return;
        }
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.loadUrl("about:blank"); webView.destroy(); }
        if (popupWebView != null) popupWebView.destroy();
        super.onDestroy();
    }
}
