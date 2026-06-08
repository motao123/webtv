package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterQualityBinding;

public class QualityAdapter extends RecyclerView.Adapter<QualityAdapter.ViewHolder> {

    private final OnClickListener listener;
    private Result result;
    private int position;

    public QualityAdapter(OnClickListener listener) {
        this.listener = listener;
        this.result = Result.empty();
    }

    public interface OnClickListener {

        void onItemClick(Result result);
    }

    public int getPosition() {
        return position;
    }

    public void addAll(Result result) {
        this.result = result;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return result.getUrl().getValues().size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterQualityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setText(result.getUrl().n(position));
        holder.binding.text.setOnClickListener(v -> onItemClick(position));
        holder.binding.text.setSelected(result.getUrl().getPosition() == position);
    }

    private void onItemClick(int position) {
        this.position = position;
        result.getUrl().set(position);
        listener.onItemClick(result);
        notifyItemRangeChanged(0, getItemCount());
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQualityBinding binding;

        ViewHolder(@NonNull AdapterQualityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}