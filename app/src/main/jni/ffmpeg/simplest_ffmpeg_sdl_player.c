#include <stdio.h>
#include <time.h>
#include <unistd.h>
#include <pthread.h>

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include "libavutil/log.h"

#include "libyuv.h"

#include "mediacodec/mediacodec.h"

#ifdef ANDROID
#include <jni.h>
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO , "libSDL2", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR , "libSDL2", __VA_ARGS__)
#else
#define LOGE(format, ...)  printf("libSDL2" format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf("libSDL2" format "\n", ##__VA_ARGS__)
#endif

#define CONCAT1(prefix, class, function)    CONCAT2(prefix, class, function)
#define CONCAT2(prefix, class, function)    Java_ ## prefix ## _ ## class ## _ ## function
#define JAVA_PACKET							org_uvccamera_playback
#define JAVA_RENDER(function)            	CONCAT1(JAVA_PACKET, GLRenderer, function)
#define JAVA_CLASS_RENDER     				"org/uvccamera/playback/GLRenderer"

#define MEDIACODEC_DEC_BUFFER_ARRAY_SIZE  1024*1024*1
#define MEDIACODEC_DEC_YUV_ARRAY_SIZE  2000*1200*3/2
#define MEDIACODEC_ANDROID  0

volatile int thread_exit=0;
volatile int thread_back=0;
volatile int thread_pause=0;
volatile int thread_render=0;
int thread_forward=0;
int thread_backward=0;

int thread_picture=0;
int thread_video=0;

int thread_codec_type = 0;

long long skipFrame;
int thread_Seek=0;
long long seekFrame;
int is_Locating_I=0;

int screen_w=640,screen_h=480;
int pixel_w=640,pixel_h=480;
int angle=0;
int zoom=0;
int delay=40;

pthread_mutex_t mut_render = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond_render = PTHREAD_COND_INITIALIZER;
JavaVM* gs_jvm;

void updateSdlRect(int zoom){

}

JNIEXPORT void JNICALL JAVA_RENDER(nativeBackSDLThread)(JNIEnv* env, jobject obj)
{
	thread_back = 1;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativePauseSDLThread)(JNIEnv* env, jobject obj)
{
	thread_pause = 1;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativePlaySDLThread)(JNIEnv* env, jobject obj)
{
	thread_pause = 0;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeZoomSDLThread)(JNIEnv* env, jobject obj, jint jzoom)
{
	LOGI("zoom：%d",jzoom);
	updateSdlRect(jzoom);
	zoom = jzoom;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeForwardSDLThread)(JNIEnv* env, jobject obj, jlong jskipFrame)
{
	thread_forward = 1;
	is_Locating_I = 1;
	skipFrame = jskipFrame;
	if(thread_pause){
		thread_pause = -1;
	}
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeBackwardSDLThread)(JNIEnv* env, jobject obj, jlong jskipFrame)
{
	thread_backward = 1;
	is_Locating_I = 1;
	skipFrame = jskipFrame;
		if(thread_pause){
		thread_pause = -1;
	}
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeSeekSDLThread)(JNIEnv* env, jobject obj, jlong jseekFrame)
{
	thread_Seek = 1;
	is_Locating_I = 1;
	seekFrame = jseekFrame;
		if(thread_pause){
		thread_pause = -1;
	}
}
JNIEXPORT void JNICALL JAVA_RENDER(nativePictureSDLThread)(JNIEnv* env, jobject obj)
{
	thread_picture=1;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeStartVideoSDLThread)(JNIEnv* env, jobject obj)
{
	thread_video=1;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeEndVideoSDLThread)(JNIEnv* env, jobject obj)
{
	thread_video=3;
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeCodecType)(JNIEnv* env, jobject obj, jint jcodec_type)
{
	thread_codec_type=jcodec_type;
	LOGI("thread_codec_type = %d",thread_codec_type);
}
JNIEXPORT void JNICALL JAVA_RENDER(nativeUpdateSdlRect)(JNIEnv* env, jobject obj, jint jscreen_w, jint jscreen_h)
{
	screen_w = jscreen_w;
	screen_h = jscreen_h;
	updateSdlRect(zoom);
	LOGI("update : screen_w:%d  screen_h:%d",screen_w,screen_h);
}

void initGlobalState(){
	LOGI("清空所有变量");
	thread_exit = 0;
	thread_back = 0;
	
	thread_pause=0;
	thread_forward=0;
	thread_backward=0;

	thread_picture=0;
	thread_video=0;

	thread_Seek=0;
	is_Locating_I=0;
}

typedef struct MediacodecContext{
	jbyteArray dec_buffer_array;
    jbyteArray dec_yuv_array;
	
	jclass class_Codec;
	jclass class_HH264Decoder;
	jclass class_H264Utils;
	jclass class_Integer;
	jclass class_MediaFormat;
	jclass class_CodecCapabilities;
	
	jmethodID methodID_HH264Decoder_constructor;
	jmethodID methodID_HH264Decoder_config;
	jmethodID methodID_HH264Decoder_getConfig;
	jmethodID methodID_HH264Decoder_open;
	jmethodID methodID_HH264Decoder_decode;
	jmethodID methodID_HH264Decoder_getErrorCode;
	jmethodID methodID_HH264Decoder_close;
	jmethodID methodID_H264Utils_ffAvcFindStartcode;
	jmethodID methodID_Integer_intValue;
	jfieldID fieldID_Codec_ERROR_CODE_INPUT_BUFFER_FAILURE;
	jfieldID fieldID_MediaFormat_KEY_WIDTH;
	jfieldID fieldID_MediaFormat_KEY_HEIGHT;
	jfieldID fieldID_MediaFormat_KEY_COLOR_FORMAT;
	jfieldID fieldID_CodecCapabilities_COLOR_FormatYUV420Planar;
	jfieldID fieldID_CodecCapabilities_COLOR_FormatYUV420SemiPlanar;
	
	jint ERROR_CODE_INPUT_BUFFER_FAILURE;
	jobject KEY_WIDTH;
	jobject KEY_HEIGHT;
	jobject KEY_COLOR_FORMAT;
	jint COLOR_FormatYUV420Planar;
	jint COLOR_FormatYUV420SemiPlanar;
	
	jobject object_decoder;
}MediacodecContext;
void mediacodec_decode_video(JNIEnv* env, MediacodecContext* mediacodecContext, AVPacket *pPacket, AVFrame *pFrame, int *got_picture);
void mediacodec_decode_video2(MediaCodecDecoder* decoder, AVPacket *pPacket, AVFrame *pFrame, int *got_picture, int *error_code);

typedef struct RenderContext{
	uint8* buffer;
	int buffer_size;
	int frameSize;
	int qFrameSize;
	enum FourCC pixformat;
	jmethodID methodID_updateData;
	jobject gs_obj;
}RenderContext;

static void* thread_render_opengles(void* arg)
{
	LOGI("thread_render_opengles start");
	JNIEnv *env;
	(*gs_jvm)->AttachCurrentThread(gs_jvm,&env,NULL);
	struct timeval time1,time2;
	
	RenderContext *renderContext = (RenderContext *)arg;

	uint8* buffer = (uint8*)malloc(renderContext->buffer_size);
	uint8* dst_y = (uint8*)malloc(renderContext->frameSize);
	uint8* dst_u = (uint8*)malloc(renderContext->qFrameSize);
	uint8* dst_v = (uint8*)malloc(renderContext->qFrameSize);
	jbyteArray array_y = (*env)->NewByteArray(env, renderContext->frameSize);
	jbyteArray array_u = (*env)->NewByteArray(env, renderContext->qFrameSize);
	jbyteArray array_v = (*env)->NewByteArray(env, renderContext->qFrameSize);
	
	while(!thread_exit && !thread_back){
		pthread_mutex_lock(&mut_render);
		while(!thread_render){
			pthread_cond_wait(&cond_render,&mut_render);//等待触发渲染工作
		}
		
		//进入工作状态
		if(!thread_exit && !thread_back){
			memmove(buffer, renderContext->buffer, renderContext->buffer_size);
			thread_render = 0;
			pthread_mutex_unlock(&mut_render);

			if(buffer)	{
                int allzero = 1;
                int i = 0;
                for(i=0; i< renderContext->buffer_size/2; i += sizeof(int))
                {
                    if(*((int *)(buffer+i)) != 0)
                    {
                        allzero = 0;
                        break;
                    }
                }

                if(allzero)	{
                    continue;
                }
            }

			// gettimeofday(&time1,NULL);
			ConvertToI420(buffer, renderContext->buffer_size,
						dst_y, pixel_w,
						dst_u, pixel_w / 2,
						dst_v, pixel_w / 2,
						0, 0,
						pixel_w, pixel_h,
						pixel_w, pixel_h,
						kRotate0, renderContext->pixformat);
			
			(*env)->SetByteArrayRegion(env, array_y, 0, renderContext->frameSize, (jbyte *)dst_y);
			(*env)->SetByteArrayRegion(env, array_u, 0, renderContext->qFrameSize, (jbyte *)dst_u);
			(*env)->SetByteArrayRegion(env, array_v, 0, renderContext->qFrameSize, (jbyte *)dst_v);
			(*env)->CallVoidMethod(env, renderContext->gs_obj, renderContext->methodID_updateData, array_y, array_u, array_v);
			// gettimeofday(&time2,NULL);
			// LOGI("render time:%lf ms", ((double)(time2.tv_sec * 1000000 + time2.tv_usec - time1.tv_sec * 1000000 - time1.tv_usec)) / 1000);
		}
		else{
			thread_render = 0;
			pthread_mutex_unlock(&mut_render);
		}
	}
	
	(*env)->DeleteLocalRef(env, array_y);
	(*env)->DeleteLocalRef(env, array_u);
	(*env)->DeleteLocalRef(env, array_v);
	free(buffer);
	free(dst_y);
	free(dst_u);
	free(dst_v);
	
	(*gs_jvm)->DetachCurrentThread(gs_jvm);
	LOGI("thread_render_opengles end");
	return (void*)0;
}

JNIEXPORT jint JNICALL JAVA_RENDER(nativeInitSDLThreadYUV)(JNIEnv* env, jobject obj ,jstring url, jint wdith, jint height, jint pixel_type, jint fps)
{
	initGlobalState();
	int error_code = 0;
	FILE *fp;
	int frameConut = 0;

	pixel_w = wdith;
	pixel_h = height;
	LOGI("pixel_w:%d,pixel_h:%d",pixel_w,pixel_h);

	uint8* buffer;
	int buffer_size;
	
	jclass class_render = (*env)->FindClass(env, JAVA_CLASS_RENDER);
	jmethodID methodID_setProgressRate = (*env)->GetMethodID(env, class_render, "setProgressRate", "(I)V");
	jmethodID methodID_setProgressRateFull = (*env)->GetMethodID(env, class_render, "setProgressRateFull", "()V");
	jmethodID methodID_initOrientation = (*env)->GetMethodID(env, class_render, "initOrientation", "()V");
	
	jmethodID methodID_updateData = (*env)->GetMethodID(env, class_render, "updateData", "([B[B[B)V");
	jmethodID methodID_updateParameter = (*env)->GetMethodID(env, class_render, "updateParameter", "(III)V");
	
	char url_str[255]={0};
	const char * url_jstr = (*env)->GetStringUTFChars(env,url, NULL);
	sprintf(url_str,"%s", url_jstr);
	(*env)->ReleaseStringUTFChars(env,url, url_jstr);
	
	fp=fopen(url_str,"rb+");
	if(fp==NULL)
	{
		LOGE("cannot open this file:  %s", url_str);
		error_code = -1;
		goto on_error;
	}

	angle=0;
	zoom=0;
	updateSdlRect(zoom);

	delay = (int)(1000.0/fps);
	LOGI("delay: %d",delay);
	
	if(pixel_w > pixel_h){
		(*env)->CallVoidMethod(env, obj, methodID_initOrientation);
	}
	(*env)->CallVoidMethod(env, obj, methodID_updateParameter,pixel_w,pixel_h,angle);
	
//--------------------------render_thread------------------------------
	RenderContext renderContext;
	renderContext.methodID_updateData = methodID_updateData;
	renderContext.gs_obj=(*env)->NewGlobalRef(env,obj);
	
	LOGI("pixel_type:%d",pixel_type);
	switch(pixel_type){
		case 0: renderContext.pixformat = FOURCC_I420; break;
		case 1: renderContext.pixformat = FOURCC_YV12; break;
		case 2: renderContext.pixformat = FOURCC_NV12; break;
		case 3: renderContext.pixformat = FOURCC_NV21; break;
		case 4: renderContext.pixformat = FOURCC_YUYV; break;
		case 5: renderContext.pixformat = FOURCC_UYVY; break;
		default:renderContext.pixformat = FOURCC_I420; break;
	}
	
	if(renderContext.pixformat == FOURCC_I420 || renderContext.pixformat == FOURCC_YV12  
		|| renderContext.pixformat == FOURCC_NV12 || renderContext.pixformat == FOURCC_NV21){
		buffer_size = pixel_w * pixel_h * 3 / 2;
		buffer = (uint8*)malloc(buffer_size);
		
		renderContext.buffer_size = buffer_size;
		renderContext.buffer = (uint8*)malloc(buffer_size);
		
		renderContext.frameSize = pixel_w * pixel_h;
		renderContext.qFrameSize = renderContext.frameSize / 4;
	}
	else if(renderContext.pixformat == FOURCC_YUYV || renderContext.pixformat == FOURCC_UYVY){
		buffer_size = pixel_w * pixel_h * 2;
		buffer = (uint8*)malloc(buffer_size);
		
		renderContext.buffer_size = buffer_size;
		renderContext.buffer = (uint8*)malloc(buffer_size);
		
		renderContext.frameSize = pixel_w * pixel_h;
		renderContext.qFrameSize = renderContext.frameSize / 4;
	}
	
	pthread_t render_thread;
	pthread_create(&render_thread, NULL, thread_render_opengles, &renderContext);

	struct timeval old = {0},new = {0};
	
	LOGI("LOOP thread_exit = %d , thread_back = %d",thread_exit,thread_back);
	while(!thread_exit && !thread_back){
		if(old.tv_sec != 0 && old.tv_usec != 0){
			gettimeofday(&new,NULL);
			while(new.tv_sec * 1000000 + new.tv_usec - old.tv_sec * 1000000 - old.tv_usec < delay * 1000){
				usleep(500);
				gettimeofday(&new,NULL);
			}
			old.tv_sec = new.tv_sec;
			old.tv_usec = new.tv_usec;
		}
		else{
			gettimeofday(&old,NULL);
		}
		
		if (thread_forward == 1)
		{
			frameConut = frameConut + skipFrame - 1;
			if(frameConut<0){
				frameConut=0;
			}
			LOGI("前进到%5d帧~~~~",frameConut);
			
			fseek(fp, buffer_size * frameConut,SEEK_SET);
			thread_forward = 0;
		}
		if (thread_backward == 1)
		{
			frameConut = frameConut + skipFrame - 1;
			if(frameConut<0){
				frameConut=0;
			}
			LOGI("后退到%5d帧~~~~",frameConut);
			
			fseek(fp, buffer_size * frameConut,SEEK_SET);
			thread_backward = 0;
		}
		if(thread_Seek == 1)
		{
			frameConut = seekFrame - 1;
			if(frameConut<0){
				frameConut=0;
			}
			LOGI("跳转到%5d帧~~~~",frameConut);
			
			fseek(fp, buffer_size * seekFrame,SEEK_SET);
			thread_Seek = 0;
		}
		if(thread_pause == 0 || thread_pause == -1){
			if (fread(buffer, 1, buffer_size, fp) != buffer_size)
			{
				//Loop
				//fseek(fp, 0, SEEK_SET);
				//fread(buffer, 1, buffer_size, fp);
				thread_exit = 1;
				break;
			}	
			
			pthread_mutex_lock(&mut_render);
			if(!thread_render){
				memmove(renderContext.buffer, buffer, buffer_size);
				thread_render = 1;
				pthread_cond_signal(&cond_render);
			}
			else{
				LOGE("render thread run already");
			}
			pthread_mutex_unlock(&mut_render);

			frameConut++;
			
			if(thread_pause == -1){
				thread_pause = 1;
			}
		}
		
		if(thread_exit == 0){
			(*env)->CallVoidMethod(env, obj, methodID_setProgressRate,frameConut);
		}
	}
	
	if(thread_exit == 1)
	{
		(*env)->CallVoidMethod(env, obj, methodID_setProgressRateFull);
	}
	
on_error:
	LOGI("Destory thread_exit = %d , thread_back = %d",thread_exit,thread_back);
	
	if(render_thread && (error_code == 0 || error_code < -1)){
		pthread_mutex_lock(&mut_render);
		LOGI("Destory thread_render = %d",thread_render);
		if(!thread_render){
			thread_render = 1;
			pthread_cond_signal(&cond_render);
		}
		pthread_mutex_unlock(&mut_render);
		
		pthread_join(render_thread,NULL);
		LOGI("join finished");
	}
	
	
	if(fp && (error_code == 0 || error_code < -1)){
		fclose(fp);
	}
	if(buffer && (error_code == 0 || error_code < -1)){
		free(buffer);
	}
	if(class_render){
		(*env)->DeleteLocalRef(env, class_render);
	}
	if(renderContext.gs_obj && (error_code == 0 || error_code < -1)){
		(*env)->DeleteGlobalRef(env, renderContext.gs_obj);
	}
	if(renderContext.buffer && (error_code == 0 || error_code < -1)){
		free(renderContext.buffer);
	}
	return error_code;
}

static int CheckInterrupt(void* opaque)//ffmpeg中断回调
{
	if(thread_exit || thread_back){
		return 1;
	}
	else{
		return 0;
	}
}

JNIEXPORT jint JNICALL JAVA_RENDER(nativeInitSDLThread)(JNIEnv* env, jobject obj ,jstring url, jboolean isStreamMedia, jint rotate)
{
	initGlobalState();
	
	int error_code = 0;
	AVFormatContext	*pFormatCtx;
	AVDictionary *options;
	int				i, videoindex;
	AVCodecContext	*pCodecCtx;
	AVCodec			*pCodec;
	AVFrame	*pFrame,*pFrameYUV;
	AVPacket *pPacket;
	AVBitStreamFilterContext* bsfc;
	// struct SwsContext *img_convert_ctx;
	int ret, got_picture;
	long long frameConut = 0;
	long long current_dts = 0;
	long long forward_dts = 0;
	int forwardOffset = 0;

	LOGI("isStreamMedia: %d",isStreamMedia);
	
	jclass class_render = (*env)->FindClass(env, JAVA_CLASS_RENDER);
	jmethodID methodID_setProgressRateFull = (*env)->GetMethodID(env, class_render, "setProgressRateFull", "()V");
	jmethodID methodID_setProgressDTS = (*env)->GetMethodID(env, class_render, "setProgressDTS", "(J)V");
	jmethodID methodID_setProgressDuration = (*env)->GetMethodID(env, class_render, "setProgressDuration", "(J)V");
	jmethodID methodID_showIFrameDTS = (*env)->GetMethodID(env, class_render, "showIFrameDTS", "(JI)V");
	jmethodID methodID_initOrientation = (*env)->GetMethodID(env, class_render, "initOrientation", "()V");
	jmethodID methodID_hideLoading = (*env)->GetMethodID(env, class_render, "hideLoading", "()V");
	jmethodID methodID_showLoading = (*env)->GetMethodID(env, class_render, "showLoading", "()V");
	jmethodID methodID_changeCodec = (*env)->GetMethodID(env, class_render, "changeCodec", "(Ljava/lang/String;)V");
	jmethodID methodID_setTimeBase = (*env)->GetMethodID(env, class_render, "setTimeBase", "(D)V");
	
	jmethodID methodID_updateData = (*env)->GetMethodID(env, class_render, "updateData", "([B[B[B)V");
	jmethodID methodID_updateParameter = (*env)->GetMethodID(env, class_render, "updateParameter", "(III)V");
	

//-----------------------------------------------------------------------------------
	// char filepath[]="sintel264.h264";
	// char filepath[]="rtsp://192.168.133.145:8554/111";

	av_register_all();
	avcodec_register_all();
	avformat_network_init();
	pFormatCtx = avformat_alloc_context();
	pFormatCtx->interrupt_callback.callback = CheckInterrupt;//超时回调
	pFormatCtx->interrupt_callback.opaque = NULL;

	options = NULL;
	av_dict_set(&options,"rtsp_transport","tcp",0);
	av_dict_set(&options,"stimeout","5000000",0);//超时5秒
	
	char url_str[255]={0};
	const char * url_jstr = (*env)->GetStringUTFChars(env,url, NULL);
	sprintf(url_str,"%s", url_jstr);
	(*env)->ReleaseStringUTFChars(env,url, url_jstr);
	LOGI("url_str = %s",url_str);
	if(avformat_open_input(&pFormatCtx,url_str,NULL,&options)!=0)
	{
		LOGE("Couldn't open input stream.\n");
		error_code = -1;
		goto on_error;
	}
	if(avformat_find_stream_info(pFormatCtx,NULL)<0)
	{
		LOGE("Couldn't find stream information.\n");
		error_code = -2;
		goto on_error;
	}

	videoindex=av_find_best_stream(pFormatCtx,AVMEDIA_TYPE_VIDEO,-1,-1,NULL,0);
	if(videoindex==-1){
		LOGE("Didn't find a video stream.\n");
		error_code = -3;
		goto on_error;
	}
	
	pCodec=avcodec_find_decoder(pFormatCtx->streams[videoindex]->codecpar->codec_id);
	if(pCodec==NULL){
		LOGE("Codec not found.\n");
		error_code = -4;
		goto on_error;
	}
	pCodecCtx=avcodec_alloc_context3(pCodec);
	avcodec_parameters_to_context(pCodecCtx,pFormatCtx->streams[videoindex]->codecpar);

	if(avcodec_open2(pCodecCtx, pCodec,NULL)<0){
		LOGE("Could not open codec.\n");
		error_code = -5;
		goto on_error;
	}
	
	pixel_w = pCodecCtx->width;
	pixel_h = pCodecCtx->height;
	LOGI("pixel_w:%d  pixel_h:%d",pixel_w,pixel_h);
	if(pixel_w == 0 || pixel_h == 0){
		LOGE("video stream error.\n");
		error_code = -6;
		goto on_error;
	}

	LOGI("format name: %s",pFormatCtx->iformat->name);

	if(strcmp("avi",pFormatCtx->iformat->name) == 0 || strcmp("h264",pFormatCtx->iformat->name) == 0){
		(*env)->CallVoidMethod(env, obj, methodID_setTimeBase, 1/av_q2d(pFormatCtx->streams[videoindex]->r_frame_rate));
		LOGI("frame_rate: %lf",av_q2d(pFormatCtx->streams[videoindex]->r_frame_rate));
	}
	else{
		(*env)->CallVoidMethod(env, obj, methodID_setTimeBase, av_q2d(pFormatCtx ->streams[videoindex]->time_base));
		LOGI("time_base: %lf",av_q2d(pFormatCtx ->streams[videoindex]->time_base));
	}

	long long duration;
	if(pFormatCtx ->streams[videoindex]->duration<0){
		duration = (long long)((double)pFormatCtx ->duration / 1000000 / av_q2d(pFormatCtx ->streams[videoindex]->time_base)) ;
	}
	else{
		duration = pFormatCtx ->streams[videoindex]->duration;
	}
	if(strcmp("h264",pFormatCtx->iformat->name) == 0)duration = 0;
	(*env)->CallVoidMethod(env, obj, methodID_setProgressDuration,duration);

	pPacket=av_packet_alloc();
	av_init_packet(pPacket);
	// pPacketBSF=av_packet_alloc();
	// av_init_packet(pPacketBSF);
	
	pFrame=av_frame_alloc();
	pFrameYUV=av_frame_alloc();
	
	int buffer_size = av_image_alloc((uint8_t**)pFrameYUV->data, pFrameYUV->linesize, pCodecCtx->width, pCodecCtx->height, AV_PIX_FMT_YUV420P, 1);
	
	// img_convert_ctx = sws_getContext(pCodecCtx->width, pCodecCtx->height, pCodecCtx->pix_fmt,
									// pCodecCtx->width, pCodecCtx->height, AV_PIX_FMT_YUV420P, 
									// SWS_BICUBIC, NULL, NULL, NULL);


	bsfc = av_bitstream_filter_init("h264_mp4toannexb");
	
	AVDictionaryEntry *tag = NULL;
	tag = av_dict_get(pFormatCtx ->streams[videoindex]->metadata,"rotate",tag,0);
	if(tag == NULL){
		angle = 0;
	}
	else{
		angle = atoi(tag->value);
		angle %= 360;
	}
	if(rotate != 0){
		angle = rotate;
	}
	LOGI("视频旋转了 %d 度~~~~",angle);
	
	zoom=0;
	updateSdlRect(zoom);
	
	delay = (int)(1000.0/av_q2d(pFormatCtx->streams[videoindex]->r_frame_rate));
	LOGI("delay: %d",delay);
	
	if(pixel_w > pixel_h && angle == 0){
		(*env)->CallVoidMethod(env, obj, methodID_initOrientation);
	}
	(*env)->CallVoidMethod(env, obj, methodID_updateParameter,pixel_w,pixel_h,angle);
//------------------------Mediacodec init------------------------		
#if MEDIACODEC_ANDROID
	MediacodecContext mediacodecContext;

	mediacodecContext.dec_buffer_array = (*env)->NewByteArray(env, MEDIACODEC_DEC_BUFFER_ARRAY_SIZE);
	mediacodecContext.dec_yuv_array = (*env)->NewByteArray(env, MEDIACODEC_DEC_YUV_ARRAY_SIZE);

	mediacodecContext.class_Codec = (*env)->FindClass(env, "com/example/ffmpegdecoder/mediacodec/Codec");
	mediacodecContext.class_HH264Decoder = (*env)->FindClass(env, "com/example/ffmpegdecoder/mediacodec/HH264Decoder");
	mediacodecContext.class_H264Utils = (*env)->FindClass(env, "com/example/ffmpegdecoder/mediacodec/H264Utils");
	mediacodecContext.class_Integer = (*env)->FindClass(env, "java/lang/Integer");
	mediacodecContext.class_MediaFormat = (*env)->FindClass(env,"android/media/MediaFormat");
	mediacodecContext.class_CodecCapabilities = (*env)->FindClass(env,"android/media/MediaCodecInfo$CodecCapabilities");

	mediacodecContext.methodID_HH264Decoder_constructor = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"<init>","()V");
	mediacodecContext.methodID_HH264Decoder_config = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"config","(Ljava/lang/String;Ljava/lang/Object;)V");
	mediacodecContext.methodID_HH264Decoder_getConfig = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"getConfig","(Ljava/lang/String;)Ljava/lang/Object;");
	mediacodecContext.methodID_HH264Decoder_open = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"open","()V");
	mediacodecContext.methodID_HH264Decoder_decode = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"decode","([BI[BI)I");
	mediacodecContext.methodID_HH264Decoder_getErrorCode = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"getErrorCode","()I");
	mediacodecContext.methodID_HH264Decoder_close = (*env)->GetMethodID(env,mediacodecContext.class_HH264Decoder,"close","()V");
	mediacodecContext.methodID_H264Utils_ffAvcFindStartcode = (*env)->GetStaticMethodID(env,mediacodecContext.class_H264Utils,"ffAvcFindStartcode","([BII)I");
	mediacodecContext.methodID_Integer_intValue = (*env)->GetMethodID(env,mediacodecContext.class_Integer,"intValue","()I");
	mediacodecContext.fieldID_Codec_ERROR_CODE_INPUT_BUFFER_FAILURE = (*env)->GetStaticFieldID(env, mediacodecContext.class_Codec, "ERROR_CODE_INPUT_BUFFER_FAILURE", "I");
	mediacodecContext.fieldID_MediaFormat_KEY_WIDTH = (*env)->GetStaticFieldID(env, mediacodecContext.class_MediaFormat, "KEY_WIDTH", "Ljava/lang/String;");
	mediacodecContext.fieldID_MediaFormat_KEY_HEIGHT = (*env)->GetStaticFieldID(env, mediacodecContext.class_MediaFormat, "KEY_HEIGHT", "Ljava/lang/String;");
	mediacodecContext.fieldID_MediaFormat_KEY_COLOR_FORMAT = (*env)->GetStaticFieldID(env, mediacodecContext.class_MediaFormat, "KEY_COLOR_FORMAT", "Ljava/lang/String;");
	mediacodecContext.fieldID_CodecCapabilities_COLOR_FormatYUV420Planar = (*env)->GetStaticFieldID(env,  mediacodecContext.class_CodecCapabilities, "COLOR_FormatYUV420Planar", "I");
	mediacodecContext.fieldID_CodecCapabilities_COLOR_FormatYUV420SemiPlanar = (*env)->GetStaticFieldID(env,  mediacodecContext.class_CodecCapabilities, "COLOR_FormatYUV420SemiPlanar", "I");

	mediacodecContext.ERROR_CODE_INPUT_BUFFER_FAILURE = (*env)->GetStaticIntField(env, mediacodecContext.class_Codec, mediacodecContext.fieldID_Codec_ERROR_CODE_INPUT_BUFFER_FAILURE);
	mediacodecContext.KEY_WIDTH = (*env)->GetStaticObjectField(env, mediacodecContext.class_MediaFormat, mediacodecContext.fieldID_MediaFormat_KEY_WIDTH);
	mediacodecContext.KEY_HEIGHT = (*env)->GetStaticObjectField(env, mediacodecContext.class_MediaFormat, mediacodecContext.fieldID_MediaFormat_KEY_HEIGHT);
	mediacodecContext.KEY_COLOR_FORMAT = (*env)->GetStaticObjectField(env, mediacodecContext.class_MediaFormat, mediacodecContext.fieldID_MediaFormat_KEY_COLOR_FORMAT);
	mediacodecContext.COLOR_FormatYUV420Planar = (*env)->GetStaticIntField(env, mediacodecContext.class_CodecCapabilities, mediacodecContext.fieldID_CodecCapabilities_COLOR_FormatYUV420Planar);
	mediacodecContext.COLOR_FormatYUV420SemiPlanar = (*env)->GetStaticIntField(env, mediacodecContext.class_CodecCapabilities, mediacodecContext.fieldID_CodecCapabilities_COLOR_FormatYUV420SemiPlanar);

	mediacodecContext.object_decoder = (*env)->NewObject(env, mediacodecContext.class_HH264Decoder, mediacodecContext.methodID_HH264Decoder_constructor);
	(*env)->CallVoidMethod(env, mediacodecContext.object_decoder, mediacodecContext.methodID_HH264Decoder_open);
#else		
	
	MediaCodecDecoder* mediacodec_decoder = mediacodec_decoder_alloc3();
	mediacodec_decoder_open(mediacodec_decoder);
#endif	
//--------------------------render_thread------------------------------
	RenderContext renderContext;
	renderContext.methodID_updateData = methodID_updateData;
	renderContext.gs_obj=(*env)->NewGlobalRef(env,obj);
	
	renderContext.buffer_size = buffer_size;
	renderContext.buffer = (uint8*)malloc(buffer_size);
	
	renderContext.frameSize = pixel_w * pixel_h;
	renderContext.qFrameSize = (buffer_size - renderContext.frameSize) / 2;
	pthread_t render_thread;
	pthread_create(&render_thread, NULL, thread_render_opengles, &renderContext);

	struct timeval old = {0},new = {0};
	
	LOGI("LOOP thread_exit = %d , thread_back = %d",thread_exit,thread_back);
	while(!thread_exit && !thread_back){
		if(old.tv_sec != 0 && old.tv_usec != 0){
			gettimeofday(&new,NULL);
			while(new.tv_sec * 1000000 + new.tv_usec - old.tv_sec * 1000000 - old.tv_usec < delay * 1000){
				usleep(500);
				gettimeofday(&new,NULL);
			}
			old.tv_sec = new.tv_sec;
			old.tv_usec = new.tv_usec;
		}
		else{
			gettimeofday(&old,NULL);
		}
		
		if (thread_forward == -1)
		{
			long long skip_dts = current_dts + (long long)((double)skipFrame / av_q2d(pFormatCtx ->streams[videoindex]->time_base));
			av_seek_frame(pFormatCtx,videoindex,skip_dts,AVSEEK_FLAG_BACKWARD);
			LOGI("%lld######11#####%lld",current_dts,skip_dts);
		}
		if (thread_forward == 1)
		{
			forward_dts = current_dts;
			long long skip_dts = current_dts + (long long)((double)skipFrame / av_q2d(pFormatCtx ->streams[videoindex]->time_base));
			thread_forward = -1;
			LOGI("%lld######22#####%lld",current_dts,skip_dts);
			av_seek_frame(pFormatCtx,videoindex,skip_dts,AVSEEK_FLAG_BACKWARD);

			if(skip_dts >= duration){
				av_seek_frame(pFormatCtx,videoindex,skip_dts,AVSEEK_FLAG_ANY);
				thread_forward = -2;
			}
		}
		if (thread_backward == 1)
		{
			long long skip_dts = current_dts + (long long)((double)skipFrame / av_q2d(pFormatCtx ->streams[videoindex]->time_base));
			av_seek_frame(pFormatCtx,videoindex,skip_dts,AVSEEK_FLAG_BACKWARD);
			thread_backward = 0;
		}
		if(thread_Seek == 1)
		{
			LOGI("%lld######33#####",seekFrame);
			av_seek_frame(pFormatCtx,videoindex,seekFrame,AVSEEK_FLAG_BACKWARD);
			thread_Seek = 0;
		}
		
		if(thread_pause == 0 || thread_pause == -1 || thread_pause == -2){
			while (1)
			{
				if(av_read_frame(pFormatCtx, pPacket)>=0)
				{
					if(pPacket->stream_index==videoindex){
						frameConut++;
						current_dts = pPacket->dts;
						break;
					}
					else{
						av_packet_unref(pPacket);
					}
				}
				else
				{
					if(!isStreamMedia){
						//Exit Thread
						thread_exit=1;
						break;
					}
					else{
						LOGI("thread_back %d",thread_back);
						if(thread_back==1){
							goto on_error;
						}
						(*env)->CallVoidMethod(env, obj, methodID_showLoading);
					
						// sws_freeContext(img_convert_ctx);
						// av_bitstream_filter_close(bsfc);
						avformat_close_input(&pFormatCtx);
						avcodec_close(pCodecCtx);
						av_dict_free(&options);
						
						pFormatCtx = avformat_alloc_context();
						pFormatCtx->interrupt_callback.callback = CheckInterrupt;//超时回调
						pFormatCtx->interrupt_callback.opaque = NULL;

						options = NULL;
						av_dict_set(&options,"rtsp_transport","tcp",0);
						av_dict_set(&options,"stimeout","5000000",0);//超时5秒
						
						while(avformat_open_input(&pFormatCtx,url_str,NULL,&options)!=0)
						{
							usleep(5000);
							LOGE("[LOOP]Couldn't open input stream.\n");
							if(thread_back==1)
							{
								error_code = -1;
								goto on_error;
							}
						}
						if(avformat_find_stream_info(pFormatCtx,NULL)<0)
						{
							LOGE("[LOOP]Couldn't find stream information.\n");
							error_code = -2;
							goto on_error;
						}

						videoindex=av_find_best_stream(pFormatCtx,AVMEDIA_TYPE_VIDEO,-1,-1,NULL,0);
						if(videoindex==-1){
							LOGE("[LOOP]Didn't find a video stream.\n");
							error_code = -3;
							goto on_error;
						}
						
						pCodec=avcodec_find_decoder(pFormatCtx->streams[videoindex]->codecpar->codec_id);
						if(pCodec==NULL){
							LOGE("[LOOP]Codec not found.\n");
							error_code = -4;
							goto on_error;
						}
						pCodecCtx=avcodec_alloc_context3(pCodec);
						avcodec_parameters_to_context(pCodecCtx,pFormatCtx->streams[videoindex]->codecpar);

						if(avcodec_open2(pCodecCtx, pCodec,NULL)<0){
							LOGE("[LOOP]Could not open codec.\n");
							error_code = -5;
							goto on_error;
						}
						(*env)->CallVoidMethod(env, obj, methodID_hideLoading);
						
						// img_convert_ctx = sws_getContext(pCodecCtx->width, pCodecCtx->height, pCodecCtx->pix_fmt,
								// pCodecCtx->width, pCodecCtx->height, AV_PIX_FMT_YUV420P, 
								// SWS_BICUBIC, NULL, NULL, NULL);
						// bsfc = av_bitstream_filter_init("h264_mp4toannexb");
					}
				}
			}
			
			if(strcmp("h264", pCodec->name) && thread_codec_type == 1){//非h264编码 且 选择硬件解码
				(*env)->CallVoidMethod(env, obj, methodID_changeCodec, (*env)->NewStringUTF(env,pCodec->name));
				thread_codec_type = 0;
			}
			
			if(thread_codec_type == 0){
				renderContext.pixformat = FOURCC_I420;
				
				ret = avcodec_send_packet(pCodecCtx, pPacket);

				if(ret < 0)
				{
					LOGE("Decode Error.\n");
					av_packet_unref(pPacket);
					error_code = -7;
					continue;
				}
				while(avcodec_receive_frame(pCodecCtx, pFrame) == 0)
				{	
					// LOGI("pFrame->linesize=%d %d %d",pFrame->linesize[0],pFrame->linesize[1],pFrame->linesize[2]);
					// LOGI("pFrame->width=%d pFrame->height=%d",pFrame->width,pFrame->height);
					// LOGI("pCodecCtx->width=%d pCodecCtx->height=%d",pCodecCtx->width,pCodecCtx->height);
					// sws_scale(img_convert_ctx, (const uint8_t**)pFrame->data, pFrame->linesize, 0, pCodecCtx->height, pFrameYUV->data, pFrameYUV->linesize);
					av_image_copy((uint8_t**)pFrameYUV->data,pFrameYUV->linesize,
								(const uint8_t**)pFrame->data,pFrame->linesize,
												pCodecCtx->pix_fmt,pCodecCtx->width,pCodecCtx->height);
					pFrameYUV->width = pFrame->width;
					pFrameYUV->height = pFrame->height;
					
					pthread_mutex_lock(&mut_render);
					if(!thread_render){
						memmove(renderContext.buffer, pFrameYUV->data[0], buffer_size);
						thread_render = 1;
						pthread_cond_signal(&cond_render);
					}
					else{
						LOGE("render thread run already");
					}
					pthread_mutex_unlock(&mut_render);

					if(thread_picture == 1){
						thread_picture = 0;
					}
				}
			}
			else if(thread_codec_type == 1){
#if MEDIACODEC_ANDROID
				renderContext.pixformat = FOURCC_I420;
#else
				renderContext.pixformat = FOURCC_NV12;
#endif
				
				AVPacket* filter_packet = av_packet_alloc();
				AVPacket* filtered_packet = av_packet_alloc();
				av_packet_ref(filter_packet, pPacket);
				
				if(filter_packet->size > 4){
					int nalu_type = *(filter_packet->data + 4) & 0x1F;
					LOGI("filter_packet[DTS:%lld]:nalu_first=%0X %0X %0X %0X %0X\t nalu_type=%0X", filter_packet->dts, *(filter_packet->data), *(filter_packet->data+1), *(filter_packet->data+2), *(filter_packet->data+3), *(filter_packet->data+4), nalu_type);
				}
				
				av_bitstream_filter_filter(bsfc, pCodecCtx, NULL, &filtered_packet->data, &filtered_packet->size, filter_packet->data, filter_packet->size, 0);
				
				if(filtered_packet->size > 4){
					int nalu_type = *(filtered_packet->data + 4) & 0x1F;
					LOGI("filtered_packet->[DTS:%lld]:nalu_first=%0X %0X %0X %0X %0X\t nalu_type=%0X", filtered_packet->dts, *(filtered_packet->data), *(filtered_packet->data+1), *(filtered_packet->data+2), *(filtered_packet->data+3), *(filtered_packet->data+4), nalu_type);
				}
				
				// {
					// static FILE * fin_rtsp = NULL;
					// if(!fin_rtsp)
					// {
						// fin_rtsp = fopen("/sdcard/rtsp.h264","wb");
					// }
					// if(fin_rtsp)
					// {
						// fwrite(whole_frame, 1, whole_frame_len, fin_rtsp);
						// fflush(fin_rtsp);
					// }
				// }
				do{
#if MEDIACODEC_ANDROID
                    mediacodec_decode_video(env, &mediacodecContext, filtered_packet, pFrameYUV, &got_picture);
#else
				    mediacodec_decode_video2(mediacodec_decoder, filtered_packet, pFrameYUV, &got_picture, &ret);
#endif
                    if(got_picture)
                    {
                        pthread_mutex_lock(&mut_render);
                        if(!thread_render){
                            memmove(renderContext.buffer, pFrameYUV->data[0], buffer_size);
                            thread_render = 1;
                            pthread_cond_signal(&cond_render);
                        }
                        else{
                            LOGE("render thread run already");
                        }
                        pthread_mutex_unlock(&mut_render);

                        if(thread_picture == 1){

                        }
                    }
				}
				while(ret == -3);

				av_packet_unref(filter_packet);
				av_free(filter_packet->data);
				av_free(filtered_packet->data);//ffmpeg BUG:bsf过滤后需要手动释放packet->data
				av_packet_free(&filter_packet);
				av_packet_free(&filtered_packet);
			}
			av_packet_unref(pPacket);
			
			if(thread_pause == -2){
				thread_pause = 1;
			}
			if(thread_pause == -1 && !is_Locating_I){
				thread_pause = -2;
			}
		}
		
		if(!thread_exit){
			// LOGI("%d================%d",thread_pause,is_Locating_I);
			if(is_Locating_I == 1){
				if(thread_forward == -2){
					(*env)->CallVoidMethod(env, obj, methodID_showIFrameDTS, current_dts,-1);
					thread_forward = 0;
					is_Locating_I = 0;
				}
				else if(thread_forward == -1){
					if(current_dts < forward_dts){//快进后dts比之前dts还小
						forwardOffset = 1;
						(*env)->CallVoidMethod(env, obj, methodID_showIFrameDTS, current_dts,forwardOffset);
					}
					else if(current_dts == forward_dts){//快进后dts等于之前dts
						forwardOffset++;
						(*env)->CallVoidMethod(env, obj, methodID_showIFrameDTS, current_dts,forwardOffset);
					}
					else{//快进后dts比之前dts大，结束快进流程，正常播放
						(*env)->CallVoidMethod(env, obj, methodID_showIFrameDTS, current_dts,0);
						thread_forward = 0;
						is_Locating_I = 0;
					}
				}
				else{//快退流程
					(*env)->CallVoidMethod(env, obj, methodID_showIFrameDTS, current_dts,0);
					is_Locating_I = 0;
				};
			}

			// LOGI("正在解码第%05lld帧~~~~",frameConut);
			if(frameConut == 1){
				(*env)->CallVoidMethod(env, obj, methodID_hideLoading);
			}
			
			if(!strcmp("h264",pFormatCtx->iformat->name)){
				(*env)->CallVoidMethod(env, obj, methodID_setProgressDTS,frameConut);
			}
			else{
				(*env)->CallVoidMethod(env, obj, methodID_setProgressDTS,current_dts);
			}
		}
		else{
			thread_forward = 0;
			is_Locating_I = 0;
		}
	}
	
	if(thread_exit==1)
	{
		(*env)->CallVoidMethod(env, obj, methodID_setProgressRateFull);
	}
	
on_error:
	LOGE("error_code = %d",error_code);
	LOGI("Destory thread_exit = %d , thread_back = %d",thread_exit,thread_back);
	if(render_thread && (error_code == 0 || error_code < -6)){
		pthread_mutex_lock(&mut_render);
		LOGI("Destory thread_render = %d",thread_render);
		if(!thread_render){
			thread_render = 1;
			pthread_cond_signal(&cond_render);
		}
		pthread_mutex_unlock(&mut_render);
		
		pthread_join(render_thread,NULL);
		LOGI("join finished");
	}

	if(pFrameYUV && (error_code == 0 || error_code < -6)){
		av_frame_free(&pFrameYUV);
	}
	if(pFrame && (error_code == 0 || error_code < -6)){
		av_frame_free(&pFrame);
	}
	if(pPacket && (error_code == 0 || error_code < -6)){
		av_packet_free(&pPacket);
	}
	if(options && (error_code == 0 || error_code < -6)){
		av_dict_free(&options);
	}

	// if(img_convert_ctx && (error_code == 0 || error_code < -6)){
		// sws_freeContext(img_convert_ctx);
	// }
	if(bsfc && (error_code == 0 || error_code < -6)){
		av_bitstream_filter_close(bsfc);
	}

	if(pCodecCtx && (error_code == 0 || error_code < -4)){
		avcodec_close(pCodecCtx);
	}

	if(pFormatCtx && (error_code == 0 || error_code < -1)){
		avformat_close_input(&pFormatCtx);
	}
#if MEDIACODEC_ANDROID		
	if(error_code == 0 || error_code < -6){
		(*env)->DeleteLocalRef(env, mediacodecContext.dec_yuv_array);
		(*env)->DeleteLocalRef(env, mediacodecContext.dec_buffer_array);

		(*env)->DeleteLocalRef(env, mediacodecContext.class_Codec);
		(*env)->DeleteLocalRef(env, mediacodecContext.class_HH264Decoder);
		(*env)->DeleteLocalRef(env, mediacodecContext.class_H264Utils);
		(*env)->DeleteLocalRef(env, mediacodecContext.class_Integer);
		(*env)->DeleteLocalRef(env, mediacodecContext.class_MediaFormat);
		(*env)->DeleteLocalRef(env, mediacodecContext.class_CodecCapabilities);

		(*env)->CallVoidMethod(env, mediacodecContext.object_decoder, mediacodecContext.methodID_HH264Decoder_close);
		(*env)->DeleteLocalRef(env, mediacodecContext.object_decoder);
		(*env)->DeleteLocalRef(env, mediacodecContext.KEY_WIDTH);
		(*env)->DeleteLocalRef(env, mediacodecContext.KEY_HEIGHT);
		(*env)->DeleteLocalRef(env, mediacodecContext.KEY_COLOR_FORMAT);
	}
#else
	if(mediacodec_decoder && (error_code == 0 || error_code < -6)){
		int status = mediacodec_decoder_close(mediacodec_decoder);
		LOGI("mediacodec_decoder_close status=%d",status);
		mediacodec_decoder_free(mediacodec_decoder);
	}
#endif
	if(class_render){
		(*env)->DeleteLocalRef(env, class_render);
	}
	if(renderContext.gs_obj && (error_code == 0 || error_code < -6)){
		(*env)->DeleteGlobalRef(env, renderContext.gs_obj);
	}
	if(renderContext.buffer && (error_code == 0 || error_code < -6)){
		free(renderContext.buffer);
	}
	
	return error_code;
}


void mediacodec_decode_video(JNIEnv* env, MediacodecContext* mediacodecContext, AVPacket *pPacket, AVFrame *pFrame, int *got_picture){
	uint8_t *in,*out;
	int in_len = pPacket->size;
	int out_len = 0;
	in = pPacket->data;
	out = pFrame->data[0];
	int repeat_count = 0;
	jint jindex,
		 jyuv_len,
		 jerror_code,
		 jyuv_wdith,
		 jyuv_height,
		 jyuv_pixel;
		 
	(*env)->SetByteArrayRegion(env, mediacodecContext->dec_buffer_array, 0, in_len, (jbyte*)in);
		 
	while(1){
		jyuv_len = (*env)->CallIntMethod(env,mediacodecContext->object_decoder,mediacodecContext->methodID_HH264Decoder_decode,mediacodecContext->dec_buffer_array,0,mediacodecContext->dec_yuv_array,in_len);
					
		jerror_code = (*env)->CallIntMethod(env,mediacodecContext->object_decoder,mediacodecContext->methodID_HH264Decoder_getErrorCode);
		LOGI("yuv_len:%6d\t error_code:%6d", jyuv_len,jerror_code);
		
		if(jerror_code == mediacodecContext->ERROR_CODE_INPUT_BUFFER_FAILURE){
			if(repeat_count < 5){
				repeat_count++;
				usleep(1000);
				continue;
			}
			else{
				repeat_count = 0;
			}
		}
		
		if(jyuv_len > 0){
			jobject yuv_wdith = (*env)->CallObjectMethod(env, mediacodecContext->object_decoder, mediacodecContext->methodID_HH264Decoder_getConfig, mediacodecContext->KEY_WIDTH);
			jobject yuv_height = (*env)->CallObjectMethod(env, mediacodecContext->object_decoder, mediacodecContext->methodID_HH264Decoder_getConfig, mediacodecContext->KEY_HEIGHT);
			jobject yuv_pixel = (*env)->CallObjectMethod(env, mediacodecContext->object_decoder, mediacodecContext->methodID_HH264Decoder_getConfig, mediacodecContext->KEY_COLOR_FORMAT);
			jyuv_wdith = (*env)->CallIntMethod(env, yuv_wdith, mediacodecContext->methodID_Integer_intValue);
			jyuv_height = (*env)->CallIntMethod(env, yuv_height, mediacodecContext->methodID_Integer_intValue);
			jyuv_pixel = (*env)->CallIntMethod(env, yuv_pixel, mediacodecContext->methodID_Integer_intValue);
			LOGI("W x H : %d x %d\t yuv_pixel:%6d", jyuv_wdith,jyuv_height,jyuv_pixel);
			
			(*env)->DeleteLocalRef(env,yuv_wdith);
			(*env)->DeleteLocalRef(env,yuv_height);
			(*env)->DeleteLocalRef(env,yuv_pixel);
			
			(*env)->GetByteArrayRegion(env, mediacodecContext->dec_yuv_array, 0, jyuv_len, (jbyte*)out);
			out_len = jyuv_len;
		}
		break;
	}
	
	if(out_len > 0){
		*got_picture = 1;
	}
	else{
		*got_picture = 0;
	}
}

void mediacodec_decode_video2(MediaCodecDecoder* decoder, AVPacket *pPacket, AVFrame *pFrame, int *got_picture, int *error_code){
	uint8_t *in,*out;
	int in_len = pPacket->size;
	int out_len = 0;
	in = pPacket->data;
	out = pFrame->data[0];
	int repeat_count = 0;
	int jindex,
		jyuv_wdith,
		jyuv_height,
		jyuv_pixel;

    out_len = mediacodec_decoder_decode(decoder, in, 0, out, in_len, error_code);
    LOGI("yuv_len:%6d\t error_code:%6d", out_len, *error_code);
    if(out_len > 0){
        jyuv_wdith = mediacodec_decoder_getConfig_int(decoder, "width");
        jyuv_height = mediacodec_decoder_getConfig_int(decoder, "height");;
        jyuv_pixel = mediacodec_decoder_getConfig_int(decoder, "color-format");
        LOGI("W x H : %d x %d\t yuv_pixel:%6d", jyuv_wdith,jyuv_height,jyuv_pixel);

        pFrame->width = jyuv_wdith;
        pFrame->height = jyuv_height;
    }

    if(*error_code <= -10000){
        LOGE("硬件编解码器损坏，请更换编解码器");
        thread_codec_type = 0;
    }

	if(out_len > 0){
		*got_picture = 1;
	}
	else{
		*got_picture = 0;
	}
}

jint JNI_OnLoad(JavaVM* vm, void* reserved){
	gs_jvm = vm;
	LOGI("JNI_OnLoad");
	return JNI_VERSION_1_4;
}