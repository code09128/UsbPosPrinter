package com.ezpay.usbposprint;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 * @author dustin.hsieh
 * @Date on 2020/12/11
 * @Description
 */
public class USBPrinter {
    private final String TAG = getClass().getSimpleName();

    public static final String ACTION_USB_PERMISSION = "com.usb.printer.USB_PERMISSION";
    private static final int TIME_OUT = 100000;

    @SuppressLint("StaticFieldLeak")
    private static USBPrinter mInstance;

    private Context mContext;
    private UsbManager mUsbManager;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint mEndpoint; //通信通道
    private PendingIntent mPermissionIntent;

    public static USBPrinter getInstance() {
        if (mInstance == null) {
            synchronized (USBPrinter.class) {
                if (mInstance == null) {
                    mInstance = new USBPrinter();
                }
            }
        }
        return mInstance;
    }

    public static void initPrinter(Context context) {
        getInstance().init(context);
    }

    private void init(Context context) {
        openUsb(context);
    }

    public void openUsb(Context context) {
        mContext = context;
        mUsbManager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mContext.registerReceiver(mUsbReceiver, filter);

        //列出所有USB設備 並且獲取USB權限
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

        for (UsbDevice device : deviceList.values()) {
            usbInterface = device.getInterface(0);
            if (usbInterface.getInterfaceClass() == 7) {
                Log.d("device", device.getProductName() + "     " + device.getManufacturerName());
                Log.d("device", device.getVendorId() + "     " + device.getProductId() + "      " + device.getDeviceId());
                Log.d("device", usbInterface.getInterfaceClass() + "");

                if (!mUsbManager.hasPermission(device)) {
                    mUsbManager.requestPermission(device, mPermissionIntent);
                }else {
                    connectUsbPrinter(device);
                }
            }
        }
    }

    private void connectUsbPrinter(UsbDevice mUsbDevice) {
        if (mUsbDevice != null) {
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = usbInterface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
//                        mUsbDeviceConnection = mUsbManager.openDevice(mUsbDevice);
                        UsbDeviceConnection connection = mUsbManager.openDevice(mUsbDevice);

                        mEndpoint = ep;
                        if (connection != null && connection.claimInterface(usbInterface, true)) {
                            mUsbDeviceConnection = connection;
                            Log.d(TAG, "設備已連接");
                            mUsbDeviceConnection.claimInterface(usbInterface, true);
                            mUsbDeviceConnection.releaseInterface(usbInterface);
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "未發現可用打印機");
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectUsbPrinter(device);
                        } else {
                            closeUsb();
                            // mDevice = device;
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    public void closeUsb() {
        if(mUsbDeviceConnection != null){
            mUsbDeviceConnection.close();
            mUsbDeviceConnection = null;
        }

        mContext.unregisterReceiver(mUsbReceiver);
        mContext = null;
        mUsbManager = null;
    }

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "未發現可用打印機");
        }
    };

    private void write(byte[] bytes) {
        if (mUsbDeviceConnection != null) {
            int b = mUsbDeviceConnection.bulkTransfer(mEndpoint, bytes, bytes.length, 10000);
            Log.i("Return Status", "b-->" + b);
        } else {
            Looper.prepare();
            handler.sendEmptyMessage(0);
            Looper.loop();
        }
    }

    /**
     * 打印文字
     * @param msg 訊息
     * */
    public void printText(String msg) {
        byte[] bytes = new byte[0];
        try {
            bytes = msg.getBytes("gbk");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        write(bytes);
    }

    /**
     * 換行打印文字
     * @param msg 訊息
     * */
    public void printTextNewLine(String msg) {
        byte[] bytes = new byte[0];
        try {
            bytes = msg.getBytes("gbk");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        write("\n".getBytes());
        write(bytes);
    }
}
