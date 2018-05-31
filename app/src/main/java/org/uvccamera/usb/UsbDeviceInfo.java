package org.uvccamera.usb;

/**
 * Created by th on 17-2-16.
 */
public class UsbDeviceInfo {
    public String usb_version;
    public String manufacturer;
    public String product;
    public String version;
    public String serial;

    public void clear() {
        usb_version = manufacturer = product = version = serial = null;
    }

    @Override
    public String toString() {
        return String.format("UsbDevice:usb_version=%s,manufacturer=%s,product=%s,version=%s,serial=%s",
                usb_version != null ? usb_version : "",
                manufacturer != null ? manufacturer : "",
                product != null ? product : "",
                version != null ? version : "",
                serial != null ? serial : "");
    }

}
