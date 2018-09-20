package org.uvccamera.bean;

/**
 * Created by h26376 on 2018/3/21.
 */

public class MediaInfo {
    private String url;
    private long time;
    private String type;
    private String thumbnailPath;
    private boolean isDownLoadThumbnailTask;

    private int width;
    private int height;
    private int rotate;
    private String codec;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public boolean isDownLoadThumbnailTask() {
        return isDownLoadThumbnailTask;
    }

    public void setDownLoadThumbnailTask(boolean downLoadThumbnailTask) {
        isDownLoadThumbnailTask = downLoadThumbnailTask;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getRotate() {
        return rotate;
    }

    public void setRotate(int rotate) {
        this.rotate = rotate;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    @Override
    public String toString() {
        return "MediaInfo{" +
                "url='" + url + '\'' +
                ", time=" + time +
                ", type='" + type + '\'' +
                ", thumbnailPath='" + thumbnailPath + '\'' +
                ", isDownLoadThumbnailTask=" + isDownLoadThumbnailTask +
                ", width=" + width +
                ", height=" + height +
                ", rotate=" + rotate +
                ", codec='" + codec + '\'' +
                '}';
    }
}
