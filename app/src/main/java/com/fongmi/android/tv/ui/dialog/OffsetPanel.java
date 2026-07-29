package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.media3.common.C;

import com.fongmi.android.tv.databinding.DialogOffsetBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.google.android.material.slider.Slider;

import java.util.Locale;
import java.util.function.LongConsumer;

final class OffsetPanel {

    private final DialogOffsetBinding binding;
    private final PlayerManager player;
    private final int type;

    OffsetPanel(DialogOffsetBinding binding, PlayerManager player, int type) {
        this.binding = binding;
        this.player = player;
        this.type = type;
    }

    void bind() {
        setupOffset(binding.audioSlider, binding.audioValue, player.getAudioOffsetMs(), player::setAudioOffsetMs);
        setupOffset(binding.textSlider, binding.textValue, player.getTextOffsetMs(), player::setTextOffsetMs);
        binding.reset.setOnClickListener(this::onReset);
        getSection().setVisibility(View.VISIBLE);
        getSlider().requestFocus();
    }

    private ViewGroup getSection() {
        return type == C.TRACK_TYPE_AUDIO ? binding.audioSection : binding.textSection;
    }

    private Slider getSlider() {
        return type == C.TRACK_TYPE_AUDIO ? binding.audioSlider : binding.textSlider;
    }

    private TextView getLabel() {
        return type == C.TRACK_TYPE_AUDIO ? binding.audioValue : binding.textValue;
    }

    private void onReset(View view) {
        getSlider().setValue(0);
        getLabel().setText(format(0));
        if (type == C.TRACK_TYPE_TEXT) player.setTextOffsetMs(0);
        if (type == C.TRACK_TYPE_AUDIO) player.setAudioOffsetMs(0);
    }

    private void setupOffset(Slider slider, TextView label, long valueMs, LongConsumer setter) {
        float clamped = snapToStep(slider, valueMs);
        slider.clearOnChangeListeners();
        slider.setLabelFormatter(this::format);
        slider.setValue(clamped);
        label.setText(format(clamped));
        slider.addOnChangeListener((source, value, fromUser) -> {
            if (!fromUser) return;
            float snapped = snapToStep(source, value);
            setter.accept(Math.round(snapped));
            label.setText(format(snapped));
        });
    }

    private float snapToStep(Slider slider, float value) {
        float step = slider.getStepSize();
        float clamped = Math.clamp(value, slider.getValueFrom(), slider.getValueTo());
        if (step <= 0) return clamped;
        float snapped = slider.getValueFrom() + Math.round((clamped - slider.getValueFrom()) / step) * step;
        return Math.clamp(snapped, slider.getValueFrom(), slider.getValueTo());
    }

    private String format(float value) {
        return String.format(Locale.getDefault(), "%+.1fs", value / 1000f);
    }
}
