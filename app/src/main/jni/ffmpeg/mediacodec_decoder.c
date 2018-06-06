#include "mediacodec/mediacodec.h"

MediaCodecDecoder* mediacodec_decoder_alloc1(int isDebug, int timeout, YUV_PIXEL_FORMAT yuv_pixel_format) {
	if (isDebug) {
		MediaCodec_LOGI("[alloc]");
	}
	MediaCodecDecoder* decoder = (MediaCodecDecoder*)malloc(sizeof(MediaCodecDecoder));	
	decoder->codec = NULL;
    
	decoder->width = 0;
	decoder->height = 0;
	decoder->stride = 0;
	decoder->sliceHeight = 0;
	decoder->crop_left = 0;
	decoder->crop_right = 0;
	decoder->crop_top = 0;
	decoder->crop_bottom = 0;
	decoder->mime = NULL;
	decoder->colorFormat = 0;
    
	decoder->DEBUG = isDebug;
	decoder->TIME_OUT = timeout;
	decoder->MAX_TIME_OUT = 0;
	decoder->MIMETYPE_VIDEO_AVC = "video/avc";
	decoder->yuv_pixel_format = yuv_pixel_format;//输出的yuv像素格式
	
	decoder->SDK_INT = 0;
	decoder->phone_type = NULL;
	decoder->hardware = NULL;
	return decoder;
}

MediaCodecDecoder* mediacodec_decoder_alloc2(int isDebug) {
	return mediacodec_decoder_alloc1(isDebug, 0, NV12);
}

MediaCodecDecoder* mediacodec_decoder_alloc3(){
	return mediacodec_decoder_alloc2(1);
}


int mediacodec_decoder_free(MediaCodecDecoder* decoder) {
	if(decoder){
		if (decoder->DEBUG) {
			MediaCodec_LOGI("[free]");
		}
		free(decoder);
		return 0;
	}
	else{
		if (decoder->DEBUG) {
			MediaCodec_LOGE("[free]ERROR_SDK_DECODER_IS_NULL");
		}
		return -1;
	}
}

int mediacodec_decoder_open(MediaCodecDecoder* decoder) {
	if(decoder){
		if (decoder->DEBUG) {
			MediaCodec_LOGI("[open]");
		}
		char sdk[10] = {0};
		__system_property_get("ro.build.version.sdk",sdk);
		decoder->SDK_INT = atoi(sdk);
		
		if(decoder-> SDK_INT >= 16){
			if (decoder->DEBUG) {
				MediaCodec_LOGI("[open]SDK_INT : %d", decoder->SDK_INT);
			}
			if(decoder-> SDK_INT >= 21){
				decoder->MAX_TIME_OUT = 3000;
			}
			else{
				decoder->MAX_TIME_OUT = 8000;
			}
		}
		else{
			if (decoder->DEBUG) {
				MediaCodec_LOGE("[open]ERROR_SDK_VERSION_TOO_LOW  SDK_INT : %d", decoder->SDK_INT);
			}
			return -1;//ERROR_SDK_VERSION_TOO_LOW
		}
		
		char phone[20] = {0};
		__system_property_get("ro.product.model",phone);
		decoder->phone_type = (char*)malloc(20);
		memmove(decoder->phone_type, phone, 20);
		if (decoder->DEBUG) {
			MediaCodec_LOGI("[open]phone_type : %s", decoder->phone_type);
		}
		
		char cpu[20] = {0};
		__system_property_get("ro.hardware",cpu);
		decoder->hardware = (char*)malloc(20);
		memmove(decoder->hardware, cpu, 20);
		if (decoder->DEBUG) {
			MediaCodec_LOGI("[open]hardware : %s", decoder->hardware);
		}
		
		int status;

		decoder->codec = AMediaCodec_createDecoderByType(decoder->MIMETYPE_VIDEO_AVC);
		AMediaFormat* mediaFormat = AMediaFormat_new();
		AMediaFormat_setString(mediaFormat, AMEDIAFORMAT_KEY_MIME, decoder->MIMETYPE_VIDEO_AVC);
		if(decoder->hardware[0] == 'm' && decoder->hardware[1] == 't'){ //MTK CPU特殊处理
			AMediaFormat_setInt32(mediaFormat, AMEDIAFORMAT_KEY_WIDTH, 1920);
			AMediaFormat_setInt32(mediaFormat, AMEDIAFORMAT_KEY_HEIGHT, 1088);
		}
		else if(decoder->hardware[0] == 'h' && decoder->hardware[1] == 'i'){ //海思 CPU特殊处理
			AMediaFormat_setInt32(mediaFormat, AMEDIAFORMAT_KEY_WIDTH, 1920);
			AMediaFormat_setInt32(mediaFormat, AMEDIAFORMAT_KEY_HEIGHT, 1088);
		}
		else{
			AMediaFormat_setInt32(mediaFormat, AMEDIAFORMAT_KEY_WIDTH, 0);
			AMediaFormat_setInt32(mediaFormat, AMEDIAFORMAT_KEY_HEIGHT, 0);
		}
		
		status = AMediaCodec_configure(decoder->codec, mediaFormat, NULL, NULL, 0);
		if(status){
			if (decoder->DEBUG) {
				MediaCodec_LOGE("[open]AMediaCodec_configure error");
			}
			return status;
		}
		status = AMediaCodec_start(decoder->codec);
		if(status){
			if (decoder->DEBUG) {
				MediaCodec_LOGE("[open]AMediaCodec_start error");
			}
			return status;
		}
		status = AMediaCodec_flush(decoder->codec);
		if(status){
			if (decoder->DEBUG) {
				MediaCodec_LOGE("[open]AMediaCodec_flush error");
			}
			return status;
		}
		return status;
	}
	else{
		if (decoder->DEBUG) {
			MediaCodec_LOGE("[open]ERROR_SDK_DECODER_IS_NULL");
		}
		return -1;
	}
}	

int mediacodec_decoder_close(MediaCodecDecoder* decoder) {
	if(decoder){
		if (decoder->DEBUG) {
			MediaCodec_LOGI("[close]");
		}
		
		int status;
		if(decoder->codec){
			status = AMediaCodec_flush(decoder->codec);
			if(status){
				if (decoder->DEBUG) {
					MediaCodec_LOGE("[close]AMediaCodec_flush error");
				}
				return status;
			}
			status = AMediaCodec_stop(decoder->codec);
			if(status){
				if (decoder->DEBUG) {
					MediaCodec_LOGE("[close]AMediaCodec_stop error");
				}
				return status;
			}
			status = AMediaCodec_delete(decoder->codec);
			if(status){
				if (decoder->DEBUG) {
					MediaCodec_LOGE("[close]AMediaCodec_delete error");
				}
				return status;
			}
			decoder->codec = NULL;
		}
		if(decoder->phone_type){
			free(decoder->phone_type);
			decoder->phone_type = NULL;
		}
		if(decoder->hardware){
			free(decoder->hardware);
			decoder->hardware = NULL;
		}
		return status;
	}
	else{
		if (decoder->DEBUG) {
			MediaCodec_LOGE("[close]ERROR_SDK_DECODER_IS_NULL");
		}
		return -1;
	}
}

int mediacodec_decoder_decode(MediaCodecDecoder* decoder, uint8_t* in, int offset, uint8_t* out, int length, int* error_code) {
	if(decoder){
		if (decoder->DEBUG) {
			MediaCodec_LOGI("[decode]");
		}
	
		int size = 0;
		*error_code = 0;

		if (out == NULL) {
			if (decoder->DEBUG) {
				MediaCodec_LOGE("ERROR_CODE_OUT_BUF_NULL");
			}
			return -2;//ERROR_CODE_OUT_BUF_NULL
		}

		ssize_t inputBufferIndex = AMediaCodec_dequeueInputBuffer(decoder->codec, decoder->TIME_OUT);
		size_t inputBufferSize = 0;

		if (decoder->DEBUG) {
			MediaCodec_LOGI("inputBufferIndex : %d", inputBufferIndex);
		}
	
		if (inputBufferIndex >= 0) {
			uint8_t* inputBuffer = AMediaCodec_getInputBuffer(decoder->codec, inputBufferIndex, &inputBufferSize);
			if(inputBuffer != NULL && inputBufferSize >= length){
				memmove(inputBuffer, in+offset, length);
				AMediaCodec_queueInputBuffer(decoder->codec, inputBufferIndex, 0, length, 0, 0);
			}
			else{
				if (decoder->DEBUG) {
					MediaCodec_LOGE("ERROR_CODE_INPUT_BUFFER_FAILURE inputBufferSize/in.length : %d/%d", inputBufferIndex, length);
				}
				*error_code = -3;//ERROR_CODE_INPUT_BUFFER_FAILURE
			}
		} else {
			if (decoder->DEBUG) {
				MediaCodec_LOGE("ERROR_CODE_INPUT_BUFFER_FAILURE inputBufferIndex : %d", inputBufferIndex);
			}
			*error_code = -3;//ERROR_CODE_INPUT_BUFFER_FAILURE
		}

		AMediaCodecBufferInfo bufferInfo;
		ssize_t outputBufferIndex = 0;
		size_t outputBufferSize = 0;
		size_t expectBufferSize = 0;
	
		while (outputBufferIndex != AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
			outputBufferIndex = AMediaCodec_dequeueOutputBuffer(decoder->codec, &bufferInfo, decoder->TIME_OUT);

			if (decoder->DEBUG) {
				MediaCodec_LOGI("outputBufferIndex : %d",outputBufferIndex);
			}
		
		
			if(outputBufferIndex <= -20000){
				if (decoder->DEBUG) {
					MediaCodec_LOGE("AMEDIA_DRM_ERROR_BASE");
				}
				*error_code = outputBufferIndex;
				return size;
			}
			if(outputBufferIndex <= -10000){
				if (decoder->DEBUG) {
					MediaCodec_LOGE("AMEDIA_ERROR_BASE");
				}
				*error_code = outputBufferIndex;
				return size;
			}

			if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
				// outputBuffers = codec.getOutputBuffers();
				continue;
			} 
			else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
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
				 *    }
				 *	</code>
				 */
			
				AMediaFormat* mediaFormat = AMediaCodec_getOutputFormat(decoder->codec);

				AMediaFormat_getInt32(mediaFormat, AMEDIAFORMAT_KEY_WIDTH, &(decoder->width));
				AMediaFormat_getInt32(mediaFormat, AMEDIAFORMAT_KEY_HEIGHT, &(decoder->height));
			
				AMediaFormat_getInt32(mediaFormat, "crop-left", &(decoder->crop_left));
				AMediaFormat_getInt32(mediaFormat, "crop-right", &(decoder->crop_right));
				AMediaFormat_getInt32(mediaFormat, "crop-top", &(decoder->crop_top));
				AMediaFormat_getInt32(mediaFormat, "crop-bottom", &(decoder->crop_bottom));
			
				//crop属性获取失败，均为0，需手动修正
				if(decoder->crop_left == 0 && decoder->crop_left == 0 && decoder->crop_left == 0 && decoder->crop_left == 0){
					decoder->crop_left = 0;
					decoder->crop_right = decoder->width - 1;
					decoder->crop_top = 0;
					decoder->crop_bottom = decoder->height - 1;
				
					if(decoder->width == 1920 && decoder->height == 1088){
						decoder->crop_left = 0;
						decoder->crop_right = 1919;
						decoder->crop_top = 0;
						decoder->crop_bottom = 1079;
					}
				
					if(decoder->width == 1088 && decoder->height == 1920){
						decoder->crop_left = 0;
						decoder->crop_right = 1079;
						decoder->crop_top = 0;
						decoder->crop_bottom = 1919;
					}
				}
			
				if (decoder->SDK_INT >= 23) {
					AMediaFormat_getInt32(mediaFormat, AMEDIAFORMAT_KEY_STRIDE, &(decoder->stride));
					AMediaFormat_getInt32(mediaFormat, "slice-height", &(decoder->sliceHeight));
				} else {
					decoder->stride = decoder->width;
					decoder->sliceHeight = decoder->height;
				}

				AMediaFormat_getString(mediaFormat, AMEDIAFORMAT_KEY_MIME, &(decoder->mime));
				AMediaFormat_getInt32(mediaFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT, &(decoder->colorFormat));

				if (decoder->DEBUG) {
					MediaCodec_LOGI("mediaFormat : %s",AMediaFormat_toString(mediaFormat));
					MediaCodec_LOGI("[width=%d, height=%d, stride=%d, sliceHeight=%d]", decoder->width, decoder->height, decoder->stride, decoder->sliceHeight);
					MediaCodec_LOGI("[crop-left=%d, crop-right=%d, crop-top=%d, crop-bottom=%d]", decoder->crop_left, decoder->crop_right, decoder->crop_top, decoder->crop_bottom);
					MediaCodec_LOGI("[mime=%s, colorFormat=%d]", decoder->mime, decoder->colorFormat);
				}

				if (decoder->stride == 0 || decoder->stride < decoder->width) {
					decoder->stride = decoder->width;
				}

				if (decoder->sliceHeight == 0 || decoder->sliceHeight < decoder->height) {
					decoder->sliceHeight = decoder->height;
				}
			
				expectBufferSize =  decoder->stride * decoder->sliceHeight * 3 / 2;
			} 
			else if (outputBufferIndex >= 0) {
				expectBufferSize =  decoder->stride * decoder->sliceHeight * 3 / 2;
				if (decoder->DEBUG) {
					MediaCodec_LOGI("expectBufferSize=%d bufferInfo.size=%d bufferInfo.offset=%d", expectBufferSize, bufferInfo.size, bufferInfo.offset);
				}

				uint8_t* outputBuffer = AMediaCodec_getOutputBuffer(decoder->codec, outputBufferIndex, &outputBufferSize);

				if(outputBuffer != NULL && outputBufferSize >= expectBufferSize){
					if (decoder->width == decoder->stride && decoder->height == decoder->sliceHeight) {
						memmove(out, outputBuffer, expectBufferSize);
					} else {
						int offset0 = 0;
						int offset1 = 0;
						int i = 0;
						for (i = 0; i < decoder->sliceHeight; i++) {
							memmove(out + offset1, outputBuffer + offset0, decoder->width);
							offset0 += decoder->stride;
							offset1 += decoder->width;
						}
						int j = 0;
						for (j = 0; j < decoder->sliceHeight / 2; j++) {
							memmove(out + offset1, outputBuffer + offset0, decoder->width / 2);
							offset0 += decoder->stride / 2;
							offset1 += decoder->width / 2;
						}
					}					

					if (decoder->crop_right - decoder->crop_left + 1 < decoder->width || decoder->crop_bottom - decoder->crop_top + 1 < decoder->height) {
						size = (decoder->crop_right - decoder->crop_left + 1) * (decoder->crop_bottom - decoder->crop_top + 1) * 3 / 2;
						
						if(decoder->hardware[0] == 'm' && decoder->hardware[1] == 't'){
							CropYUV420Planar(out, decoder->width, decoder->height, outputBuffer, 
								decoder->crop_left, decoder->crop_right, decoder->crop_top, decoder->crop_bottom);//根据crop属性裁剪YUV
							
							if(decoder->yuv_pixel_format == I420){
								memmove(out, outputBuffer, size);
							}else if(decoder->yuv_pixel_format == NV12){
								I420toYUV420SemiPlanar(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}else if(decoder->yuv_pixel_format == NV21){
								I420toNV21(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}
						}
						else{
							CropYUV420SemiPlanar(out, decoder->width, decoder->height, outputBuffer, 
								decoder->crop_left, decoder->crop_right, decoder->crop_top, decoder->crop_bottom);//根据crop属性裁剪YUV
							
							if(decoder->yuv_pixel_format == I420){
								NV12toYUV420Planar(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}else if(decoder->yuv_pixel_format == NV12){
								memmove(out, outputBuffer, size);
							}else if(decoder->yuv_pixel_format == NV21){
								swapNV12toNV21(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}
						}
					} else {
						size = decoder->width * decoder->height * 3 / 2;
						if(decoder->hardware[0] == 'm' && decoder->hardware[1] == 't'){
							if(decoder->yuv_pixel_format == I420){
								// memmove(outputBuffer, out, size);
								// memmove(out, outputBuffer, size);
							}else if(decoder->yuv_pixel_format == NV12){
								memmove(outputBuffer, out, size);
								I420toYUV420SemiPlanar(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}else if(decoder->yuv_pixel_format == NV21){
								memmove(outputBuffer, out, size);
								I420toNV21(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}
						}
						else{
							if(decoder->yuv_pixel_format == I420){
								memmove(outputBuffer, out, size);
								NV12toYUV420Planar(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}else if(decoder->yuv_pixel_format == NV12){
								// memmove(outputBuffer, out, size);
								// memmove(out, outputBuffer, size);
							}else if(decoder->yuv_pixel_format == NV21){
								memmove(outputBuffer, out, size);
								swapNV12toNV21(outputBuffer, 0, out, decoder->crop_right - decoder->crop_left + 1, decoder->crop_bottom - decoder->crop_top + 1);
							}
						}
					}

				
				}
				AMediaCodec_releaseOutputBuffer(decoder->codec, outputBufferIndex, 0);
			}
		}

		return size;
	}
	else{
		if (decoder->DEBUG) {
			MediaCodec_LOGE("[decode]ERROR_SDK_DECODER_IS_NULL");
		}
		*error_code = -4;
		return 0;
	}
}

int mediacodec_decoder_getConfig_int(MediaCodecDecoder* decoder, char* key) {
	if (!strcmp(AMEDIAFORMAT_KEY_WIDTH, key)) {
		return decoder->crop_right - decoder->crop_left + 1 < decoder->width ? decoder->crop_right - decoder->crop_left + 1 : decoder->width;
	} 
	else if (!strcmp(AMEDIAFORMAT_KEY_HEIGHT, key)) {
		return decoder->crop_bottom - decoder->crop_top + 1 < decoder->height ? decoder->crop_bottom - decoder->crop_top + 1 : decoder->height;
	} 
	else if (!strcmp(AMEDIAFORMAT_KEY_COLOR_FORMAT, key)) {
		return decoder->colorFormat;
	}
	else if (!strcmp("timeout", key)) {
		return decoder->TIME_OUT;
	}
	else if (!strcmp("max-timeout", key)) {
		return decoder->MAX_TIME_OUT;
	}
	return -1;
}

int mediacodec_decoder_setConfig_int(MediaCodecDecoder* decoder, char* key, int value) {
	if (!strcmp("timeout", key)) {
		decoder->TIME_OUT = value;
		return 0;
	}
	else{
		return -1;
	}
}
