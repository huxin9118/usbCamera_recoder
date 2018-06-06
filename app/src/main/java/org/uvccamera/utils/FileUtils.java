package org.uvccamera.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.text.DecimalFormat;

/**
 * Created by h26376 on 2017/9/5.
 */

public class FileUtils {
    private static final String TIME_SP_CACHE_NAME = "timeCache";
    private static final String LENGTH_SP_CACHE_NAME = "lengthCache";
    private static final String FRAME_SP_CACHE_NAME = "frameCache";

    public static boolean isFileChange(Context context, String url){
        File file = new File(url);
        if(!file.exists()){
            return false;
        }

        String yuvName = url.substring(url.lastIndexOf("/")+1);
        SharedPreferences timeSharedPreferences = context.getSharedPreferences(TIME_SP_CACHE_NAME
                , Context.MODE_PRIVATE);
        long timeCache = timeSharedPreferences.getLong(yuvName,-1);
        long lastFileModify = file.lastModified();

        SharedPreferences lengthSharedPreferences = context.getSharedPreferences(LENGTH_SP_CACHE_NAME
                , Context.MODE_PRIVATE);
        long lengthCache = lengthSharedPreferences.getLong(yuvName,-1);
        long filelength = file.length();


        if(timeCache == -1 || lengthCache == -1){
            SharedPreferences.Editor timeEdit = timeSharedPreferences.edit();
            timeEdit.putLong("yuvName",lastFileModify);
            SharedPreferences.Editor lengthEdit = lengthSharedPreferences.edit();
            lengthEdit.putLong("yuvName",filelength);
        }


        if(lastFileModify == timeCache && filelength == lengthCache){
            return true;
        }
        else{
            return false;
        }
    }

    public static int getFrameCount(Context context, String url){
        SharedPreferences frameSharedPreferences = context.getSharedPreferences(FRAME_SP_CACHE_NAME
                , Context.MODE_PRIVATE);
        return frameSharedPreferences.getInt("yuvName",-1);
    }

    public static void putFrameCount(Context context, String url, int frameCount){
        SharedPreferences frameSharedPreferences = context.getSharedPreferences(FRAME_SP_CACHE_NAME
                , Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = frameSharedPreferences.edit();
        edit.putInt("yuvName",frameCount);
    }

    public static String getDataSize(long size){
        DecimalFormat decimalFormat = new DecimalFormat("####.00");
        if(size<1024){
            return size+"B";
        }
        else if(size<1024*1024){
            return decimalFormat.format(size/1024f)+"KB";
        }
        else if(size<1024*1024*1024){
            return decimalFormat.format(size/1024f/1024f)+"MB";
        }
        else if(size<1024*1024*1024*1024){
            return decimalFormat.format(size/1024f/1024f/1024f)+"GB";
        }
        else {
            return "";
        }
    }
}
