package org.uvccamera.usb;

/**
 * Created by th on 17-2-16.
 */
public class UsbCameraFlag {
    public static final int USB_DIR_OUT = 0;
    public static final int USB_DIR_IN = 0x80;
    public static final int USB_TYPE_MASK = (0x03 << 5);
    public static final int USB_TYPE_STANDARD = (0x00 << 5);
    public static final int USB_TYPE_CLASS = (0x01 << 5);
    public static final int USB_TYPE_VENDOR = (0x02 << 5);
    public static final int USB_TYPE_RESERVED = (0x03 << 5);
    public static final int USB_RECIP_MASK = 0x1f;
    public static final int USB_RECIP_DEVICE = 0x00;
    public static final int USB_RECIP_INTERFACE = 0x01;
    public static final int USB_RECIP_ENDPOINT = 0x02;
    public static final int USB_RECIP_OTHER = 0x03;
    public static final int USB_RECIP_PORT = 0x04;
    public static final int USB_RECIP_RPIPE = 0x05;
    public static final int USB_REQ_GET_STATUS = 0x00;
    public static final int USB_REQ_CLEAR_FEATURE = 0x01;
    public static final int USB_REQ_SET_FEATURE = 0x03;
    public static final int USB_REQ_SET_ADDRESS = 0x05;
    public static final int USB_REQ_GET_DESCRIPTOR = 0x06;
    public static final int USB_REQ_SET_DESCRIPTOR = 0x07;
    public static final int USB_REQ_GET_CONFIGURATION = 0x08;
    public static final int USB_REQ_SET_CONFIGURATION = 0x09;
    public static final int USB_REQ_GET_INTERFACE = 0x0A;
    public static final int USB_REQ_SET_INTERFACE = 0x0B;
    public static final int USB_REQ_SYNCH_FRAME = 0x0C;
    public static final int USB_REQ_SET_SEL = 0x30;
    public static final int USB_REQ_SET_ISOCH_DELAY = 0x31;
    public static final int USB_REQ_SET_ENCRYPTION = 0x0D;
    public static final int USB_REQ_GET_ENCRYPTION = 0x0E;
    public static final int USB_REQ_RPIPE_ABORT = 0x0E;
    public static final int USB_REQ_SET_HANDSHAKE = 0x0F;
    public static final int USB_REQ_RPIPE_RESET = 0x0F;
    public static final int USB_REQ_GET_HANDSHAKE = 0x10;
    public static final int USB_REQ_SET_CONNECTION = 0x11;
    public static final int USB_REQ_SET_SECURITY_DATA = 0x12;
    public static final int USB_REQ_GET_SECURITY_DATA = 0x13;
    public static final int USB_REQ_SET_WUSB_DATA = 0x14;
    public static final int USB_REQ_LOOPBACK_DATA_WRITE = 0x15;
    public static final int USB_REQ_LOOPBACK_DATA_READ = 0x16;
    public static final int USB_REQ_SET_INTERFACE_DS = 0x17;

    public static final int USB_REQ_STANDARD_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_DEVICE);		// 0x10
    public static final int USB_REQ_STANDARD_DEVICE_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE);			// 0x90
    public static final int USB_REQ_STANDARD_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_INTERFACE);	// 0x11
    public static final int USB_REQ_STANDARD_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_INTERFACE);	// 0x91
    public static final int USB_REQ_STANDARD_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT);	// 0x12
    public static final int USB_REQ_STANDARD_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT);		// 0x92

    public static final int USB_REQ_CS_DEVICE_SET  = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_DEVICE);				// 0x20
    public static final int USB_REQ_CS_DEVICE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_DEVICE);					// 0xa0
    public static final int USB_REQ_CS_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE);			// 0x21
    public static final int USB_REQ_CS_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_INTERFACE);			// 0xa1
    public static final int USB_REQ_CS_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);				// 0x22
    public static final int USB_REQ_CS_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);				// 0xa2

    public static final int USB_REQ_VENDER_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_DEVICE);				// 0x40
    public static final int USB_REQ_VENDER_DEVICE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_DEVICE);				// 0xc0
    public static final int USB_REQ_VENDER_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE);		// 0x41
    public static final int USB_REQ_VENDER_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_INTERFACE);		// 0xc1
    public static final int USB_REQ_VENDER_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);			// 0x42
    public static final int USB_REQ_VENDER_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);			// 0xc2

    public static final int USB_DT_DEVICE = 0x01;
    public static final int USB_DT_CONFIG = 0x02;
    public static final int USB_DT_STRING = 0x03;
    public static final int USB_DT_INTERFACE = 0x04;
    public static final int USB_DT_ENDPOINT = 0x05;
    public static final int USB_DT_DEVICE_QUALIFIER = 0x06;
    public static final int USB_DT_OTHER_SPEED_CONFIG = 0x07;
    public static final int USB_DT_INTERFACE_POWER = 0x08;
    public static final int USB_DT_OTG = 0x09;
    public static final int USB_DT_DEBUG = 0x0a;
    public static final int USB_DT_INTERFACE_ASSOCIATION = 0x0b;
    public static final int USB_DT_SECURITY = 0x0c;
    public static final int USB_DT_KEY = 0x0d;
    public static final int USB_DT_ENCRYPTION_TYPE = 0x0e;
    public static final int USB_DT_BOS = 0x0f;
    public static final int USB_DT_DEVICE_CAPABILITY = 0x10;
    public static final int USB_DT_WIRELESS_ENDPOINT_COMP = 0x11;
    public static final int USB_DT_WIRE_ADAPTER = 0x21;
    public static final int USB_DT_RPIPE = 0x22;
    public static final int USB_DT_CS_RADIO_CONTROL = 0x23;
    public static final int USB_DT_PIPE_USAGE = 0x24;
    public static final int USB_DT_SS_ENDPOINT_COMP = 0x30;
    public static final int USB_DT_CS_DEVICE = (USB_TYPE_CLASS | USB_DT_DEVICE);
    public static final int USB_DT_CS_CONFIG = (USB_TYPE_CLASS | USB_DT_CONFIG);
    public static final int USB_DT_CS_STRING = (USB_TYPE_CLASS | USB_DT_STRING);
    public static final int USB_DT_CS_INTERFACE = (USB_TYPE_CLASS | USB_DT_INTERFACE);
    public static final int USB_DT_CS_ENDPOINT = (USB_TYPE_CLASS | USB_DT_ENDPOINT);
    public static final int USB_DT_DEVICE_SIZE = 18;
}
