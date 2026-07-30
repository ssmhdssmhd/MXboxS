package com.ssmhdssmhd.mxboxs.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.databinding.ActivityCrashBinding;
import com.ssmhdssmhd.mxboxs.ui.base.BaseActivity;
import com.github.catvod.utils.Prefers;

import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCrash();
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.restart.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent()))));
    }

    private void setCrash() {
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (TextUtils.isEmpty(log)) return;
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            if (lines[i].contains(HomeActivity.class.getSimpleName())) {
                Prefers.put("crash", true);
                break;
            }
        }
    }

    private void showError() {
        String errorDetails = CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent());
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setTextIsSelectable(true);
        textView.setText(errorDetails);
        textView.setPadding(48, 32, 48, 32);
        textView.setTextSize(12);
        scrollView.addView(textView);
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setView(scrollView)
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}
