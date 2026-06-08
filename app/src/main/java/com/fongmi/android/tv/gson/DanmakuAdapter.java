package com.fongmi.android.tv.gson;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.github.catvod.utils.Json;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DanmakuAdapter implements JsonDeserializer<List<Danmaku>> {

    @Override
    public List<Danmaku> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        List<Danmaku> items = json.isJsonPrimitive() ? parsePrimitive(json.getAsString().trim(), typeOfT) : App.gson().fromJson(json, typeOfT);
        return items.stream().filter(d -> !d.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Danmaku> parsePrimitive(String text, Type type) {
        return Json.isArray(text) ? App.gson().fromJson(text, type) : List.of(Danmaku.from(text));
    }
}
