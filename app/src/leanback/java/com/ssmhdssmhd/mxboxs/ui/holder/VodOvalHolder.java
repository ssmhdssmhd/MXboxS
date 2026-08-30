package com.ssmhdssmhd.mxboxs.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.ssmhdssmhd.mxboxs.bean.Vod;
import com.ssmhdssmhd.mxboxs.databinding.AdapterVodOvalBinding;
import com.ssmhdssmhd.mxboxs.ui.base.BaseVodHolder;
import com.ssmhdssmhd.mxboxs.ui.presenter.VodPresenter;
import com.ssmhdssmhd.mxboxs.utils.ImgUtil;

public class VodOvalHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodOvalBinding binding;

    public VodOvalHolder(@NonNull AdapterVodOvalBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    public VodOvalHolder size(int[] size) {
        binding.image.getLayoutParams().width = size[0];
        binding.image.getLayoutParams().height = size[1];
        return this;
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.name.setVisibility(item.getNameVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}
