package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.impl.Diffable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class BaseDiffAdapter<T extends Diffable<T>, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected final AsyncListDiffer<T> differ;

    public BaseDiffAdapter() {
        this.differ = new AsyncListDiffer<>(this, new BaseItemCallback<T>());
    }

    private boolean listsAreSame(List<T> oldList, List<T> newList) {
        if (oldList.size() != newList.size()) return false;
        for (int i = 0; i < oldList.size(); i++) {
            T oldItem = oldList.get(i);
            T newItem = newList.get(i);
            if (!oldItem.isSameItem(newItem) || !oldItem.isSameContent(newItem)) return false;
        }
        return true;
    }

    public T getItem(int position) {
        return differ.getCurrentList().get(position);
    }

    public List<T> getItems() {
        return differ.getCurrentList();
    }

    public void setItems(List<T> items) {
        setItems(items, () -> {});
    }

    public void setItems(List<T> items, Runnable runnable) {
        differ.submitList(Objects.requireNonNullElseGet(items, ArrayList::new), runnable);
    }

    public void setItems(List<T> items, Callback callback) {
        List<T> oldItems = getItems();
        List<T> newItems = Objects.requireNonNullElseGet(items, ArrayList::new);
        boolean hasChange = !listsAreSame(oldItems, newItems);
        if (!hasChange) callback.onUpdateFinished(false);
        else differ.submitList(newItems, () -> callback.onUpdateFinished(true));
    }

    public void add(T item) {
        add(item, null);
    }

    public void add(T item, Runnable runnable) {
        List<T> current = new ArrayList<>(getItems());
        current.add(item);
        setItems(current, runnable);
    }

    public void addAll(List<T> items) {
        addAll(items, null);
    }

    public void addAll(List<T> items, Runnable runnable) {
        List<T> current = new ArrayList<>(getItems());
        current.addAll(items);
        setItems(current, runnable);
    }

    public void sort(T item) {
        sort(item, null);
    }

    public void sort(T item, Runnable runnable) {
        List<T> current = Stream.concat(getItems().stream(), Stream.of(item)).distinct().sorted().toList();
        setItems(current, runnable);
    }

    public void sort(List<T> items) {
        sort(items, null);
    }

    public void sort(List<T> items, Runnable runnable) {
        List<T> current = Stream.concat(getItems().stream(), items.stream()).distinct().sorted().toList();
        setItems(current, runnable);
    }

    public void remove(T item) {
        remove(item, null);
    }

    public void remove(T item, Runnable runnable) {
        List<T> current = new ArrayList<>(getItems());
        if (current.remove(item)) setItems(current, runnable);
    }

    public void clear() {
        clear(null);
    }

    public void clear(Runnable runnable) {
        setItems(new ArrayList<>(), runnable);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public abstract VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType);

    @Override
    public abstract void onBindViewHolder(@NonNull VH holder, int position);

    public interface Callback {

        void onUpdateFinished(boolean hasChange);
    }
}
