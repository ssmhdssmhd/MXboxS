package com.ssmhdssmhd.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ssmhdssmhd.android.tv.R;
import com.ssmhdssmhd.android.tv.utils.Notify;

public class ShortcutReceiver extends BroadcastReceiver {

    public static final String ACTION = ShortcutReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Notify.show(R.string.shortcut);
    }
}