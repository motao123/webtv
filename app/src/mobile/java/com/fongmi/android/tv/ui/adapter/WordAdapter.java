package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.AdapterSearchWordBinding;

public class WordAdapter extends BaseDiffAdapter<Word.Data, WordAdapter.ViewHolder> {

    private final OnClickListener listener;

    public WordAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(String text);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Word.Data item = getItem(position);
        holder.binding.text.setText(item.getTitle());
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(item.getTitle()));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchWordBinding binding;

        public ViewHolder(@NonNull AdapterSearchWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
