package org.uvccamera.usb;

/**
 * Created by h26376 on 2018/1/25.
 */

public class UsbFrameSize {
    private int wdith;
    private int height;

    public UsbFrameSize(int wdith, int height){
        this.wdith = wdith;
        this.height = height;
    }

    public int getWdith() {
        return wdith;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return wdith +" x "+ height;
    }
}
