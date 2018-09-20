package org.uvccamera.mediacodec;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import org.mediacodec.H264Encoder;
import org.mediacodec.H264Utils;

import java.nio.ByteBuffer;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ;

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
	private MediaCodec videoCodec;
	private MediaCodec audioCodec;
	/**
	 * 帧索引
	 */
	private int frameIndex = 0;

	/**
	 * 第一个I帧是否编码出
	 */
	private boolean open = false;

	private byte[] inData;

	private final MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
	private final MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

	// private FileOutputStream outfile;
	private String outputPath;
	private int rotate;
	private MediaMuxer mMuxer;
	private boolean mMuxerStarted;
	private int mVideoTrackIndex;
	private int mAudioTrackIndex;

	private long startTime = -1;

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
		if (inData != null) {
			byte[] yuvBuffer = new byte[width * height * 3 / 2];
			synchronized (inData) {
				H264Utils.swapNV12toNV21(inData, 0, yuvBuffer, width, height);
			}
			YuvImage image = new YuvImage(yuvBuffer, ImageFormat.NV21, width, height, null);
			return image;
		}
		return null;
	}

	public boolean isOpen() {
		return open;
	}

	@Override
	public void open() {
		if(videoCodec != null) {
			close();
		}
		try {
			videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
			MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
			videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
			videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, BITRATE_MODE_CQ);
			videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
			videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
			videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); // 关键帧间隔时间
			videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			videoCodec.start();

			audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
			MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 32000, 2);
			audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
			audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
			audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 48000);
			audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			audioCodec.start();

			mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
			mMuxer.setOrientationHint(rotate);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		mVideoTrackIndex = -1;
		mAudioTrackIndex = -1;
		mMuxerStarted = false;
		if (DEBUG) {
			Log.i(TAG, "encoder open colorFormat " + colorFormat);
		}
		open = true;
	}

	@Override
	public void close() {
		open = false;

		try {
			if(videoCodec != null) {
				videoCodec.stop();
				videoCodec.release();
			}

			if(audioCodec != null) {
				audioCodec.stop();
				audioCodec.release();
			}

			if (mMuxer != null) {
				mMuxer.stop();
				mMuxer.release();
				mMuxer = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (DEBUG) {
			Log.i(TAG, "encoder close");
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

	@Override
	public void flush() {
		if(videoCodec != null) {
			if (DEBUG) {
				Log.i(TAG, "flush");
			}
			videoCodec.flush();
		}
	}

	@Override
	public int encode(byte[] in, int offset, byte[] out, int length) {
		return 0;
	}

	public int encodeVideo(byte[] in, int offset, int length) {
		if (DEBUG) {
			Log.i(TAG, "encodeVideo");
		}

		errorCode = ERROR_CODE_NO_ERROR;
		
		if (!open) {
			errorCode = ERROR_CODE_CODEC_NOT_OPEN;
			return 0;
		}

		int inputBufferIndex = videoCodec.dequeueInputBuffer(-1);
		if (DEBUG) {
			Log.i(TAG, "inputBufferIndex : " + inputBufferIndex);
		}
		if (inData == null) {
			inData = new byte[length];
		}

		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = videoCodec.getInputBuffer(inputBufferIndex);
			if(colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
				H264Utils.NV12toYUV420Planar(in, offset, inData, width, height);
			}else {
				System.arraycopy(in, offset, inData, 0, length);
			}
			inputBuffer.clear();
			inputBuffer.put(inData, offset, length);
			videoCodec.queueInputBuffer(inputBufferIndex, 0, length, computePresentationTime(System.nanoTime() / 1000), 0);
		} else {
			errorCode = ERROR_CODE_INPUT_BUFFER_FAILURE;
		}

		int outputBufferIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, TIME_OUT);
		if (DEBUG) {
			Log.i(TAG, "outputBufferIndex : " + outputBufferIndex);
		}

		if (outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
			if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

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
				MediaFormat mediaFormat = videoCodec.getOutputFormat();

				if (DEBUG) {
					Log.i(TAG, "mediaFormat : " + mediaFormat.toString());
				}
				
				// now that we have the Magic Goodies, start the muxer
				mVideoTrackIndex = mMuxer.addTrack(mediaFormat);
				Log.i(TAG, "encode: mVideoTrackIndex = "+mVideoTrackIndex);
				if(mVideoTrackIndex != -1 && mAudioTrackIndex != -1 && !mMuxerStarted) {
					mMuxer.start();
					mMuxerStarted = true;
				}
			} else if (outputBufferIndex >= 0) {
				if (DEBUG) {
					Log.i(TAG, "videoBufferInfo.size=" + videoBufferInfo.size + " videoBufferInfo.offset=" + videoBufferInfo.offset);
				}
				ByteBuffer outputBuffer = videoCodec.getOutputBuffer(outputBufferIndex);
				outputBuffer.position(videoBufferInfo.offset);
				outputBuffer.limit(videoBufferInfo.offset + videoBufferInfo.size);
				if(mMuxerStarted && outputBuffer != null && videoBufferInfo.size != 0) {
					mMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, videoBufferInfo);
				}
				videoCodec.releaseOutputBuffer(outputBufferIndex, false);
			}
		}
		return videoBufferInfo.size;
	}

	public int encodeAudio(byte[] in, int offset, int length) {
		if (DEBUG) {
			Log.i(TAG, "encodeAudio");
		}

		errorCode = ERROR_CODE_NO_ERROR;

		if (!open) {
			errorCode = ERROR_CODE_CODEC_NOT_OPEN;
			return 0;
		}

		int inputBufferIndex = audioCodec.dequeueInputBuffer(TIME_OUT);
		if (DEBUG) {
			Log.i(TAG, "inputBufferIndex : " + inputBufferIndex);
		}

		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = audioCodec.getInputBuffer(inputBufferIndex);
			inputBuffer.clear();
			inputBuffer.put(in, offset, length);
			audioCodec.queueInputBuffer(inputBufferIndex, 0, length, computePresentationTime(System.nanoTime() / 1000), 0);
		} else {
			errorCode = ERROR_CODE_INPUT_BUFFER_FAILURE;
		}

		int outputBufferIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, TIME_OUT);
		if (DEBUG) {
			Log.i(TAG, "outputBufferIndex : " + outputBufferIndex);
		}

		if (outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
			if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				MediaFormat mediaFormat = audioCodec.getOutputFormat();
				if (DEBUG) {
					Log.i(TAG, "mediaFormat : " + mediaFormat.toString());
				}
				// now that we have the Magic Goodies, start the muxer
				mAudioTrackIndex = mMuxer.addTrack(mediaFormat);
				Log.i(TAG, "encode: mAudioTrackIndex = "+mAudioTrackIndex);
				if(mVideoTrackIndex != -1 && mAudioTrackIndex != -1 && !mMuxerStarted) {
					mMuxer.start();
					mMuxerStarted = true;
				}
			} else if (outputBufferIndex >= 0) {
				if (DEBUG) {
					Log.i(TAG, "audioBufferInfo.size=" + audioBufferInfo.size + " audioBufferInfo.offset=" + audioBufferInfo.offset);
				}
				ByteBuffer outputBuffer = audioCodec.getOutputBuffer(outputBufferIndex);
				outputBuffer.position(audioBufferInfo.offset);
				outputBuffer.limit(audioBufferInfo.offset + audioBufferInfo.size);
				if(mMuxerStarted && outputBuffer != null && audioBufferInfo.size != 0) {
					mMuxer.writeSampleData(mAudioTrackIndex, outputBuffer, audioBufferInfo);
				}
				audioCodec.releaseOutputBuffer(outputBufferIndex, false);
			}
		}
		return audioBufferInfo.size;
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
				Log.i(TAG, "colorFormats:" + c);
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
//	private long computePresentationTime(long frameIndex) {
//		return 132l + frameIndex * 1000000l / (long) framerate;
//	}

	private long computePresentationTime(long time) {
		if(startTime == -1){
			startTime = time;
		}
		return time - startTime;
	}

}
