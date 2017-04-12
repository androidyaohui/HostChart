package com.tcl.navigator.hostchart.activity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.tcl.navigator.hostchart.R;
import com.tcl.navigator.hostchart.base.MyApplication;
import com.tcl.navigator.hostchart.receiver.OpenDevicesReceiver;
import com.tcl.navigator.hostchart.receiver.UsbDetachedReceiver;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 车机端
 */

public class MainActivity extends AppCompatActivity implements UsbDetachedReceiver.UsbDetachedListener, OpenDevicesReceiver.OpenDevicesListener, View.OnClickListener {

    private static final int    CONNECTED_SUCCESS        = 0;
    private static final int    RECEIVER_MESSAGE_SUCCESS = 1;
    private static final int    SEND_MESSAGE_SUCCESS     = 2;
    private static final String USB_ACTION               = "com.tcl.navigator.hostchart";
    private TextView            mLog;
    private EditText            mMessage;
    private UsbDetachedReceiver mUsbDetachedReceiver;
    private ExecutorService     mThreadPool;
    private UsbManager          mUsbManager;
    private OpenDevicesReceiver mOpenDevicesReceiver;
    private TextView            mError;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbEndpoint         mUsbEndpointOut;
    private UsbEndpoint         mUsbEndpointIn;
    private boolean mToggle = true;
    private Button mSendMessage;
    private boolean isDetached        = false;
    private byte[]  mBytes            = new byte[1024];
    private boolean isReceiverMessage = true;
    private UsbInterface mUsbInterface;
    private StringBuffer mStringBuffer = new StringBuffer();
    private Context mContext;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECTED_SUCCESS://车机和手机连接成功
                    mError.setText("");
                    mSendMessage.setEnabled(true);
                    loopReceiverMessage();
                    break;

                case RECEIVER_MESSAGE_SUCCESS://成功接受到数据
                    mLog.setText(mStringBuffer.toString());
                    break;

                case SEND_MESSAGE_SUCCESS://成功发送数据
                    mMessage.setText("");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        initView();
        initListener();
        initData();
    }

    private void initView() {
        mLog = (TextView) findViewById(R.id.log);
        mError = (TextView) findViewById(R.id.error);
        mMessage = (EditText) findViewById(R.id.message);
        mSendMessage = (Button) findViewById(R.id.sendmessage);
    }

    private void initListener() {
        mSendMessage.setOnClickListener(this);
    }

    private void initData() {
        mContext = getApplicationContext();
        mSendMessage.setEnabled(false);
        mUsbDetachedReceiver = new UsbDetachedReceiver(this);
        IntentFilter intentFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbDetachedReceiver, intentFilter);

        mThreadPool = Executors.newFixedThreadPool(5);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        openDevices();
    }

    /**
     * 打开设备 , 让车机和手机端连起来
     */
    private void openDevices() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(USB_ACTION), 0);
        IntentFilter intentFilter = new IntentFilter(USB_ACTION);
        mOpenDevicesReceiver = new OpenDevicesReceiver(this);
        registerReceiver(mOpenDevicesReceiver, intentFilter);

        //列举设备(手机)
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        if (deviceList != null) {
            for (UsbDevice usbDevice : deviceList.values()) {
                int productId = usbDevice.getProductId();
                if (productId != 377 && productId != 7205) {
                    if (mUsbManager.hasPermission(usbDevice)) {
                        initAccessory(usbDevice);
                    } else {
                        mUsbManager.requestPermission(usbDevice, pendingIntent);
                    }
                }
            }
        } else {
            mError.setText("请连接USB");
        }
    }

    /**
     * 发送命令 , 让手机进入Accessory模式
     *
     * @param usbDevice
     */
    private void initAccessory(UsbDevice usbDevice) {
        UsbDeviceConnection usbDeviceConnection = mUsbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            mError.setText("请连接USB");
            return;
        }

        //根据AOA协议打开Accessory模式
        initStringControlTransfer(usbDeviceConnection, 0, "Google, Inc."); // MANUFACTURER
        initStringControlTransfer(usbDeviceConnection, 1, "AccessoryChat"); // MODEL
        initStringControlTransfer(usbDeviceConnection, 2, "Accessory Chat"); // DESCRIPTION
        initStringControlTransfer(usbDeviceConnection, 3, "1.0"); // VERSION
        initStringControlTransfer(usbDeviceConnection, 4, "http://www.android.com"); // URI
        initStringControlTransfer(usbDeviceConnection, 5, "0123456789"); // SERIAL
        usbDeviceConnection.controlTransfer(0x40, 53, 0, 0, new byte[]{}, 0, 100);
        usbDeviceConnection.close();
        MyApplication.printLogDebug("initAccessory success");
        initDevice();
    }

    private void initStringControlTransfer(UsbDeviceConnection deviceConnection, int index, String string) {
        deviceConnection.controlTransfer(0x40, 52, 0, index, string.getBytes(), string.length(), 100);
    }

    /**
     * 初始化设备(手机) , 当手机进入Accessory模式后 , 手机的PID会变为Google定义的2个常量值其中的一个 ,
     */
    private void initDevice() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (mToggle) {
                    SystemClock.sleep(1000);
                    HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                    Collection<UsbDevice> values = deviceList.values();
                    if (!values.isEmpty()) {
                        for (UsbDevice usbDevice : values) {
                            int productId = usbDevice.getProductId();
                            if (productId == 0x2D00 || productId == 0x2D01) {
                                if (mUsbManager.hasPermission(usbDevice)) {
                                    mUsbDeviceConnection = mUsbManager.openDevice(usbDevice);
                                    if (mUsbDeviceConnection != null) {
                                        mUsbInterface = usbDevice.getInterface(0);
                                        int endpointCount = mUsbInterface.getEndpointCount();
                                        for (int i = 0; i < endpointCount; i++) {
                                            UsbEndpoint usbEndpoint = mUsbInterface.getEndpoint(i);
                                            if (usbEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                                                if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                                                    mUsbEndpointOut = usbEndpoint;
                                                } else if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                                                    mUsbEndpointIn = usbEndpoint;
                                                }
                                            }
                                        }
                                        if (mUsbEndpointOut != null && mUsbEndpointIn != null) {
                                            MyApplication.printLogDebug("connected success");
                                            mHandler.sendEmptyMessage(CONNECTED_SUCCESS);
                                            mToggle = false;
                                            isDetached = true;
                                        }
                                    }
                                } else {
                                    mUsbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(mContext, 0, new Intent(""), 0));
                                }
                            }
                        }
                    } else {
                        finish();
                    }
                }
            }
        });
    }

    /**
     * 接受消息线程 , 此线程在设备(手机)初始化完成后 , 就一直循环接受消息
     */
    private void loopReceiverMessage() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(1000);
                while (isReceiverMessage) {
                    /**
                     * 循环接受数据的地方 , 只接受byte数据类型的数据
                     */
                    if (mUsbDeviceConnection != null && mUsbEndpointIn != null) {
                        int i = mUsbDeviceConnection.bulkTransfer(mUsbEndpointIn, mBytes, mBytes.length, 3000);
                        MyApplication.printLogDebug(i + "");
                        if (i > 0) {
                            mStringBuffer.append(new String(mBytes, 0, i) + "\n");
                            mHandler.sendEmptyMessage(RECEIVER_MESSAGE_SUCCESS);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void usbDetached() {
        if (isDetached) {
            finish();
        }
    }

    @Override
    public void openAccessoryModel(UsbDevice usbDevice) {
        initAccessory(usbDevice);
    }

    @Override
    public void openDevicesError() {
        mError.setText("USB连接错误");
    }

    @Override
    public void onClick(View v) {
        final String messageContent = mMessage.getText().toString();
        if (!TextUtils.isEmpty(messageContent)) {
            mThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    /**
                     * 发送数据的地方 , 只接受byte数据类型的数据
                     */
                    int i = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, messageContent.getBytes(), messageContent.getBytes().length, 3000);
                    if (i > 0) {//大于0表示发送成功
                        mHandler.sendEmptyMessage(SEND_MESSAGE_SUCCESS);
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();

        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection.releaseInterface(mUsbInterface);
            mUsbDeviceConnection.close();
            mUsbDeviceConnection = null;
        }
        mUsbEndpointIn = null;
        mUsbEndpointOut = null;
        mToggle = false;
        isReceiverMessage = false;
        mThreadPool.shutdownNow();
        unregisterReceiver(mUsbDetachedReceiver);
        unregisterReceiver(mOpenDevicesReceiver);
    }
}
