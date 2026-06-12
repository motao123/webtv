package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityConfigCenterBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class ConfigCenterActivity extends BaseActivity implements ConfigAdapter.OnClickListener, ConfigListener {

    private ActivityConfigCenterBinding binding;
    private ConfigAdapter adapter;
    private int type;

    public static void start(Activity activity, int type) {
        Intent intent = new Intent(activity, ConfigCenterActivity.class);
        intent.putExtra("type", type);
        activity.startActivity(intent);
    }

    private int getTypeValue() {
        return getIntent().getIntExtra("type", 0);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityConfigCenterBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        type = getTypeValue();
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle(type == 0 ? R.string.config_center_vod : type == 1 ? R.string.config_center_live : R.string.config_center_wall);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 12));
        binding.recycler.setAdapter(adapter = new ConfigAdapter(this).readOnly(false).addAll(type));
        binding.progressLayout.showContent(true, adapter.getItemCount());
    }

    @Override
    public void onTextClick(Config item) {
        setConfig(item);
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item) == 0) binding.progressLayout.showContent(true, 0);
    }

    @Override
    public void setConfig(Config config) {
        if (type == 0) VodActivityRouter.loadVod(this, config);
        else if (type == 1) VodActivityRouter.loadLive(this, config);
        else VodActivityRouter.loadWall(this, config);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        adapter = new ConfigAdapter(this).readOnly(false).addAll(type);
        binding.recycler.setAdapter(adapter);
        binding.progressLayout.showContent(true, adapter.getItemCount());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        return super.onOptionsItemSelected(item);
    }
}
