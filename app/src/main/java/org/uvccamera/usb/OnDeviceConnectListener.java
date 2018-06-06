package org.uvccamera.usb;

import android.hardware.usb.UsbDevice;

/**
 * Created by th on 17-2-16.
 */
public interface OnDeviceConnectListener {
    /**
     * called when device attached
     * @param device
     */
    public void onAttach(UsbDevice device);
    /**
     * called when device dettach(after onDisconnect)
     * @param device
     */
    public void onDettach(UsbDevice device);
    /**
     * called after device opend
     * @param device
     * @param ctrlBlock
     */
    public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock);
    /**
     * called when USB device removed or its power off (this callback is called after device closing)
     * @param device
     * @param ctrlBlock
     */
    public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock);
    /**
     * called when canceled or could not get permission from user
     * @param device
     */
    public void onCancel(UsbDevice device);

    public void onStartPreview();

    public void onStopPreview();
}
