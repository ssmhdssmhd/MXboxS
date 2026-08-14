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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.player.parse.ParseDiskCache;
import com.ssmhdssmhd.mxboxs.player.parse.ParseJob;
import com.ssmhdssmhd.mxboxs.setting.PlayerSetting;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.FeatureFlags;
import com.ssmhdssmhd.mxboxs.utils.Notify;

/**
 * 高级设置页面
 * 需在主设置页点击版本号 20 次解锁后才可见。
 *
 *  - 播放优化：缓存写入 / 自适应码率 / 缓冲模式 / 画质偏好
 *  - AI 播放优化：AI 自动调节缓冲与画质 + 解析缓存查看/清理
 *  - AI 实验项：总开关 / AB 分桶 / LLM 嗅探 / 源质量评分 / 预解析 / 超分
 *  - LLM 配置：Endpoint / API Key / Model
 * 设置在下次播放时生效。
 */
public class SettingAdvancedActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private MaterialTextView lockedHint;
    private MaterialCardView playOptCard;
    private MaterialCardView aiOptCard;
    private MaterialCardView aiExpCard;
    private MaterialCardView llmCard;

    private SwitchMaterial cacheWriteSwitch;
    private SwitchMaterial adaptiveSwitch;
    private MaterialTextView bufferModeText;
    private MaterialTextView qualityPrefText;
    private SwitchMaterial webviewSniffSwitch;

    private SwitchMaterial aiAutoSwitch;
    private MaterialTextView parseCacheText;

    private SwitchMaterial aiExpMasterSwitch;
    private MaterialTextView aiExpBucketText;
    private SwitchMaterial aiExpLlmSwitch;
    private SwitchMaterial aiExpSqSwitch;
    private SwitchMaterial aiExpPpnSwitch;
    private SwitchMaterial aiExpSrSwitch;

    private TextInputEditText llmEndpointEdit;
    private TextInputEditText llmKeyEdit;
    private TextInputEditText llmModelEdit;

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
        aiExpCard = findViewById(R.id.aiExpCard);
        llmCard = findViewById(R.id.llmCard);

        cacheWriteSwitch = findViewById(R.id.cacheWriteSwitch);
        adaptiveSwitch = findViewById(R.id.adaptiveSwitch);
        bufferModeText = findViewById(R.id.bufferModeText);
        qualityPrefText = findViewById(R.id.qualityPrefText);
        webviewSniffSwitch = findViewById(R.id.webviewSniffSwitch);
        aiAutoSwitch = findViewById(R.id.aiAutoSwitch);
        parseCacheText = findViewById(R.id.parseCacheText);

        aiExpMasterSwitch = findViewById(R.id.aiExpMasterSwitch);
        aiExpBucketText = findViewById(R.id.aiExpBucketText);
        aiExpLlmSwitch = findViewById(R.id.aiExpLlmSwitch);
        aiExpSqSwitch = findViewById(R.id.aiExpSqSwitch);
        aiExpPpnSwitch = findViewById(R.id.aiExpPpnSwitch);
        aiExpSrSwitch = findViewById(R.id.aiExpSrSwitch);

        llmEndpointEdit = findViewById(R.id.llmEndpointEdit);
        llmKeyEdit = findViewById(R.id.llmKeyEdit);
        llmModelEdit = findViewById(R.id.llmModelEdit);

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
        aiExpCard.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        llmCard.setVisibility(unlocked ? View.VISIBLE : View.GONE);
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
        webviewSniffSwitch.setChecked(PlayerSetting.isWebviewSniffDefaultOn());
        aiAutoSwitch.setChecked(PlayerSetting.isAiPlayOptEnabled());
        parseCacheText.setText(getString(R.string.setting_ai_parse_cache_sub)
                + "（内存 " + ParseJob.cacheSize() + " 条 · 磁盘 " + ParseDiskCache.size() + " 条）");

        // AI 实验项回填
        aiExpMasterSwitch.setChecked(FeatureFlags.isMasterEnabled());
        aiExpBucketText.setText(String.format(getString(R.string.setting_ai_exp_bucket_fmt),
                FeatureFlags.bucketPercent()));
        aiExpLlmSwitch.setChecked(FeatureFlags.isFlagOn(FeatureFlags.LLM_SNIFFER));
        aiExpSqSwitch.setChecked(FeatureFlags.isFlagOn(FeatureFlags.SOURCE_QUALITY));
        aiExpPpnSwitch.setChecked(FeatureFlags.isFlagOn(FeatureFlags.PREPARSE_NEXT));
        aiExpSrSwitch.setChecked(FeatureFlags.isFlagOn(FeatureFlags.AI_SUPER_RES));

        // LLM 配置回填
        llmEndpointEdit.setText(PlayerSetting.getLlmEndpoint());
        llmKeyEdit.setText(PlayerSetting.getLlmKey());
        llmModelEdit.setText(PlayerSetting.getLlmModel());
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

        // WebView 嗅探默认开启
        View.OnClickListener webviewSniffToggle = v -> {
            boolean on = !PlayerSetting.isWebviewSniffDefaultOn();
            PlayerSetting.putWebviewSniffDefaultOn(on);
            webviewSniffSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.webviewSniffRow).setOnClickListener(webviewSniffToggle);

        // AI 自动调节
        View.OnClickListener aiToggle = v -> {
            boolean on = !PlayerSetting.isAiPlayOptEnabled();
            PlayerSetting.putAiPlayOptEnabled(on);
            aiAutoSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiAutoRow).setOnClickListener(aiToggle);

        // 解析缓存：点击弹出分级清理对话框
        findViewById(R.id.parseCacheRow).setOnClickListener(v -> showParseCacheClearDialog());

        // ===== AI 实验项 =====
        // 总开关
        View.OnClickListener aiExpMasterToggle = v -> {
            boolean on = !FeatureFlags.isMasterEnabled();
            FeatureFlags.setMasterEnabled(on);
            aiExpMasterSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiExpMasterRow).setOnClickListener(aiExpMasterToggle);

        // LLM 嗅探
        View.OnClickListener aiExpLlmToggle = v -> {
            boolean on = !FeatureFlags.isFlagOn(FeatureFlags.LLM_SNIFFER);
            FeatureFlags.setFlag(FeatureFlags.LLM_SNIFFER, on);
            aiExpLlmSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiExpLlmRow).setOnClickListener(aiExpLlmToggle);

        // 源质量评分
        View.OnClickListener aiExpSqToggle = v -> {
            boolean on = !FeatureFlags.isFlagOn(FeatureFlags.SOURCE_QUALITY);
            FeatureFlags.setFlag(FeatureFlags.SOURCE_QUALITY, on);
            aiExpSqSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiExpSqRow).setOnClickListener(aiExpSqToggle);

        // 预解析下一集
        View.OnClickListener aiExpPpnToggle = v -> {
            boolean on = !FeatureFlags.isFlagOn(FeatureFlags.PREPARSE_NEXT);
            FeatureFlags.setFlag(FeatureFlags.PREPARSE_NEXT, on);
            aiExpPpnSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiExpPpnRow).setOnClickListener(aiExpPpnToggle);

        // 超分（占位）
        View.OnClickListener aiExpSrToggle = v -> {
            boolean on = !FeatureFlags.isFlagOn(FeatureFlags.AI_SUPER_RES);
            FeatureFlags.setFlag(FeatureFlags.AI_SUPER_RES, on);
            aiExpSrSwitch.setChecked(on);
            Notify.show(R.string.setting_playopt_apply_hint);
        };
        findViewById(R.id.aiExpSrRow).setOnClickListener(aiExpSrToggle);

        // LLM 配置保存
        findViewById(R.id.llmSaveRow).setOnClickListener(v -> {
            CharSequence ep = llmEndpointEdit.getText();
            CharSequence key = llmKeyEdit.getText();
            CharSequence model = llmModelEdit.getText();
            PlayerSetting.putLlmEndpoint(ep == null ? "" : ep.toString().trim());
            PlayerSetting.putLlmKey(key == null ? "" : key.toString().trim());
            PlayerSetting.putLlmModel(model == null ? "" : model.toString().trim());
            Notify.show(R.string.setting_llm_saved);
        });
    }

    /** 解析缓存分级清理：内存 / 磁盘 / 全部 */
    private void showParseCacheClearDialog() {
        final String[] items = {
                "清空内存缓存（" + ParseJob.cacheSize() + " 条）",
                "清空磁盘缓存（" + ParseDiskCache.size() + " 条）",
                "清空全部缓存"
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_ai_parse_cache)
                .setItems(items, (d, which) -> {
                    int mem = 0, disk = 0;
                    if (which == 0 || which == 2) {
                        mem = ParseJob.clearCache();
                    }
                    if (which == 1 || which == 2) {
                        disk = ParseDiskCache.clear();
                    }
                    if (mem == 0 && disk == 0) {
                        Notify.show(R.string.setting_ai_parse_cache_empty);
                    } else {
                        Notify.show(String.format(getString(R.string.setting_ai_parse_cache_clear),
                                mem + disk));
                    }
                    parseCacheText.setText(getString(R.string.setting_ai_parse_cache_sub)
                            + "（内存 " + ParseJob.cacheSize() + " 条 · 磁盘 " + ParseDiskCache.size() + " 条）");
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
