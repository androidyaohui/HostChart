package com.tcl.navigator.hostchart.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;

/**
 * Created by yaohui on 2017/4/13.
 */

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String FILE_NAME        = "crash";
    private static final String FILE_NAME_SUFFIX = ".trace";
    private Thread.UncaughtExceptionHandler mDefaulCrashHandler;
    private Context                         mContext;
    private SimpleDateFormat                mSdf;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        return CrashHandlerHolder.CRASH_HANDLER;
    }

    private static class CrashHandlerHolder {
        private static final CrashHandler CRASH_HANDLER = new CrashHandler();
    }

    public void init(Context context) {
        //        mDefaulCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mContext = context.getApplicationContext();
        mSdf = (SimpleDateFormat) SimpleDateFormat.getInstance();
        mSdf.applyPattern("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            dumpException(e);
            uploadExceptionToServer();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        e.printStackTrace();
        if (mDefaulCrashHandler != null)
            mDefaulCrashHandler.uncaughtException(t, e);
        else
            Process.killProcess(Process.myPid());
    }

    private void dumpException(Throwable ex) throws IOException {
        String path = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable())
            path = mContext.getExternalFilesDir(null).getAbsolutePath() + "/crash/log";
        else
            path = mContext.getFilesDir().getAbsolutePath() + "/crash/log";
        File dir = new File(path);
        if (!dir.exists())
            dir.mkdirs();
        long current = System.currentTimeMillis();
        String time = mSdf.format(current);
        File file = new File(path + File.separator + FILE_NAME + time + FILE_NAME_SUFFIX);
        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            pw.println(time);
            dumpPhoneInfo(pw);
            pw.println();
            ex.printStackTrace(pw);
            pw.close();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void dumpPhoneInfo(PrintWriter pw) throws PackageManager.NameNotFoundException {
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        pw.print("App Version: ");
        pw.print(pi.versionName);
        pw.print('_');
        pw.println(pi.versionCode);

        //Android版本号
        pw.print("OS Version: ");
        pw.print(Build.VERSION.RELEASE);
        pw.print('_');
        pw.println(Build.VERSION.SDK_INT);

        //手机制造商
        pw.print("Vendor: ");
        pw.println(Build.MANUFACTURER);

        //手机型号
        pw.print("Model: ");
        pw.println(Build.MODEL);

        //CPU架构
        pw.print("CPU ABI: ");
        pw.println(Build.CPU_ABI);
    }

    private void uploadExceptionToServer() {
    }
}
