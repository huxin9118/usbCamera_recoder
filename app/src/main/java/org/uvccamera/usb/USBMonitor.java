package org.uvccamera.usb;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2016 saki t_saki@serenegiant.com
 *
 * File name: USBMonitor.java
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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mediacodec.MPEG4Encoder;

import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public final class USBMonitor {
	private static final String TAG = "Debug_USBMonitor";

	private static final String ACTION_USB_PERMISSION_BASE = "org.uvccamera.USB_PERMISSION";
	private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE + "." + hashCode();

	private UsbManager mUsbManager = null;
	private WeakReference<Context> mWeakContext = null;
	private PendingIntent mPermissionIntent = null;
	private UsbDevice device = null;
	private Boolean usbCameraConnect = false;
	private UsbControlBlock controlBlock = null;
	private UVCCamera mUVCCamera = null;
	private SurfaceHolder surfaceHolder = null;
	private boolean isCapture = false;
	private OnDeviceConnectListener mOnDeviceConnectListener;
	private List<UsbFrameSize> mSupportedSizeList = null;

	private boolean isAutoFocus;
	private boolean isFocus;
	private boolean isAutoContrast;
	private boolean isContrast;
	private boolean isAutoHue;
	private boolean isHue;
	private boolean isAutoWhiteBalance;
	private boolean isWhiteBalance;
	private boolean isBrightness;
	private boolean isSaturation;
	private boolean isSharpness;
	private boolean isGamma;
	private boolean isBacklight;
	private boolean isGain;

	//debug
	FileOutputStream outputStream;
	private static int index = 0;

	private MPEG4Encoder mEncoder;
//	private HH264Encoder mEncoder;

	public USBMonitor(final Context context) {
		mWeakContext = new WeakReference<Context>(context);
		mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
	}

	public void register() {
		final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		// ACTION_USB_DEVICE_ATTACHED never comes on some devices so it should not be added here
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

		if (mWeakContext.get() != null && mUsbReceiver != null){
			mWeakContext.get().registerReceiver(mUsbReceiver, filter);
			mPermissionIntent = PendingIntent.getBroadcast(mWeakContext.get(), 0, new Intent(ACTION_USB_PERMISSION), 0);
		}
	}

	public void unregister(){
		if (mWeakContext.get() != null && mUsbReceiver != null) {
			mWeakContext.get().unregisterReceiver(mUsbReceiver);
		}
	}

	public void initCheckUVCState(){
		HashMap<String,UsbDevice> usbDeviceList = mUsbManager.getDeviceList();
		if(usbDeviceList != null) {
			Iterator<UsbDevice> usbDevices = usbDeviceList.values().iterator();
			while(usbDevices.hasNext()){
				device = usbDevices.next();
				if((device != null)) {
					if(device.getDeviceClass() == 239 && device.getDeviceSubclass() == 2) {
						if(!mUsbManager.hasPermission(device)) {
							mUsbManager.requestPermission(device, mPermissionIntent);
						}
						else {
							if(!getUsbCameraConnect()) {
								try {
									controlBlock = new UsbControlBlock(USBMonitor.this, device);
									connectCamera(controlBlock);
									mOnDeviceConnectListener.onConnect(device, controlBlock);
								}
								catch (Exception e){
									e.printStackTrace();
									mOnDeviceConnectListener.onDisconnect(device, controlBlock);
								}
							}
						}
					}
				}
			}
		}
	}

	public void startCapture(int width, int height, int rotate) {
		Log.i(TAG, "startCapture");
		if(device != null && mUVCCamera != null && surfaceHolder != null && getUsbCameraConnect()  && !isCapture()) {
			mOnDeviceConnectListener.onStartPreview();

			if(!TextUtils.isEmpty(mUVCCamera.mSupportedSize)) {
				Log.i(TAG, "supportedSize:" + mUVCCamera.mSupportedSize);
			}

			Log.e(TAG, "setPreviewSize!");
			mUVCCamera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
			Log.e(TAG, "setPreviewDisplay!");
			mUVCCamera.setPreviewDisplay(surfaceHolder, rotate);

			mUVCCamera.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
			Log.e(TAG, "startPreview!");
			mUVCCamera.startPreview();
			isCapture = true;

//			if (outputStream == null) {
//				boolean sdCardExist = Environment.getExternalStorageState()
//						.equals(Environment.MEDIA_MOUNTED);
//				if(sdCardExist) {
//					File env = Environment.getExternalStorageDirectory();
//					File file = new File(env.toString() + "/NV12_1280x720.yuv");
//					if(file.exists()){
//						file.delete();
//					}
//					try {
//						file.createNewFile();
//					} catch (IOException e) {
//						Log.e(TAG, " createNewFile error");
//					}
//					if(file.exists()) {
//						try {
//							outputStream = new FileOutputStream(file);
//						} catch (FileNotFoundException e) {
//							Log.e(TAG, "new FileOutputStream(file) error");
//						}
//					}
//				}
//			}
		}
	}

	public void stopCapture() {
		Log.i(TAG, "stopCapture");
		if(device != null && mUVCCamera != null && getUsbCameraConnect() && isCapture()) {
			isCapture = false;
			mUVCCamera.stopPreview();

//			try{
//				if(outputStream != null)
//					outputStream.close();
//			}catch (IOException e){
//				Log.e(TAG , " outputStream close error");
//			}
//
//			if(outputStream != null){
//				try {
//					outputStream.close();
//					outputStream = null;
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}

			mOnDeviceConnectListener.onStopPreview();
		}
	}

	public void startRecoder(int width, int height, int frameRate, int bitRate, String outputPath, int rotate) {
		if(mEncoder != null && !isCapture()){
			mEncoder.close();
			mEncoder = null;
		}
		mEncoder = new MPEG4Encoder(width, height, frameRate, bitRate, outputPath, rotate);
//		mEncoder = new HH264Encoder(width, height, frameRate, bitRate);
		mEncoder.open();
	}

	/**
	 * Get a YUVImage from mEncoder,
	 * Use the Function After startRecoder() and Before stopRecoder().
	 * @return
	 */
	public YuvImage getThumbnailImage() {
		if(mEncoder != null){
			return mEncoder.getThumbnailImage();
		}
		return null;
	}

	public void stopRecoder() {
		if(mEncoder != null && isCapture()){
			mEncoder.close();
			mEncoder = null;
		}
	}

	public boolean isCapture() {
		return isCapture;
	}

	private final IStatusCallback statusCallback = new IStatusCallback(){
		@Override
		public void onStatus(int statusClass, int event, int selector, int statusAttribute, ByteBuffer data) {
			Log.d(TAG,"EVENT:" + event +";" + "selector:" + selector + ";" + "statusAttribute:" + statusAttribute+";" +"data" + data +"!");
		}
	};

	private final IFrameCallback frameCallback = new IFrameCallback() {
		@Override
		public void onFrame(ByteBuffer frame) {
//			Log.e(TAG, "receive the ["+index+"] frame!");
//			if(outputStream != null) {
//				try {
//					byte[] byteFrame = new byte[frame.remaining()];
//					frame.get(byteFrame, 0, byteFrame.length);
//
//					outputStream.write(byteFrame);
//				} catch (Exception e) {
//					e.printStackTrace();
//					Log.e(TAG, " fileChannel write error");
//				}
//				index ++;
//			}

			if(mEncoder != null && mEncoder.isOpen()) {
				byte[] byteFrame = new byte[frame.remaining()];
				frame.get(byteFrame, 0, byteFrame.length);
				mEncoder.encode(byteFrame, 0, new byte[1920*1080*3/2], byteFrame.length);
			}
		}
	};

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				// when received the result of requesting USB permission
				synchronized (this) {
					device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if(!getUsbCameraConnect()) {
							try {
								controlBlock = new UsbControlBlock(USBMonitor.this, device);
								connectCamera(controlBlock);
								mOnDeviceConnectListener.onConnect(device, controlBlock);
							}
							catch (Exception e){
								e.printStackTrace();
								mOnDeviceConnectListener.onDisconnect(device, controlBlock);
							}
						}
					}
				}
			} else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				synchronized (this) {
					device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null) {
						if (device.getDeviceClass() == 239 && device.getDeviceSubclass() == 2) {
							if(!mUsbManager.hasPermission(device)) {
								mUsbManager.requestPermission(device, mPermissionIntent);
								mOnDeviceConnectListener.onAttach(device);
							}
							else {
								if(!getUsbCameraConnect()) {
									controlBlock = new UsbControlBlock(USBMonitor.this, device);
									connectCamera(controlBlock);
								}
							}
						}
					}
				}
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				synchronized (this) {
					device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null) {
						stopRecoder();
						stopCapture();
						releaseCamera();
						mOnDeviceConnectListener.onDettach(device);
					}
				}
			}
		}
	};

	private List<UsbFrameSize> parseSupportedSize(String mSupportedSize) throws JSONException {
		List<UsbFrameSize> list = new ArrayList<>();
		JSONObject jsonObject = new JSONObject(mSupportedSize);
		JSONArray formats = jsonObject.getJSONArray("formats");
		JSONObject format = formats.getJSONObject(0);
		JSONArray sizes = format.getJSONArray("size");
		for(int i = 0; i < sizes.length(); i++){
			String size = sizes.getString(i);
			int wdith = Integer.parseInt(size.substring(0,size.indexOf("x")));
			int height = Integer.parseInt(size.substring(size.indexOf("x")+1,size.length()));
			list.add(new UsbFrameSize(wdith, height));
		}
		return list;
	}


	public synchronized void connectCamera(UsbControlBlock ctrlBlock){
		synchronized (this) {
			Log.i(TAG, "connectCamera: "+mUVCCamera);
			//Open camera
			if(mUVCCamera != null) {
				try{
					Log.i(TAG, "releaseCamera: ");
					mUVCCamera.setStatusCallback(null);
					mUVCCamera.close();
					mUVCCamera.destroy();

				}catch (final Exception e){
					e.printStackTrace();
				}
				finally {
					setUsbCameraConnect(false);
					mUVCCamera = null;
				}
			}
			mUVCCamera = new UVCCamera();
			mUVCCamera.open(ctrlBlock);
			mUVCCamera.setStatusCallback(statusCallback);
			setUsbCameraConnect(true);

			if(!TextUtils.isEmpty(mUVCCamera.mSupportedSize)) {
				Log.i(TAG, "supportedSize:" + mUVCCamera.mSupportedSize);
				try {
					mSupportedSizeList = parseSupportedSize(mUVCCamera.mSupportedSize);
				}
				catch (JSONException e){
					e.printStackTrace();
				}

				Log.i("UVC", "mSupportedSizeList: "+mSupportedSizeList);
			}

			isAutoFocus = mUVCCamera.checkSupportFlag(UVCCamera.CTRL_FOCUS_AUTO);
			isFocus = mUVCCamera.checkSupportFlag(UVCCamera.CTRL_FOCUS_ABS);
			isAutoContrast = mUVCCamera.checkSupportFlag(UVCCamera.PU_CONTRAST_AUTO);
			isContrast = mUVCCamera.checkSupportFlag(UVCCamera.PU_CONTRAST);
			isAutoHue = mUVCCamera.checkSupportFlag(UVCCamera.PU_HUE_AUTO);
			isHue = mUVCCamera.checkSupportFlag(UVCCamera.PU_HUE);
			isAutoWhiteBalance = mUVCCamera.checkSupportFlag(UVCCamera.PU_WB_TEMP_AUTO);
			isWhiteBalance = mUVCCamera.checkSupportFlag(UVCCamera.PU_WB_TEMP);
			isBrightness = mUVCCamera.checkSupportFlag(UVCCamera.PU_BRIGHTNESS);
			isSaturation = mUVCCamera.checkSupportFlag(UVCCamera.PU_SATURATION);
			isSharpness = mUVCCamera.checkSupportFlag(UVCCamera.PU_SHARPNESS);
			isGamma = mUVCCamera.checkSupportFlag(UVCCamera.PU_GAMMA);
			isBacklight = mUVCCamera.checkSupportFlag(UVCCamera.PU_BACKLIGHT);
			isGain = mUVCCamera.checkSupportFlag(UVCCamera.PU_GAIN);
			Log.e(TAG, "connectCamera: CTRL_FOCUS_AUTO  ["+isAutoFocus+"]");
			Log.e(TAG, "connectCamera: CTRL_FOCUS_ABS   ["+isFocus+"]");
			Log.e(TAG, "connectCamera: PU_CONTRAST_AUTO ["+isAutoContrast+"]");
			Log.e(TAG, "connectCamera: PU_CONTRAST      ["+isContrast+"]");
			Log.e(TAG, "connectCamera: PU_HUE_AUTO      ["+isAutoHue+"]");
			Log.e(TAG, "connectCamera: PU_HUE           ["+isHue+"]");
			Log.e(TAG, "connectCamera: PU_WB_TEMP_AUTO  ["+isAutoWhiteBalance+"]");
			Log.e(TAG, "connectCamera: PU_WB_TEMP       ["+isWhiteBalance+"]");
			Log.e(TAG, "connectCamera: PU_BRIGHTNESS    ["+isBrightness+"]");
			Log.e(TAG, "connectCamera: PU_SATURATION    ["+isSaturation+"]");
			Log.e(TAG, "connectCamera: PU_SHARPNESS     ["+isSharpness+"]");
			Log.e(TAG, "connectCamera: PU_GAMMA         ["+isGamma+"]");
			Log.e(TAG, "connectCamera: PU_BACKLIGHT     ["+ isBacklight +"]");
			Log.e(TAG, "connectCamera: PU_GAIN          ["+isGain+"]");
		}
	}

	public synchronized void releaseCamera(){
		synchronized (this) {
			if(mUVCCamera != null) {
				try{
					Log.i(TAG, "releaseCamera: ");
					mUVCCamera.setStatusCallback(null);
					mUVCCamera.close();
					mUVCCamera.destroy();

				}catch (final Exception e){
					e.printStackTrace();
				}
				finally {
					setUsbCameraConnect(false);
					mUVCCamera = null;
				}
			}
		}
	}

	public Boolean getUsbCameraConnect() {
		return usbCameraConnect;
	}

	public void setUsbCameraConnect(Boolean usbCameraConnect) {
		this.usbCameraConnect = usbCameraConnect;
	}

	public SurfaceHolder getSurfaceHolder() {
		return surfaceHolder;
	}

	public void setSurfaceHolder(SurfaceHolder surfaceHolder) {
		this.surfaceHolder = surfaceHolder;
	}

	public void setOnDeviceConnectListener(OnDeviceConnectListener onDeviceConnectListener) {
		this.mOnDeviceConnectListener = onDeviceConnectListener;
	}

	public List<UsbFrameSize> getSupportedSizeList() {
		return mSupportedSizeList;
	}

	public UsbManager getUsbManager() {
		return mUsbManager;
	}

	public UVCCamera getUVCCamera() {
		return mUVCCamera;
	}

	public boolean isAutoFocus() {
		return isAutoFocus;
	}

	public boolean isFocus() {
		return isFocus;
	}

	public boolean isAutoContrast() {
		return isAutoContrast;
	}

	public boolean isContrast() {
		return isContrast;
	}

	public boolean isAutoHue() {
		return isAutoHue;
	}

	public boolean isHue() {
		return isHue;
	}

	public boolean isAutoWhiteBalance() {
		return isAutoWhiteBalance;
	}

	public boolean isWhiteBalance() {
		return isWhiteBalance;
	}

	public boolean isBrightness() {
		return isBrightness;
	}

	public boolean isSaturation() {
		return isSaturation;
	}

	public boolean isSharpness() {
		return isSharpness;
	}

	public boolean isGamma() {
		return isGamma;
	}

	public boolean isBacklight() {
		return isBacklight;
	}

	public boolean isGain() {
		return isGain;
	}
}
