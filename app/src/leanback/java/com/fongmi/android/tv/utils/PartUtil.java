package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartUtil {

    private static final Pattern PATTERN_SEASON = Pattern.compile("^(.*?)第");
    private static final Pattern PATTERN_YEAR = Pattern.compile("^(.*)(19|20)\\d{2}$");

    public static List<String> split(String text) {
        Set<String> items = new LinkedHashSet<>();
        String[] rawSplits;
        if (text.contains("：")) rawSplits = text.split("：");
        else if (text.contains("第") && text.contains("季")) rawSplits = extractSeason(text);
        else if (text.contains("(")) rawSplits = new String[]{text.split(Pattern.quote("("))[0]};
        else if (text.contains(" ")) rawSplits = text.split(" ");
        else rawSplits = extractYear(text);
        items.add(text);
        Arrays.stream(rawSplits).map(String::trim).map(s -> s.contains(" ") ? s.split(" ")[0].trim() : s).filter(s -> !s.isEmpty()).forEach(items::add);
        return new ArrayList<>(items);
    }

    private static String[] extractSeason(String text) {
        Matcher matcher = PATTERN_SEASON.matcher(text);
        if (matcher.find()) {
            String s = matcher.group(1).trim();
            if (!s.isEmpty()) return new String[]{s};
        }
        return Arrays.stream(text.split("第")).filter(s -> !s.isEmpty() && !s.contains("季")).toArray(String[]::new);
    }

    private static String[] extractYear(String text) {
        Matcher matcher = PATTERN_YEAR.matcher(text);
        if (matcher.find()) {
            String s = matcher.group(1).trim();
            if (!s.isEmpty()) return new String[]{s};
        }
        return new String[0];
    }
}
