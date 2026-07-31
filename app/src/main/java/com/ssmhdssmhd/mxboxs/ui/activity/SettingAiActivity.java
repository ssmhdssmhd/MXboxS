package com.ssmhdssmhd.mxboxs.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;

/**
 * AI 智能设置页面
 * 提供画质优化、音质增强、流畅度优化等 AI 功能开关
 */
public class SettingAiActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private LinearLayout container;

    public static void start(android.app.Activity activity) {
        activity.startActivity(new Intent(activity, SettingAiActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_ai);

        toolbar = findViewById(R.id.toolbar);
        container = findViewById(R.id.container);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        initAiSettings();
    }

    private void initAiSettings() {
        // 画质优化组
        addSectionHeader("🎨 画质优化");

        addSwitchSetting("AI 画质增强", "智能提升分辨率与色彩",
            PlayerSetting.isAiQualityBoost(), PlayerSetting::putAiQualityBoost);

        addSwitchSetting("HDR 效果", "高动态范围图像增强",
            PlayerSetting.isAiHdr(), PlayerSetting::putAiHdr);

        addSwitchSetting("智能降噪", "去除画面噪点与压缩伪影",
            PlayerSetting.isAiDenoise(), PlayerSetting::putAiDenoise);

        addSwitchSetting("动态锐化", "AI 识别边缘并增强细节",
            PlayerSetting.isAiSharpness(), PlayerSetting::putAiSharpness);

        // 流畅度优化组
        addSectionHeader("⚡ 流畅度优化");

        addSwitchSetting("运动补偿", "插帧技术提升帧率至 60/120fps",
            PlayerSetting.isAiSmoothPlayback(), PlayerSetting::putAiSmoothPlayback);

        addSwitchSetting("自适应帧率", "根据内容自动调整帧率以节省电量",
            PlayerSetting.isAiAutoFrameRate(), PlayerSetting::putAiAutoFrameRate);

        // 音质优化组
        addSectionHeader("🎵 音质优化");

        addSwitchSetting("AI 音质增强", "智能提升清晰度与层次感",
            PlayerSetting.isAiAudioEnhance(), PlayerSetting::putAiAudioEnhance);

        addSwitchSetting("超重低音", "增强低频响应",
            PlayerSetting.isAiBassBoost(), PlayerSetting::putAiBassBoost);

        addSwitchSetting("对白增强", "智能提升人声清晰度",
            PlayerSetting.isAiDialogEnhance(), PlayerSetting::putAiDialogEnhance);
    }

    private void addSectionHeader(String title) {
        MaterialTextView header = new MaterialTextView(this);
        header.setText(title);
        header.setTextColor(getResources().getColor(R.color.white, getTheme()));
        header.setTextSize(18);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(24);
        params.bottomMargin = dpToPx(12);
        container.addView(header, params);
    }

    private void addSwitchSetting(String title, String summary, boolean initialValue, BiConsumer setter) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackgroundResource(R.drawable.shape_item);
        int padding = dpToPx(16);
        item.setPadding(padding, dpToPx(12), padding, dpToPx(12));

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        itemParams.bottomMargin = dpToPx(10);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);

        MaterialTextView titleView = new MaterialTextView(this);
        titleView.setText(title);
        titleView.setTextColor(getResources().getColor(R.color.white, getTheme()));
        titleView.setTextSize(16);
        textLayout.addView(titleView);

        MaterialTextView summaryView = new MaterialTextView(this);
        summaryView.setText(summary);
        summaryView.setTextColor(getResources().getColor(R.color.white, getTheme()));
        summaryView.setAlpha(0.7f);
        summaryView.setTextSize(13);
        textLayout.addView(summaryView);

        item.addView(textLayout, textParams);

        SwitchMaterial switchMaterial = new SwitchMaterial(this);
        switchMaterial.setChecked(initialValue);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        item.addView(switchMaterial, switchParams);

        container.addView(item, itemParams);

        switchMaterial.setOnCheckedChangeListener((buttonView, isChecked) -> setter.accept(isChecked));
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    @FunctionalInterface
    private interface BiConsumer {
        void accept(boolean value);
    }
}
