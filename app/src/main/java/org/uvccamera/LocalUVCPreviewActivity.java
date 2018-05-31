package org.uvccamera;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.uvccamera.usb.OnDeviceConnectListener;
import org.uvccamera.usb.USBMonitor;
import org.uvccamera.usb.UsbControlBlock;
import org.uvccamera.usb.UsbFrameSize;

import java.util.List;
import java.util.Timer;

public class LocalUVCPreviewActivity extends Activity{
    private static final boolean DEBUG = true;	// TODO set false on release
    private static final String TAG = "UVCDebug_MainActivity";
    public static final String PERMISSION_WRITE_EXTERNAL_STORAGE= "android.permission.WRITE_EXTERNAL_STORAGE";
    public static final int PERMISSION_REQUESTCODE = 0;

    private RelativeLayout cameraSurfaceViewWrapper;
    private SurfaceView cameraSurfaceView;
    private USBMonitor usbMonitor;
    private OnDeviceConnectListener usbDeviceConnectListener;

    private Switch btnCapture;
    private TextView textStatus;

    private LinearLayout btnRotate;
    private TextView textRotate;

    private TextView textResolution;
    private AlertDialog.Builder builder;

    public int screen_width;
    public int screen_height;
    public int surfaceview_default_width;
    public int surfaceview_default_height;

    public int capture_width;
    public int capture_height;
    public int capture_rotate;

    private LinearLayout bottomBar;
    private RelativeLayout btnPreview;
    private ImageView imgPreview;
    private RelativeLayout lyShoot;
    private ImageView btnShoot;
    private ImageView imgShoot;
    private RelativeLayout lySwitch;
    private ImageView btnSwitch;
    private ImageView imgSwitch;

    private LinearLayout bottomBar_anim;
    private RelativeLayout lyShoot_anim;
    private ImageView btnShoot_anim;
    private ImageView imgShoot_anim;
    private RelativeLayout lySwitch_anim;
    private ImageView btnSwitch_anim;
    private ImageView imgSwitch_anim;

    private LinearLayout lyTime;
    private TextView textTime;
    private int time = 0;
    private Timer updateShootTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        screen_width = dm.widthPixels;
        screen_height = dm.heightPixels;

        cameraSurfaceViewWrapper = (RelativeLayout)findViewById(R.id.uvc_camera_activity_local_preview);
        cameraSurfaceView = (SurfaceView) findViewById(R.id.camera_surface_view);

        btnCapture = (Switch) findViewById(R.id.btnCapture);
        btnCapture.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isCheck) {
                if (isCheck && usbMonitor != null) {
                    usbMonitor.setSurfaceHolder(cameraSurfaceView.getHolder());
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                } else {
                    usbMonitor.stopCapture();
                }
            }
        });

        btnRotate = (LinearLayout) findViewById(R.id.btnRotate);
        textRotate = (TextView) findViewById(R.id.textRotate);
        textRotate.setText(""+capture_rotate);
        btnRotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(capture_rotate == 0){
                    capture_rotate = 90;
                    textRotate.setText(""+capture_rotate);
                }
                else if(capture_rotate == 90){
                    capture_rotate = 180;
                    textRotate.setText(""+capture_rotate);
                }
                else if(capture_rotate == 180){
                    capture_rotate = 270;
                    textRotate.setText(""+capture_rotate);
                }
                else if(capture_rotate == 270){
                    capture_rotate = 0;
                    textRotate.setText(""+capture_rotate);
                }
                if(btnCapture.isChecked() && usbMonitor != null){
                    usbMonitor.stopCapture();
                    usbMonitor.setSurfaceHolder(cameraSurfaceView.getHolder());
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                }
            }
        });

        textResolution = (TextView) findViewById(R.id.textResolution);
        textResolution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showResolutionDialog();
            }
        });

        textStatus = (TextView) findViewById(R.id.textStatus);
        textStatus.setText("onDettach");

        usbDeviceConnectListener = new OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                textStatus.setText("onAttach");
            }
            @Override
            public void onDettach(UsbDevice device) {
                textStatus.setText("onDettach");
            }

            @Override
            public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock) {
                textStatus.setText("onConnect");

                int expectedFrameSize = 1280 * 720 * 3 / 2;
                int index = 0;
                int diff = Integer.MAX_VALUE;
                List<UsbFrameSize> usbFrameSizeList = usbMonitor.mSupportedSizeList;
                if (usbFrameSizeList != null) {
                    for (int i = 0; i < usbFrameSizeList.size(); i++) {
                        int frameSize = usbFrameSizeList.get(i).getWdith() * usbFrameSizeList.get(i).getHeight() * 3 / 2;
                        if (Math.abs(expectedFrameSize - frameSize) < diff) {
                            diff = Math.abs(expectedFrameSize - frameSize);
                            index = i;
                        }
                    }
                    capture_width = usbFrameSizeList.get(index).getWdith();
                    capture_height = usbFrameSizeList.get(index).getHeight();
                } else {
                    Log.e(TAG, "UVCCamera is not supportedSizeList!!!");
                    capture_width = 1280;
                    capture_height = 720;
                }
                Log.i(TAG, "usbFrameSizeList: W x H : " + capture_width + " x "+capture_height);
                textResolution.setText(capture_width + " x "+capture_height);
            }
            @Override
            public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock) {
                Toast.makeText(LocalUVCPreviewActivity.this,"USB驱动获取失败。请检查链接是否稳定！",Toast.LENGTH_SHORT);
            }
            @Override
            public void onCancel(UsbDevice device) {}
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(PERMISSION_WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{PERMISSION_WRITE_EXTERNAL_STORAGE},PERMISSION_REQUESTCODE);
            }
            else{
                usbMonitor = new USBMonitor(this);
                usbMonitor.register();
                usbMonitor.setOnDeviceConnectListener(usbDeviceConnectListener);
                usbMonitor.initCheckUVCState();
            }
        }
        else{
            usbMonitor = new USBMonitor(this);
            usbMonitor.register();
            usbMonitor.setOnDeviceConnectListener(usbDeviceConnectListener);
            usbMonitor.initCheckUVCState();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUESTCODE:
                if ("android.permission.WRITE_EXTERNAL_STORAGE".equals(permissions[0])
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    usbMonitor = new USBMonitor(this);
                    usbMonitor.register();
                    usbMonitor.setOnDeviceConnectListener(usbDeviceConnectListener);
                    usbMonitor.initCheckUVCState();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(btnCapture.isChecked() && usbMonitor != null){
            cameraSurfaceView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    usbMonitor.setSurfaceHolder(cameraSurfaceView.getHolder());
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                }
            },200);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(btnCapture.isChecked() && usbMonitor != null){
            usbMonitor.stopCapture();
        }
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.v(TAG, "onDestroy:");
        if(usbMonitor != null) {
            usbMonitor.stopCapture();
            usbMonitor.releaseCamera();
            usbMonitor.unregister();
        }
        super.onDestroy();
    }

    private void showResolutionDialog(){
        builder = new AlertDialog.Builder(this);
        builder.setTitle("请选择分辨率");

        final List<UsbFrameSize> usbFrameSizeList = usbMonitor.mSupportedSizeList;
        String[] usbFrameSizeStringList;
        int index = 0;
        if (usbFrameSizeList != null) {
            usbFrameSizeStringList = new String[usbFrameSizeList.size()];
            for (int i = 0; i < usbFrameSizeList.size(); i++) {
                usbFrameSizeStringList[i] = usbFrameSizeList.get(i).toString();
                if(textResolution.getText().toString().equals(usbFrameSizeStringList[i])){
                    index = i;
                }
            }
        } else {
            Log.e(TAG, "UVCCamera is not supportedSizeList!!!");
            return;
        }

        builder.setSingleChoiceItems(usbFrameSizeStringList, index, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                textResolution.setText(usbFrameSizeList.get(which).toString());
                capture_width = usbFrameSizeList.get(which).getWdith();
                capture_height = usbFrameSizeList.get(which).getHeight();
                dialog.dismiss();
                if(btnCapture.isChecked() && usbMonitor != null){
                    usbMonitor.stopCapture();
                    usbMonitor.setSurfaceHolder(cameraSurfaceView.getHolder());
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                }
            }
        });
        builder.show();
    }

    private void updateSurfaceViewSzie(int wihth, int height, int rotate){
        ViewGroup.LayoutParams ly = cameraSurfaceViewWrapper.getLayoutParams();
        if(ly.width == ViewGroup.LayoutParams.MATCH_PARENT && ly.width == ViewGroup.LayoutParams.MATCH_PARENT){
            surfaceview_default_width = cameraSurfaceViewWrapper.getWidth();
            surfaceview_default_height = cameraSurfaceViewWrapper.getHeight();
        }
        Log.i(TAG, "getWidth()="+cameraSurfaceViewWrapper.getWidth());
        Log.i(TAG, "getHeight()="+cameraSurfaceViewWrapper.getHeight());
        Log.i(TAG, "ly.width="+ly.width);
        Log.i(TAG, "ly.height="+ly.height);


        int surface_width = 0;
        int surface_height = 0;
        if(rotate == 0 || rotate == 180){
            surface_width = wihth;
            surface_height = height;
        }
        else if(rotate == 90 || rotate == 270){
            surface_width = height;
            surface_height = wihth;
        }

        Log.i(TAG, "capture_width="+surface_width);
        Log.i(TAG, "capture_height="+surface_height);
        ly.width = surface_width >= surface_height ? surfaceview_default_width
                : (int)((float)surface_width / (float)surface_height * surfaceview_default_height);
        ly.height = surface_height >= surface_width ? surfaceview_default_height
                : (int)((float)surface_height / (float)surface_width * surfaceview_default_width);
        Log.i(TAG, "ly.width="+ly.width);
        Log.i(TAG, "ly.height="+ly.height);
        cameraSurfaceViewWrapper.setLayoutParams(ly);
    }
}
