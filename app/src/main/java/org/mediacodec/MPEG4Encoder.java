package org.mediacodec;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import org.mediacodec.utils.H264Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Android 4.1.2以上系统提供MediaCodec接口，可以对H264进行编解码
 * 
 * @author chenyang
 * 
 */
@SuppressLint("NewApi")
public class MPEG4Encoder extends H264Encoder {

	/**
	 * 日志标签
	 */
	public static final String TAG = MPEG4Encoder.class.getSimpleName();

	/**
	 * 调试日志开关
	 */
	public static final boolean DEBUG = true;

	/**
	 * 缓存获取超时时间
	 */
	public static final long TIME_OUT = 100;

	/**
	 * MediaCodec接口
	 */
	private MediaCodec mediaCodec;

	/**
	 * 编码器是否开启
	 */
	private boolean open = false;
	/**
	 *
	 */
	private byte[] yuv420 = null;
	/**
	 * 帧索引
	 */
	private int frameIndex = 0;

	// private FileOutputStream outfile;
	private String outputPath;
	private int rotate;
	private MediaMuxer mMuxer;
	private int mTrackIndex;
	private boolean mMuxerStarted;

	/**
	 * 构造方法
	 *
	 * @param width
	 *            视频宽度
	 * @param height
	 *            视频高度
	 * @param framerate
	 *            帧率
	 * @param bitrate
	 *            码流
	 */
	public MPEG4Encoder(int width, int height, int framerate, int bitrate, String outputPath, int rotate) {
		super(width, height, framerate, bitrate);
		colorFormat = getFinalSupportColorFormat();
		this.outputPath = outputPath;
		this.rotate = rotate;
	}

	public YuvImage getThumbnailImage() {
		if (yuv420 != null) {
			byte[] yuvBuffer = new byte[mWidth * mHeight * 3 / 2];
			synchronized (yuv420) {
				H264Utils.swapNV12toNV21(yuv420, 0, yuvBuffer, mWidth, mHeight);
			}
			YuvImage image = new YuvImage(yuvBuffer, ImageFormat.NV21, mWidth, mHeight, null);
			return image;
		}
		return null;
	}

	public boolean isOpen() {
		return open;
	}

	/**
	 * {@link Encoder#open()}
	 */
	@Override
	public void open() {
		yuv420 = new byte[mWidth * mHeight * 3 / 2];

		try {
			mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); // 关键帧间隔时间
																		// 单位s

		mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mediaCodec.start();

		frameIndex = 0;

		try {
			mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
			mMuxer.setOrientationHint(rotate);
		} catch (IOException ioe) {
			throw new RuntimeException("MediaMuxer creation failed", ioe);
		}

		mTrackIndex = -1;
		mMuxerStarted = false;

		if (DEBUG) {
			Log.d(TAG, "encoder open colorFormat " + colorFormat);
		}

		open = true;
	}

	/**
	 * {@link Encoder#close()}
	 */
	@Override
	public void close() {
		open = false;

		try {
			mediaCodec.stop();
		} catch (Exception e) {
		}

		try {
			mediaCodec.release();
		} catch (Exception e) {
		}

		if (mMuxer != null) {
			mMuxer.stop();
			mMuxer.release();
			mMuxer = null;
		}

		if (DEBUG) {
			Log.d(TAG, "encoder close");
		}

		// if(outfile != null){
			// try {
		 		// outfile.close();
		 	// }catch (IOException e) {
		 		// e.printStackTrace();
			// }
			// outfile = null;
		// }
	}

	/**
	 * {@link Encoder#encode(byte[], int, byte[], int)}
	 * 
	 */
	@SuppressWarnings("deprecation")
	@Override
	public int encode(byte[] in, int offset, byte[] out, int length) {
		if (DEBUG) {
			Log.d(TAG, "encode");
		}
		
		errorCode = ERROR_CODE_NO_ERROR;
		
		if (!open) {
			errorCode = ERROR_CODE_CODEC_NOT_OPEN;
			return 0;
		}

		int pos = 0;
		byte[] inBuf = in;
		int l = length;

		Log.d(TAG, "input colorFormat:"+colorFormat);
		synchronized (yuv420) {
			if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
				inBuf = yuv420;
				l = yuv420.length;
				System.arraycopy(in, offset, yuv420, 0, yuv420.length);
			} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
				inBuf = yuv420;
				l = yuv420.length;
				H264Utils.I420toYUV420SemiPlanar(in, offset, yuv420, mWidth, mHeight);
			}
		}

		try {
			ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
			ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
			int inputBufferIndex = mediaCodec.dequeueInputBuffer(TIME_OUT);
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();
				inputBuffer.put(inBuf, offset, l);
				mediaCodec.queueInputBuffer(inputBufferIndex, 0, l, computePresentationTime(frameIndex++), 0);
			} else {
				errorCode = ERROR_CODE_INPUT_BUFFER_FAILURE;
			}

	
			MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
			int outputBufferIndex = 0;

			while (outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
				outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

				if (DEBUG) {
					Log.d(TAG, outputBufferIndex + " outputBufferIndex");
				}

				if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					outputBuffers = mediaCodec.getOutputBuffers();
				} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

					/**
					 * <code>
					 *	mediaFormat : {
					 *		image-data=java.nio.HeapByteBuffer[pos=0 lim=104 cap=104], 
					 *		mime=video/raw, 
					 *		crop-top=0, 
					 *		crop-right=703, 
					 *		slice-height=576, 
					 *		color-format=21, 
					 *		height=576, width=704, 
					 *		crop-bottom=575, crop-left=0, 
					 *		hdr-static-info=java.nio.HeapByteBuffer[pos=0 lim=25 cap=25], 
					 *		stride=704
					 *	}
					 *	</code>
					 */
					MediaFormat mediaFormat = mediaCodec.getOutputFormat();

					if (DEBUG) {
						Log.d(TAG, "mediaFormat : " + mediaFormat.toString());
					}

					// now that we have the Magic Goodies, start the muxer
					mTrackIndex = mMuxer.addTrack(mediaFormat);
					Log.d(TAG, "encode: mTrackIndex = "+mTrackIndex);
					mMuxer.start();
					mMuxerStarted = true;
				} else if (outputBufferIndex >= 0) {
					ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

					if (DEBUG) {
						Log.d(TAG, outputBufferIndex + " outputBufferIndex");
						Log.d(TAG, bufferInfo.size + " bufferInfo.size");
					}
					
					if (DEBUG) {
						Log.d(TAG, outputBuffer.remaining() + " outputBuffer.remaining");
					}

					if(outputBuffer != null && bufferInfo.size != 0) {
						outputBuffer.position(bufferInfo.offset);
						outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
						mMuxer.writeSampleData(mTrackIndex, outputBuffer, bufferInfo);
					}

					mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return pos;
	}

	/**
	 * 获取最终的颜色模式
	 * 
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public int getFinalSupportColorFormat() {
		int[] colorFormats = getSupportColorFormat();

		if (colorFormats == null || colorFormats.length <= 0) {
			return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
		} else {
			for (int c : colorFormats) {
				Log.d(TAG, "colorFormats:" + c);
				if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
					return c;
				}
			}

			for (int c : colorFormats) {
				if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
					return c;
				}
			}

			return colorFormats[0];
		}
	}

	/**
	 * 获取支持的颜色模式列表
	 * 
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public int[] getSupportColorFormat() {
		int count = MediaCodecList.getCodecCount();

		for (int i = 0; i < count; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);

			if (info.isEncoder()) {
				try {
					return info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats;
				} catch (Exception e) {
					continue;
				}
			}
		}

		return null;
	}
	
	/**
	 * Generates the presentation time for frame N, in microseconds.
	 */
	private long computePresentationTime(long frameIndex) {
		return 132l + frameIndex * 1000000l / (long) framerate;
	}

}
