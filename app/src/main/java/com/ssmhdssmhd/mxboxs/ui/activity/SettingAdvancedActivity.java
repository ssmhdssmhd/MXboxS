package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.player.parse.ParseJob;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.Notify;

/**
 * 高级设置页面
 * 需在主设置页点击版本号 20 次解锁后才可见。
 *
 *  - 播放优化：缓存写入 / 自适应码率 / 缓冲模式 / 画质偏好
 *  - AI 播放优化：AI 自动调节缓冲与画质 + 解析缓存查看/清理
 * 设置在下次播放时生效。
 */
public class SettingAdvancedActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private MaterialTextView lockedHint;
    private MaterialCardView playOptCard;
    private MaterialCardView aiOptCard;

    private SwitchMaterial cacheWriteSwitch;
    private SwitchMaterial adaptiveSwitch;
    private MaterialTextView bufferModeText;
    private MaterialTextView qualityPrefText;

    private SwitchMaterial aiAutoSwitch;
    private MaterialTextView parseCacheText;

    private final String[] bufferModes = new String[]{"快起播", "流畅"};
    private final String[] qualityPrefs = new String[]{"自适应", "最高画质", "720P", "480P"};

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAdvancedActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_advanced);

        toolbar = findViewById(R.id.toolbar);
        lockedHint = findViewById(R.id.lockedHint);
        playOptCard = findViewById(R.id.playOptCard);
        aiOptCard = findViewById(R.id.aiOptCard);
        cacheWriteSwitch = findViewById(R.id.cacheWriteSwitch);
        adaptiveSwitch = findViewById(R.id.adaptiveSwitch);
        bufferModeText = findViewById(R.id.bufferModeText);
        qualityPrefText = findViewById(R.id.qualityPrefText);
        aiAutoSwitch = findViewById(R.id.aiAutoSwitch);
        parseCacheText = findViewById(R.id.parseCacheText);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        setupListeners();
        updateUnlockState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUnlockState();
    }

    private void updateUnlockState() {
        boolean unlocked = Setting.isAdvancedUnlocked();
        lockedHint.setVisibility(unlocked ? View.GONE : View.VISIBLE);
        playOptCard.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        aiOptCard.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        if (unlocked) refreshValues();
    }

    /** 从配置回填到 UI */
    private void refreshValues() {
        cacheWriteSwitch.setChecked(PlayerSetting.isCacheWriteEnabled());
        adaptiveSwitch.setChecked(PlayerSetting.isAdaptiveBitrateEnabled());
        int bm = PlayerSetting.getBufferMode();
        bufferModeText.setText(bufferModes[Math.min(bm, bufferModes.length - 1)]);
        int qp = PlayerSetting.getQualityPref();
        qualityPrefText.setText(qualityPrefs[Math.min(qp, qualityPrefs.length - 1)]);
        aiAutoSwitch.setChecked(PlayerSetting.isAiPlayOptEnabled());
        parseCacheText.setText(getString(R.string.setting_ai_parse_cache_sub)
                + "（当前 " + ParseJob.cacheSize() + " 条）");
    }

    private void setupListeners() {
        // 缓存写入：点行或开关都切换
        View.OnClickListener cacheToggle = v -> {
            boolean on = !PlayerSetting.isCacheWriteEnabled();
            PlayerSetting.putCacheWriteEnabled(on);
            cacheWriteSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.cacheWriteRow).setOnClickListener(cacheToggle);

        // 自适应码率
        View.OnClickListener adaptiveToggle = v -> {
            boolean on = !PlayerSetting.isAdaptiveBitrateEnabled();
            PlayerSetting.putAdaptiveBitrateEnabled(on);
            adaptiveSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.adaptiveRow).setOnClickListener(adaptiveToggle);

        // 缓冲模式：弹出选择
        findViewById(R.id.bufferModeRow).setOnClickListener(v -> showBufferModeDialog());

        // 画质偏好：弹出选择
        findViewById(R.id.qualityPrefRow).setOnClickListener(v -> showQualityPrefDialog());

        // AI 自动调节
        View.OnClickListener aiToggle = v -> {
            boolean on = !PlayerSetting.isAiPlayOptEnabled();
            PlayerSetting.putAiPlayOptEnabled(on);
            aiAutoSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiAutoRow).setOnClickListener(aiToggle);

        // 解析缓存：点击清空
        findViewById(R.id.parseCacheRow).setOnClickListener(v -> {
            int cleared = ParseJob.clearCache();
            if (cleared > 0) {
                Notify.show(String.format(getString(R.string.setting_ai_parse_cache_clear), cleared));
            } else {
                Notify.show(R.string.setting_ai_parse_cache_empty);
            }
            parseCacheText.setText(getString(R.string.setting_ai_parse_cache_sub)
                    + "（当前 0 条）");
        });
    }

    private void showBufferModeDialog() {
        int cur = PlayerSetting.getBufferMode();
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_playopt_buffer)
                .setSingleChoiceItems(bufferModes, Math.min(cur, bufferModes.length - 1),
                        (d, which) -> {
                            PlayerSetting.putBufferMode(which);
                            bufferModeText.setText(bufferModes[which]);
                            Notify.show(R.string.setting_playopt_apply_hint);
                            d.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showQualityPrefDialog() {
        int cur = PlayerSetting.getQualityPref();
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_playopt_quality)
                .setSingleChoiceItems(qualityPrefs, Math.min(cur, qualityPrefs.length - 1),
                        (d, which) -> {
                            PlayerSetting.putQualityPref(which);
                            qualityPrefText.setText(qualityPrefs[which]);
                            Notify.show(R.string.setting_playopt_apply_hint);
                            d.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
