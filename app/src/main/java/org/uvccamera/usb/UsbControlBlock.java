package org.uvccamera.usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.SparseArray;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;

/**
 * Created by th on 17-2-16.
 */
public class UsbControlBlock implements  Cloneable{
    private WeakReference<USBMonitor> mWeakMonitor = null;
    private WeakReference<UsbDevice> mWeakDevice = null;
    protected UsbDeviceConnection mConnection;
    protected UsbDeviceInfo mInfo = null;
    private int mBusNum;
    private int mDevNum;
    private final SparseArray<SparseArray<UsbInterface>> mInterfaces = new SparseArray<SparseArray<UsbInterface>>();


    public UsbControlBlock(final USBMonitor monitor, final UsbDevice device) {
        mWeakMonitor = new WeakReference<USBMonitor>(monitor);
        mWeakDevice = new WeakReference<UsbDevice>(device);
        mConnection = monitor.getmUsbManager().openDevice(device);
        mInfo = updateDeviceInfo(monitor.getmUsbManager(), device, null);

        String name = device.getDeviceName();
        String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
        if(v != null) {
            mBusNum = Integer.parseInt(v[v.length - 2]);
            mDevNum = Integer.parseInt(v[v.length - 1]);
        }

    }

    public UsbDeviceInfo updateDeviceInfo(final UsbManager manager, final UsbDevice device, final UsbDeviceInfo _info) {
        final UsbDeviceInfo info = _info != null ? _info : new UsbDeviceInfo();
        info.clear();

        if (device != null) {

            info.manufacturer = device.getManufacturerName();
            info.product = device.getProductName();
            info.serial = device.getSerialNumber();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info.usb_version = device.getVersion();
            }
            else{
                info.usb_version = "2.0";
            }

            if ((manager != null) && manager.hasPermission(device)) {
                final UsbDeviceConnection connection = manager.openDevice(device);
                final byte[] desc = connection.getRawDescriptors();

                if (TextUtils.isEmpty(info.usb_version)) {
                    info.usb_version = String.format("%x.%02x", ((int)desc[3] & 0xff), ((int)desc[2] & 0xff));
                }
                if (TextUtils.isEmpty(info.version)) {
                    info.version = String.format("%x.%02x", ((int)desc[13] & 0xff), ((int)desc[12] & 0xff));
                }
                if (TextUtils.isEmpty(info.serial)) {
                    info.serial = connection.getSerial();
                }

                final byte[] languages = new byte[256];
                int languageCount = 0;
                // controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout)
                try {
                    int result = connection.controlTransfer(
                            UsbCameraFlag.USB_REQ_STANDARD_DEVICE_GET, // USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE
                            UsbCameraFlag.USB_REQ_GET_DESCRIPTOR,
                            (UsbCameraFlag.USB_DT_STRING << 8) | 0, 0, languages, 256, 0);
                    if (result > 0) {
                        languageCount = (result - 2) / 2;
                    }
                    if (languageCount > 0) {
                        if (TextUtils.isEmpty(info.manufacturer)) {
                            info.manufacturer = getString(connection, desc[14], languageCount, languages);
                        }
                        if (TextUtils.isEmpty(info.product)) {
                            info.product = getString(connection, desc[15], languageCount, languages);
                        }
                        if (TextUtils.isEmpty(info.serial)) {
                            info.serial = getString(connection, desc[16], languageCount, languages);
                        }
                    }
                } finally {
                    connection.close();
                }
            }
            if (TextUtils.isEmpty(info.manufacturer)) {
                info.manufacturer = USBVendorId.vendorName(device.getVendorId());
            }
            if (TextUtils.isEmpty(info.manufacturer)) {
                info.manufacturer = String.format("%04x", device.getVendorId());
            }
            if (TextUtils.isEmpty(info.product)) {
                info.product = String.format("%04x", device.getProductId());
            }
        }
        return info;
    }

    private String getString(final UsbDeviceConnection connection, final int id, final int languageCount, final byte[] languages) {
        final byte[] work = new byte[256];
        String result = null;
        for (int i = 1; i <= languageCount; i++) {
            int ret = connection.controlTransfer(
                    UsbCameraFlag.USB_REQ_STANDARD_DEVICE_GET, // USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE
                    UsbCameraFlag.USB_REQ_GET_DESCRIPTOR,
                    (UsbCameraFlag.USB_DT_STRING << 8) | id, languages[i], work, 256, 0);
            if ((ret > 2) && (work[0] == ret) && (work[1] == UsbCameraFlag.USB_DT_STRING)) {
                // skip first two bytes(bLength & bDescriptorType), and copy the rest to the string
                try {
                    result = new String(work, 2, ret - 2, "UTF-16LE");
                    if (!"Љ".equals(result)) {
                        break;
                    } else {
                        result = null;
                    }
                } catch (final UnsupportedEncodingException e) {
                    // ignore
                }
            }
        }
        return result;
    }

    @Override
    public UsbControlBlock clone() throws CloneNotSupportedException {
        final UsbControlBlock ctrlblock;
        try {
            ctrlblock = new UsbControlBlock(this);
        } catch (final IllegalStateException e) {
            throw new CloneNotSupportedException(e.getMessage());
        }
        return ctrlblock;
    }
    private UsbControlBlock(final UsbControlBlock src) throws IllegalStateException {
        final USBMonitor monitor = src.getUSBMonitor();
        final UsbDevice device = src.getDevice();
        if (device == null) {
            throw new IllegalStateException("device may already be removed");
        }
        mConnection = monitor.getmUsbManager().openDevice(device);
        if (mConnection == null) {
            throw new IllegalStateException("device may already be removed or have no permission");
        }
        mInfo = updateDeviceInfo(monitor.getmUsbManager(), device, null);
        mWeakMonitor = new WeakReference<USBMonitor>(monitor);
        mWeakDevice = new WeakReference<UsbDevice>(device);
        mBusNum = src.mBusNum;
        mDevNum = src.mDevNum;
        // FIXME USBMonitor.mCtrlBlocksに追加する(今はHashMapなので追加すると置き換わってしまうのでだめ, ListかHashMapにListをぶら下げる?)
    }
    public USBMonitor getUSBMonitor() {
        return mWeakMonitor.get();
    }

    public final UsbDevice getDevice() {
        return mWeakDevice.get();
    }

    /**
     * get device name
     * @return
     */
    public String getDeviceName() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getDeviceName() : "";
    }

    /**
     * get device id
     * @return
     */
    public int getDeviceId() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getDeviceId() : 0;
    }


    public synchronized UsbDeviceConnection getConnection() {
        return mConnection;
    }

    /**
     * get file descriptor to access USB device
     * @return
     * @throws IllegalStateException
     */
    public synchronized int getFileDescriptor() throws IllegalStateException {
        checkConnection();
        return mConnection.getFileDescriptor();
    }

    /**
     * get raw descriptor for the USB device
     * @return
     * @throws IllegalStateException
     */
    public synchronized byte[] getRawDescriptors() throws IllegalStateException {
        checkConnection();
        return mConnection.getRawDescriptors();
    }

    /**
     * get vendor id
     * @return
     */
    public int getVenderId() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getVendorId() : 0;
    }

    /**
     * get product id
     * @return
     */
    public int getProductId() {
        final UsbDevice device = mWeakDevice.get();
        return device != null ? device.getProductId() : 0;
    }

    /**
     * get version string of USB
     * @return
     */
    public String getUsbVersion() {
        return mInfo.usb_version;
    }

    /**
     * get manufacture
     * @return
     */
    public String getManufacture() {
        return mInfo.manufacturer;
    }

    /**
     * get product name
     * @return
     */
    public String getProductName() {
        return mInfo.product;
    }

    /**
     * get version
     * @return
     */
    public String getVersion() {
        return mInfo.version;
    }

    /**
     * get serial number
     * @return
     */
    public String getSerial() {
        return mInfo.serial;
    }

    public int getBusNum() {
        return mBusNum;
    }

    public int getDevNum() {
        return mDevNum;
    }

    /**
     * get interface
     * @param interface_id
     * @throws IllegalStateException
     */
    public synchronized UsbInterface getInterface(final int interface_id) throws IllegalStateException {
        return getInterface(interface_id, 0);
    }

    /**
     * get interface
     * @param interface_id
     * @param altsetting
     * @return
     * @throws IllegalStateException
     */
    public synchronized UsbInterface getInterface(final int interface_id, final int altsetting) throws IllegalStateException {
        checkConnection();
        SparseArray<UsbInterface> intfs = mInterfaces.get(interface_id);
        if (intfs == null) {
            intfs = new SparseArray<UsbInterface>();
            mInterfaces.put(interface_id, intfs);
        }
        UsbInterface intf = intfs.get(altsetting);
        if (intf == null) {
            final UsbDevice device = mWeakDevice.get();
            final int n = device.getInterfaceCount();
            for (int i = 0; i < n; i++) {
                final UsbInterface temp = device.getInterface(i);
                if ((temp.getId() == interface_id) && (temp.getAlternateSetting() == altsetting)) {
                    intf = temp;
                    break;
                }
            }
            if (intf != null) {
                intfs.append(altsetting, intf);
            }
        }
        return intf;
    }

    /**
     * open specific interface
     * @param intf
     */
    public synchronized void claimInterface(final UsbInterface intf) {
        claimInterface(intf, true);
    }

    public synchronized void claimInterface(final UsbInterface intf, final boolean force) {
        checkConnection();
        mConnection.claimInterface(intf, force);
    }

    /**
     * close interface
     * @param intf
     * @throws IllegalStateException
     */
    public synchronized void releaseInterface(final UsbInterface intf) throws IllegalStateException {
        checkConnection();
        final SparseArray<UsbInterface> intfs = mInterfaces.get(intf.getId());
        if (intfs != null) {
            final int index = intfs.indexOfValue(intf);
            intfs.removeAt(index);
            if (intfs.size() == 0) {
                mInterfaces.remove(intf.getId());
            }
        }
        mConnection.releaseInterface(intf);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null) return false;
        if (o instanceof UsbControlBlock) {
            final UsbDevice device = ((UsbControlBlock) o).getDevice();
            return device == null ? mWeakDevice.get() == null
                    : device.equals(mWeakDevice.get());
        } else if (o instanceof UsbDevice) {
            return o.equals(mWeakDevice.get());
        }
        return super.equals(o);
    }

//		@Override
//		protected void finalize() throws Throwable {
///			close();
//			super.finalize();
//		}

    private synchronized void checkConnection() throws IllegalStateException {
        if (mConnection == null) {
            throw new IllegalStateException("already closed");
        }
    }

    public synchronized void close() {
        mConnection.close();
        mConnection = null;
    }

}
