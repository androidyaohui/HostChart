package com.tcl.navigator.hostchart.base;

import android.app.Application;
import android.util.Log;

import com.tcl.navigator.hostchart.utils.CrashHandler;

/**
 * Created by yaohui on 2017/3/3.
 */

public class MyApplication extends Application {

    private static final String TAG = "yaohui";

    @Override
    public void onCreate() {
        super.onCreate();

        CrashHandler.getInstance().init(this);
    }

    public static void printLogDebug(String logString) {
        Log.d(TAG, logString);
    }
}
