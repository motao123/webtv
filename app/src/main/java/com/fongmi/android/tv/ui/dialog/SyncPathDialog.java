package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSyncPathDirBinding;
import com.fongmi.android.tv.databinding.DialogSyncPathBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SyncFiles;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SyncPathDialog extends BaseAlertDialog {

    private static final int TREE_LIMIT = 300;

    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Item> items = new ArrayList<>();

    private DialogSyncPathBinding binding;
    private DirAdapter adapter;
    private Runnable callback;
    private String current = "";

    public static void show(Fragment fragment, Runnable callback) {
        SyncPathDialog dialog = new SyncPathDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSyncPathBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        selected.addAll(SyncFiles.getPaths(Setting.getSyncPaths()));
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 6));
        binding.recycler.setAdapter(adapter = new DirAdapter());
        load("");
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(v -> up());
        binding.refresh.setOnClickListener(v -> load(current));
        binding.reset.setOnClickListener(v -> reset());
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(ResUtil.isLand(requireContext()) ? 0.52f : 0.92f);
    }

    private void load(String path) {
        current = SyncFiles.normalize(path);
        items.clear();
        File root;
        File dir;
        try {
            root = Path.root().getCanonicalFile();
            dir = current.isEmpty() ? root : new File(root, current).getCanonicalFile();
            if (!inside(root, dir) || !dir.isDirectory()) {
                current = "";
                dir = root;
            }
            File[] files = dir.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
            if (files != null) Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            int count = 0;
            for (File file : files == null ? new File[0] : files) {
                if (count++ >= TREE_LIMIT) break;
                String child = relativeTo(root, file);
                if (!child.isEmpty()) items.add(new Item(file.getName(), child));
            }
        } catch (Exception e) {
            current = "";
        }
        adapter.notifyDataSetChanged();
        updateState();
    }

    private void up() {
        if (current.isEmpty()) return;
        int index = current.lastIndexOf('/');
        load(index < 0 ? "" : current.substring(0, index));
    }

    private void reset() {
        selected.clear();
        selected.addAll(SyncFiles.getPaths(SyncFiles.DEFAULT_PATHS));
        adapter.notifyDataSetChanged();
        updateState();
    }

    private void toggle(String path) {
        path = SyncFiles.normalize(path);
        if (path.isEmpty()) return;
        if (selected.contains(path)) selected.remove(path);
        else selected.add(path);
        adapter.notifyDataSetChanged();
        updateState();
    }

    private void onPositive() {
        Setting.putSyncPaths(SyncFiles.getPathsText(new ArrayList<>(selected)));
        if (callback != null) callback.run();
        dismiss();
    }

    private void updateState() {
        binding.current.setText(current.isEmpty() ? "/" : "/" + current);
        binding.up.setEnabled(!current.isEmpty());
        binding.recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        List<String> paths = SyncFiles.getPaths(SyncFiles.getPathsText(new ArrayList<>(selected)));
        selected.clear();
        selected.addAll(paths);
        binding.summary.setText(paths.isEmpty() ? getString(R.string.sync_paths_selected_empty) : getString(R.string.sync_paths_selected, TextUtils.join(", ", paths)));
    }

    private boolean inside(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private String relativeTo(File root, File file) throws IOException {
        return root.toPath().relativize(file.getCanonicalFile().toPath()).toString().replace(File.separatorChar, '/');
    }

    private static class Item {

        private final String name;
        private final String path;

        private Item(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    private class DirAdapter extends RecyclerView.Adapter<DirAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterSyncPathDirBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterSyncPathDirBinding binding;

            private ViewHolder(@NonNull AdapterSyncPathDirBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            private void bind(Item item) {
                binding.name.setText(item.name);
                binding.path.setText(item.path);
                binding.check.setChecked(selected.contains(item.path));
                binding.getRoot().setOnClickListener(v -> toggle(item.path));
                binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    load(item.path);
                    return true;
                });
                binding.enter.setOnClickListener(v -> load(item.path));
            }
        }
    }
}
