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
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
	public List<UsbFrameSize> mSupportedSizeList = null;
	//debug
	FileOutputStream outputStream;
	private static int index = 0;

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

			if (outputStream == null) {
				boolean sdCardExist = Environment.getExternalStorageState()
						.equals(Environment.MEDIA_MOUNTED);
				if(sdCardExist) {
					File env = Environment.getExternalStorageDirectory();
					File file = new File(env.toString() + "/NV12_1280x720.yuv");
					if(file.exists()){
						file.delete();
					}
					try {
						file.createNewFile();
					} catch (IOException e) {
						Log.e(TAG, " createNewFile error");
					}
					if(file.exists()) {
						try {
							outputStream = new FileOutputStream(file);
						} catch (FileNotFoundException e) {
							Log.e(TAG, "new FileOutputStream(file) error");
						}
					}
				}
			}
		}
	}

	public void stopCapture() {
		Log.i(TAG, "stopCapture");
		if(device != null && mUVCCamera != null && getUsbCameraConnect() && isCapture()) {
			isCapture = false;
			mUVCCamera.stopPreview();
			try{
				if(outputStream != null)
					outputStream.close();
			}catch (IOException e){
				Log.e(TAG , " outputStream close error");
			}

			if(outputStream != null){
				try {
					outputStream.close();
					outputStream = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
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
			Log.e(TAG, "receive the ["+index+"] frame!");

			if(outputStream != null) {
				try {
					byte[] byteFrame = new byte[frame.remaining()];
					frame.get(byteFrame, 0, byteFrame.length);

					outputStream.write(byteFrame);
				} catch (Exception e) {
					e.printStackTrace();
					Log.e(TAG, " fileChannel write error");
				}
				index ++;
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
					mUVCCamera.destory();

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
		}
	}

	public synchronized void releaseCamera(){
		synchronized (this) {
			if(mUVCCamera != null) {
				try{
					Log.i(TAG, "releaseCamera: ");
					mUVCCamera.setStatusCallback(null);
					mUVCCamera.close();
					mUVCCamera.destory();

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

	public UsbManager getmUsbManager() {
		return mUsbManager;
	}
}
