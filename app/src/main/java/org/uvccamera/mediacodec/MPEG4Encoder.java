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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ;

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
	private MediaMuxer mediaMuxer;
	private MediaMuxer oldMediaMuxerMuxer;
	private String fileName;
	private String oldFileName;
	private boolean mediaMuxerStarted;
	private Timer mediaMuxerTimer;
	private MPEG4EncoderListener mediaMuxerListener;
	public static final long AUTO_SAVE_TIME = 10 * 60 * 1000;
	private int videoTrackIndex;
	private int audioTrackIndex;
	private MediaFormat videoMediaFormat;
	private MediaFormat audioMediaFormat;

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
		if (DEBUG) {
			Log.i(TAG, "new");
		}
		colorFormat = getFinalSupportColorFormat();
		this.outputPath = outputPath;
		this.rotate = rotate;
	}

	public synchronized YuvImage getThumbnailImage() {
		if (DEBUG) {
			Log.i(TAG, "getThumbnailImage");
		}
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
		if (DEBUG) {
			Log.i(TAG, "open");
		}
		if(mediaMuxer != null) {
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

			fileName = outputPath + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) +".mp4";
			mediaMuxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
			mediaMuxer.setOrientationHint(rotate);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		videoTrackIndex = -1;
		audioTrackIndex = -1;
		mediaMuxerStarted = false;
		if(mediaMuxerTimer != null){
			mediaMuxerTimer.cancel();
			mediaMuxerTimer = null;
		}
		if (DEBUG) {
			Log.i(TAG, "open colorFormat " + colorFormat);
		}
		open = true;
	}

	@Override
	public void close() {
		if (DEBUG) {
			Log.i(TAG, "close");
		}
		open = false;
		mediaMuxerStarted = false;
		videoTrackIndex = -1;
		audioTrackIndex = -1;
		if(mediaMuxerTimer != null){
			mediaMuxerTimer.cancel();
			mediaMuxerTimer = null;
		}

		try {
			if(videoCodec != null) {
				videoCodec.stop();
				videoCodec.release();
			}

			if(audioCodec != null) {
				audioCodec.stop();
				audioCodec.release();
			}

			if (mediaMuxer != null) {
				mediaMuxer.stop();
				mediaMuxer.release();
				mediaMuxer = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
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

	public void reset() {
		if (DEBUG) {
			Log.i(TAG, "reset");
		}
		if(mediaMuxerStarted) {
			mediaMuxerStarted = false;
			videoTrackIndex = -1;
			audioTrackIndex = -1;
			if(mediaMuxerTimer != null){
				mediaMuxerTimer.cancel();
				mediaMuxerTimer = null;
			}

			try {
				oldMediaMuxerMuxer = mediaMuxer;
				oldFileName = fileName;

				fileName = outputPath + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) +".mp4";
				mediaMuxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
				mediaMuxer.setOrientationHint(rotate);

				if(videoMediaFormat != null) {
					videoTrackIndex = mediaMuxer.addTrack(videoMediaFormat);
				}
				if(audioMediaFormat != null) {
					audioTrackIndex = mediaMuxer.addTrack(audioMediaFormat);
				}
				if (videoTrackIndex != -1 && audioTrackIndex != -1 && !mediaMuxerStarted) {
					mediaMuxer.start();
					mediaMuxerStarted = true;
					mediaMuxerTimer = new Timer();
					mediaMuxerTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							reset();
						}
					},AUTO_SAVE_TIME);
				}

				if (oldMediaMuxerMuxer != null) {
					oldMediaMuxerMuxer.stop();
					oldMediaMuxerMuxer.release();
					oldMediaMuxerMuxer = null;
					Log.i(TAG, "reset: 0000000000");
					if(mediaMuxerListener != null){
						Log.i(TAG, "reset: 11111111111"+oldFileName);
						mediaMuxerListener.onAutoSave(oldFileName);
						oldFileName = null;
					}
					Log.i(TAG, "reset: 2222222222");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void flush() {
		if (DEBUG) {
			Log.i(TAG, "flush");
		}
		if(videoCodec != null) {
			videoCodec.flush();
		}
		if(audioCodec != null) {
			audioCodec.flush();
		}
	}

	@Override
	public int encode(byte[] in, int offset, byte[] out, int length) {
		return 0;
	}

	public synchronized int encodeVideo(byte[] in, int offset, int length) {
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
				videoMediaFormat = videoCodec.getOutputFormat();

				if (DEBUG) {
					Log.i(TAG, "mediaFormat : " + videoMediaFormat.toString());
				}
				
				// now that we have the Magic Goodies, start the muxer
				videoTrackIndex = mediaMuxer.addTrack(videoMediaFormat);
				Log.i(TAG, "encode: videoTrackIndex = "+ videoTrackIndex);
				synchronized (this) {
					if (videoTrackIndex != -1 && audioTrackIndex != -1 && !mediaMuxerStarted) {
						mediaMuxer.start();
						mediaMuxerStarted = true;
						mediaMuxerTimer = new Timer();
						mediaMuxerTimer.schedule(new TimerTask() {
							@Override
							public void run() {
								reset();
							}
						},AUTO_SAVE_TIME);
					}
				}
			} else if (outputBufferIndex >= 0) {
				if (DEBUG) {
					Log.i(TAG, "videoBufferInfo.size=" + videoBufferInfo.size + " videoBufferInfo.offset=" + videoBufferInfo.offset);
				}
				ByteBuffer outputBuffer = videoCodec.getOutputBuffer(outputBufferIndex);
				outputBuffer.position(videoBufferInfo.offset);
				outputBuffer.limit(videoBufferInfo.offset + videoBufferInfo.size);
				if(mediaMuxerStarted && videoBufferInfo.size != 0) {
					mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferInfo);
				}
				videoCodec.releaseOutputBuffer(outputBufferIndex, false);
			}
		}
		return videoBufferInfo.size;
	}

	public synchronized int encodeAudio(byte[] in, int offset, int length) {
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
				audioMediaFormat = audioCodec.getOutputFormat();
				if (DEBUG) {
					Log.i(TAG, "mediaFormat : " + audioMediaFormat.toString());
				}
				// now that we have the Magic Goodies, start the muxer
				audioTrackIndex = mediaMuxer.addTrack(audioMediaFormat);
				Log.i(TAG, "encode: audioTrackIndex = "+ audioTrackIndex);
				synchronized (this) {
					if (videoTrackIndex != -1 && audioTrackIndex != -1 && !mediaMuxerStarted) {
						mediaMuxer.start();
						mediaMuxerStarted = true;
						mediaMuxerTimer = new Timer();
						mediaMuxerTimer.schedule(new TimerTask() {
							@Override
							public void run() {
								reset();
							}
						},AUTO_SAVE_TIME,AUTO_SAVE_TIME);
					}
				}
			} else if (outputBufferIndex >= 0) {
				if (DEBUG) {
					Log.i(TAG, "audioBufferInfo.size=" + audioBufferInfo.size + " audioBufferInfo.offset=" + audioBufferInfo.offset);
				}
				ByteBuffer outputBuffer = audioCodec.getOutputBuffer(outputBufferIndex);
				outputBuffer.position(audioBufferInfo.offset);
				outputBuffer.limit(audioBufferInfo.offset + audioBufferInfo.size);
				if(mediaMuxerStarted && audioBufferInfo.size != 0) {
					mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, audioBufferInfo);
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

	public void setMediaMuxerListener(MPEG4EncoderListener mediaMuxerListener) {
		this.mediaMuxerListener = mediaMuxerListener;
	}

	public void removeMediaMuxerListener() {
		this.mediaMuxerListener = null;
	}

	public String getFileName() {
		return fileName;
	}

	public interface MPEG4EncoderListener{
		void onAutoSave(String fileName);
	}
}
