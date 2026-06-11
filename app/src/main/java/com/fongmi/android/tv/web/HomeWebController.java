package com.fongmi.android.tv.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.Proxy;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.fongmi.android.tv.web.ext.WebHomeExtension;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeWebController {

    private static final String BRIDGE = "fongmiBridge";
    private static final int SLOW_KEY_MS = 24;
    private static HomeWebController active;
    private static boolean extensionReloadRequested;

    private final Listener listener;
    private final Activity activity;
    private final Set<String> injectedExtensions;
    private final boolean debugTools;
    private WebView webView;
    private final float density;
    private ScriptHandler documentStartHandler;
    private Site site;
    private String documentStartKey;
    private String homePage;
    private volatile String trustedOrigin = "";
    private long pauseAt;
    private long lastKeyAt;
    private boolean sdkReady;
    private boolean paused;
    private volatile boolean trusted;

    public HomeWebController(Activity activity, WebView webView, Listener listener) {
        this(activity, webView, listener, false);
    }

    public HomeWebController(Activity activity, WebView webView, Listener listener, boolean debugTools) {
        this.activity = activity;
        this.webView = webView;
        this.listener = listener;
        this.debugTools = debugTools;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.injectedExtensions = new HashSet<>();
        active = this;
        init();
    }

    public static void requestExtensionReload() {
        extensionReloadRequested = true;
        HomeWebController controller = active;
        if (controller != null) App.post(controller::consumeExtensionReload);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init() {
        if (debugTools) WebView.setWebContentsDebuggingEnabled(true);
        WebViewUtil.configureHome(webView);
        if (Util.isLeanback()) webView.setNextFocusUpId(R.id.title);
        webView.setBackgroundColor(Color.TRANSPARENT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setOnFocusChangeListener((v, hasFocus) -> SpiderDebug.log("webhome-focus", "webview focus=%s visible=%s url=%s", hasFocus, isVisible(), webView.getUrl()));
        webView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> injectViewport());
        webView.addJavascriptInterface(new HomeWebBridge(this, activity, webView), BRIDGE);
        webView.setWebViewClient(client());
        webView.setWebChromeClient(chrome());
        WebViewUtil.logProvider("webhome");
    }

    public boolean load(Site site) {
        return load(site, false);
    }

    public boolean load(Site site, boolean force) {
        if (!site.hasHomePage()) return false;
        Server.get().start();
        String url = getHomePage(site);
        boolean reload = force || !url.equals(homePage);
        this.site = site;
        this.homePage = url;
        prepareExtensions(site);
        registerDocumentStartScripts();
        if (reload) {
            sdkReady = false;
            injectedExtensions.clear();
            refreshTrust();
            webView.loadUrl(force ? reloadUrl(homePage) : homePage);
        } else {
            refreshTrust();
        }
        show();
        return true;
    }

    public void reload() {
        refreshTrust();
        if (TextUtils.isEmpty(homePage)) {
            webView.reload();
        } else {
            webView.clearCache(false);
            webView.loadUrl(reloadUrl(homePage));
        }
    }

    public void reloadExtensions() {
        extensionReloadRequested = true;
        consumeExtensionReload();
    }

    public void evaluate(String script, ValueCallback<String> callback) {
        webView.post(() -> webView.evaluateJavascript(script, callback));
    }

    public void dispatchDebugConsole(String level, String message) {
        if (!debugTools) return;
        String text = (TextUtils.isEmpty(level) ? "log" : level).toUpperCase(Locale.ROOT) + " " + (message == null ? "" : message);
        App.post(() -> listener.onWebConsole(text));
    }

    public void dispatchDebugNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        if (!debugTools) return;
        App.post(() -> listener.onWebNetwork(type, method, url, status, durationMs, detail));
    }

    public void show() {
        active = this;
        webView.setVisibility(View.VISIBLE);
        focusWebView("show");
        consumeExtensionReload();
    }

    public void hide() {
        webView.setVisibility(View.GONE);
    }

    public boolean isVisible() {
        return webView.getVisibility() == View.VISIBLE;
    }

    public boolean handleBack() {
        if (!isVisible()) return false;
        if (!webView.canGoBack()) return false;
        webView.goBack();
        return true;
    }

    public void setToolbar(boolean visible) {
        listener.setToolbar(visible);
    }

    public void openSetting() {
        listener.openSetting();
    }

    public boolean isTrustedHomePage() {
        return trusted;
    }

    public String getTrustedOrigin() {
        return trustedOrigin;
    }

    private void refreshTrust() {
        String url = webView.getUrl();
        if (TextUtils.isEmpty(url)) url = homePage;
        trustedOrigin = TextUtils.isEmpty(url) ? "" : originOf(url);
        trusted = isTrustedHomeUrl(url);
    }

    private boolean isTrustedHomeUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (TextUtils.isEmpty(scheme)) return false;
            if ("file".equalsIgnoreCase(scheme)) return isAllowedFilePath(uri.getPath());
            String host = uri.getHost();
            if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host)) {
                int port = uri.getPort();
                return port == -1 || port == Proxy.getPort();
            }
            if (sameOrigin(url, VodConfig.getUrl())) return true;
            return matchesAllowedOrigin(url);
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isAllowedFilePath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String appDir = App.get().getFilesDir().getParent();
        if (path.startsWith(appDir)) return true;
        if (path.startsWith("/android_asset/")) return true;
        return false;
    }

    private boolean matchesAllowedOrigin(String url) {
        String origin = originOf(url);
        if (TextUtils.isEmpty(origin)) return false;
        for (String allowed : VodConfig.get().getAllowedOrigins()) {
            if (!TextUtils.isEmpty(allowed) && origin.equals(allowed)) return true;
        }
        return false;
    }

    private boolean sameOrigin(String a, String b) {
        String left = originOf(a);
        String right = originOf(b);
        return !TextUtils.isEmpty(left) && left.equals(right);
    }

    private String originOf(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(host)) return "";
            int port = uri.getPort();
            String ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            return scheme.toLowerCase(Locale.ROOT) + "://" + ascii.toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
        } catch (Throwable e) {
            return "";
        }
    }

    public void onResume() {
        paused = false;
        webView.onResume();
        webView.resumeTimers();
        recoverAfterResume();
        consumeExtensionReload();
    }

    public void onPause() {
        paused = true;
        pauseAt = System.currentTimeMillis();
        dispatchLifecycle("fmpause", "{time:" + pauseAt + "}");
        webView.onPause();
    }

    public void destroy() {
        removeDocumentStartScripts();
        webView.stopLoading();
        webView.destroy();
        if (debugTools) WebView.setWebContentsDebuggingEnabled(false);
        if (active == this) active = null;
    }

    private void consumeExtensionReload() {
        if (!extensionReloadRequested || paused || !isVisible() || site == null || TextUtils.isEmpty(homePage)) return;
        extensionReloadRequested = false;
        Site current = site;
        WebHomeExtensionRegistry.get().refresh(current, () -> {
            if (site == null || !current.getKey().equals(site.getKey())) return;
            registerDocumentStartScripts();
            reload();
        });
    }

    private void recreateWebView() {
        ViewGroup parent = webView.getParent() instanceof ViewGroup ? (ViewGroup) webView.getParent() : null;
        if (parent == null) return;
        int index = parent.indexOfChild(webView);
        int id = webView.getId();
        int visibility = webView.getVisibility();
        ViewGroup.LayoutParams params = webView.getLayoutParams();
        try {
            removeDocumentStartScripts();
            webView.stopLoading();
            parent.removeView(webView);
            webView.destroy();
        } catch (Throwable ignored) {
        }
        webView = new WebView(activity);
        webView.setId(id);
        webView.setVisibility(visibility);
        parent.addView(webView, Math.max(0, index), params);
        init();
        registerDocumentStartScripts();
    }

    private void recoverAfterResume() {
        if (!isVisible()) return;
        webView.setBackgroundColor(Color.TRANSPARENT);
        focusWebView("resume");
        webView.requestLayout();
        webView.invalidate();
        webView.postInvalidateOnAnimation();
        nudgeCompositor();
        dispatchResume(0);
        dispatchResume(80);
        dispatchResume(260);
    }

    private void dispatchResume(long delay) {
        webView.postDelayed(() -> {
            injectViewport();
            long now = System.currentTimeMillis();
            long pausedMs = pauseAt > 0 ? Math.max(0, now - pauseAt) : 0;
            dispatchLifecycle("fmresume", "{time:" + now + ",pausedMs:" + pausedMs + "}");
        }, delay);
    }

    private void nudgeCompositor() {
        webView.setAlpha(0.99f);
        webView.postDelayed(() -> {
            webView.setAlpha(1f);
            webView.invalidate();
            webView.postInvalidateOnAnimation();
        }, 50);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isVisible() || !Util.isLeanback() || !isRemoteKey(event)) return false;
        long start = System.currentTimeMillis();
        long gap = lastKeyAt > 0 ? start - lastKeyAt : -1;
        lastKeyAt = start;
        focusWebView("key");
        boolean handled = webView.dispatchKeyEvent(event);
        long cost = System.currentTimeMillis() - start;
        if (cost >= SLOW_KEY_MS || (KeyUtil.isActionDown(event) && event.getRepeatCount() > 0 && cost >= 12)) {
            SpiderDebug.log("webhome-key", "slow action=%s key=%s repeat=%s handled=%s cost=%sms gap=%sms focus=%s url=%s",
                    event.getAction(), event.getKeyCode(), event.getRepeatCount(), handled, cost, gap, webView.hasFocus(), webView.getUrl());
        }
        return handled;
    }

    public boolean requestFocus(String reason) {
        return focusWebView(reason);
    }

    private boolean isRemoteKey(KeyEvent event) {
        return KeyUtil.isUpKey(event)
                || KeyUtil.isDownKey(event)
                || KeyUtil.isLeftKey(event)
                || KeyUtil.isRightKey(event)
                || KeyUtil.isEnterKey(event);
    }

    private boolean focusWebView(String reason) {
        if (webView.hasFocus()) return true;
        boolean ok = webView.requestFocus();
        SpiderDebug.log("webhome-focus", "request reason=%s ok=%s visible=%s width=%s height=%s url=%s", reason, ok, isVisible(), webView.getWidth(), webView.getHeight(), webView.getUrl());
        return ok;
    }

    private void dispatchLifecycle(String event, String detail) {
        String script = "(function(){try{window.dispatchEvent(new CustomEvent('" + event + "',{detail:" + detail + "}));}catch(e){}})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private WebViewClient client() {
        return new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                SpiderDebug.log("webhome-webview", "page started url=%s", url);
                listener.onWebRequest("PAGE", url, true);
                sdkReady = false;
                injectedExtensions.clear();
                markDocumentStartInjected();
                refreshTrust();
                listener.onWebLoading();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                SpiderDebug.log("webhome-webview", "page finished url=%s title=%s", url, view.getTitle());
                refreshTrust();
                injectSdk();
                focusWebView("page-finished");
                listener.onWebReady();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                SpiderDebug.log("webhome-webview", "resource error main=%s code=%s desc=%s url=%s", request.isForMainFrame(), error.getErrorCode(), error.getDescription(), request.getUrl());
                listener.onWebConsole("ERROR " + error.getErrorCode() + " " + error.getDescription() + " " + request.getUrl());
                if (request.isForMainFrame()) {
                    homePage = null;
                    trusted = false;
                    Notify.show(error.getDescription().toString());
                    listener.onWebError();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("intent".equalsIgnoreCase(uri.getScheme())) return true;
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                listener.onWebRequest(request.getMethod(), request.getUrl().toString(), request.isForMainFrame(), request.getRequestHeaders());
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                SpiderDebug.log("webhome-webview", "render process gone didCrash=%s priority=%s", detail.didCrash(), detail.rendererPriorityAtExit());
                recreateWebView();
                if (!TextUtils.isEmpty(homePage)) {
                    refreshTrust();
                    listener.onWebLoading();
                    webView.loadUrl(reloadUrl(homePage, true));
                } else {
                    trusted = false;
                    listener.onWebError();
                }
                return true;
            }
        };
    }

    private WebChromeClient chrome() {
        return new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message != null) {
                    String line = String.format(Locale.ROOT, "%s %s:%s %s", message.messageLevel(), message.sourceId(), message.lineNumber(), message.message());
                    SpiderDebug.log("webhome-console", line);
                    listener.onWebConsole(line);
                }
                return super.onConsoleMessage(message);
            }
        };
    }

    private void injectSdk() {
        injectViewport();
        webView.evaluateJavascript(getSdk(), value -> {
            sdkReady = true;
            injectExtensions(WebHomeExtension.RUN_AT_END);
            webView.postDelayed(() -> injectExtensions(WebHomeExtension.RUN_AT_IDLE), 600);
        });
    }

    private void prepareExtensions(Site site) {
        String key = site.getKey();
        WebHomeExtensionRegistry.get().prepare(site, () -> {
            if (this.site == null || !key.equals(this.site.getKey())) return;
            registerDocumentStartScripts();
            injectExtensions(WebHomeExtension.RUN_AT_END);
            webView.postDelayed(() -> injectExtensions(WebHomeExtension.RUN_AT_IDLE), 600);
        });
    }

    private void registerDocumentStartScripts() {
        removeDocumentStartScripts();
        if (site == null || !isDocumentStartSupported()) return;
        String script = documentStartScript();
        if (TextUtils.isEmpty(script)) return;
        try {
            Set<String> origins = documentStartOrigins();
            if (origins.isEmpty()) return;
            documentStartHandler = WebViewCompat.addDocumentStartJavaScript(webView, script, origins);
            documentStartKey = site.getKey();
            SpiderDebug.log("webhome-ext", "document-start registered site=%s", documentStartKey);
        } catch (Throwable e) {
            documentStartHandler = null;
            documentStartKey = "";
            SpiderDebug.log("webhome-ext", "document-start register failed site=%s error=%s", site.getKey(), e.getMessage());
        }
    }

    private void removeDocumentStartScripts() {
        try {
            if (documentStartHandler != null) documentStartHandler.remove();
        } catch (Throwable e) {
            SpiderDebug.log("webhome-ext", "document-start remove failed error=%s", e.getMessage());
        }
        documentStartHandler = null;
        documentStartKey = "";
    }

    private String documentStartScript() {
        if (site == null) return "";
        StringBuilder script = new StringBuilder();
        for (WebHomeExtension extension : WebHomeExtensionRegistry.get().get(site.getKey())) {
            if (!WebHomeExtension.RUN_AT_START.equals(extension.getRunAt())) continue;
            if (script.length() == 0) script.append(getSdk());
            script.append('\n').append(extension.script(site.getKey()));
        }
        return script.toString();
    }

    private Set<String> documentStartOrigins() {
        String origin = originOf(homePage);
        return TextUtils.isEmpty(origin) ? Collections.emptySet() : Collections.singleton(origin);
    }

    private void markDocumentStartInjected() {
        if (site == null || TextUtils.isEmpty(documentStartKey) || !documentStartKey.equals(site.getKey())) return;
        for (WebHomeExtension extension : WebHomeExtensionRegistry.get().get(site.getKey())) {
            if (!WebHomeExtension.RUN_AT_START.equals(extension.getRunAt())) continue;
            injectedExtensions.add(extension.getId());
            WebHomeExtensionRegistry.get().recordInject(extension, site.getKey(), WebHomeExtension.RUN_AT_START);
        }
    }

    private boolean isDocumentStartSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        } catch (Throwable e) {
            return false;
        }
    }

    private void injectExtensions(String runAt) {
        if (!sdkReady || site == null || !isVisible()) return;
        for (WebHomeExtension extension : WebHomeExtensionRegistry.get().get(site.getKey())) {
            if (!extension.shouldInjectAt(runAt)) continue;
            if (!injectedExtensions.add(extension.getId())) continue;
            if (WebHomeExtension.RUN_AT_START.equals(extension.getRunAt())) SpiderDebug.log("webhome-ext", "document-start downgraded id=%s site=%s", extension.getId(), site.getKey());
            SpiderDebug.log("webhome-ext", "inject id=%s runAt=%s target=%s site=%s", extension.getId(), extension.getRunAt(), runAt, site.getKey());
            WebHomeExtensionRegistry.get().recordInject(extension, site.getKey(), runAt);
            if (debugTools) dispatchDebugConsole("EXT", "inject id=" + extension.getId() + " runAt=" + extension.getRunAt() + " target=" + runAt);
            webView.evaluateJavascript(extension.script(site.getKey()), null);
        }
    }

    private void injectViewport() {
        if (webView.getWidth() <= 0 || webView.getHeight() <= 0) return;
        float width = webView.getWidth() / density;
        float height = webView.getHeight() / density;
        String script = "(function(){if(!document||!document.documentElement)return;"
                + "document.documentElement.style.setProperty('--fm-web-width','" + width + "px');"
                + "document.documentElement.style.setProperty('--fm-web-height','" + height + "px');"
                + "document.documentElement.style.setProperty('--fm-safe-bottom','0px');"
                + "window.dispatchEvent(new CustomEvent('fmviewport',{detail:{width:" + width + ",height:" + height + ",safeBottom:0}}));"
                + "})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private String reloadUrl(String url) {
        return reloadUrl(url, false);
    }

    private String reloadUrl(String url, boolean restore) {
        try {
            Uri.Builder builder = Uri.parse(url).buildUpon().appendQueryParameter("_fm_reload", String.valueOf(System.currentTimeMillis()));
            if (restore) builder.appendQueryParameter("_fm_restore", "1");
            return builder.build().toString();
        } catch (Throwable e) {
            return url + (url.contains("?") ? "&" : "?") + "_fm_reload=" + System.currentTimeMillis() + (restore ? "&_fm_restore=1" : "");
        }
    }

    private String getHomePage(Site site) {
        String url = site.getHomePage();
        if (UrlUtil.scheme(url).isEmpty()) url = UrlUtil.resolve(VodConfig.getUrl(), url);
        return UrlUtil.convert(url);
    }

    private String getSdk() {
        return String.format(Locale.ROOT, """
                (function(){
                  if(window.fm&&window.fongmi){window.dispatchEvent(new CustomEvent('fmsdk'));return;}
                  if(document&&document.documentElement)document.documentElement.classList.add('fm-native');
                  window.fongmiClient={mode:'%s',isLeanback:%s};
                  const callbacks={};
                  let seq=0;
                  function invoke(method,payload){
                    return new Promise((resolve,reject)=>{
                      const id='fm_'+Date.now()+'_'+(++seq);
                      callbacks[id]={resolve,reject};
                      fongmiBridge.invoke(id,method,JSON.stringify(payload||{}));
                    });
                  }
                  function hydrate(data){
                    if(!data||!data.__fmResultId)return data;
                    const resultId=data.__fmResultId;
                    const length=fongmiBridge.resultLength(resultId);
                    let text='';
                    for(let start=0;start<length;start+=60000)text+=fongmiBridge.resultChunk(resultId,start);
                    fongmiBridge.clearResult(resultId);
                    return JSON.parse(text);
                  }
                  window.fongmiNative={
                    resolve:(id,data)=>{ if(callbacks[id]){ callbacks[id].resolve(hydrate(data)); delete callbacks[id]; } },
                    reject:(id,error)=>{ if(callbacks[id]){ callbacks[id].reject(new Error(error||'')); delete callbacks[id]; } }
                  };
                  if(!window.__fmUrlHook&&window.history){
                    window.__fmUrlHook=true;
                    const emit=()=>window.dispatchEvent(new CustomEvent('fmurlchange',{detail:{url:location.href}}));
                    const rawPush=history.pushState;
                    const rawReplace=history.replaceState;
                    history.pushState=function(){const r=rawPush.apply(this,arguments);emit();return r;};
                    history.replaceState=function(){const r=rawReplace.apply(this,arguments);emit();return r;};
                    window.addEventListener('popstate',emit);
                  }
                  %s
                  const player={
                    playUrl:(url,title,options)=>invoke('player.playUrl',Object.assign({},options||{},{url,title})),
                    playVod:(siteKey,vodId,title,pic,options)=>invoke('player.playVod',Object.assign({},options||{},{siteKey,vodId,title,pic})),
                    control:(action)=>invoke('player.control',{action}),
                    status:()=>invoke('player.status',{})
                  };
                  const net={
                    request:(url,options)=>invoke('net.request',Object.assign({},options||{},{url})),
                    resourceUrl:(url,options)=>fongmiBridge.resourceUrl(url,JSON.stringify(options||{}))
                  };
                  const cache={
                    get:(key,rule)=>invoke('cache.get',{key,rule}),
                    set:(key,value,rule)=>invoke('cache.set',{key,value,rule}),
                    del:(key,rule)=>invoke('cache.del',{key,rule})
                  };
                  const pan={
                    check:(items)=>invoke('pan.check',{items}),
                    play:(payload)=>invoke('pan.play',payload||{})
                  };
                  const ext={
                    info:()=>invoke('ext.info',{}),
                    log:(message,data)=>invoke('ext.log',{message,data}),
                    toast:(message)=>invoke('ext.toast',{message})
                  };
                  const ui={
                    setToolbar:(visible)=>invoke('ui.setToolbar',{visible:visible!==false})
                  };
                  window.fongmi={invoke,player,net,cache,
                    app:{
                      search:(keyword,options)=>invoke('app.search',Object.assign({},options||{},{keyword})),
                      openLive:()=>invoke('app.openLive',{}),
                      openKeep:()=>invoke('app.openKeep',{}),
                      openSetting:()=>invoke('app.openSetting',{}),
                      history:()=>invoke('app.history',{})
                    },
                    pan,
                    ext,
                    device:{info:()=>invoke('device.info',{})},
                    site:{info:()=>invoke('site.info',{})},
                    config:{info:()=>invoke('config.info',{})},
                    ui,
                    navigation:{
                      back:()=>invoke('navigation.back',{}),
                      reload:()=>invoke('navigation.reload',{})
                    }
                  };
                  window.fm={
                    req:net.request,
                    res:net.resourceUrl,
                    play:player.playUrl,
                    vod:player.playVod,
                    ctrl:player.control,
                    stat:player.status,
                    search:window.fongmi.app.search,
                    openLive:window.fongmi.app.openLive,
                    openKeep:window.fongmi.app.openKeep,
                    openSetting:window.fongmi.app.openSetting,
                    history:window.fongmi.app.history,
                    pan,
                    check:window.fongmi.pan.check,
                    cache,
                    ext,
                    ui,
                    device:window.fongmi.device.info,
                    site:window.fongmi.site.info,
                    config:window.fongmi.config.info,
                    back:window.fongmi.navigation.back,
                    reload:window.fongmi.navigation.reload
                  };
                  window.dispatchEvent(new CustomEvent('fmsdk'));
                })();
                """, com.fongmi.android.tv.BuildConfig.FLAVOR_mode, com.fongmi.android.tv.utils.Util.isLeanback(), debugTools ? debugSdkHook() : "");
    }

    private String debugSdkHook() {
        return """
                  if(!window.__fmConsoleHook){
                    window.__fmConsoleHook=true;
                    ['log','info','warn','error','debug'].forEach(function(level){
                      const raw=console[level]||console.log;
                      console[level]=function(){
                        const args=Array.prototype.slice.call(arguments);
                        try{fongmiBridge.console(level,args.map(function(v){try{return typeof v==='string'?v:JSON.stringify(v);}catch(e){return String(v);}}).join(' '));}catch(e){}
                        return raw&&raw.apply(console,args);
                      };
                    });
                  }
                  if(!window.__fmNetworkHook){
                    window.__fmNetworkHook=true;
                    const absolute=function(url){try{return new URL(String(url),location.href).href;}catch(e){return String(url||'');}};
                    const clip=function(value){value=String(value||'');return value.length>2000?value.slice(0,2000)+'\\n...truncated':value;};
                    const bodyText=function(body){
                      if(!body)return '';
                      if(typeof body==='string')return 'payload:\\n'+clip(body);
                      if(body instanceof URLSearchParams)return 'payload:\\n'+clip(body.toString());
                      if(body instanceof FormData){
                        const out=[];
                        try{body.forEach(function(v,k){out.push(k+'='+(v&&v.name?'[file '+v.name+']':String(v)));});}catch(e){}
                        return 'payload:\\n'+clip(out.join('\\n'));
                      }
                      return 'payloadType='+Object.prototype.toString.call(body)+'\\npayloadBytes='+String(body).length;
                    };
                    const headers=function(headers){
                      const out=[];
                      try{
                        if(headers&&headers.forEach)headers.forEach(function(v,k){out.push(k+': '+v);});
                        else if(headers)Object.keys(headers).forEach(function(k){out.push(k+': '+headers[k]);});
                      }catch(e){}
                      return out.join('\\n');
                    };
                    const rawFetch=window.fetch;
                    if(rawFetch){
                      window.fetch=function(input,init){
                        const started=Date.now();
                        const method=(init&&init.method)||(input&&input.method)||'GET';
                        const url=absolute(input&&input.url?input.url:input);
                        const requestHeaders=headers((init&&init.headers)||(input&&input.headers));
                        const body=bodyText(init&&init.body);
                        try{fongmiBridge.network('FETCH_START',method,url,0,0,[requestHeaders,body].filter(Boolean).join('\\n'));}catch(e){}
                        return rawFetch.apply(this,arguments).then(function(resp){
                          try{fongmiBridge.network('FETCH_DONE',method,url,resp.status||0,Date.now()-started,['type='+(resp.type||''),'headers:',headers(resp.headers)].join('\\n'));}catch(e){}
                          return resp;
                        }).catch(function(err){
                          try{fongmiBridge.network('FETCH_ERROR',method,url,0,Date.now()-started,String(err&&err.message||err));}catch(e){}
                          throw err;
                        });
                      };
                    }
                    const RawXHR=window.XMLHttpRequest;
                    if(RawXHR&&RawXHR.prototype){
                      const rawOpen=RawXHR.prototype.open;
                      const rawSend=RawXHR.prototype.send;
                      RawXHR.prototype.open=function(method,url){
                        this.__fmMethod=method||'GET';
                        this.__fmUrl=absolute(url);
                        return rawOpen.apply(this,arguments);
                      };
                      RawXHR.prototype.send=function(){
                        const xhr=this;
                        const started=Date.now();
                        const body=arguments.length?bodyText(arguments[0]):'';
                        try{fongmiBridge.network('XHR_START',xhr.__fmMethod||'GET',xhr.__fmUrl||'',0,0,body);}catch(e){}
                        xhr.addEventListener('loadend',function(){
                          try{fongmiBridge.network('XHR_DONE',xhr.__fmMethod||'GET',xhr.__fmUrl||'',xhr.status||0,Date.now()-started,[xhr.statusText||'',xhr.getAllResponseHeaders&&xhr.getAllResponseHeaders()||''].filter(Boolean).join('\\n'));}catch(e){}
                        });
                        xhr.addEventListener('error',function(){
                          try{fongmiBridge.network('XHR_ERROR',xhr.__fmMethod||'GET',xhr.__fmUrl||'',xhr.status||0,Date.now()-started,'error');}catch(e){}
                        });
                        return rawSend.apply(this,arguments);
                      };
                    }
                  }
                """;
    }

    public interface Listener {

        void onWebLoading();

        void onWebReady();

        void onWebError();

        default void setToolbar(boolean visible) {
        }

        default void openSetting() {
        }

        default void onWebConsole(String line) {
        }

        default void onWebRequest(String method, String url, boolean mainFrame) {
        }

        default void onWebRequest(String method, String url, boolean mainFrame, Map<String, String> headers) {
            onWebRequest(method, url, mainFrame);
        }

        default void onWebNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        }
    }
}
