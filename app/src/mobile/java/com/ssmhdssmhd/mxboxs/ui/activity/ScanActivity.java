package com.ssmhdssmhd.mxboxs.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.ssmhdssmhd.mxboxs.databinding.ActivityScanBinding;
import com.ssmhdssmhd.mxboxs.ui.base.BaseActivity;
import com.ssmhdssmhd.mxboxs.utils.Util;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.List;

public class ScanActivity extends BaseActivity implements BarcodeCallback {

    public static final int REQUEST_SCAN = 10086;

    /** 可选：把扫描用途作为 extra 传入，这里仅原样返回，方便调用方区分 TG/X/普通 http。 */
    public static final String EXTRA_SCAN_PURPOSE = "scan_purpose";
    public static final String EXTRA_RESULT_ADDRESS = "address";
    public static final String EXTRA_RESULT_PURPOSE = "purpose";

    /** 扫描类型：允许任意非空文本（默认），true=仅放行 http/https 地址（兼容老入口）。 */
    public static final String EXTRA_HTTP_ONLY = "http_only";

    private ActivityScanBinding mBinding;
    private CaptureManager mCapture;
    private String mPurpose;
    private boolean mHttpOnly;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ScanActivity.class));
    }

    public static void startForResult(androidx.fragment.app.Fragment fragment, int requestCode, String purpose) {
        Intent i = new Intent(fragment.requireActivity(), ScanActivity.class);
        i.putExtra(EXTRA_SCAN_PURPOSE, purpose);
        i.putExtra(EXTRA_HTTP_ONLY, false);
        fragment.startActivityForResult(i, requestCode);
    }

    public static void startForResult(Activity activity, int requestCode, String purpose) {
        Intent i = new Intent(activity, ScanActivity.class);
        i.putExtra(EXTRA_SCAN_PURPOSE, purpose);
        i.putExtra(EXTRA_HTTP_ONLY, false);
        activity.startActivityForResult(i, requestCode);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityScanBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        mPurpose = i == null ? null : i.getStringExtra(EXTRA_SCAN_PURPOSE);
        mHttpOnly = i != null && i.getBooleanExtra(EXTRA_HTTP_ONLY, false);
        Util.hideSystemUI(this);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mCapture = new CaptureManager(this, mBinding.scanner);
        mBinding.scanner.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(List.of(BarcodeFormat.QR_CODE)));
    }

    @Override
    public void barcodeResult(BarcodeResult result) {
        String text = result == null ? null : result.getText();
        if (TextUtils.isEmpty(text)) return;
        String trim = text.trim();
        if (mHttpOnly) {
            if (!trim.startsWith("http")) return;
        }
        Intent out = new Intent();
        out.putExtra(EXTRA_RESULT_ADDRESS, trim);
        out.putExtra(EXTRA_RESULT_PURPOSE, mPurpose);
        setResult(RESULT_OK, out);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mCapture.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCapture.onResume();
        mBinding.scanner.decodeSingle(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCapture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCapture.onDestroy();
    }
}
