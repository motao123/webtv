package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.FragmentSettingEnhanceBinding;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.CustomCspDialog;
import com.fongmi.android.tv.ui.dialog.DebugLogDialog;
import com.fongmi.android.tv.ui.dialog.ManagePageDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;
import com.fongmi.android.tv.ui.dialog.SiteHealthDialog;
import com.fongmi.android.tv.ui.dialog.WebHomeExtensionDialog;
import com.fongmi.android.tv.utils.FamilyFilter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;

public class SettingEnhanceFragment extends BaseFragment {

    private FragmentSettingEnhanceBinding mBinding;

    public static SettingEnhanceFragment newInstance() {
        return new SettingEnhanceFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_enable : R.string.setting_disable);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingEnhanceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.siteHealthSort.setOnClickListener(view -> SiteHealthDialog.show(this, this::setText));
        mBinding.siteHealthSort.setOnLongClickListener(this::clearSiteHealth);
        mBinding.webHomeExtension.setOnClickListener(view -> WebHomeExtensionDialog.show(this, this::setText));
        mBinding.webHomeExtension.setOnLongClickListener(this::clearWebHomeExtension);
        mBinding.familyFilter.setOnClickListener(this::setFamilyFilter);
        mBinding.managePage.setOnClickListener(view -> ManagePageDialog.show(this));
        mBinding.shellProxy.setOnClickListener(view -> ShellProxyDialog.show(this, this::setText));
        mBinding.shellProxy.setOnLongClickListener(v -> false);
        mBinding.shellProxyConfig.setVisibility(View.GONE);
        mBinding.customCsp.setOnClickListener(view -> CustomCspDialog.show(this, this::setText));
        mBinding.oneKeySync.setOnClickListener(v -> OneKeySyncDialog.create().show(requireActivity()));
    }

    private void setText() {
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        mBinding.siteHealthSortText.setText(getSwitch(Setting.isSiteHealthSort()));
        WebHomeExtensionRegistry.Snapshot webHomeExtension = WebHomeExtensionRegistry.get().snapshot();
        mBinding.webHomeExtensionText.setText(getSwitch(Setting.isWebHomeExtension()) + " · " + webHomeExtension.readyCount + "/" + webHomeExtension.installedCount);
        mBinding.familyFilterText.setText(Setting.isFamilyFilter() ? getString(R.string.setting_family_filter_summary_on, FamilyFilter.keywords().size()) : getString(R.string.setting_family_filter_summary_off));
        mBinding.managePageText.setText(R.string.manage_page_web);
        mBinding.shellProxyText.setText(getSwitch(Setting.isShellProxy()) + " · " + getString(R.string.setting_proxy_rule_count, ProxySetting.count()));
        mBinding.shellProxyConfigText.setText(getString(R.string.setting_proxy_rule_count, ProxySetting.count()));
        CustomCspSetting.Registry registry = CustomCspSetting.load();
        CustomCspSetting.Count count = CustomCspSetting.count();
        mBinding.customCspText.setText(getSwitch(registry.isEnabled()) + " · " + getString(R.string.setting_custom_csp_count, count.active(), count.enabled()));
    }

    private void setDebugLog(View view) {
        Setting.putDebugLog(!Setting.isDebugLog());
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        if (!Setting.isDebugLog()) return;
        DebugLogDialog.show(this);
    }

    private void setFamilyFilter(View view) {
        EditText input = new EditText(requireContext());
        input.setText(Setting.getFamilyFilterKeywords());
        input.setHint(R.string.setting_family_filter_keywords_hint);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.setting_family_filter)
                .setMultiChoiceItems(new CharSequence[]{getString(R.string.setting_enable)}, new boolean[]{Setting.isFamilyFilter()}, (dialog, which, isChecked) -> Setting.putFamilyFilter(isChecked))
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putFamilyFilterKeywords(input.getText().toString());
                    setText();
                    Notify.show(R.string.setting_family_filter_saved);
                }).show();
    }

    private boolean clearSiteHealth(View view) {
        SiteHealthStore.clear();
        Notify.show(R.string.site_health_clear_done);
        return true;
    }

    private boolean clearWebHomeExtension(View view) {
        WebHomeExtensionRegistry.get().clear();
        Notify.show(R.string.web_home_extension_clear_done);
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }

    @Override
    public void onResume() {
        super.onResume();
        setText();
    }
}
