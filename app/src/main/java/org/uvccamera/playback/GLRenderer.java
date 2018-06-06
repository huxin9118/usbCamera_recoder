package org.uvccamera.playback;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by h26376 on 2018/4/16.
 */

public class GLRenderer implements GLSurfaceView.Renderer {
    private final String TAG = "GLRenderer";
    private WeakReference<GLSurfaceView> mSurface;
    private Thread updateThread;
    private GLProgram prog = new GLProgram(0);
    private int screen_width, screen_height;
    private int pixel_width, pixel_height;
    private int render_zoom = OpenGLActivity.ZOOM_INSIDE;
    private ByteBuffer y;
    private ByteBuffer u;
    private ByteBuffer v;
    private float[] squareVertices;
    private int rotate;
    private RenderListener listener;

    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("yuv");
        System.loadLibrary("render");
    }

    public GLRenderer(GLSurfaceView surface) {
        Log.i(TAG, "GLRenderer :: GLRenderer");
        mSurface = new WeakReference<GLSurfaceView>(surface);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(TAG, "GLRenderer :: onSurfaceCreated");
        if (!prog.isProgramBuilt()) {
            prog.buildProgram();
            Log.i(TAG, "GLRenderer :: buildProgram done");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(TAG, "GLRenderer :: onSurfaceChanged screen W x H : " + width + "x" + height);
        screen_width = width;
        screen_height = height;
        updateZoom(render_zoom);
        GLES20.glViewport(0, 0, screen_width, screen_height);
    }


    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            if (y != null) {
                // reset position, have to be done
                y.position(0);
                u.position(0);
                v.position(0);
                prog.buildTextures(y, u, v, pixel_width, pixel_height);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                prog.drawFrame();
            }
        }
    }


    float[] multSquareVertices(float[] a, float[] b){
        for(int i = 0; i<a.length; i++){
            b[i] = a[i] * b[i];
        }
        return b;
    }
    public void updateZoom(int zoom) {// 调整比例
        Log.i(TAG, "updateZoom: "+zoom);
        render_zoom = zoom;
        if (pixel_width > 0 && pixel_height > 0 && screen_width > 0 && screen_height > 0) {
            if(render_zoom == OpenGLActivity.ZOOM_INSIDE) {
                float screen_wh_scale = 1f * screen_width / screen_height;
                float pixel_wh_scale;
                if(rotate == 0 || rotate == 180) {
                    pixel_wh_scale = 1f * pixel_width / pixel_height;
                }
                else{
                    pixel_wh_scale = 1f * pixel_height / pixel_width;
                }
                if (screen_wh_scale == pixel_wh_scale) {
                    Log.i(TAG, "updateZoom(ZOOM_INSIDE): screen_wh_scale == pixel_wh_scale : " + screen_wh_scale + " == " + pixel_wh_scale);
                    prog.createBuffers(squareVertices);
                } else if (screen_wh_scale > pixel_wh_scale) {
                    Log.i(TAG, "updateZoom(ZOOM_INSIDE): screen_wh_scale > pixel_wh_scale : " + screen_wh_scale + " < " + pixel_wh_scale);
                    float width_scale = pixel_wh_scale / screen_wh_scale;
                    prog.createBuffers(multSquareVertices(squareVertices,new float[]{width_scale, 1.0f, width_scale, 1.0f, width_scale, 1.0f, width_scale, 1.0f}));
                } else {
                    Log.i(TAG, "updateZoom(ZOOM_INSIDE): screen_wh_scale < pixel_wh_scale : " + screen_wh_scale + " > " + pixel_wh_scale);
                    float height_scale = screen_wh_scale / pixel_wh_scale;
                    prog.createBuffers(multSquareVertices(squareVertices,new float[]{1.0f, height_scale, 1.0f, height_scale, 1.0f, height_scale, 1.0f, height_scale}));
                }
            }
            else if(render_zoom == OpenGLActivity.ZOOM_ORIGINAL) {
                float wdith_ps_scale;
                float height_ps_scale;
                if(rotate == 0 || rotate == 180) {
                    wdith_ps_scale = 1f * pixel_width / screen_width;
                    height_ps_scale = 1f * pixel_height / screen_height;
                }
                else{
                    wdith_ps_scale = 1f * pixel_height / screen_width;
                    height_ps_scale = 1f * pixel_width / screen_height;
                }
                prog.createBuffers(multSquareVertices(squareVertices,new float[]{wdith_ps_scale, height_ps_scale, wdith_ps_scale, height_ps_scale,
                                                                                wdith_ps_scale, height_ps_scale, wdith_ps_scale, height_ps_scale}));
            }
            else if(render_zoom == OpenGLActivity.ZOOM_STRETCH) {
                Log.i(TAG, "updateZoom(ZOOM_STRETCH)");
                prog.createBuffers(squareVertices);
            }

            // request to render
            if(mSurface.get() != null) {
                mSurface.get().requestRender();
            }
        }
    }
    /**
     * this method will be called from native code, it happens when the video is about to play or
     * the video size changes.
     */
    public void updateParameter(int width, int height, int rotate) {
        Log.i(TAG, "updateParameter pixel W x H : "+ width + "x" + height + " rotate :" + rotate);
        this.rotate = rotate;
        switch (rotate){
            case 0: squareVertices = GLProgram.squareVertices0C; break;
            case 90: squareVertices = GLProgram.squareVertices90C; break;
            case 180: squareVertices = GLProgram.squareVertices180C; break;
            case 270: squareVertices = GLProgram.squareVertices270C; break;
            default: squareVertices = GLProgram.squareVertices0C; break;
        }
        if (width > 0 && height > 0) {
            // 初始化容器
            if (width != pixel_width && height != pixel_height) {
                pixel_width = width;
                pixel_height = height;
                int yarraySize = width * height;
                int uvarraySize = yarraySize / 4;
                synchronized (this) {
                    y = ByteBuffer.allocate(yarraySize);
                    u = ByteBuffer.allocate(uvarraySize);
                    v = ByteBuffer.allocate(uvarraySize);
                }
            }
        }
        updateZoom(render_zoom);
//        clearScreen(127);
    }

    /**
     * this method will be called from native code, it's used for passing yuv data to me.
     */
    public void updateData(byte[] ydata, byte[] udata, byte[] vdata) {
//        Log.i(TAG, "updateData y : "+ ydata.length +" u : "+ udata.length +" v : "+ vdata.length);
        synchronized (this) {
            y.clear();
            u.clear();
            v.clear();
            y.put(ydata, 0, ydata.length);
            u.put(udata, 0, udata.length);
            v.put(vdata, 0, vdata.length);
        }

        // request to render
        if(mSurface.get() != null) {
            mSurface.get().requestRender();
        }
    }

    void clearScreen(byte data){
        if (pixel_width > 0 && pixel_height > 0) {
            int yarraySize = pixel_width * pixel_height;
            int uvarraySize = yarraySize / 4;
            byte[] ydata = new byte[yarraySize];
            byte[] udata = new byte[uvarraySize];
            byte[] vdata = new byte[uvarraySize];
            Arrays.fill(ydata, data);
            Arrays.fill(udata, data);
            Arrays.fill(vdata, data);
            updateData(ydata, udata, vdata);
        }
    }


    public void startRender(final boolean isYUV, final String url, final int wdith, final int height, final int pixel_type , final int fps, final boolean isStreamMedia) {
        Log.i(TAG, "startRender");
        if(updateThread == null){
            updateThread = new Thread(){
                @Override
                public void run() {
                    int status;
                    if(isYUV) {
                        status = nativeInitSDLThreadYUV(url, wdith, height, pixel_type, fps);
                    }
                    else{
                        status = nativeInitSDLThread(url, isStreamMedia, 0);
                    }
                    if(listener != null) {
                        listener.renderThreadFinish(status);
                    }
                }
            };
            updateThread.start();
        }
    }

    public void stopRender() {
        Log.i(TAG, "stopRender");
        if(updateThread != null){
            nativeBackSDLThread();
            try {
                updateThread.join();
            }
            catch (InterruptedException e){
                e.printStackTrace();
            }
            updateThread = null;
        }
    }


    public void setListener(RenderListener listener) {
        this.listener = listener;
    }

    public void removeListener() {
        this.listener = null;
    }

    public void setProgressRate(int frameConut){
        if(listener != null) {
            listener.setProgressRate(frameConut);
        }
    }

    public void setProgressRateFull(){
        if(listener != null) {
            listener.setProgressRateFull();
        }
    }

    public void setProgressDuration(long duration){
        if(listener != null) {
            listener.setProgressDuration(duration);
        }
    }

    public void setProgressDTS(long dts){
        if(listener != null) {
            listener.setProgressDTS(dts);
        }
    }

    public void showIFrameDTS(long I_Frame_dts,int forwardOffset){
        if(listener != null) {
            listener.showIFrameDTS(I_Frame_dts,forwardOffset);
        }
    }

    public void initOrientation(){
        if(listener != null) {
            listener.initOrientation();
        }
    }

    public void hideLoading(){
        if(listener != null) {
            listener.hideLoading();
        }
    }

    public void showLoading(){
        if(listener != null) {
            listener.showLoading();
        }
    }

    public void changeCodec(String codec_name){
        if(listener != null) {
            listener.changeCodec(codec_name);
        }
    }

    public void setTimeBase(double timeBase){
        if(listener != null) {
            listener.setTimeBase(timeBase);
        }
    }

    interface RenderListener{
        void setProgressRate(int frameConut);
        void setProgressRateFull();
        void setProgressDuration(long duration);
        void setProgressDTS(long dts);
        void showIFrameDTS(long I_Frame_dts, int forwardOffset);
        void initOrientation();
        void hideLoading();
        void showLoading();
        void changeCodec(String codec_name);
        void setTimeBase(double timeBase);
        void renderThreadFinish(int result_code);
    }

    //CUSTOM JNI
    private native int nativeInitSDLThreadYUV(String url, int wdith, int height, int pixel_type, int fps);
    private native int nativeInitSDLThread(String url, boolean isStreamMedia, int rotate);
    private native void nativeBackSDLThread();
    public native void nativeCodecType(int codec_type);
    public native void nativePauseSDLThread();
    public native void nativePlaySDLThread();
    public native void nativeZoomSDLThread(int zoom);
    public native void nativeBackwardSDLThread(long skipFrame);
    public native void nativeForwardSDLThread(long skipFrame);
    public native void nativeSeekSDLThread(long seekFrame);
    public native void nativeUpdateSdlRect(int wdith,int height);
}

