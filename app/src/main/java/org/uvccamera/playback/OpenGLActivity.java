package org.uvccamera.playback;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.AnimationDrawable;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import org.uvccamera.R;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class OpenGLActivity extends AppCompatActivity {
    private Toast mToast;
    private static final String TAG = "opengl";
	public static final int ZOOM_INSIDE = 0;//适应屏幕
	public static final int ZOOM_ORIGINAL = 1;//原始
	public static final int ZOOM_STRETCH = 2;//拉伸

    public static final int CODEC_TYPE_FFMPEG = 0;//ffmpeg
    public static final int CODEC_TYPE_MEDIACODEC = 1;//mediacodec

    private boolean isStreamMedia = false;
    private boolean isYUV = false;
    private int  codec_type = 0;
    private double time_base = 0;
    public static final int forward_offset_step = 2;

    private RelativeLayout root;
    private Timer updateShowUITimer;
    private static Handler showUIHandler;

    private LinearLayout bottomBar;
    private ImageView imgPause;
    private ImageView imgBackward;
    private ImageView imgForward;
    private ImageView imgBack;
    private ImageView imgZoom;
    private RelativeLayout btnPause;
    private RelativeLayout btnBackward;
    private RelativeLayout btnForward;
    private RelativeLayout btnBack;
    private RelativeLayout btnZoom;
    private TextView btnCodecType;
    private int zoom = ZOOM_INSIDE;
    private boolean isPause = false;

    private RelativeLayout topBar;
    private TextView title;
    private static Handler progressRateHandler;

    private LinearLayout progressBar;
    private TextView timeStart;
    private TextView timeEnd;
    private SeekBar progressRate;
    private ImageView btnRotate;

    private TextView textStatus;
    private Timer updateStatusTimer;

    private ImageView loading;
    private AnimationDrawable loadingAnimation;

    private String input_url = null;
    private int yuv_pixel_w = -1;
    private int yuv_pixel_h = -1;
    private int yuv_pixel_type = 0;
    private int yuv_fps = 0;
    private AlertDialog.Builder builder;
    private boolean isSetPixel = false;
    private boolean isSetPixelWandH = false;

    private GLSurfaceView mGLSurface;
    private GLRenderer mGLRenderer;
    GLRenderer.RenderListener mGLRenderListener;

    // Setup
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        input_url = intent.getStringExtra("input_url");
//        input_url ="/storage/emulated/0/testvideo/cwssm_848x480.yuv";
        isYUV = intent.getBooleanExtra("isYUV",false);
        isStreamMedia = intent.getBooleanExtra("isStreamMedia",false);
        codec_type = intent.getIntExtra("codec_type",0);
        Log.i(TAG, "input_url: " + input_url);
        Log.i(TAG, "isYUV: " + isYUV);
        Log.i(TAG, "isStreamMedia: " + isStreamMedia);
        Log.i(TAG, "codec_type: " + codec_type);

        setContentView(R.layout.activity_opengl);

        root = (RelativeLayout) findViewById(R.id.root);
        bottomBar = (LinearLayout) findViewById(R.id.bottomBar);
        topBar = (RelativeLayout) findViewById(R.id.topBar);
        progressBar = (LinearLayout) findViewById(R.id.progressBar);
        btnRotate = (ImageView) findViewById(R.id.btnRotate);
        root.setVisibility(View.GONE);

        imgPause = (ImageView) findViewById(R.id.imgPause);
        imgBackward = (ImageView) findViewById(R.id.imgBackward);
        imgForward = (ImageView) findViewById(R.id.imgForward);
        imgBack = (ImageView) findViewById(R.id.imgBack);
        imgZoom = (ImageView) findViewById(R.id.imgZoom);

        btnPause = (RelativeLayout) findViewById(R.id.btnPause);
        btnBackward = (RelativeLayout) findViewById(R.id.btnBackward);
        btnForward = (RelativeLayout) findViewById(R.id.btnForward);
        btnBack = (RelativeLayout) findViewById(R.id.btnBack);
        btnZoom = (RelativeLayout) findViewById(R.id.btnZoom);

        textStatus = (TextView) findViewById(R.id.textStatus);
        textStatus.setVisibility(View.GONE);

        title = (TextView) findViewById(R.id.title);
        btnCodecType = (TextView) findViewById(R.id.btnCodecType);
        if(isStreamMedia) {
            title.setText(input_url);

            loading = (ImageView) findViewById(R.id.loading);
            loading.setImageResource(R.drawable.animation_list_loading_blue);
            loadingAnimation = (AnimationDrawable) loading.getDrawable();
            loadingAnimation.start();
        } else {
            title.setText(input_url.substring(input_url.lastIndexOf("/") + 1));
        }

        timeStart = (TextView) findViewById(R.id.timeStart);
        timeEnd = (TextView) findViewById(R.id.timeEnd);
        progressRate = (SeekBar) findViewById(R.id.progressRate);

        if(isYUV) {
            if(updatePixelData(input_url)) {
                isSetPixel = true;
                File file = new File(input_url);
                int frameCount = (int) ((file.length() * 2) / (yuv_pixel_h * yuv_pixel_w * 3));
                Log.i(TAG, "frameCount: " + frameCount);
                timeStart.setText("0");
                timeEnd.setText("" + frameCount);
                progressRate.setProgress(0);
                progressRate.setMax(frameCount);
            }
            else {
                isSetPixel = false;
                showSetPixelDialog();
            }
        }
        else{
            isSetPixel = true;
            timeStart.setText("00:00:00");
            timeEnd.setText("00:00:00");
            progressRate.setProgress(0);
            progressRate.setMax(0);
        }

        if(isStreamMedia){
            btnPause.setAlpha(0.5f);
            btnBackward.setAlpha(0.5f);
            btnForward.setAlpha(0.5f);
            btnZoom.setAlpha(0.5f);
            btnRotate.setAlpha(0.5f);
            progressRate.setAlpha(0.5f);
            btnPause.setEnabled(false);
            btnBackward.setEnabled(false);
            btnForward.setEnabled(false);
            btnZoom.setEnabled(false);
            btnRotate.setEnabled(false);
            progressRate.setEnabled(false);
        }

        progressRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    updateShowUI();
                    updateSeekStatus(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(!isPause){
                    mGLRenderer.nativePauseSDLThread();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(!isPause){
                    mGLRenderer.nativePlaySDLThread();
                }
                mGLRenderer.nativeSeekSDLThread(seekBar.getProgress());
            }
        });

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPause) {
                    updateShowUITimer.cancel();
                    updateShowUITimer = null;
                    isPause = true;
                    imgPause.setImageResource(R.drawable.ic_button_play);
                    mGLRenderer.nativePauseSDLThread();
                } else {
                    updateShowUI();
                    isPause = false;
                    imgPause.setImageResource(R.drawable.ic_button_pause);
                    mGLRenderer.nativePlaySDLThread();
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                mGLRenderer.stopRender();
            }
        });

        btnZoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                switch (zoom){
                    case ZOOM_INSIDE:
                        zoom = ZOOM_ORIGINAL;
                        imgZoom.setImageResource(R.drawable.ic_zoom_stretch);
                        break;
                    case ZOOM_ORIGINAL:
                        zoom = ZOOM_STRETCH;
                        imgZoom.setImageResource(R.drawable.ic_zoom_inside);
                        break;
                    case ZOOM_STRETCH:
                        zoom = ZOOM_INSIDE;
                        imgZoom.setImageResource(R.drawable.ic_zoom_original);
                        break;
                }
                updateZoomStatus(zoom);
                mGLRenderer.updateZoom(zoom);
            }
        });

        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(isYUV) {
                    mGLRenderer.nativeForwardSDLThread(25);//前进25帧
                    updateSkipStatus(25,false);
                }
                else{
                    mGLRenderer.nativeForwardSDLThread(5);//前进5秒
                    updateSkipStatus(5,false);
                }
            }
        });

        btnBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(isYUV) {
                    mGLRenderer.nativeBackwardSDLThread(-25);//后退25帧
                    updateSkipStatus(-25,false);
                }
                else{
                    mGLRenderer.nativeBackwardSDLThread(-5);//后退5秒
                    updateSkipStatus(-5,false);
                }
            }
        });

        btnRotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateShowUI();
                if(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                else if(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                else{//SCREEN_ORIENTATION_UNSPECIFIED
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            }
        });

        btnCodecType.setText(codec_type == 0 ? "软解" : "硬解");
        btnCodecType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isPause) {
                    mGLRenderer.nativePauseSDLThread();
                }
                showCodecTypeDialog();
            }
        });
        if(isYUV){
            btnCodecType.setVisibility(View.GONE);
        }

        showUIHandler = new ShowUIHandler(this);
        progressRateHandler = new ProgressRateHandler(this);

//        loading = (ImageView) findViewById(R.id.loading);
//        loading.setImageResource(R.drawable.animation_list_loading_blue);
//        AnimationDrawable animationDrawable = (AnimationDrawable) loading.getDrawable();
//        animationDrawable.start();

        mGLSurface = (GLSurfaceView)findViewById(R.id.mGLSurface);
        mGLSurface.setEGLContextClientVersion(2);
        mGLRenderer = new GLRenderer(mGLSurface);
        mGLSurface.setRenderer(mGLRenderer);
        mGLSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mGLSurface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(root.getVisibility() == View.VISIBLE){
                    root.setVisibility(View.GONE);
                }
                else {
                    updateShowUI();
                }
            }
        });
        mGLRenderListener = new GLRenderer.RenderListener() {
            public void setProgressRate(int frameConut){
//               Log.i(TAG, "--------正在播放第"+frameConut+"帧-------");
                progressRateHandler.sendEmptyMessage(frameConut);
            }

            public void setProgressRateFull(){
                Log.i(TAG, "##########播放完成##########");
                progressRateHandler.sendEmptyMessage(-1);
            }

            public void setProgressDuration(long duration){
//               Log.i(TAG, "###########"+duration);
                Message msg = Message.obtain();
                msg.what = -2;
                Bundle bundle = new Bundle();
                bundle.putLong("duration",duration);
                msg.setData(bundle);
                progressRateHandler.sendMessage(msg);
            }

            public void setProgressDTS(long dts){
                Message msg = Message.obtain();
                msg.what = -3;
                Bundle bundle = new Bundle();
                bundle.putLong("dts",dts);
                msg.setData(bundle);
                progressRateHandler.sendMessage(msg);
            }

            public void showIFrameDTS(long I_Frame_dts,int forwardOffset){
                if(forwardOffset > 0){//前进偏移
                    mGLRenderer.nativeForwardSDLThread(5+forward_offset_step * forwardOffset);//前进5秒
                }
                else if(forwardOffset == -1){//前进偏移终止（文件结尾）
                    Message msg = Message.obtain();
                    msg.what = -5;
                    Bundle bundle = new Bundle();
                    bundle.putLong("I_Frame_dts",I_Frame_dts);
                    msg.setData(bundle);
                    progressRateHandler.sendMessage(msg);
                }
                else{
                    Message msg = Message.obtain();
                    msg.what = -4;
                    Bundle bundle = new Bundle();
                    bundle.putLong("I_Frame_dts",I_Frame_dts);
                    msg.setData(bundle);
                    progressRateHandler.sendMessage(msg);
                }
            }

            public void initOrientation(){
                Log.i(TAG, "##########initOrientation##########");
                showUIHandler.sendEmptyMessage(2);
            }

            public void hideLoading(){
                Log.i(TAG, "##########hideLoading##########");
                showUIHandler.sendEmptyMessage(3);
            }

            public void showLoading(){
                Log.i(TAG, "##########showLoading##########");
                showUIHandler.sendEmptyMessage(4);
            }

            public void changeCodec(String codec_name){
                Log.i(TAG, "##########changeCodec##########");
                Message msg = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putString("codec_name",codec_name);
                msg.what = 5;
                msg.setData(bundle);
                showUIHandler.sendMessage(msg);
            }

            public void setTimeBase(double timeBase) {
                time_base = timeBase;
            }

            @Override
            public void renderThreadFinish(int result_code) {
                showUIHandler.sendEmptyMessage(result_code);
                finish();
            }
        };
        if(isSetPixel) {
            mGLRenderer.setListener(mGLRenderListener);
            mGLRenderer.startRender(isYUV, input_url,yuv_pixel_w, yuv_pixel_h, yuv_pixel_type, yuv_fps, isStreamMedia);
        }
    }

    private void showSetPixelDialog(){
        builder = new AlertDialog.Builder(this);
        builder.setTitle("YUV格式设置");
        LayoutInflater layoutInflater = getLayoutInflater();
        View dialogView = layoutInflater.inflate(R.layout.dialog_set_pixel,null);
        final EditText textWdith = (EditText) dialogView.findViewById(R.id.textWdith);
        final EditText textHeight = (EditText) dialogView.findViewById(R.id.textHeight);
        final EditText textFps = (EditText) dialogView.findViewById(R.id.textFps);
        final CheckBox checkBox = (CheckBox) dialogView.findViewById(R.id.rename);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.spinner);

        if(yuv_pixel_w != -1){
            textWdith.setText(yuv_pixel_w +"");
        }
        if(yuv_pixel_h != -1){
            textHeight.setText(yuv_pixel_h +"");
        }
        if(yuv_fps != 0){
            textFps.setText(yuv_fps +"");
        }

        final ArrayList<String> items = new ArrayList<String>();
        items.add("I420");
        items.add("YV12");
        items.add("NV12");
        items.add("NV21");
        items.add("YUYV");
        items.add("UYVY");
        items.add("请选择YUV像素类型"); // Last item

        SpinnerAdapter adapter = new SpinnerAdapter(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(items.size() - 1);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                yuv_pixel_type = position % (items.size()-1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                yuv_pixel_type = 0;
            }
        });


        builder.setView(dialogView);
        builder.setPositiveButton("确定", null);
        builder.setNegativeButton("使用缺省值(640x360)", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                yuv_pixel_w = 640;
                yuv_pixel_h = 480;
                yuv_pixel_type = 0;
                yuv_fps = 25;
                isSetPixel = true;
                File file = new File(input_url);
                int frameCount = (int) ((file.length() * 2) / (yuv_pixel_h * yuv_pixel_w * 3));
                Log.i(TAG, "frameCount: " + frameCount);
                timeStart.setText("0");
                timeEnd.setText("" + frameCount);
                progressRate.setProgress(0);
                progressRate.setMax(frameCount);

                mGLRenderer.setListener(mGLRenderListener);
                mGLRenderer.startRender(isYUV, input_url,yuv_pixel_w, yuv_pixel_h, yuv_pixel_type, yuv_fps, isStreamMedia);
                dialog.dismiss();
            }
        });
        builder.setNeutralButton("返回",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelToast();
                        finish();
                    }
        });
        builder.setCancelable(false);
        final AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.customBlue));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.textSecondaryColor));
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(input_url);
                try {
                    long file_length = file.length();
                    yuv_pixel_w = Integer.parseInt(textWdith.getText().toString().trim());
                    yuv_pixel_h = Integer.parseInt(textHeight.getText().toString().trim());
                    yuv_fps = Integer.parseInt(textFps.getText().toString().trim());
                    if(checkBox.isChecked()){
                        String old_url = input_url;
                        if(isSetPixelWandH){
                            int _index = input_url.lastIndexOf("_");
                            input_url = input_url.substring(0, _index) + "&" + yuv_pixel_type + "@" + yuv_fps + "_" + yuv_pixel_w + "x" + yuv_pixel_h + ".yuv";
                        }
                        else {
                            input_url = input_url.substring(0, input_url.length() - 4) + "&" + yuv_pixel_type + "@" + yuv_fps + "_" + yuv_pixel_w + "x" + yuv_pixel_h + ".yuv";
                        }
                        if(file.renameTo(new File(input_url))) {
                            showToast("YUV重命名成功!", Toast.LENGTH_SHORT);
//                            UrlService urlService = new UrlService();
//                            urlService.updateUrl(old_url,input_url);
                            setResult(RESULT_OK);
                        }
                        else{
                            showToast("YUV重命名失败!", Toast.LENGTH_SHORT);
                        }
                    }
                    isSetPixel = true;
                    int frameCount = (int) ((file_length * 2) / (yuv_pixel_h * yuv_pixel_w * 3));
                    Log.i(TAG, "frameCount: " + frameCount);
                    timeStart.setText("0");
                    timeEnd.setText("" + frameCount);
                    progressRate.setProgress(0);
                    progressRate.setMax(frameCount);

                    mGLRenderer.setListener(mGLRenderListener);
                    mGLRenderer.startRender(isYUV, input_url,yuv_pixel_w, yuv_pixel_h, yuv_pixel_type, yuv_fps, isStreamMedia);
                    dialog.dismiss();
                }
                catch (Exception e){
                    showToast("请输入正确的YUV解码参数~~", Toast.LENGTH_SHORT);
                }
            }
        });
    }

    private void showCodecTypeDialog(){
        final SharedPreferences sp = getSharedPreferences("user", Context.MODE_PRIVATE);
        Log.i(TAG, "showCodecTypeDialog: codec_type = "+codec_type);

        builder = new AlertDialog.Builder(this);
        builder.setTitle("解码器");
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if(!isPause) {
                    mGLRenderer.nativePlaySDLThread();
                }
            }
        });

        final String[] codec_type_items = new String[]{"软件解码器","硬件解码器"};

        builder.setSingleChoiceItems(codec_type_items, codec_type, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mGLRenderer.nativeCodecType(which);
                if(codec_type != which) {
                    codec_type = which;
                    updateShowUI();
                    mGLRenderer.nativeBackwardSDLThread(-1);//后退5秒
                    updateSkipStatus(-1,true);
                }
                if(!isPause) {
                    mGLRenderer.nativePlaySDLThread();
                }
                btnCodecType.setText(codec_type == 0 ? "软解" : "硬解");
                dialog.dismiss();
            }
        });
        builder.show();
    }

    class SpinnerAdapter extends ArrayAdapter<String> {
        public SpinnerAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public int getCount() {
            return super.getCount() - 1; // This makes the trick: do not show last item
        }

        @Override
        public String getItem(int position) {
            return super.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return super.getItemId(position);
        }

    }

    private void updateZoomStatus(int zoom){
        String textZoomStatus = "";
        switch (zoom){
            case ZOOM_INSIDE:
                textZoomStatus = "适应屏幕";
                break;
            case ZOOM_ORIGINAL:
                textZoomStatus = "原始";
                break;
            case ZOOM_STRETCH:
                textZoomStatus = "拉伸";
                break;
        }
        textStatus.setText(textZoomStatus);
        textStatus.setVisibility(View.VISIBLE);
        if(updateStatusTimer == null){
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1500);
        }
        else{
            updateStatusTimer.cancel();
            updateStatusTimer = null;
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1500);
        }
    }

    private void updateSkipStatus(int skipFrame, boolean isChangeCodec){
        String textSkipStatus;
        if(isYUV) {
            if (skipFrame > 0) {
                textSkipStatus = "前进 " + skipFrame + " 帧";
            } else {
                textSkipStatus = "后退 " + -1 * skipFrame + " 帧";
            }
        }
        else {
            if (skipFrame > 0) {
                textSkipStatus = "前进 " + skipFrame + " s";
            } else {
                textSkipStatus = "后退 " + -1 * skipFrame + " s";
            }
        }
        if(isChangeCodec){
            textStatus.setText("更换解码器，重新定位");
        }
        else {
            textStatus.setText(textSkipStatus);
        }
        textStatus.setVisibility(View.VISIBLE);
        if(updateStatusTimer == null){
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
        else{
            updateStatusTimer.cancel();
            updateStatusTimer = null;
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
    }

    private void updateSeekStatus(int seekFrame){
        String textSeekFrame;
        if(isYUV) {
            if(seekFrame < 1){
                seekFrame = 1;
            }
            textSeekFrame = "跳转到 第 " + seekFrame + " 帧";
        }
        else{
            int time = (int)((double)seekFrame * time_base);
            int hour = time/3600;
            int minute = time/60 % 60;
            int second = time % 60;
            textSeekFrame = "跳转到 " + String.format("[%02d:%02d:%02d]",hour,minute,second);
        }
        textStatus.setText(textSeekFrame);
        textStatus.setVisibility(View.VISIBLE);
        if(updateStatusTimer == null){
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
        else{
            updateStatusTimer.cancel();
            updateStatusTimer = null;
            updateStatusTimer = new Timer();
            updateStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(0);
                }
            },1000);
        }
    }

    private void updateShowUI(){
        root.setVisibility(View.VISIBLE);
        if(updateShowUITimer == null){
            updateShowUITimer = new Timer();
            updateShowUITimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(1);
                }
            },4000);
        }
        else{
            updateShowUITimer.cancel();
            updateShowUITimer = null;
            updateShowUITimer = new Timer();
            updateShowUITimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showUIHandler.sendEmptyMessage(1);
                }
            },4000);
        }
    }


    private boolean updatePixelData(String input_url){
        int typeIndex = input_url.lastIndexOf("&");
        int fpsIndex = input_url.lastIndexOf("@");
        int _Index = input_url.lastIndexOf("_");
        int xIndex = input_url.lastIndexOf("x");
        int pointIndex = input_url.lastIndexOf(".");
        if (_Index < xIndex && xIndex < pointIndex) {
            try {
                yuv_pixel_w = Integer.parseInt(input_url.substring(_Index + 1, xIndex));
                yuv_pixel_h = Integer.parseInt(input_url.substring(xIndex + 1, pointIndex));
                isSetPixelWandH = true;
                if(typeIndex == -1 && fpsIndex == -1){
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_h);
                    return false;
                }
                if(typeIndex == -1) {
                    yuv_fps = Integer.parseInt(input_url.substring(fpsIndex + 1, _Index));
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_h+" fps:"+yuv_fps);
                    return false;
                }
                if(fpsIndex == -1){
                    yuv_pixel_type = Integer.parseInt(input_url.substring(typeIndex + 1, _Index));
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_h+" type:"+yuv_pixel_type);
                    return false;
                }
                if(typeIndex < fpsIndex && fpsIndex < _Index){
                    yuv_pixel_type = Integer.parseInt(input_url.substring(typeIndex + 1, fpsIndex));
                    yuv_fps = Integer.parseInt(input_url.substring(fpsIndex + 1, _Index));
                    Log.i(TAG, "输入YUV格式信息为 W:"+yuv_pixel_w+" H:"+yuv_pixel_w+" type:"+yuv_pixel_type+" fps:"+yuv_fps);
                    return true;
                }
            } catch (Exception e) {
                Log.i(TAG, "输入YUV格式信息解析错误");
                return false;
            }
        }
        Log.i(TAG, "输入YUV格式信息解析错误");
        return false;
    }

    static class ProgressRateHandler extends Handler {
        WeakReference<OpenGLActivity> weakReference;
        ProgressRateHandler(OpenGLActivity activity){
            weakReference = new WeakReference<OpenGLActivity>(activity);
        }
        
        @Override
        public void handleMessage(Message msg) {
            OpenGLActivity activity = weakReference.get();
            if (activity != null) {
                if(msg.what == -5){//封装格式视频dts定位终止（文件结尾）专用消息
                    long I_Frame_dts = msg.getData().getLong("I_Frame_dts");
                    int frameCount = msg.getData().getInt("frameCount");
                    int time = (int) ((double) I_Frame_dts * activity.time_base);
                    int hour = time / 3600;
                    int minute = time / 60 % 60;
                    int second = time % 60;

                    activity.progressRate.setProgress((int) I_Frame_dts);
                    activity.timeStart.setText(String.format("%02d:%02d:%02d", hour, minute, second));
//                    activity.showToast("临近文件末尾，视频前进无效~~",Toast.LENGTH_SHORT);
                }
                else if(msg.what == -4){//封装格式视频dts定位专用消息
                    long I_Frame_dts = msg.getData().getLong("I_Frame_dts");
                    int frameCount = msg.getData().getInt("frameCount");
                    int time = (int) ((double) I_Frame_dts * activity.time_base);
                    int hour = time / 3600;
                    int minute = time / 60 % 60;
                    int second = time % 60;

                    activity.progressRate.setProgress((int) I_Frame_dts);
                    activity.timeStart.setText(String.format("%02d:%02d:%02d", hour, minute, second));
                }
                else if(msg.what == -3){//封装格式视频进度更新专用消息
                    long dts = msg.getData().getLong("dts");
//                    Log.i(TAG, "=================="+dts);
                    int time = (int)((double)dts * activity.time_base);
                    int hour = time/3600;
                    int minute = time/60 % 60;
                    int second = time % 60;

                    if(dts>activity.progressRate.getProgress()) {//消除dts乱序影响
                        activity.progressRate.setProgress((int)dts);
                        activity.timeStart.setText(String.format("%02d:%02d:%02d",hour,minute,second));
                    }
                }
                else if(msg.what == -2){//封装格式视频总时长设置专用消息
                    long duration = 0;
                    if(!activity.isStreamMedia){
                        duration = msg.getData().getLong("duration");
                    }
                    if(duration == 0){
                        activity.btnBackward.setEnabled(false);
                        activity.btnForward.setEnabled(false);
                        activity.progressRate.setOnSeekBarChangeListener(null);
                        activity.btnBackward.setAlpha(0.5f);
                        activity.btnForward.setAlpha(0.5f);
                    }
                    int maxTime = (int)((double)duration * activity.time_base);
                    int hour = maxTime/3600;
                    int minute = maxTime/60 % 60;
                    int second = maxTime % 60;

                    activity.timeStart.setText("00:00:00");
                    activity.timeEnd.setText(String.format("%02d:%02d:%02d",hour,minute,second));
                    activity.progressRate.setProgress(0);
                    activity.progressRate.setMax((int)duration);
                }
                else if(msg.what == -1) {//播放完成消息
                    activity.showToast("播放完成~~", Toast.LENGTH_SHORT);
                }
                else {//YUV进度更新专用消息
                    activity.timeStart.setText(msg.what + "");
                    activity.progressRate.setProgress(msg.what);
                }
            }
        }
    }

    static class ShowUIHandler extends Handler {
        WeakReference<OpenGLActivity> weakReference;
        ShowUIHandler(OpenGLActivity activity){
            weakReference = new WeakReference<OpenGLActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            OpenGLActivity activity = weakReference.get();
            if (activity != null) {
                if(msg.what == 0){//statusTimer
                    activity.textStatus.setVisibility(View.GONE);
                }else if(msg.what == 1) {//UITimer
                    activity.root.setVisibility(View.GONE);
                }else if(msg.what == 2) {//initOrientation
                    activity.btnRotate.performClick();
                }else if(msg.what == 3) {//hideLoading
                    if(activity.isStreamMedia) {
                        activity.loadingAnimation.stop();
                        activity.loading.setVisibility(View.GONE);
                        if (activity.isStreamMedia) {
                            activity.btnPause.setAlpha(1f);
                            activity.btnZoom.setAlpha(1f);
                            activity.btnRotate.setAlpha(1f);
                            activity.progressRate.setAlpha(1f);
                            activity.btnPause.setEnabled(true);
                            activity.btnZoom.setEnabled(true);
                            activity.btnRotate.setEnabled(true);
                            activity.progressRate.setEnabled(true);
                        }
                    }
                }else if(msg.what == 4) {//showLoading
                    if(activity.isStreamMedia) {
                        activity.loadingAnimation.start();
                        activity.loading.setVisibility(View.VISIBLE);
                        if (activity.isStreamMedia) {
                            activity.btnPause.setAlpha(0.5f);
                            activity.btnZoom.setAlpha(0.5f);
                            activity.btnRotate.setAlpha(0.5f);
                            activity.progressRate.setAlpha(0.5f);
                            activity.btnPause.setEnabled(false);
                            activity.btnZoom.setEnabled(false);
                            activity.btnRotate.setEnabled(false);
                            activity.progressRate.setEnabled(false);
                        }
                    }
                }else if(msg.what == 5) {//changeCodec
                    activity.showToast("该视频的编码格式为 “"+ msg.getData().get("codec_name") + "”，硬件解码器暂不支持，尝试切换至软件解码器。", Toast.LENGTH_LONG);
                    activity.codec_type = 0;
                    activity.btnCodecType.setText("软解");
                }else{//error toast
                    switch (msg.what) {
                        case -1:
                            if(activity.isStreamMedia){
                                activity.showToast("无法获取流媒体，请检查网络链接是否建立！", Toast.LENGTH_SHORT);
                            }else {
                                activity.showToast("无法打开视频文件，请检查是否拥有读写权限！", Toast.LENGTH_SHORT);
                            }
                            break;
                        case -2:
                            activity.showToast( "无法从该文件获取流信息，请确认该文件是否为视频文件！", Toast.LENGTH_SHORT);
                            break;
                        case -3:
                            activity.showToast( "无法从该文件获取视频流信息，该封装格式无视频流！", Toast.LENGTH_SHORT);
                            break;
                        case -4:
                            activity.showToast( "无法获取正确的解码器，该视频文件被损坏！", Toast.LENGTH_SHORT);
                            break;
                        case -5:
                            activity.showToast( "无法打开解码器，该视频文件被损坏！", Toast.LENGTH_SHORT);
                            break;
                        case -6:
                            activity.showToast( "视频帧解码失败，该视频文件被损坏！", Toast.LENGTH_SHORT);
                            break;
                    }
                }
            }
        }
    }

    /**
     * 显示Toast，解决重复弹出问题
     */
    public void showToast(String text , int time) {
        if(mToast == null) {
            mToast = Toast.makeText(this, "", time);
            mToast.setText(text);
        } else {
            mToast.setText(text);
            mToast.setDuration(time);
        }
        mToast.show();
    }

    /**
     * 隐藏Toast
     */
    public void cancelToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

    // Events
    @Override
    protected void onPause() {
        Log.v(TAG, "onPause()");
        super.onPause();
        if(!isPause) {
            mGLRenderer.nativePauseSDLThread();
        }
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume()");
        super.onResume();
        updateShowUI();
        if(!isPause) {
            mGLRenderer.nativePlaySDLThread();
        }
    }


    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");

        mGLRenderer.removeListener();
        mGLRenderer.stopRender();

        if(loadingAnimation != null){
            if(loadingAnimation.isRunning()){
                loadingAnimation.stop();
                loading.setVisibility(View.GONE);
            }
        }

        if(updateShowUITimer != null){
            updateShowUITimer.cancel();
            updateShowUITimer = null;
        }
        if(updateStatusTimer != null){
            updateStatusTimer.cancel();
            updateStatusTimer = null;
        }
        cancelToast();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mGLRenderer.stopRender();
    }
}

