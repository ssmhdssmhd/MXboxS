package com.ssmhdssmhd.mxboxs.ui.holder;

import androidx.annotation.NonNull;

import com.ssmhdssmhd.mxboxs.bean.Episode;
import com.ssmhdssmhd.mxboxs.databinding.AdapterEpisodeHoriBinding;
import com.ssmhdssmhd.mxboxs.ui.adapter.EpisodeAdapter;
import com.ssmhdssmhd.mxboxs.ui.base.BaseEpisodeHolder;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;

public class EpisodeHoriHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeHoriBinding binding;
    private final int maxWidth;

    public EpisodeHoriHolder(@NonNull AdapterEpisodeHoriBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(32);
    }

    @Override
    public void initView(Episode item) {
        binding.text.setMaxWidth(maxWidth);
        binding.text.setSelected(item.isSelected());
        binding.text.setText(item.getDesc().concat(item.getName()));
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }
}
