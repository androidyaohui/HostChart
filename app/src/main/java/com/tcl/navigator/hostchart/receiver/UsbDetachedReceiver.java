package com.tcl.navigator.hostchart.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UsbDetachedReceiver extends BroadcastReceiver {

    private UsbDetachedListener mUsbDetachedListener;

    public UsbDetachedReceiver(UsbDetachedListener usbDetachedListener) {
        mUsbDetachedListener = usbDetachedListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mUsbDetachedListener.usbDetached();
    }

    public interface UsbDetachedListener {
        /**
         * usb断开连接
         */
        void usbDetached();
    }
}
