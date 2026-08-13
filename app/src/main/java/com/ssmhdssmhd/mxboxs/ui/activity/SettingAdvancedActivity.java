package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textview.MaterialTextView;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.setting.Setting;

/**
 * 高级设置页面
 * 需在主设置页点击版本号 20 次解锁后才可见。
 */
public class SettingAdvancedActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private MaterialTextView lockedHint;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAdvancedActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_advanced);

        toolbar = findViewById(R.id.toolbar);
        lockedHint = findViewById(R.id.lockedHint);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

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
    }
}
