package com.github.catvod.net;

import com.github.catvod.bean.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OkProxySelector extends ProxySelector {

    private final List<Proxy> proxy;
    private final ProxySelector system;
    private boolean authSet;

    public OkProxySelector() {
        proxy = new CopyOnWriteArrayList<>();
        system = ProxySelector.getDefault();
        Authenticator.setDefault(new ProxyAuthenticator(this));
    }

    public synchronized void addAll(List<Proxy> items) {
        if (items.isEmpty()) return;
        Authenticator.setDefault(new ProxyAuthenticator(this));
        items.forEach(Proxy::init);
        proxy.addAll(items);
        proxy.sort(null);
        SpiderDebug.log("proxy", "selector add rules=%s total=%s", items.size(), proxy.size());
    }

    public synchronized void remove(String name) {
        int before = proxy.size();
        proxy.removeIf(item -> item.getName().equals(name));
        int removed = before - proxy.size();
        if (removed > 0) SpiderDebug.log("proxy", "selector remove name=%s removed=%s total=%s", name, removed, proxy.size());
    }

    public synchronized void clear() {
        Authenticator.setDefault(null);
        proxy.clear();
        SpiderDebug.log("proxy", "selector clear");
    }

    public List<Proxy> getProxy() {
        return proxy;
    }

    private List<java.net.Proxy> fallback(URI uri) {
        return system != null ? system.select(uri) : List.of(java.net.Proxy.NO_PROXY);
    }

    @Override
    public List<java.net.Proxy> select(URI uri) {
        String host = uri.getHost();
        if (proxy.isEmpty()) return fallback(uri, "no-rule");
        if (host == null) return fallback(uri, "no-host");
        if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) return fallback(uri, "local-target");
        for (Proxy item : proxy) {
            for (String rule : item.getHosts()) {
                if (!matches(host, rule)) continue;
                List<java.net.Proxy> selected = item.getProxies().isEmpty() ? fallback(uri, "empty-proxy") : item.getProxies();
                SpiderDebug.log("proxy", "select hit uri=%s host=%s rule=%s name=%s proxy=%s", uri, host, rule, item.getName(), selected);
                return selected;
            }
        }
        return fallback(uri, "no-match");
    }

    private List<java.net.Proxy> fallback(URI uri, String reason) {
        List<java.net.Proxy> selected = fallback(uri);
        SpiderDebug.log("proxy", "select fallback reason=%s uri=%s proxy=%s", reason, uri, selected);
        return selected;
    }

    private boolean matches(String host, String rule) {
        return "*".equals(rule) || Util.containOrMatch(host, rule);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        SpiderDebug.log("proxy", "connectFailed uri=%s address=%s error=%s", uri, socketAddress, e.getMessage());
        if (system != null) system.connectFailed(uri, socketAddress, e);
    }
}
