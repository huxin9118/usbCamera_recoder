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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.uvccamera.mediacodec.MPEG4Encoder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class USBMonitor {
	private static final String TAG = "Debug_USBMonitor";

	private static final String ACTION_USB_PERMISSION_BASE = "org.uvccamera.USB_PERMISSION";
	private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE + "." + hashCode();

	private static final int DEVICE_STATE_ATTACH = 0;
	private static final int DEVICE_STATE_DETHCH = 1;
	private static final int DEVICE_STATE_CONNECT = 2;
	private static final int DEVICE_STATE_DISCONNECT = 3;
	private static final int DEVICE_STATE_CANCEL = 4;
	private static final int DEVICE_STATE_START_CAPTURE = 5;
	private static final int DEVICE_STATE_STOP_CAPTURE = 6;
	private static final int DEVICE_STATE_START_RECODER = 7;
	private static final int DEVICE_STATE_STOP_RECODER = 8;
	private static final int DEVICE_STATE_AUTO_SAVE_RECODER = 9;
	private static final int DEVICE_FIRST_RECEIVER = 10;

	private UsbManager mUsbManager = null;
	private WeakReference<Context> mWeakContext = null;
	private PendingIntent mPermissionIntent = null;
	private UsbDevice device = null;
	private boolean usbCameraConnect = false;
	private UsbControlBlock controlBlock = null;
	private UVCCamera mUVCCamera = null;
	private IFrameCallback frameCallback = null;
	private SurfaceHolder surfaceHolder = null;
	private boolean isCapture = false;
	private Set<OnDeviceConnectListener> onDeviceConnectListenerSet;
	private List<UsbFrameSize> mSupportedSizeList = null;
	private boolean isRegister;

	private boolean isLock;

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

	private MPEG4Encoder mp4Encoder;
//	private HH264Encoder mp4Encoder;
	private AudioRecord audioRecord;
	private byte[] audioRecordBuffer;
	private boolean isAudioRecord;
	private AudioRecordThread audioRecordThread;

	public USBMonitor(final Context context) {
		mWeakContext = new WeakReference<Context>(context);
		mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
		onDeviceConnectListenerSet = new HashSet<>();
	}

	public synchronized void register() {
		if(!isRegister) {
			final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
			// ACTION_USB_DEVICE_ATTACHED never comes on some devices so it should not be added here
			filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
			filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
			filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

			if (mWeakContext.get() != null && mUsbReceiver != null) {
				mWeakContext.get().registerReceiver(mUsbReceiver, filter);
				mPermissionIntent = PendingIntent.getBroadcast(mWeakContext.get(), 0, new Intent(ACTION_USB_PERMISSION), 0);
				isRegister = true;
			}
		}
	}

	public synchronized void unregister(){
		if(isRegister) {
			if (mWeakContext.get() != null && mUsbReceiver != null) {
				mWeakContext.get().unregisterReceiver(mUsbReceiver);
				isRegister = false;
			}
		}
	}

	private void notifyOnDeviceConnectListener(int state, Object... args){
		for(OnDeviceConnectListener listener : onDeviceConnectListenerSet) {
			switch (state) {
				case DEVICE_STATE_ATTACH:
					listener.onAttach(device);
					break;
				case DEVICE_STATE_DETHCH:
					listener.onDettach(device);
					break;
				case DEVICE_STATE_CONNECT:
					listener.onConnect(device, controlBlock);
					break;
				case DEVICE_STATE_DISCONNECT:
					listener.onDisconnect(device, controlBlock);
					break;
				case DEVICE_STATE_CANCEL:
					listener.onCancel(device);
					break;
				case DEVICE_STATE_START_CAPTURE:
					listener.onStartCapture();
					break;
				case DEVICE_STATE_STOP_CAPTURE:
					listener.onStopCapture();
					break;
				case DEVICE_STATE_START_RECODER:
					listener.onStartReoder();
					break;
				case DEVICE_STATE_STOP_RECODER:
					listener.onStopReoder((String)args[0]);
					break;
				case DEVICE_STATE_AUTO_SAVE_RECODER:
					listener.onAutoSaveReoder((String)args[0]);
					break;
				case DEVICE_FIRST_RECEIVER:
					listener.onFirstReceiver();
					break;
			}
		}
	}

	public void initCheckUVCState(OnDeviceConnectListener listener){
		HashMap<String,UsbDevice> usbDeviceList = mUsbManager.getDeviceList();
		if(usbDeviceList != null) {
			synchronized (this) {
				Iterator<UsbDevice> usbDevices = usbDeviceList.values().iterator();
				while (usbDevices.hasNext()) {
					device = usbDevices.next();
					if ((device != null)) {
						if (device.getDeviceClass() == 239 && device.getDeviceSubclass() == 2) {
							if (!mUsbManager.hasPermission(device)) {
								mUsbManager.requestPermission(device, mPermissionIntent);
								if (listener != null) {
									listener.onAttach(device);
								}
							} else {
								try {
									controlBlock = new UsbControlBlock(USBMonitor.this, device);
									connectCamera(controlBlock);
									if (listener != null) {
										listener.onConnect(device, controlBlock);
									}
								} catch (Exception e) {
									e.printStackTrace();
									if (listener != null) {
										listener.onDisconnect(device, controlBlock);
									}
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
			notifyOnDeviceConnectListener(DEVICE_STATE_START_CAPTURE);

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
			notifyOnDeviceConnectListener(DEVICE_STATE_STOP_CAPTURE);
		}
	}

	public void startRecoder(int width, int height, int frameRate, int bitRate, String outputPath, int rotate) {
		if(isCapture()) {
			notifyOnDeviceConnectListener(DEVICE_STATE_START_RECODER);

			if (mp4Encoder != null) {
				mp4Encoder.close();
				mp4Encoder = null;
			}

			if (audioRecordThread != null && audioRecord != null) {
				if(audioRecordThread != null && audioRecord != null){
					isAudioRecord = false;
					try {
						audioRecordThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			int audioSource = MediaRecorder.AudioSource.MIC;
			int sampleRate = 32000;
			int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
			int autioFormat = AudioFormat.ENCODING_PCM_16BIT;//PCM_16是所有android系统都支持的
			int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, autioFormat);//计算AudioRecord内部最小buffer
			Log.i(TAG, "startRecoder: minBufferSize="+minBufferSize);
			audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, autioFormat, minBufferSize * 2);
			audioRecordBuffer = new byte[sampleRate / (1000/20) * 2 * (16/8)];//20ms 双通道 16bit 的一帧数据
			audioRecord.startRecording();
			audioRecordThread = new AudioRecordThread();
			audioRecordThread.start();

//			File env = Environment.getExternalStorageDirectory();
//			File file = new File(env.toString() + "/record.pcm");
//			if(file.exists()){
//				file.delete();
//			}
//			try {
//				outputStream = new FileOutputStream(file);
//			} catch (FileNotFoundException e) {
//				e.printStackTrace();
//			}

			mp4Encoder = new MPEG4Encoder(width, height, frameRate, bitRate, outputPath, rotate);
			mp4Encoder.setMediaMuxerListener(new MPEG4Encoder.MPEG4EncoderListener() {
				@Override
				public void onAutoSave(String fileName) {
					notifyOnDeviceConnectListener(DEVICE_STATE_AUTO_SAVE_RECODER, fileName);
				}
			});
//			mp4Encoder = new HH264Encoder(width, height, frameRate, bitRate);
			mp4Encoder.open();
		}
	}

	private void audioVolumeChange(byte[] src, float times) {
		for (int i = 0; i < src.length; i += 2) {
			short shortData = (short) ((src[i] & 0xff) | ((src[i + 1] << 8) & 0xff00));
			int handleData = (int)(shortData * times);
			if (handleData > 32767) {
				shortData = 32767;
			}else if (handleData < -32767) {
				shortData = -32767;
			}
			 else{
				shortData = (short)handleData;
			}
			src[i] = (byte)(shortData & 0xff);
			src[i + 1] = (byte)((shortData & 0xff00) >> 8);
		}
	}

	private class AudioRecordThread extends Thread{
		@Override
		public void run() {
			if(audioRecord != null) {
				isAudioRecord = true;
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				while (isAudioRecord) {
					int read_result = audioRecord.read(audioRecordBuffer, 0, audioRecordBuffer.length);
					if(read_result == 0){
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						continue;
					}
//					audioVolumeChange(audioRecordBuffer, 7);
					Log.i(TAG, "run: read_result = " + read_result);
					if(mp4Encoder != null && mp4Encoder.isOpen()) {
						mp4Encoder.encodeAudio(audioRecordBuffer, 0, read_result);
//						try {
//							outputStream.write(audioRecordBuffer , 0 , read_result);
//						} catch (IOException e) {
//							e.printStackTrace();
//						}
					}
				}
				audioRecord.stop();
				audioRecord = null;
			}
		}
	}


	/**
	 * Get a YUVImage from mp4Encoder,
	 * Use the Function After startRecoder() and Before stopRecoder().
	 * @return
	 */
	public YuvImage getThumbnailImage() {
		if(mp4Encoder != null){
			return mp4Encoder.getThumbnailImage();
		}
		return null;
	}

	public void stopRecoder() {
		if(isCapture()) {
			if (mp4Encoder != null) {
				String fileName = mp4Encoder.getFileName();
				if(fileName == null){
					fileName = "";
				}
				notifyOnDeviceConnectListener(DEVICE_STATE_STOP_RECODER, fileName);

				mp4Encoder.removeMediaMuxerListener();
				mp4Encoder.close();
				mp4Encoder = null;
			}

			if(audioRecordThread != null && audioRecord != null){
				isAudioRecord = false;
				try {
					audioRecordThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

//			try {
//				outputStream.close();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
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

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				// when received the result of requesting USB permission
				synchronized (this) {
					Log.i(TAG, "onReceive: ACTION_USB_PERMISSION");
					notifyOnDeviceConnectListener(DEVICE_FIRST_RECEIVER);
					device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						try {
							controlBlock = new UsbControlBlock(USBMonitor.this, device);
							connectCamera(controlBlock);
							notifyOnDeviceConnectListener(DEVICE_STATE_CONNECT);
						}
						catch (Exception e){
							e.printStackTrace();
							notifyOnDeviceConnectListener(DEVICE_STATE_CANCEL);
						}
					}
				}
			} else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				synchronized (this) {
					Log.i(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED");
					device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null) {
						if (device.getDeviceClass() == 239 && device.getDeviceSubclass() == 2) {
							if(!mUsbManager.hasPermission(device)) {
								mUsbManager.requestPermission(device, mPermissionIntent);
								notifyOnDeviceConnectListener(DEVICE_STATE_ATTACH);
							}
							else {
								try {
									controlBlock = new UsbControlBlock(USBMonitor.this, device);
									connectCamera(controlBlock);
									notifyOnDeviceConnectListener(DEVICE_STATE_CONNECT);
								}
								catch (Exception e){
									e.printStackTrace();
									notifyOnDeviceConnectListener(DEVICE_STATE_CANCEL);
								}
							}
						}
					}
				}
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				synchronized (this) {
					Log.i(TAG, "onReceive: ACTION_USB_DEVICE_DETACHED");
					device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null) {
						stopRecoder();
						stopCapture();
						if(getUsbCameraConnect()){
							notifyOnDeviceConnectListener(DEVICE_STATE_DISCONNECT);
						}
						releaseCamera();
						notifyOnDeviceConnectListener(DEVICE_STATE_DETHCH);
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
					setUsbCameraConnect(false);
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

	public boolean getUsbCameraConnect() {
		return usbCameraConnect;
	}

	private void setUsbCameraConnect(boolean usbCameraConnect) {
		this.usbCameraConnect = usbCameraConnect;
	}

	public SurfaceHolder getSurfaceHolder() {
		return surfaceHolder;
	}

	public void setSurfaceHolder(SurfaceHolder surfaceHolder) {
		this.surfaceHolder = surfaceHolder;
	}

	public void addOnDeviceConnectListener(OnDeviceConnectListener onDeviceConnectListener) {
		this.onDeviceConnectListenerSet.add(onDeviceConnectListener);
	}

	public void removeOnDeviceConnectListener(OnDeviceConnectListener onDeviceConnectListener) {
		this.onDeviceConnectListenerSet.remove(onDeviceConnectListener);
	}

	public int getOnDeviceConnectListenerSize() {
		return this.onDeviceConnectListenerSet.size();
	}

	public boolean isLock() {
		return isLock;
	}

	public void setLock(boolean lock) {
		isLock = lock;
	}

	public void setIFrameCallback(IFrameCallback frameCallback) {
		this.frameCallback = frameCallback;
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

	public MPEG4Encoder getEncoder() {
		return mp4Encoder;
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
