package org.uvccamera.usb;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2016 saki t_saki@serenegiant.com
 *
 * File name: UVCCamera.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


import java.util.List;

public class UVCCamera {
	private static final String TAG = "Debug_UVCCamera";
	private static final String DEFAULT_USBFS = "/dev/bus/usb";

	public static final int DEFAULT_PREVIEW_WIDTH = 640;
	public static final int DEFAULT_PREVIEW_HEIGHT = 480;
	public static final int DEFAULT_PREVIEW_MODE = 0;
	public static final int DEFAULT_PREVIEW_MIN_FPS = 1;
	public static final int DEFAULT_PREVIEW_MAX_FPS = 30;
	public static final float DEFAULT_BANDWIDTH = 1.0f;

	public static final int FRAME_FORMAT_YUYV = 0;
	public static final int FRAME_FORMAT_MJPEG = 1;

	public static final int PIXEL_FORMAT_RAW = 0;
	public static final int PIXEL_FORMAT_YUV = 1;
	public static final int PIXEL_FORMAT_RGB565 = 2;
	public static final int PIXEL_FORMAT_RGBX = 3;
	public static final int PIXEL_FORMAT_YUV420SP = 4;
	public static final int PIXEL_FORMAT_NV21 = 5;		// = YVU420SemiPlanar

	//--------------------------------------------------------------------------------
    public static final int	CTRL_SCANNING		= 0x00000001;	// D0:  Scanning Mode
    public static final int CTRL_AE				= 0x00000002;	// D1:  Auto-Exposure Mode
    public static final int CTRL_AE_PRIORITY	= 0x00000004;	// D2:  Auto-Exposure Priority
    public static final int CTRL_AE_ABS			= 0x00000008;	// D3:  Exposure Time (Absolute)
    public static final int CTRL_AR_REL			= 0x00000010;	// D4:  Exposure Time (Relative)
    public static final int CTRL_FOCUS_ABS		= 0x00000020;	// D5:  Focus (Absolute)
    public static final int CTRL_FOCUS_REL		= 0x00000040;	// D6:  Focus (Relative)
    public static final int CTRL_IRIS_ABS		= 0x00000080;	// D7:  Iris (Absolute)
    public static final int CTRL_IRIS_REL		= 0x00000100;	// D8:  Iris (Relative)
    public static final int CTRL_ZOOM_ABS		= 0x00000200;	// D9:  Zoom (Absolute)
    public static final int CTRL_ZOOM_REL		= 0x00000400;	// D10: Zoom (Relative)
    public static final int CTRL_PANTILT_ABS	= 0x00000800;	// D11: PanTilt (Absolute)
    public static final int CTRL_PANTILT_REL	= 0x00001000;	// D12: PanTilt (Relative)
    public static final int CTRL_ROLL_ABS		= 0x00002000;	// D13: Roll (Absolute)
    public static final int CTRL_ROLL_REL		= 0x00004000;	// D14: Roll (Relative)
    public static final int CTRL_FOCUS_AUTO		= 0x00020000;	// D17: Focus, Auto
    public static final int CTRL_PRIVACY		= 0x00040000;	// D18: Privacy
    public static final int CTRL_FOCUS_SIMPLE	= 0x00080000;	// D19: Focus, Simple
    public static final int CTRL_WINDOW			= 0x00100000;	// D20: Window

    public static final int PU_BRIGHTNESS		= 0x80000001;	// D0: Brightness
    public static final int PU_CONTRAST			= 0x80000002;	// D1: Contrast
    public static final int PU_HUE				= 0x80000004;	// D2: Hue
    public static final int PU_SATURATION		= 0x80000008;	// D3: Saturation
    public static final int PU_SHARPNESS		= 0x80000010;	// D4: Sharpness
    public static final int PU_GAMMA			= 0x80000020;	// D5: Gamma
    public static final int PU_WB_TEMP			= 0x80000040;	// D6: White Balance Temperature
    public static final int PU_WB_COMPO			= 0x80000080;	// D7: White Balance Component
    public static final int PU_BACKLIGHT		= 0x80000100;	// D8: Backlight Compensation
    public static final int PU_GAIN				= 0x80000200;	// D9: Gain
    public static final int PU_POWER_LF			= 0x80000400;	// D10: Power Line Frequency
    public static final int PU_HUE_AUTO			= 0x80000800;	// D11: Hue, Auto
    public static final int PU_WB_TEMP_AUTO		= 0x80001000;	// D12: White Balance Temperature, Auto
    public static final int PU_WB_COMPO_AUTO	= 0x80002000;	// D13: White Balance Component, Auto
    public static final int PU_DIGITAL_MULT		= 0x80004000;	// D14: Digital Multiplier
    public static final int PU_DIGITAL_LIMIT	= 0x80008000;	// D15: Digital Multiplier Limit
    public static final int PU_AVIDEO_STD		= 0x80010000;	// D16: Analog Video Standard
    public static final int PU_AVIDEO_LOCK		= 0x80020000;	// D17: Analog Video Lock Status
    public static final int PU_CONTRAST_AUTO	= 0x80040000;	// D18: Contrast, Auto

	// uvc_status_class from libuvc.h
	public static final int STATUS_CLASS_CONTROL = 0x10;
	public static final int STATUS_CLASS_CONTROL_CAMERA = 0x11;
	public static final int STATUS_CLASS_CONTROL_PROCESSING = 0x12;

	// uvc_status_attribute from libuvc.h
	public static final int STATUS_ATTRIBUTE_VALUE_CHANGE = 0x00;
	public static final int STATUS_ATTRIBUTE_INFO_CHANGE = 0x01;
	public static final int STATUS_ATTRIBUTE_FAILURE_CHANGE = 0x02;
	public static final int STATUS_ATTRIBUTE_UNKNOWN = 0xff;

	private static boolean isLoaded;
	static {
		if (!isLoaded) {
			System.loadLibrary("jpeg-turbo1500");
			System.loadLibrary("usb100");
			System.loadLibrary("uvc");
			System.loadLibrary("UVCCamera");
			isLoaded = true;
		}
	}

	private UsbControlBlock mCtrlBlock;
    protected long mControlSupports;			// カメラコントロールでサポートしている機能フラグ
    protected long mProcSupports;				// プロセッシングユニットでサポートしている機能フラグ
    protected int mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
	protected int mCurrentWidth = DEFAULT_PREVIEW_WIDTH, mCurrentHeight = DEFAULT_PREVIEW_HEIGHT;
	protected float mCurrentBandwidthFactor = DEFAULT_BANDWIDTH;
    protected String mSupportedSize;
    protected List<Size> mCurrentSizeList;
	// these fields from here are accessed from native code and do not change name and remove
    protected long mNativePtr;
    protected int mScanningModeMin, mScanningModeMax, mScanningModeDef;
    protected int mExposureModeMin, mExposureModeMax, mExposureModeDef;
    protected int mExposurePriorityMin, mExposurePriorityMax, mExposurePriorityDef;
    protected int mExposureMin, mExposureMax, mExposureDef;
    protected int mAutoFocusMin, mAutoFocusMax, mAutoFocusDef;
    protected int mFocusMin, mFocusMax, mFocusDef;
    protected int mFocusRelMin, mFocusRelMax, mFocusRelDef;
    protected int mFocusSimpleMin, mFocusSimpleMax, mFocusSimpleDef;
    protected int mIrisMin, mIrisMax, mIrisDef;
    protected int mIrisRelMin, mIrisRelMax, mIrisRelDef;
    protected int mPanMin, mPanMax, mPanDef;
    protected int mTiltMin, mTiltMax, mTiltDef;
    protected int mRollMin, mRollMax, mRollDef;
    protected int mPanRelMin, mPanRelMax, mPanRelDef;
    protected int mTiltRelMin, mTiltRelMax, mTiltRelDef;
    protected int mRollRelMin, mRollRelMax, mRollRelDef;
    protected int mPrivacyMin, mPrivacyMax, mPrivacyDef;
    protected int mAutoWhiteBlanceMin, mAutoWhiteBlanceMax, mAutoWhiteBlanceDef;
    protected int mAutoWhiteBlanceCompoMin, mAutoWhiteBlanceCompoMax, mAutoWhiteBlanceCompoDef;
    protected int mWhiteBlanceMin, mWhiteBlanceMax, mWhiteBlanceDef;
    protected int mWhiteBlanceCompoMin, mWhiteBlanceCompoMax, mWhiteBlanceCompoDef;
    protected int mWhiteBlanceRelMin, mWhiteBlanceRelMax, mWhiteBlanceRelDef;
    protected int mBacklightCompMin, mBacklightCompMax, mBacklightCompDef;
    protected int mBrightnessMin, mBrightnessMax, mBrightnessDef;
    protected int mContrastMin, mContrastMax, mContrastDef;
    protected int mSharpnessMin, mSharpnessMax, mSharpnessDef;
    protected int mGainMin, mGainMax, mGainDef;
    protected int mGammaMin, mGammaMax, mGammaDef;
    protected int mSaturationMin, mSaturationMax, mSaturationDef;
    protected int mHueMin, mHueMax, mHueDef;
    protected int mZoomMin, mZoomMax, mZoomDef;
    protected int mZoomRelMin, mZoomRelMax, mZoomRelDef;
    protected int mPowerlineFrequencyMin, mPowerlineFrequencyMax, mPowerlineFrequencyDef;
    protected int mMultiplierMin, mMultiplierMax, mMultiplierDef;
    protected int mMultiplierLimitMin, mMultiplierLimitMax, mMultiplierLimitDef;
    protected int mAnalogVideoStandardMin, mAnalogVideoStandardMax, mAnalogVideoStandardDef;
    protected int mAnalogVideoLockStateMin, mAnalogVideoLockStateMax, mAnalogVideoLockStateDef;
    // until here
    /**
     * the sonctructor of this class should be call within the thread that has a looper
     * (UI thread or a thread that called Looper.prepare)
     */
    public UVCCamera() {
    	mNativePtr = nativeCreate();
    	mSupportedSize = null;
	}


    public void setStatusCallback(IStatusCallback callback) {
        if(mNativePtr != 0) {
            int result = nativeSetStatusCallback(mNativePtr, callback);
            if(result != 0) {
                Log.e(TAG, "nativeSetStatusCallback failed!");
            }
        }
    }

    public void setButtonCallback(final IButtonCallback callback) {
        if (mNativePtr != 0) {
            int result = nativeSetButtonCallback(mNativePtr, callback);
            if(result != 0) {
                Log.e(TAG, "nativeSetButtonCallback failed!");
            }
        }
    }

    public synchronized void close() {
        if(mNativePtr != 0) {
            int result = nativeRelease(mNativePtr);
            if(result != 0) {
                Log.e(TAG, "nativeRelease failed!");
            }
        }
        if(mCtrlBlock != null) {
            mCtrlBlock.close();
        }

        mSupportedSize = null;

    }

    public synchronized void destory() {
        if(mNativePtr != 0) {
            nativeDestroy(mNativePtr);
        }
    }

    private final String getUSBFSName(final UsbControlBlock ctrlBlock) {
        String result = null;
        final String name = ctrlBlock.getDeviceName();
        final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
        if ((v != null) && (v.length > 2)) {
            final StringBuilder sb = new StringBuilder(v[0]);
            for (int i = 1; i < v.length - 2; i++)
                sb.append("/").append(v[i]);
            result = sb.toString();
        }
        if (TextUtils.isEmpty(result)) {
            Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
            result = DEFAULT_USBFS;
        }
        return result;
    }

    public synchronized void open(UsbControlBlock ctrlBlock) {
        int result;
        try{
            mCtrlBlock = ctrlBlock.clone();
            result = nativeConnect(mNativePtr, mCtrlBlock.getVenderId(), mCtrlBlock.getProductId(), mCtrlBlock.getFileDescriptor(),
                    mCtrlBlock.getBusNum(), mCtrlBlock.getDevNum(), getUSBFSName(mCtrlBlock));
        }catch (final Exception e){
            Log.e(TAG,"open camera error e:" +e.toString());
            result = -1;
        }
        if(TextUtils.isEmpty(mSupportedSize)) {
            mSupportedSize = nativeGetSupportedSize(mNativePtr);
        }
    }

    public void setPreviewSize(final int width, final int height, final int format) {
        if(mNativePtr != 0 && width != 0 && height != 0) {
            final int result = nativeSetPreviewSize(mNativePtr, width, height, 1, 30, format, 1.0f);
            if(result != 0) {
                Log.e(TAG, "setPreviewSize failed!");
                return;
            }

        }
    }

    public void setPreviewDisplay(SurfaceHolder surfaceHolder, final int rotate) {
        if(mNativePtr != 0 && surfaceHolder != null) {
            Log.i(TAG, "setPreviewDisplay: "+surfaceHolder.getSurface().toString());
            int result = nativeSetPreviewDisplay(mNativePtr, surfaceHolder.getSurface(), rotate);
            if(result != 0) {
                Log.e(TAG, "nativeSetPreviewDisplay failed!");
            }
        }
    }

    public void setFrameCallback(final IFrameCallback iFrameCallback,final int format) {
        if(mNativePtr != 0) {
            int result = nativeSetFrameCallback(mNativePtr, iFrameCallback, format);
            if(result != 0) {
                Log.e(TAG, "nativeSetFrameCallback failed!");
            }
        }
    }

    public void startPreview() {
        if(mNativePtr != 0) {
            int result = nativeStartPreview(mNativePtr);
            if(result != 0) {
                Log.e(TAG, "nativeStartPreview failed!");
            }
        }
    }

    public void stopPreview() {
        if(mNativePtr != 0) {
            int result = nativeStopPreview(mNativePtr);
            if(result != 0) {
                Log.e(TAG, "nativeStopPreview failed!");
            }
        }
    }




    // #nativeCreate and #nativeDestroy are not static methods.
    private final native long nativeCreate();
    private final native void nativeDestroy(final long id_camera);

    private final native int nativeConnect(long id_camera, int venderId, int productId, int fileDescriptor, int busNum, int devAddr, String usbfs);
    private static final native int nativeRelease(final long id_camera);

    private static final native int nativeSetStatusCallback(final long mNativePtr, final IStatusCallback callback);
    private static final native int nativeSetButtonCallback(final long mNativePtr, final IButtonCallback callback);

    private static final native int nativeSetPreviewSize(final long id_camera, final int width, final int height, final int min_fps, final int max_fps, final int mode, final float bandwidth);
    private static final native String nativeGetSupportedSize(final long id_camera);
    private static final native int nativeStartPreview(final long id_camera);
    private static final native int nativeStopPreview(final long id_camera);
    private static final native int nativeSetPreviewDisplay(final long id_camera, final Surface surface, final int rotate);
    private static final native int nativeSetFrameCallback(final long mNativePtr, final IFrameCallback callback, final int pixelFormat);
    private static final native int nativeSetCaptureDisplay(final long id_camera, final Surface surface);

    private static final native long nativeGetCtrlSupports(final long id_camera);
    private static final native long nativeGetProcSupports(final long id_camera);

    private final native int nativeUpdateScanningModeLimit(final long id_camera);
    private static final native int nativeSetScanningMode(final long id_camera, final int scanning_mode);
    private static final native int nativeGetScanningMode(final long id_camera);

    private final native int nativeUpdateExposureModeLimit(final long id_camera);
    private static final native int nativeSetExposureMode(final long id_camera, final int exposureMode);
    private static final native int nativeGetExposureMode(final long id_camera);

    private final native int nativeUpdateExposurePriorityLimit(final long id_camera);
    private static final native int nativeSetExposurePriority(final long id_camera, final int priority);
    private static final native int nativeGetExposurePriority(final long id_camera);

    private final native int nativeUpdateExposureLimit(final long id_camera);
    private static final native int nativeSetExposure(final long id_camera, final int exposure);
    private static final native int nativeGetExposure(final long id_camera);

    private final native int nativeUpdateExposureRelLimit(final long id_camera);
    private static final native int nativeSetExposureRel(final long id_camera, final int exposure_rel);
    private static final native int nativeGetExposureRel(final long id_camera);

    private final native int nativeUpdateAutoFocusLimit(final long id_camera);
    private static final native int nativeSetAutoFocus(final long id_camera, final boolean autofocus);
    private static final native int nativeGetAutoFocus(final long id_camera);

    private final native int nativeUpdateFocusLimit(final long id_camera);
    private static final native int nativeSetFocus(final long id_camera, final int focus);
    private static final native int nativeGetFocus(final long id_camera);

    private final native int nativeUpdateFocusRelLimit(final long id_camera);
    private static final native int nativeSetFocusRel(final long id_camera, final int focus_rel);
    private static final native int nativeGetFocusRel(final long id_camera);

    private final native int nativeUpdateIrisLimit(final long id_camera);
    private static final native int nativeSetIris(final long id_camera, final int iris);
    private static final native int nativeGetIris(final long id_camera);

    private final native int nativeUpdateIrisRelLimit(final long id_camera);
    private static final native int nativeSetIrisRel(final long id_camera, final int iris_rel);
    private static final native int nativeGetIrisRel(final long id_camera);

    private final native int nativeUpdatePanLimit(final long id_camera);
    private static final native int nativeSetPan(final long id_camera, final int pan);
    private static final native int nativeGetPan(final long id_camera);

    private final native int nativeUpdatePanRelLimit(final long id_camera);
    private static final native int nativeSetPanRel(final long id_camera, final int pan_rel);
    private static final native int nativeGetPanRel(final long id_camera);

    private final native int nativeUpdateTiltLimit(final long id_camera);
    private static final native int nativeSetTilt(final long id_camera, final int tilt);
    private static final native int nativeGetTilt(final long id_camera);

    private final native int nativeUpdateTiltRelLimit(final long id_camera);
    private static final native int nativeSetTiltRel(final long id_camera, final int tilt_rel);
    private static final native int nativeGetTiltRel(final long id_camera);

    private final native int nativeUpdateRollLimit(final long id_camera);
    private static final native int nativeSetRoll(final long id_camera, final int roll);
    private static final native int nativeGetRoll(final long id_camera);

    private final native int nativeUpdateRollRelLimit(final long id_camera);
    private static final native int nativeSetRollRel(final long id_camera, final int roll_rel);
    private static final native int nativeGetRollRel(final long id_camera);

    private final native int nativeUpdateAutoWhiteBlanceLimit(final long id_camera);
    private static final native int nativeSetAutoWhiteBlance(final long id_camera, final boolean autoWhiteBlance);
    private static final native int nativeGetAutoWhiteBlance(final long id_camera);

    private final native int nativeUpdateAutoWhiteBlanceCompoLimit(final long id_camera);
    private static final native int nativeSetAutoWhiteBlanceCompo(final long id_camera, final boolean autoWhiteBlanceCompo);
    private static final native int nativeGetAutoWhiteBlanceCompo(final long id_camera);

    private final native int nativeUpdateWhiteBlanceLimit(final long id_camera);
    private static final native int nativeSetWhiteBlance(final long id_camera, final int whiteBlance);
    private static final native int nativeGetWhiteBlance(final long id_camera);

    private final native int nativeUpdateWhiteBlanceCompoLimit(final long id_camera);
    private static final native int nativeSetWhiteBlanceCompo(final long id_camera, final int whiteBlance_compo);
    private static final native int nativeGetWhiteBlanceCompo(final long id_camera);

    private final native int nativeUpdateBacklightCompLimit(final long id_camera);
    private static final native int nativeSetBacklightComp(final long id_camera, final int backlight_comp);
    private static final native int nativeGetBacklightComp(final long id_camera);

    private final native int nativeUpdateBrightnessLimit(final long id_camera);
    private static final native int nativeSetBrightness(final long id_camera, final int brightness);
    private static final native int nativeGetBrightness(final long id_camera);

    private final native int nativeUpdateContrastLimit(final long id_camera);
    private static final native int nativeSetContrast(final long id_camera, final int contrast);
    private static final native int nativeGetContrast(final long id_camera);

    private final native int nativeUpdateAutoContrastLimit(final long id_camera);
    private static final native int nativeSetAutoContrast(final long id_camera, final boolean autocontrast);
    private static final native int nativeGetAutoContrast(final long id_camera);

    private final native int nativeUpdateSharpnessLimit(final long id_camera);
    private static final native int nativeSetSharpness(final long id_camera, final int sharpness);
    private static final native int nativeGetSharpness(final long id_camera);

    private final native int nativeUpdateGainLimit(final long id_camera);
    private static final native int nativeSetGain(final long id_camera, final int gain);
    private static final native int nativeGetGain(final long id_camera);

    private final native int nativeUpdateGammaLimit(final long id_camera);
    private static final native int nativeSetGamma(final long id_camera, final int gamma);
    private static final native int nativeGetGamma(final long id_camera);

    private final native int nativeUpdateSaturationLimit(final long id_camera);
    private static final native int nativeSetSaturation(final long id_camera, final int saturation);
    private static final native int nativeGetSaturation(final long id_camera);

    private final native int nativeUpdateHueLimit(final long id_camera);
    private static final native int nativeSetHue(final long id_camera, final int hue);
    private static final native int nativeGetHue(final long id_camera);

    private final native int nativeUpdateAutoHueLimit(final long id_camera);
    private static final native int nativeSetAutoHue(final long id_camera, final boolean autohue);
    private static final native int nativeGetAutoHue(final long id_camera);

    private final native int nativeUpdatePowerlineFrequencyLimit(final long id_camera);
    private static final native int nativeSetPowerlineFrequency(final long id_camera, final int frequency);
    private static final native int nativeGetPowerlineFrequency(final long id_camera);

    private final native int nativeUpdateZoomLimit(final long id_camera);
    private static final native int nativeSetZoom(final long id_camera, final int zoom);
    private static final native int nativeGetZoom(final long id_camera);

    private final native int nativeUpdateZoomRelLimit(final long id_camera);
    private static final native int nativeSetZoomRel(final long id_camera, final int zoom_rel);
    private static final native int nativeGetZoomRel(final long id_camera);

    private final native int nativeUpdateDigitalMultiplierLimit(final long id_camera);
    private static final native int nativeSetDigitalMultiplier(final long id_camera, final int multiplier);
    private static final native int nativeGetDigitalMultiplier(final long id_camera);

    private final native int nativeUpdateDigitalMultiplierLimitLimit(final long id_camera);
    private static final native int nativeSetDigitalMultiplierLimit(final long id_camera, final int multiplier_limit);
    private static final native int nativeGetDigitalMultiplierLimit(final long id_camera);

    private final native int nativeUpdateAnalogVideoStandardLimit(final long id_camera);
    private static final native int nativeSetAnalogVideoStandard(final long id_camera, final int standard);
    private static final native int nativeGetAnalogVideoStandard(final long id_camera);

    private final native int nativeUpdateAnalogVideoLockStateLimit(final long id_camera);
    private static final native int nativeSetAnalogVideoLoackState(final long id_camera, final int state);
    private static final native int nativeGetAnalogVideoLoackState(final long id_camera);

    private final native int nativeUpdatePrivacyLimit(final long id_camera);
    private static final native int nativeSetPrivacy(final long id_camera, final boolean privacy);
    private static final native int nativeGetPrivacy(final long id_camera);
}
