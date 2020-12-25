package com.ezpay.usbposprint;

import androidx.appcompat.app.AppCompatActivity;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import static android.net.sip.SipErrorCode.TIME_OUT;
import static com.ezpay.usbposprint.PrinterCmdUtils.cutter;
import static com.ezpay.usbposprint.PrinterCmdUtils.init_printer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private final String TAG = getClass().getSimpleName();
    private Context mContext;
    private UsbDevice mDevice;
    private UsbManager mUsbManager;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint mEndpoint; //通信通道
    private PendingIntent mPermissionIntent;
    private final USBPrinter usbPrinter = USBPrinter.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UsbAdmin(this);
        openUsb();

        USBPrinter.initPrinter(this);
        sendCommand(init_printer());
        init_printer();

        try {
            correctEncode();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Button button = findViewById(R.id.button);
        button.setOnClickListener(this);
    }

    public void UsbAdmin(Context context) {
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(mUsbReceiver, filter);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            setDevice(device);
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

    /**設定裝置*/
    private void setDevice(UsbDevice device) {
        if (device != null) {
            Log.i(TAG, "deviceName:" + device.getDeviceName());

            UsbInterface intf = null;
            mEndpoint = null;

            int InterfaceCount = device.getInterfaceCount();
            int j;

            mDevice = device;
            for (j = 0; j < InterfaceCount; j++) {
                int i;

                intf = device.getInterface(j);
                Log.i(TAG, "接口:" + j + "類型:" + intf.getInterfaceClass());

                if (intf.getInterfaceClass() == 7) {
                    int UsbEndpointCount = intf.getEndpointCount();
                    for (i = 0; i < UsbEndpointCount; i++) {
                        mEndpoint = intf.getEndpoint(i);
                        Log.i(TAG, "端點:" + i + "方向是:" + mEndpoint.getDirection() + "類型:" + mEndpoint.getType());
                        if (mEndpoint.getDirection() == 0 && mEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            Log.i(TAG, "接口是:" + j + "端點是:" + i);
                            break;
                        }
                    }
                    if (i != UsbEndpointCount) {
                        break;
                    }
                }
            }
            if (j == InterfaceCount) {
                Log.i(TAG, "沒有打印機接口");
                return;
            }

//            mEndpointIntr = ep;

            UsbDeviceConnection connection = mUsbManager.openDevice(device);

            if (connection != null && connection.claimInterface(intf, true)) {
                Log.i(TAG, "打開成功！ ");
                mUsbDeviceConnection = connection;
            } else {
                Log.i(TAG, "打開失敗！ ");
                mUsbDeviceConnection = null;
            }
        }
    }

    public void openUsb() {
        if (mDevice != null) {
            setDevice(mDevice);
            if (mUsbDeviceConnection == null) {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    mUsbManager.requestPermission(device, mPermissionIntent);
                }
            }
        } else {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                mUsbManager.requestPermission(device, mPermissionIntent);
            }
        }
    }

    public boolean sendCommand(byte[] bytes) {
        boolean Result;
        synchronized (this) {
            int len = -1;
            if (mUsbDeviceConnection != null) {
                len = mUsbDeviceConnection.bulkTransfer(mEndpoint, bytes, bytes.length, 10000);
            }

            if (len < 0) {
                Result = false;
                Log.i(TAG, "發送失敗! " + len);
            } else {
                Result = true;
                Log.i(TAG, "發送" + len + "字節數據");
            }
        }
        return Result;
    }

    @Override
    public void onClick(View view) {
        init_printer();
        printTextNewLine("商品");
        init_printer();
        printTextNewLine("设置标签纸");
        init_printer();
        printTextNewLine("熊貓");
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
            Log.i("Return Status", "bytes-->" + bytes);
            Log.i("Return Status", "bytes.length-->" + bytes.length);
            Log.i("Return Status", "b-->" + b);
        } else {
            Looper.prepare();
            handler.sendEmptyMessage(0);
            Looper.loop();
        }
    }

    /**
     * 換行打印文字
     * @param msg 訊息
     * */
    public void printTextNewLine(String msg) {
        byte[] bytes = new byte[0];
        try {
            String gbkTransChinese = new String(msg.getBytes("GBK"),"ISO-8859-1");//轉換gbk編碼
            String unicodeTransChinese = new String(gbkTransChinese.getBytes("ISO-8859-1"),"GBK");
            Log.e("strbys","====" + unicodeTransChinese);

            bytes = unicodeTransChinese.getBytes("GBK");
            Log.e("unicodeChinese.length()",""+ Arrays.toString(toCharBytes(unicodeTransChinese)));

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        write("\n".getBytes());
        write(bytes);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        USBPrinter.getInstance().closeUsb();
    }

    public static byte[] toCharBytes(String str) {
        char[] charArr = str.toCharArray();
        byte[] byteArr = new byte[charArr.length*2];
        for(int i=0;i<charArr.length;i++) {
            //當字符是英文時，該位的編碼與ASCII和ISO8859-1均相同
            byteArr[2*i] = (byte)(charArr[i] & 0xFF);
            //當字符是英文的時候，高位是0；如果字符是中文，那麽高位不為0。並且該編碼也與GBK、UTF8、Unicode都不同
            byteArr[2*i+1] = (byte)((charArr[i] & 0xFF00) >>> 8);
        }
        return byteArr;
    }

    /**轉碼測試*/
    public static void correctEncode() throws UnsupportedEncodingException {
        String gbk = "中文";

        String gbkTransChinese = new String(gbk.getBytes("GBK"),"ISO-8859-1");//轉換gbk編碼
        String unicodeTransChinese = new String(gbkTransChinese.getBytes("ISO-8859-1"),"GBK");

//        String iso1 = new String(gbk.getBytes(StandardCharsets.UTF_8));

        Log.e("gbkChinese",unicodeTransChinese + "==== ");

        for (byte b : unicodeTransChinese.getBytes("ISO-8859-1")) {
            Log.e("string",b + "==== ");
        }

        String chinese = "中文";//java內部編碼
        String gbkChinese = new String(chinese.getBytes("GBK"),"ISO-8859-1");//轉換gbk編碼
        String unicodeChinese = new String(gbkChinese.getBytes("ISO-8859-1"),"GBK");//java內部編碼
        Log.e("unicodeChinese.length()",unicodeChinese.length() + "==== ");
        Log.e("unicodeChinese.length()",gbkChinese.getBytes("ISO-8859-1") + "==== ");
        System.out.println(unicodeChinese);//中文
        String utf8Chinese = new String(unicodeChinese.getBytes("UTF-8"),"ISO-8859-1");//utf--8編碼
        System.out.println(utf8Chinese);//亂碼
        unicodeChinese = new String(utf8Chinese.getBytes("ISO-8859-1"),"UTF-8");//java内部編碼
        System.out.println(unicodeChinese);//中文
    }
}