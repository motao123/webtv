package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterFileBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<File> mItems;

    public FileAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(File file);
    }

    public void addAll(List<File> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = mItems.get(position);
        holder.binding.name.setText(file.getName());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(file));
        holder.binding.image.setImageResource(file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterFileBinding binding;

        ViewHolder(@NonNull AdapterFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
