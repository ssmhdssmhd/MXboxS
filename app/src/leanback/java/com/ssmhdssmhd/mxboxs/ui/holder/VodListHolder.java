package com.ssmhdssmhd.mxboxs.ui.holder;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.ssmhdssmhd.mxboxs.bean.Vod;
import com.ssmhdssmhd.mxboxs.databinding.AdapterVodListBinding;
import com.ssmhdssmhd.mxboxs.ui.base.BaseVodHolder;
import com.ssmhdssmhd.mxboxs.ui.presenter.VodPresenter;
import com.ssmhdssmhd.mxboxs.utils.ImgUtil;

public class VodListHolder extends BaseVodHolder {

    private final VodPresenter.OnClickListener listener;
    private final AdapterVodListBinding binding;

    public VodListHolder(@NonNull AdapterVodListBinding binding, VodPresenter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getName());
        binding.remark.setText(item.getRemarks());
        binding.name.setVisibility(item.getNameVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getName(), item.getPic(), binding.image, true);
    }

    @Override
    public void unbind() {
        Glide.with(binding.image).clear(binding.image);
    }
}
