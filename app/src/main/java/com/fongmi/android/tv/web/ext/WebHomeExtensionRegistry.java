package com.fongmi.android.tv.web.ext;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebHomeExtensionRegistry {

    private static final WebHomeExtensionRegistry INSTANCE = new WebHomeExtensionRegistry();
    private static final String PREF_ENABLED = "web_home_ext_enabled_";
    private static final int SITE_ORDER_BASE = 10000;
    private static final int USER_ORDER_BASE = 20000;

    private final Map<String, List<WebHomeExtension>> ready;
    private final Map<String, WebHomeExtension> installed;
    private final Map<String, State> states;
    private final List<String> events;
    private final Object lock;

    private List<WebHomeExtension> globalExtensions;
    private JsonElement globalSources;
    private String globalBaseUrl;
    private String globalSignature;
    private String lastSiteKey;
    private boolean globalLoaded;
    private int lastMatchedCount;
    private int lastReadyCount;
    private int lastSourceCount;
    private long lastPreparedAt;

    private WebHomeExtensionRegistry() {
        this.ready = new ConcurrentHashMap<>();
        this.installed = new ConcurrentHashMap<>();
        this.states = new ConcurrentHashMap<>();
        this.events = new ArrayList<>();
        this.lock = new Object();
        this.globalExtensions = Collections.emptyList();
        this.globalBaseUrl = "";
        this.globalSignature = "";
        this.lastSiteKey = "";
    }

    public static WebHomeExtensionRegistry get() {
        return INSTANCE;
    }

    public void setGlobalSources(JsonElement sources, String baseUrl) {
        String signature = (sources == null || sources.isJsonNull() ? "" : sources.toString()) + "@" + (baseUrl == null ? "" : baseUrl);
        synchronized (lock) {
            if (signature.equals(globalSignature)) return;
            globalSources = sources == null || sources.isJsonNull() ? null : sources.deepCopy();
            globalBaseUrl = baseUrl == null ? "" : baseUrl;
            globalSignature = signature;
            globalExtensions = Collections.emptyList();
            globalLoaded = false;
            ready.clear();
            installed.clear();
            states.clear();
            event("sources updated");
        }
    }

    public void prepare(Site site, Runnable callback) {
        if (site == null || site.isEmpty()) {
            if (callback != null) App.post(callback);
            return;
        }
        String siteKey = site.getKey();
        if (!Setting.isWebHomeExtension()) {
            ready.remove(siteKey);
            if (callback != null) App.post(callback);
            return;
        }
        if (!hasGlobalSources() && WebHomeExtensionSourceStore.enabledCount() == 0 && isEmpty(site.getExtensions())) {
            ready.remove(siteKey);
            installed.clear();
            if (callback != null) App.post(callback);
            return;
        }
        Task.execute(() -> {
            try {
                List<WebHomeExtension> items = load(site);
                ready.put(siteKey, items);
                SpiderDebug.log("webhome-ext", "prepared site=%s matched=%s ready=%s", siteKey, lastMatchedCount, items.size());
            } catch (Throwable e) {
                ready.remove(siteKey);
                event("prepare failed " + e.getMessage());
                SpiderDebug.log("webhome-ext", "prepare failed site=%s error=%s", siteKey, e.getMessage());
            } finally {
                if (callback != null) App.post(callback);
            }
        });
    }

    public void refresh(Site site, Runnable callback) {
        synchronized (lock) {
            globalLoaded = false;
            globalExtensions = Collections.emptyList();
            ready.clear();
        }
        prepare(site == null || site.isEmpty() ? VodConfig.get().getHome() : site, callback);
    }

    public List<WebHomeExtension> get(String siteKey) {
        if (!Setting.isWebHomeExtension()) return Collections.emptyList();
        List<WebHomeExtension> items = ready.get(siteKey);
        return items == null ? Collections.emptyList() : items;
    }

    public void clear() {
        ready.clear();
        synchronized (lock) {
            globalLoaded = false;
            globalExtensions = Collections.emptyList();
            event("cache cleared");
        }
        Path.clear(Path.cache("webhome_ext"));
    }

    public void setExtensionEnabled(String id, boolean enabled) {
        if (TextUtils.isEmpty(id)) return;
        Prefers.put(prefKey(id), enabled);
        ready.clear();
        State state = state(id);
        state.status = enabled ? "enabled" : "disabled";
        state.reason = "";
        event((enabled ? "enabled " : "disabled ") + id);
    }

    public boolean isExtensionEnabled(WebHomeExtension extension) {
        SharedPreferences prefers = Prefers.getPrefers();
        String key = prefKey(extension.getId());
        return prefers.contains(key) ? prefers.getBoolean(key, extension.isDefaultEnabled()) : extension.isDefaultEnabled();
    }

    public Snapshot snapshot() {
        List<Item> items = new ArrayList<>();
        for (WebHomeExtension extension : installed.values()) {
            State state = states.get(extension.getId());
            items.add(new Item(extension, isExtensionEnabled(extension), state));
        }
        items.sort(Comparator.comparing(Item::sortKey));
        synchronized (lock) {
            return new Snapshot(
                    Setting.isWebHomeExtension(),
                    lastSiteKey,
                    lastSourceCount,
                    installed.size(),
                    lastMatchedCount,
                    lastReadyCount,
                    lastPreparedAt,
                    List.copyOf(items),
                    List.copyOf(events)
            );
        }
    }

    public void recordInject(WebHomeExtension extension, String siteKey, String targetRunAt) {
        State state = state(extension.getId());
        state.siteKey = siteKey;
        state.status = "injected";
        state.reason = targetRunAt;
        state.lastInjectAt = System.currentTimeMillis();
        event("inject " + extension.getId() + " " + targetRunAt);
    }

    public void recordScriptLog(JsonObject payload) {
        String message = payload == null ? "" : safeString(payload, "message");
        String id = "";
        if (payload != null && payload.has("data") && payload.get("data").isJsonObject()) id = safeString(payload.getAsJsonObject("data"), "id");
        if (TextUtils.isEmpty(id)) id = payload == null ? "" : safeString(payload, "id");
        if (TextUtils.isEmpty(id)) {
            event("script " + message);
            return;
        }
        State state = state(id);
        state.lastLog = message;
        state.lastLogAt = System.currentTimeMillis();
        event("script " + id + " " + message);
    }

    private List<WebHomeExtension> load(Site site) {
        String siteKey = site.getKey();
        List<WebHomeExtension> all = new ArrayList<>();
        all.addAll(loadGlobalExtensions());
        all.addAll(loadSiteExtensions(site));
        all.addAll(loadUserExtensions(site));
        installed.clear();
        rememberInstalled(all);

        List<WebHomeExtension> matched = new ArrayList<>();
        for (WebHomeExtension extension : dedupe(all)) {
            State state = state(extension.getId());
            state.siteKey = siteKey;
            if (!extension.matches(siteKey)) {
                state.status = "unmatched";
                state.reason = "";
            } else if (!isExtensionEnabled(extension)) {
                state.status = "disabled";
                state.reason = "";
            } else {
                matched.add(extension);
                state.status = "matched";
                state.reason = "";
            }
        }

        List<WebHomeExtension> resolved = resolveDependencies(matched);
        for (WebHomeExtension extension : resolved) {
            State state = state(extension.getId());
            state.status = "ready";
            state.reason = "";
        }
        synchronized (lock) {
            lastSiteKey = siteKey;
            lastSourceCount = sourceCount(site);
            lastMatchedCount = matched.size();
            lastReadyCount = resolved.size();
            lastPreparedAt = System.currentTimeMillis();
        }
        return resolved;
    }

    private List<WebHomeExtension> loadGlobalExtensions() {
        synchronized (lock) {
            if (globalLoaded) return new ArrayList<>(globalExtensions);
            if (!hasGlobalSources()) {
                globalLoaded = true;
                globalExtensions = Collections.emptyList();
                return Collections.emptyList();
            }
        }
        List<WebHomeExtension> result = new ArrayList<>();
        Order order = new Order(0);
        for (JsonElement element : elements(globalSources)) loadElement(result, element, "", globalBaseUrl, false, false, order);
        synchronized (lock) {
            globalExtensions = List.copyOf(result);
            globalLoaded = true;
            event("global loaded " + result.size());
        }
        rememberInstalled(result);
        return result;
    }

    private List<WebHomeExtension> loadSiteExtensions(Site site) {
        List<WebHomeExtension> result = new ArrayList<>();
        Order order = new Order(SITE_ORDER_BASE);
        for (JsonElement element : elements(site.getExtensions())) loadElement(result, element, site.getKey(), VodConfig.getUrl(), true, true, order);
        return result;
    }

    private List<WebHomeExtension> loadUserExtensions(Site site) {
        List<WebHomeExtension> result = new ArrayList<>();
        Order order = new Order(USER_ORDER_BASE);
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.enabledEntries()) {
            try {
                if (!entry.matches(site.getKey())) continue;
                loadElement(result, WebHomeExtensionSourceStore.parse(entry.getRaw()), site.getKey(), "", true, true, order);
            } catch (Throwable e) {
                event("user source skipped " + e.getMessage());
                SpiderDebug.log("webhome-ext", "user source skipped id=%s site=%s error=%s", entry.getId(), site.getKey(), e.getMessage());
            }
        }
        return result;
    }

    private void loadElement(List<WebHomeExtension> result, JsonElement element, String siteKey, String baseUrl, boolean siteScoped, boolean defaultEnabled, Order order) {
        try {
            if (element == null || element.isJsonNull()) return;
            if (element.isJsonPrimitive()) {
                loadRemote(result, element.getAsString(), siteKey, baseUrl, siteScoped, defaultEnabled, null, order);
                return;
            }
            if (!element.isJsonObject()) return;
            JsonObject object = element.getAsJsonObject();
            if (object.has("extensions")) {
                for (JsonElement item : elements(object.get("extensions"))) loadElement(result, item, siteKey, baseUrl, siteScoped, defaultEnabled(object, defaultEnabled), order);
                return;
            }
            boolean enabled = defaultEnabled(object, defaultEnabled);
            String manifestUrl = first(object, "manifestUrl", "manifest", "sourceUrl", "url");
            if (!TextUtils.isEmpty(manifestUrl) && !hasEntryScript(object)) {
                loadRemote(result, manifestUrl, siteKey, baseUrl, siteScoped, enabled, object, order);
                return;
            }
            add(result, WebHomeExtension.from(object, siteKey, "", baseUrl, siteScoped, enabled, order.next()));
        } catch (Throwable e) {
            event("load failed " + e.getMessage());
            SpiderDebug.log("webhome-ext", "load failed site=%s order=%s error=%s", siteKey, order.value, e.getMessage());
        }
    }

    private void loadRemote(List<WebHomeExtension> result, String url, String siteKey, String baseUrl, boolean siteScoped, boolean defaultEnabled, JsonObject wrapper, Order order) {
        String sourceUrl = WebHomeExtension.resolve(baseUrl, url == null ? "" : url.trim());
        if (TextUtils.isEmpty(sourceUrl)) return;
        JsonElement element = WebHomeExtension.isScriptUrl(sourceUrl) ? null : WebHomeExtension.json(sourceUrl);
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : elements(element)) loadElement(result, item, siteKey, sourceUrl, siteScoped, defaultEnabled, order);
            return;
        }
        JsonObject object = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        if (object.has("extensions")) {
            for (JsonElement item : elements(object.get("extensions"))) loadElement(result, item, siteKey, sourceUrl, siteScoped, defaultEnabled, order);
            return;
        }
        if (object.size() == 0 && WebHomeExtension.isScriptUrl(sourceUrl)) {
            object.addProperty("id", "remote_" + Integer.toHexString(sourceUrl.hashCode()));
            object.addProperty("name", sourceUrl.substring(sourceUrl.lastIndexOf('/') + 1));
            JsonArray js = new JsonArray();
            js.add(sourceUrl);
            object.add("js", js);
        }
        object = merge(object, wrapper);
        add(result, WebHomeExtension.from(object, siteKey, sourceUrl, sourceUrl, siteScoped, defaultEnabled, order.next()));
    }

    private JsonObject merge(JsonObject manifest, JsonObject wrapper) {
        JsonObject result = manifest == null ? new JsonObject() : manifest.deepCopy();
        if (wrapper == null) return result;
        for (Map.Entry<String, JsonElement> entry : wrapper.entrySet()) {
            String key = entry.getKey();
            if ("manifestUrl".equals(key) || "manifest".equals(key) || "sourceUrl".equals(key) || "url".equals(key)) continue;
            result.add(key, entry.getValue().deepCopy());
        }
        return result;
    }

    private List<WebHomeExtension> resolveDependencies(List<WebHomeExtension> input) {
        List<WebHomeExtension> active = dedupe(input);
        Map<String, WebHomeExtension> selected = mapById(active);
        boolean changed;
        do {
            changed = false;
            List<WebHomeExtension> removed = new ArrayList<>();
            for (WebHomeExtension extension : active) {
                String reason = dependencyFailure(extension, selected);
                if (TextUtils.isEmpty(reason)) continue;
                State state = state(extension.getId());
                state.status = "skipped";
                state.reason = reason;
                removed.add(extension);
                changed = true;
            }
            active.removeAll(removed);
            selected = mapById(active);
        } while (changed);
        return topoSort(active, selected);
    }

    private String dependencyFailure(WebHomeExtension extension, Map<String, WebHomeExtension> selected) {
        for (String spec : extension.getDepends()) {
            String id = extension.dependencyId(spec);
            WebHomeExtension dependency = selected.get(id);
            if (dependency == null) return dependencyReason(id);
            if (dependency.getRunAtOrder() > extension.getRunAtOrder()) return "依赖注入时机更晚: " + id;
            if (!extension.acceptsDependency(dependency, spec)) return "依赖版本不满足: " + spec;
        }
        return "";
    }

    private String dependencyReason(String id) {
        WebHomeExtension extension = installed.get(id);
        if (extension == null) return "缺少依赖: " + id;
        if (!isExtensionEnabled(extension)) return "依赖未启用: " + id;
        State state = states.get(id);
        if (state != null && "unmatched".equals(state.status)) return "依赖未匹配当前站点: " + id;
        return "依赖不可用: " + id;
    }

    private List<WebHomeExtension> topoSort(List<WebHomeExtension> input, Map<String, WebHomeExtension> selected) {
        List<WebHomeExtension> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingInt(WebHomeExtension::getRunAtOrder).thenComparingInt(WebHomeExtension::getOrder));
        List<WebHomeExtension> result = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (WebHomeExtension extension : sorted) if (!visit(extension, selected, visiting, visited, result)) return sorted;
        return result;
    }

    private boolean visit(WebHomeExtension extension, Map<String, WebHomeExtension> selected, Set<String> visiting, Set<String> visited, List<WebHomeExtension> result) {
        String id = extension.getId();
        if (visited.contains(id)) return true;
        if (!visiting.add(id)) {
            state(id).status = "skipped";
            state(id).reason = "依赖存在循环";
            return false;
        }
        for (String spec : extension.getDepends()) {
            WebHomeExtension dependency = selected.get(extension.dependencyId(spec));
            if (dependency != null && !visit(dependency, selected, visiting, visited, result)) return false;
        }
        visiting.remove(id);
        visited.add(id);
        if (!result.contains(extension)) result.add(extension);
        return true;
    }

    private void rememberInstalled(List<WebHomeExtension> extensions) {
        for (WebHomeExtension extension : extensions) {
            if (extension != null) installed.put(extension.getId(), extension);
        }
    }

    private List<WebHomeExtension> dedupe(List<WebHomeExtension> extensions) {
        LinkedHashMap<String, WebHomeExtension> map = new LinkedHashMap<>();
        for (WebHomeExtension extension : extensions) if (extension != null) map.put(extension.getId(), extension);
        return new ArrayList<>(map.values());
    }

    private Map<String, WebHomeExtension> mapById(List<WebHomeExtension> extensions) {
        Map<String, WebHomeExtension> map = new HashMap<>();
        for (WebHomeExtension extension : extensions) map.put(extension.getId(), extension);
        return map;
    }

    private void add(List<WebHomeExtension> result, WebHomeExtension extension) {
        if (extension != null) result.add(extension);
    }

    private boolean hasEntryScript(JsonObject object) {
        return object.has("js") || object.has("code");
    }

    private boolean defaultEnabled(JsonObject object, boolean fallback) {
        try {
            if (object.has("disabled") && object.get("disabled").isJsonPrimitive() && object.get("disabled").getAsBoolean()) return false;
            if (object.has("enabled") && object.get("enabled").isJsonPrimitive()) return object.get("enabled").getAsBoolean();
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private boolean hasGlobalSources() {
        synchronized (lock) {
            return !isEmpty(globalSources);
        }
    }

    private boolean isEmpty(JsonElement element) {
        if (element == null || element.isJsonNull()) return true;
        if (element.isJsonArray()) return element.getAsJsonArray().isEmpty();
        if (element.isJsonPrimitive()) return TextUtils.isEmpty(element.getAsString().trim());
        return false;
    }

    private List<JsonElement> elements(JsonElement element) {
        List<JsonElement> result = new ArrayList<>();
        if (element == null || element.isJsonNull()) return result;
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) result.add(item);
        } else {
            result.add(element);
        }
        return result;
    }

    private String first(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) continue;
            String value = object.get(key).getAsString().trim();
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String safeString(JsonObject object, String key) {
        try {
            return object.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private int sourceCount(Site site) {
        return WebHomeExtensionSourceStore.enabledCount(site.getKey()) + elements(globalSources).size() + elements(site.getExtensions()).size();
    }

    private State state(String id) {
        return states.computeIfAbsent(id, State::new);
    }

    private String prefKey(String id) {
        return PREF_ENABLED + Util.md5(id);
    }

    private void event(String message) {
        synchronized (lock) {
            events.add(System.currentTimeMillis() + " " + message);
            while (events.size() > 60) events.remove(0);
        }
        SpiderDebug.log("webhome-ext", message);
    }

    private static class Order {
        private int value;

        private Order(int value) {
            this.value = value;
        }

        private int next() {
            return value++;
        }
    }

    private static class State {
        private final String id;
        private String siteKey = "";
        private String status = "";
        private String reason = "";
        private String lastLog = "";
        private long lastInjectAt;
        private long lastLogAt;

        private State(String id) {
            this.id = id;
        }
    }

    public static class Snapshot {
        public final boolean enabled;
        public final String siteKey;
        public final int sourceCount;
        public final int installedCount;
        public final int matchedCount;
        public final int readyCount;
        public final long preparedAt;
        public final List<Item> items;
        public final List<String> events;

        private Snapshot(boolean enabled, String siteKey, int sourceCount, int installedCount, int matchedCount, int readyCount, long preparedAt, List<Item> items, List<String> events) {
            this.enabled = enabled;
            this.siteKey = siteKey;
            this.sourceCount = sourceCount;
            this.installedCount = installedCount;
            this.matchedCount = matchedCount;
            this.readyCount = readyCount;
            this.preparedAt = preparedAt;
            this.items = items;
            this.events = events;
        }
    }

    public static class Item {
        public final String id;
        public final String name;
        public final String version;
        public final String sourceUrl;
        public final String updateUrl;
        public final String runAt;
        public final String matchText;
        public final String excludeText;
        public final String dependsText;
        public final String siteKey;
        public final String status;
        public final String reason;
        public final String lastLog;
        public final boolean enabled;
        public final boolean defaultEnabled;
        public final boolean siteScoped;
        public final boolean remote;
        public final long lastInjectAt;
        public final long lastLogAt;

        private Item(WebHomeExtension extension, boolean enabled, State state) {
            this.id = extension.getId();
            this.name = extension.getName();
            this.version = extension.getVersion();
            this.sourceUrl = extension.getSourceUrl();
            this.updateUrl = extension.getUpdateUrl();
            this.runAt = extension.getRunAt();
            this.matchText = extension.matchText();
            this.excludeText = extension.excludeText();
            this.dependsText = extension.dependsText();
            this.enabled = enabled;
            this.defaultEnabled = extension.isDefaultEnabled();
            this.siteScoped = extension.isSiteScoped();
            this.remote = extension.isRemote();
            this.siteKey = state == null ? "" : state.siteKey;
            this.status = state == null ? "" : state.status;
            this.reason = state == null ? "" : state.reason;
            this.lastLog = state == null ? "" : state.lastLog;
            this.lastInjectAt = state == null ? 0 : state.lastInjectAt;
            this.lastLogAt = state == null ? 0 : state.lastLogAt;
        }

        private String sortKey() {
            return (enabled ? "0" : "1") + "_" + name + "_" + id;
        }
    }
}
