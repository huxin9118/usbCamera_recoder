package org.uvccamera.preview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.uvccamera.R;
import org.uvccamera.playback.FileListActivity;
import org.uvccamera.usb.IFrameCallback;
import org.uvccamera.usb.OnDeviceConnectListener;
import org.uvccamera.usb.USBMonitor;
import org.uvccamera.usb.UsbControlBlock;
import org.uvccamera.usb.UsbFrameSize;
import org.uvccamera.utils.ImageUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class LocalUVCPreviewActivity extends Activity{
    private static final boolean DEBUG = true;	// TODO set false on release
    private static final String TAG = "UVCDebug_MainActivity";
    public static final String PERMISSION_WRITE_EXTERNAL_STORAGE= "android.permission.WRITE_EXTERNAL_STORAGE";
    public static final String PERMISSION_RECORD_AUDIO= "android.permission.RECORD_AUDIO";
    public static final int PERMISSION_REQUESTCODE = 0;

    public static final String DEFAILT_LOCAL_UVC = Environment.getExternalStorageDirectory().getPath() + File.separator + "MPTT"
            + File.separator +  "uvc" + File.separator;

    private RelativeLayout cameraSurfaceViewWrapper;
    private SurfaceView cameraSurfaceView;
    private USBMonitor usbMonitor;
    private OnDeviceConnectListener usbDeviceConnectListener;
    private IFrameCallback frameCallback;

    private LinearLayout lytop;
    private Switch btnCapture;
    private TextView textStatus;

    private LinearLayout btnRotate;
    private TextView textRotate;

    private TextView textResolution;
    private AlertDialog.Builder builder;
    private Dialog dialog;

    private SensorManager sensorManager;
    private SensorEventListener sensorListener;
    private Sensor accelerometer;
    private Sensor magnetic;
    private boolean isAnimator;

    public int screen_width;
    public int screen_height;
    public int surfaceview_default_width;
    public int surfaceview_default_height;

    public int capture_width;
    public int capture_height;
    public int capture_rotate;

    private boolean isRecording = false;

    private LinearLayout bottomBar;
    private RelativeLayout lyPreview;
    private ImageView btnPreview;
    private RoundImageView imgPreview;
    private RelativeLayout lyShoot;
    private ImageView btnShoot;
    private ImageView imgShoot;
    private RelativeLayout lySetting;
    private ImageView btnSetting;
    private ImageView imgSetting;
    private boolean isSetting;

    private LinearLayout lyTime;
    private TextView textTime;
    private int time = 0;
    private Timer updateShootTimer;
    private String outputPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uvc_playback_activity_local_uvc_preview);

        File uvc_file = new File(DEFAILT_LOCAL_UVC);
        if(!uvc_file.exists()){
            uvc_file.mkdirs();
        }

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
                    usbMonitor.setIFrameCallback(frameCallback);
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                } else {
                    usbMonitor.stopCapture();
                }
            }
        });

        lytop = (LinearLayout)  findViewById(R.id.lytop);

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
                    usbMonitor.setIFrameCallback(frameCallback);
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
        textStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(usbMonitor != null && !usbMonitor.getUsbCameraConnect()) {
                    usbMonitor.initCheckUVCState(usbDeviceConnectListener);
                }
            }
        });


        lyTime = (LinearLayout) findViewById(R.id.lyTime);
        textTime = (TextView) findViewById(R.id.textTime);
        lyTime.setVisibility(View.GONE);

        bottomBar = (LinearLayout) findViewById(R.id.bottomBar);
        imgPreview = (RoundImageView) findViewById(R.id.imgPreview);
        btnPreview = (ImageView) findViewById(R.id.btnPreview);
        lyPreview = (RelativeLayout) findViewById(R.id.lyPreview);
        btnShoot = (ImageView) findViewById(R.id.btnShoot);
        imgShoot = (ImageView) findViewById(R.id.imgShoot);
        lyShoot = (RelativeLayout) findViewById(R.id.lyShoot);
        btnSetting = (ImageView) findViewById(R.id.btnSetting);
        imgSetting = (ImageView) findViewById(R.id.imgSetting);
        lySetting = (RelativeLayout) findViewById(R.id.lySetting);

        updateBtnBitmap();

//        bottomBar.setAlpha(0.4f);
//        btnPreview.setEnabled(false);
//        btnSetting.setEnabled(false);
        btnSetting.setAlpha(0.4f);
        imgSetting.setAlpha(0.4f);
        btnShoot.setAlpha(0.4f);
        btnSetting.setEnabled(false);
        btnShoot.setEnabled(false);

        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isSetting) {
                    isSetting = true;
                    showSettingDialog();
                }
            }
        });

        btnShoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!isRecording){
                    Log.i(TAG, "开启录像");
                    isRecording = true;
                    btnShoot.setImageResource(R.drawable.uvc_preview_selector_record_shoot_end);
                    lytop.setVisibility(View.INVISIBLE);
                    lyPreview.setVisibility(View.INVISIBLE);
                    lySetting.setVisibility(View.INVISIBLE);
                    bottomBar.setBackgroundColor(Color.parseColor("#00000000"));
                    updateShootTime(true);

                    outputPath = DEFAILT_LOCAL_UVC + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) +".mp4";
                    Log.i(TAG, "outputPath: ["+outputPath+"]");
                    usbMonitor.startRecoder(capture_width,capture_height,25, 4000000,outputPath, capture_rotate);
                }else{
                    Log.i(TAG, "停止录像");
                    isRecording = false;
                    btnShoot.setImageResource(R.drawable.uvc_preview_selector_record_shoot_start);
                    lytop.setVisibility(View.VISIBLE);
                    lyPreview.setVisibility(View.VISIBLE);
                    lySetting.setVisibility(View.VISIBLE);
                    bottomBar.setBackgroundColor(Color.parseColor("#664c4c4c"));
                    updateShootTime(false);

                    YuvImage thumbnailImage = usbMonitor.getThumbnailImage();
                    if(thumbnailImage != null){
                        new DownLoadThumbnailTask(new WeakReference(LocalUVCPreviewActivity.this), thumbnailImage).execute();
                    }
                    usbMonitor.stopRecoder();
                    Toast.makeText(LocalUVCPreviewActivity.this,getResources().getText(R.string.uvc_recode_success).toString()
                            +outputPath+getResources().getText(R.string.uvc_recode_success_unit).toString(),Toast.LENGTH_LONG).show();
                }
            }
        });

        btnPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LocalUVCPreviewActivity.this, FileListActivity.class);
                startActivity(intent);
            }
        });


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorListener = new SensorEventListener() {
            float[] accelerometerValues = new float[3];
            float[] magneticValues = new float[3];

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                    accelerometerValues = sensorEvent.values.clone();
                }else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
                    magneticValues = sensorEvent.values.clone();
                }
                float[] R = new float[9];
                float[] values = new float[3];
                SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticValues);
                SensorManager.getOrientation(R, values);
//                Log.i(TAG, "onSensorChanged: ["+Math.toDegrees(values[0])+","+Math.toDegrees(values[1])+","+Math.toDegrees(values[2])+"]");
                if(!isAnimator) {
                    if (compareRotationRange(Math.toDegrees(values[1]), 0, 30) && compareRotationRange(Math.toDegrees(values[2]), 0, 30)) {//平放
                        return;
                    } else if (compareRotationRange(Math.toDegrees(values[1]), -90, 60)) { //0
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateBtnRotation(0);
                            }
                        });
                    } else if (compareRotationRange(Math.toDegrees(values[1]), 90, 60)) { //180
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateBtnRotation(180);
                            }
                        });
                    } else if (compareRotationRange(Math.toDegrees(values[1]), 0, 30) && compareRotationRange(Math.toDegrees(values[2]), -90, 60)) { //90
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateBtnRotation(90);
                            }
                        });
                    } else if (compareRotationRange(Math.toDegrees(values[1]), 0, 30) && compareRotationRange(Math.toDegrees(values[2]), 90, 60)) { //270
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateBtnRotation(270);
                            }
                        });
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        usbDeviceConnectListener = new OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                textStatus.setText("onAttach");
            }
            @Override
            public void onDettach(UsbDevice device) {
                textStatus.setText("onDettach");
                if(isSetting && dialog != null){
                    dialog.dismiss();
                    dialog = null;
                    isSetting = false;
                }
            }

            @Override
            public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock) {
                textStatus.setText("onConnect");

                int expectedFrameSize = 1280 * 720 * 3 / 2;
                int index = 0;
                int diff = Integer.MAX_VALUE;
                List<UsbFrameSize> usbFrameSizeList = usbMonitor.getSupportedSizeList();
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
            }
            @Override
            public void onCancel(UsbDevice device) {
                Toast.makeText(LocalUVCPreviewActivity.this,R.string.uvc_diconnect,Toast.LENGTH_SHORT).show();
            }


            @Override
            public void onStartPreview() {
                btnSetting.setAlpha(1f);
                imgSetting.setAlpha(1f);
                btnShoot.setAlpha(1f);
//                btnPreview.setEnabled(true);
                btnSetting.setEnabled(true);
                btnShoot.setEnabled(true);
            }

            @Override
            public void onStopPreview() {
                btnSetting.setAlpha(0.4f);
                imgSetting.setAlpha(0.4f);
                btnShoot.setAlpha(0.4f);
//                btnPreview.setEnabled(false);
                btnSetting.setEnabled(false);
                btnShoot.setEnabled(false);
            }

            @Override
            public void onFirstReceiver() {}
        };

        frameCallback = new IFrameCallback() {
            @Override
            public void onFrame(ByteBuffer frame) {
                if(usbMonitor != null && usbMonitor.getEncoder() != null && usbMonitor.getEncoder().isOpen()) {
                    byte[] byteFrame = new byte[frame.remaining()];
                    frame.get(byteFrame, 0, byteFrame.length);
                    usbMonitor.getEncoder().encodeVideo(byteFrame, 0, byteFrame.length);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(PERMISSION_WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(PERMISSION_RECORD_AUDIO)!= PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{PERMISSION_WRITE_EXTERNAL_STORAGE,PERMISSION_RECORD_AUDIO},PERMISSION_REQUESTCODE);
            }
            else{
                usbMonitor = new USBMonitor(this);
                usbMonitor.register();
                usbMonitor.addOnDeviceConnectListener(usbDeviceConnectListener);
                usbMonitor.initCheckUVCState(usbDeviceConnectListener);
            }
        }
        else{
            usbMonitor = new USBMonitor(this);
            usbMonitor.register();
            usbMonitor.addOnDeviceConnectListener(usbDeviceConnectListener);
            usbMonitor.initCheckUVCState(usbDeviceConnectListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUESTCODE:
                if (!(PERMISSION_WRITE_EXTERNAL_STORAGE.equals(permissions[0])
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    break;
                }
                if (!(PERMISSION_RECORD_AUDIO.equals(permissions[1])
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    break;
                }

                usbMonitor = new USBMonitor(this);
                usbMonitor.register();
                usbMonitor.addOnDeviceConnectListener(usbDeviceConnectListener);
                usbMonitor.initCheckUVCState(usbDeviceConnectListener);
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
                    usbMonitor.setIFrameCallback(frameCallback);
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                }
            },200);
        }

        if(sensorManager != null && sensorListener != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(sensorListener, magnetic, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(btnCapture.isChecked() && usbMonitor != null){
            usbMonitor.stopCapture();
        }
        if(sensorManager != null && sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
        }
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.v(TAG, "onDestroy:");
        if(usbMonitor != null) {
            usbMonitor.stopRecoder();
            usbMonitor.stopCapture();
            usbMonitor.releaseCamera();
            usbMonitor.unregister();
            usbMonitor.removeOnDeviceConnectListener(usbDeviceConnectListener);
        }
        super.onDestroy();
    }

    private void updateShootTime(boolean isStart){
        if(isStart){
            textTime.setText("00:00");
            time = 0;
            lyTime.setVisibility(View.VISIBLE);
            if(updateShootTimer == null){
                updateShootTimer = new Timer();
                updateShootTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        time++;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(time < 60){
                                    textTime.setText(String.format("00:%02d", time));
                                }else if(time > 60){
                                    int min = time / 60;
                                    int second = time % 60;
                                    textTime.setText(String.format("%02d:%02d", min, second));
                                }
                            }
                        });
                    }
                },1000,1000);
            }
            else{
                updateShootTimer.cancel();
                updateShootTimer = null;
                updateShootTimer = new Timer();
                updateShootTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        time++;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(time < 60){
                                    textTime.setText(String.format("00:%02d", time));
                                }else if(time > 60){
                                    int min = time / 60;
                                    int second = time % 60;
                                    textTime.setText(String.format("%02d:%02d", min, second));
                                }
                            }
                        });
                    }
                },1000,1000);
            }
        }else{
            lyTime.setVisibility(View.GONE);
            if(updateShootTimer != null){
                updateShootTimer.cancel();
                updateShootTimer = null;
            }
        }
    }

    private void updateBtnBitmap(){
        File file = new File(DEFAILT_LOCAL_UVC,"thumbnail.jpg");
        if(file.exists()){
            try {
                FileInputStream inputStream = new FileInputStream(file);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                final Bitmap thumbnail = BitmapFactory.decodeStream(inputStream,null,options);
                imgPreview.setImageBitmap(thumbnail);
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean compareRotationRange(double rotate,int target,int range){
        return Math.abs(rotate - target) < range ? true : false;
    }

    private void updateBtnRotation(float rotate){
        isAnimator = true;
//        Log.i(TAG, "updateBtnRotation: rotate = "+rotate + " imgPreview.getRotation() = "+imgPreview.getRotation());
        ObjectAnimator animatorPreview = ObjectAnimator.ofFloat(imgPreview, "rotation",imgPreview.getRotation(),rotate);
        ObjectAnimator animatorSetting = ObjectAnimator.ofFloat(imgSetting, "rotation",imgSetting.getRotation(),rotate);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(animatorPreview).with(animatorSetting);
        animatorSet.setDuration(500);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isAnimator = false;
            }
        });
        animatorSet.start();
    }

    private void showResolutionDialog(){
        builder = new AlertDialog.Builder(this,R.style.NoBackDialog);
        builder.setTitle(R.string.uvc_dialog_resolution);

        final List<UsbFrameSize> usbFrameSizeList = usbMonitor.getSupportedSizeList();
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
                    usbMonitor.setIFrameCallback(frameCallback);
                    usbMonitor.startCapture(capture_width, capture_height, capture_rotate);
                    updateSurfaceViewSzie(capture_width, capture_height, capture_rotate);
                }
            }
        });
        builder.show();
    }

    private void showSettingDialog(){
        builder = new AlertDialog.Builder(this,R.style.NoBackDialog);

        LayoutInflater layoutInflater = getLayoutInflater();
        View dialogView = layoutInflater.inflate(R.layout.uvc_preview_dialog_setting,null);
        builder.setView(dialogView);
        dialog = builder.create();

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                Log.i(TAG, "showSettingDialog onCancel:");
                dialog = null;
                isSetting = false;
            }
        });

        final LinearLayout ly_auto_focus = (LinearLayout)dialogView.findViewById(R.id.ly_auto_focus);
        final LinearLayout ly_focus = (LinearLayout)dialogView.findViewById(R.id.ly_focus);
        final LinearLayout ly_auto_contrast = (LinearLayout)dialogView.findViewById(R.id.ly_auto_contrast);
        final LinearLayout ly_contrast = (LinearLayout)dialogView.findViewById(R.id.ly_contrast);
        final LinearLayout ly_auto_hue = (LinearLayout)dialogView.findViewById(R.id.ly_auto_hue);
        final LinearLayout ly_hue = (LinearLayout)dialogView.findViewById(R.id.ly_hue);
        final LinearLayout ly_auto_white_balance = (LinearLayout)dialogView.findViewById(R.id.ly_auto_white_balance);
        final LinearLayout ly_white_balance = (LinearLayout)dialogView.findViewById(R.id.ly_white_balance);
        final LinearLayout ly_brightness = (LinearLayout)dialogView.findViewById(R.id.ly_brightness);
        final LinearLayout ly_saturation = (LinearLayout)dialogView.findViewById(R.id.ly_saturation);
        final LinearLayout ly_sharpness = (LinearLayout)dialogView.findViewById(R.id.ly_sharpness);
        final LinearLayout ly_gamma = (LinearLayout)dialogView.findViewById(R.id.ly_gamma);
        final LinearLayout ly_backlight = (LinearLayout)dialogView.findViewById(R.id.ly_backlight);
        final LinearLayout ly_gain = (LinearLayout)dialogView.findViewById(R.id.ly_gain);

        ly_auto_focus.setVisibility(usbMonitor.isAutoFocus() ? View.VISIBLE : View.GONE);
        ly_focus.setVisibility(usbMonitor.isFocus() ? View.VISIBLE : View.GONE);
        ly_auto_contrast.setVisibility(usbMonitor.isAutoContrast() ? View.VISIBLE : View.GONE);
        ly_contrast.setVisibility(usbMonitor.isContrast() ? View.VISIBLE : View.GONE);
        ly_auto_hue.setVisibility(usbMonitor.isAutoHue() ? View.VISIBLE : View.GONE);
        ly_hue.setVisibility(usbMonitor.isHue() ? View.VISIBLE : View.GONE);
        ly_auto_white_balance.setVisibility(usbMonitor.isAutoWhiteBalance() ? View.VISIBLE : View.GONE);
        ly_white_balance.setVisibility(usbMonitor.isWhiteBalance() ? View.VISIBLE : View.GONE);
        ly_brightness.setVisibility(usbMonitor.isBrightness() ? View.VISIBLE : View.GONE);
        ly_saturation.setVisibility(usbMonitor.isSaturation() ? View.VISIBLE : View.GONE);
        ly_sharpness.setVisibility(usbMonitor.isSharpness() ? View.VISIBLE : View.GONE);
        ly_gamma.setVisibility(usbMonitor.isGamma() ? View.VISIBLE : View.GONE);
        ly_backlight.setVisibility(usbMonitor.isBacklight() ? View.VISIBLE : View.GONE);
        ly_gain.setVisibility(usbMonitor.isGain() ? View.VISIBLE : View.GONE);

        final Switch switch_auto_focus = (Switch)dialogView.findViewById(R.id.switch_auto_focus);
        final SeekBar seek_focus = (SeekBar)dialogView.findViewById(R.id.seek_focus);
        final Switch switch_auto_contrast = (Switch)dialogView.findViewById(R.id.switch_auto_contrast);
        final SeekBar seek_contrast = (SeekBar)dialogView.findViewById(R.id.seek_contrast);
        final Switch switch_auto_hue = (Switch)dialogView.findViewById(R.id.switch_auto_hue);
        final SeekBar seek_hue = (SeekBar)dialogView.findViewById(R.id.seek_hue);
        final Switch switch_auto_white_balance = (Switch)dialogView.findViewById(R.id.switch_auto_white_balance);
        final SeekBar seek_white_balance = (SeekBar)dialogView.findViewById(R.id.seek_white_balance);
        final SeekBar seek_brightness = (SeekBar)dialogView.findViewById(R.id.seek_brightness);
        final SeekBar seek_saturation = (SeekBar)dialogView.findViewById(R.id.seek_saturation);
        final SeekBar seek_sharpness = (SeekBar)dialogView.findViewById(R.id.seek_sharpness);
        final SeekBar seek_gamma = (SeekBar)dialogView.findViewById(R.id.seek_gamma);
        final SeekBar seek_backlight = (SeekBar)dialogView.findViewById(R.id.seek_backlight);
        final SeekBar seek_gain = (SeekBar)dialogView.findViewById(R.id.seek_gain);

        CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isCheck) {
                if(usbMonitor != null && usbMonitor.getUVCCamera() != null) {
                    switch (compoundButton.getId()) {
                        case R.id.switch_auto_focus:
                            seek_focus.setEnabled(!isCheck);
                            usbMonitor.getUVCCamera().setAutoFocus(isCheck);
                            if (!isCheck && usbMonitor.isFocus()) {
                                seek_focus.setProgress(usbMonitor.getUVCCamera().getFocus());
                            }
                            break;
                        case R.id.switch_auto_contrast:
                            seek_contrast.setEnabled(!isCheck);
                            usbMonitor.getUVCCamera().setAutoContrast(isCheck);
                            if (!isCheck && usbMonitor.isContrast()) {
                                seek_contrast.setProgress(usbMonitor.getUVCCamera().getContrast());
                            }
                            break;
                        case R.id.switch_auto_hue:
                            seek_hue.setEnabled(!isCheck);
                            usbMonitor.getUVCCamera().setAutoHue(isCheck);
                            if (!isCheck && usbMonitor.isHue()) {
                                seek_hue.setProgress(usbMonitor.getUVCCamera().getHue());
                            }
                            break;
                        case R.id.switch_auto_white_balance:
                            usbMonitor.getUVCCamera().setAutoWhiteBlance(isCheck);
                            seek_white_balance.setEnabled(!isCheck);
                            if (!isCheck && usbMonitor.isWhiteBalance()) {
                                seek_white_balance.setProgress(usbMonitor.getUVCCamera().getWhiteBlance());
                            }
                            break;
                    }
                } else{
                    dialog.dismiss();
                }
            }
        };

        switch_auto_focus.setOnCheckedChangeListener(onCheckedChangeListener);
        switch_auto_contrast.setOnCheckedChangeListener(onCheckedChangeListener);
        switch_auto_hue.setOnCheckedChangeListener(onCheckedChangeListener);
        switch_auto_white_balance.setOnCheckedChangeListener(onCheckedChangeListener);

        if(usbMonitor.isAutoFocus()) {
            switch_auto_focus.setChecked(usbMonitor.getUVCCamera().getAutoFocus());
        }
        if(usbMonitor.isAutoContrast()) {
            switch_auto_contrast.setChecked(usbMonitor.getUVCCamera().getAutoContrast());
        }
        if(usbMonitor.isAutoHue()) {
            switch_auto_hue.setChecked(usbMonitor.getUVCCamera().getAutoHue());
        }
        if(usbMonitor.isAutoWhiteBalance()) {
            switch_auto_white_balance.setChecked(usbMonitor.getUVCCamera().getAutoWhiteBlance());
        }

        SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(usbMonitor != null && usbMonitor.getUVCCamera() != null) {
                    if (fromUser) {
                        switch (seekBar.getId()) {
                            case R.id.seek_focus:
                                usbMonitor.getUVCCamera().setFocus(progress);
                                break;
                            case R.id.seek_contrast:
                                usbMonitor.getUVCCamera().setContrast(progress);
                                break;
                            case R.id.seek_hue:
                                usbMonitor.getUVCCamera().setHue(progress);
                                break;
                            case R.id.seek_white_balance:
                                usbMonitor.getUVCCamera().setWhiteBlance(progress);
                                break;
                            case R.id.seek_brightness:
                                usbMonitor.getUVCCamera().setBrightness(progress);
                                break;
                            case R.id.seek_saturation:
                                usbMonitor.getUVCCamera().setSaturation(progress);
                                break;
                            case R.id.seek_sharpness:
                                usbMonitor.getUVCCamera().setSharpness(progress);
                                break;
                            case R.id.seek_gamma:
                                usbMonitor.getUVCCamera().setGamma(progress);
                                break;
                            case R.id.seek_backlight:
                                usbMonitor.getUVCCamera().setBacklight(progress);
                                break;
                            case R.id.seek_gain:
                                usbMonitor.getUVCCamera().setGain(progress);
                                break;
                        }
                    }
                } else{
                    dialog.dismiss();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        seek_focus.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_contrast.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_hue.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_white_balance.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_brightness.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_saturation.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_sharpness.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_gamma.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_backlight.setOnSeekBarChangeListener(onSeekBarChangeListener);
        seek_gain.setOnSeekBarChangeListener(onSeekBarChangeListener);

        if(usbMonitor.isFocus()){
            seek_contrast.setProgress(usbMonitor.getUVCCamera().getFocus());
        }
        if(usbMonitor.isContrast()){
            seek_contrast.setProgress(usbMonitor.getUVCCamera().getContrast());
        }
        if(usbMonitor.isHue()){
            seek_hue.setProgress(usbMonitor.getUVCCamera().getHue());
        }
        if(usbMonitor.isWhiteBalance()){
            seek_white_balance.setProgress(usbMonitor.getUVCCamera().getWhiteBlance());
        }
        if(usbMonitor.isBrightness()){
            seek_brightness.setProgress(usbMonitor.getUVCCamera().getBrightness());
        }
        if(usbMonitor.isSaturation()){
            seek_saturation.setProgress(usbMonitor.getUVCCamera().getSaturation());
        }
        if(usbMonitor.isSharpness()){
            seek_sharpness.setProgress(usbMonitor.getUVCCamera().getSharpness());
        }
        if(usbMonitor.isGamma()){
            seek_gamma.setProgress(usbMonitor.getUVCCamera().getGamma());
        }
        if(usbMonitor.isBacklight()){
            seek_backlight.setProgress(usbMonitor.getUVCCamera().getBacklight());
        }
        if(usbMonitor.isGain()){
            seek_gain.setProgress(usbMonitor.getUVCCamera().getGain());
        }

        final TextView btn_reset = (TextView)dialogView.findViewById(R.id.btn_reset);
        btn_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(usbMonitor != null && usbMonitor.getUVCCamera() != null) {
                    if(usbMonitor.isFocus()){
                        usbMonitor.getUVCCamera().resetFocus();
                        seek_contrast.setProgress(usbMonitor.getUVCCamera().getFocus());
                    }
                    if(usbMonitor.isContrast()){
                        usbMonitor.getUVCCamera().resetContrast();
                        seek_contrast.setProgress(usbMonitor.getUVCCamera().getContrast());
                    }
                    if(usbMonitor.isHue()){
                        usbMonitor.getUVCCamera().resetHue();
                        seek_hue.setProgress(usbMonitor.getUVCCamera().getHue());
                    }
                    if(usbMonitor.isWhiteBalance()){
                        usbMonitor.getUVCCamera().resetWhiteBlance();
                        seek_white_balance.setProgress(usbMonitor.getUVCCamera().getWhiteBlance());
                    }
                    if(usbMonitor.isBrightness()){
                        usbMonitor.getUVCCamera().resetBrightness();
                        seek_brightness.setProgress(usbMonitor.getUVCCamera().getBrightness());
                    }
                    if(usbMonitor.isSaturation()){
                        usbMonitor.getUVCCamera().resetSaturation();
                        seek_saturation.setProgress(usbMonitor.getUVCCamera().getSaturation());
                    }
                    if(usbMonitor.isSharpness()){
                        usbMonitor.getUVCCamera().resetSharpness();
                        seek_sharpness.setProgress(usbMonitor.getUVCCamera().getSharpness());
                    }
                    if(usbMonitor.isGamma()){
                        usbMonitor.getUVCCamera().resetGamma();
                        seek_gamma.setProgress(usbMonitor.getUVCCamera().getGamma());
                    }
                    if(usbMonitor.isBacklight()){
                        usbMonitor.getUVCCamera().resetBacklight();
                        seek_backlight.setProgress(usbMonitor.getUVCCamera().getBacklight());
                    }
                    if(usbMonitor.isGain()){
                        usbMonitor.getUVCCamera().resetGain();
                        seek_gain.setProgress(usbMonitor.getUVCCamera().getGain());
                    }
                } else{
                    dialog.dismiss();
                }
            }
        });

        dialog.show();
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

    class DownLoadThumbnailTask extends AsyncTask<Void,Void,Boolean> {
        private WeakReference<LocalUVCPreviewActivity> activityWeakReference;
        private YuvImage thumbnailImage;

        public DownLoadThumbnailTask(WeakReference<LocalUVCPreviewActivity> activityWeakReference, YuvImage thumbnailImage){
            this.activityWeakReference = activityWeakReference;
            this.thumbnailImage = thumbnailImage;
        }

        @Override
        protected Boolean doInBackground(Void... args) {
            Log.i(TAG, "doInBackground: outputPath = "+outputPath);
            
            LocalUVCPreviewActivity activity = activityWeakReference.get();
            if(activity != null) {
                ByteArrayOutputStream bas = new ByteArrayOutputStream();
                thumbnailImage.compressToJpeg(new Rect(0,0, thumbnailImage.getWidth(),thumbnailImage.getHeight()),100, bas);
                ByteArrayInputStream bis = new ByteArrayInputStream(bas.toByteArray());

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                Bitmap thumbnail = BitmapFactory.decodeStream(bis,null,options);
                if(thumbnail != null){
                    thumbnail = ImageUtils.rotateBitmap(thumbnail,capture_rotate);
                    final Bitmap saveThumbnail = ImageUtils.cropBitmap(thumbnail);
                    final String savePath = ImageUtils.saveBitmap(activity, saveThumbnail, DEFAILT_LOCAL_UVC, "thumbnail");
                    if (savePath != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imgPreview.setImageBitmap(saveThumbnail);
                            }
                        });
                    }
                }
                try {
                    bas.close();
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
    }
}
