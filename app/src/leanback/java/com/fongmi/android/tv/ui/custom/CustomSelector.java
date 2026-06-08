package com.fongmi.android.tv.ui.custom;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import java.util.LinkedHashSet;
import java.util.Set;

public class CustomSelector extends PresenterSelector {

    private final Set<Presenter> presenters = new LinkedHashSet<>();
    private final ArrayMap<Key, Presenter> cacheMap = new ArrayMap<>();
    private final ArrayMap<Class<?>, Presenter> singleMap = new ArrayMap<>();
    private final ArrayMap<Class<?>, ArrayMap<Class<? extends Presenter>, Presenter>> nestedMap = new ArrayMap<>();

    public void addPresenter(Class<?> cls, Presenter presenter) {
        singleMap.put(cls, presenter);
        presenters.add(presenter);
        cacheMap.clear();
    }

    public void addPresenter(Class<?> cls, Presenter presenter, Class<? extends Presenter> childType) {
        nestedMap.computeIfAbsent(cls, k -> new ArrayMap<>()).put(childType, presenter);
        presenters.add(presenter);
        cacheMap.clear();
    }

    @Override
    public Presenter getPresenter(Object item) {
        if (item == null) return null;
        Class<?> cls = item.getClass();
        Presenter presenter = singleMap.get(cls);
        if (presenter != null) return presenter;
        ArrayMap<Class<? extends Presenter>, Presenter> map = nestedMap.get(cls);
        if (map == null || map.isEmpty()) return null;
        if (map.size() == 1) {
            Presenter only = map.valueAt(0);
            Class<? extends Presenter> childType = map.keyAt(0);
            cacheMap.put(new Key(cls, childType), only);
            return only;
        }
        if (item instanceof ListRow row) {
            if (row.getAdapter() == null) return null;
            Presenter child = row.getAdapter().getPresenter(row);
            if (child == null) return null;
            Class<?> childCls = child.getClass();
            Key key = new Key(cls, childCls);
            Presenter cached = cacheMap.get(key);
            if (cached != null) return cached;
            for (Class<?> type = childCls; type != null && type != Object.class; type = type.getSuperclass()) {
                Presenter found = map.get(type);
                if (found != null) {
                    cacheMap.put(key, found);
                    return found;
                }
            }
        }
        return null;
    }

    @NonNull
    @Override
    public Presenter[] getPresenters() {
        return presenters.toArray(new Presenter[0]);
    }

    private record Key(Class<?> parent, Class<?> child) {
    }
}