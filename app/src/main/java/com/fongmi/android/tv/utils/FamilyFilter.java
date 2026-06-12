package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FamilyFilter {

    public static boolean enabled() {
        return Setting.isFamilyFilter();
    }

    public static List<String> keywords() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String line : Setting.getFamilyFilterKeywords().split("[\\r\\n,，;；]+")) {
            String value = normalize(line);
            if (!value.isEmpty()) items.add(value);
        }
        return new ArrayList<>(items);
    }

    public static Result apply(Result result) {
        if (!enabled() || result == null) return result;
        List<String> words = keywords();
        if (words.isEmpty()) return result;

        List<Class> types = new ArrayList<>();
        for (Class type : result.getTypes()) if (!match(type.getTypeName(), words)) types.add(type);
        result.setTypes(types);

        List<Vod> vods = new ArrayList<>();
        for (Vod vod : result.getList()) if (!match(vod, words)) vods.add(vod);
        result.setList(vods);

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (var entry : result.getFilters().entrySet()) {
            List<Filter> keep = new ArrayList<>();
            for (Filter filter : entry.getValue()) {
                if (match(filter.getName(), words)) continue;
                List<Value> values = new ArrayList<>();
                for (Value value : filter.getValue()) if (!match(join(value.getN(), value.getV()), words)) values.add(value);
                if (!values.isEmpty()) {
                    Filter copy = filter.copy();
                    copy.setValue(values);
                    keep.add(copy);
                }
            }
            if (!keep.isEmpty()) filters.put(entry.getKey(), keep);
        }
        result.setFilters(filters);
        result.getTypes().forEach(type -> {
            if (filters.containsKey(type.getTypeId())) type.setFilters(filters.get(type.getTypeId()));
            else type.setFilters(new ArrayList<>());
        });
        return result;
    }

    public static boolean match(Vod vod, List<String> words) {
        return match(join(vod.getTypeName(), vod.getName(), vod.getRemarks(), vod.getContent(), vod.getTag(), vod.getActor(), vod.getDirector()), words);
    }

    public static boolean match(String text, List<String> words) {
        String value = normalize(text);
        if (value.isEmpty()) return false;
        for (String word : words) if (!word.isEmpty() && value.contains(word)) return true;
        return false;
    }

    private static String join(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
