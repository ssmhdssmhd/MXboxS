package com.ssmhdssmhd.mxboxs.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaMetadata;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.api.DanmakuApi;
import com.ssmhdssmhd.mxboxs.bean.Danmaku;
import com.ssmhdssmhd.mxboxs.databinding.DialogDanmakuSearchBinding;
import com.ssmhdssmhd.mxboxs.impl.DanmakuListener;
import com.ssmhdssmhd.mxboxs.player.PlayerManager;
import com.ssmhdssmhd.mxboxs.setting.DanmakuSetting;
import com.ssmhdssmhd.mxboxs.ui.adapter.DanmakuAdapter;
import com.ssmhdssmhd.mxboxs.ui.custom.SpaceItemDecoration;
import com.ssmhdssmhd.mxboxs.utils.KeyUtil;
import com.ssmhdssmhd.mxboxs.utils.Notify;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.Util;

import java.util.List;

public final class DanmakuSearchDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener, DanmakuListener {

    private final DanmakuAdapter adapter;
    private DialogDanmakuSearchBinding binding;
    private PlayerManager player;

    public DanmakuSearchDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuSearchDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        Util.showKeyboard(binding.keyword);
        setKeyword(getTitle());
    }

    @Override
    protected void initEvent() {
        binding.setting.setOnClickListener(this::onSetting);
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !getKeyword().isEmpty()) search();
            return true;
        });
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && binding.recycler.getVisibility() == VISIBLE) return binding.recycler.requestFocus();
            return false;
        });
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.setDanmaku(item);
        dismiss();
    }

    private CharSequence getTitle() {
        MediaMetadata metadata = player.getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.title) ? "" : metadata.title;
    }

    private String getEpisode() {
        MediaMetadata metadata = player.getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.artist) ? "" : metadata.artist.toString().trim();
    }

    private void setKeyword(CharSequence text) {
        if (text == null) text = "";
        binding.keyword.setText(text);
        binding.keyword.setSelection(text.length());
    }

    private String getKeyword() {
        CharSequence text = binding.keyword.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void onSetting(View view) {
        DanmakuApiDialog.show(this);
    }

    private void showProgress() {
        binding.recycler.setVisibility(GONE);
        binding.progress.setVisibility(VISIBLE);
    }

    private void showResults(boolean empty) {
        binding.progress.setVisibility(GONE);
        binding.recycler.setVisibility(empty ? GONE : VISIBLE);
        if (!empty) binding.recycler.requestFocus();
    }

    private void search() {
        showProgress();
        adapter.clear();
        Util.hideKeyboard(binding.keyword);
        DanmakuApi.search(getKeyword(), getEpisode(), this::onSuccess, this::onError);
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        if (!getKeyword().isEmpty() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl())) search();
    }

    private void onSuccess(List<Danmaku> items) {
        adapter.addAll(items);
        showResults(items.isEmpty());
        if (items.isEmpty()) Notify.show(R.string.error_empty);
    }

    private void onError(Exception e) {
        showResults(true);
        Notify.show(TextUtils.isEmpty(e.getMessage()) ? ResUtil.getString(R.string.error_empty) : e.getMessage());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        DanmakuApi.cancel();
        super.onDestroyView();
    }
}
