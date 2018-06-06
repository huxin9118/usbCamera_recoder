LOCAL_PATH := $(call my-dir)

###########################
#
# FFmpeg shared library
#
###########################
# FFmpeg library
include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg
LOCAL_SRC_FILES := libffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)

###########################
#
# libyuv shared library
#
###########################
# libyuv library
include $(CLEAR_VARS)
LOCAL_MODULE := yuv
LOCAL_SRC_FILES := libyuv.so
include $(PREBUILT_SHARED_LIBRARY)

###########################
#
# mediacodec ndk shared library
#
###########################

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec16
LOCAL_SRC_FILES:= libnative_codec16.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec17
LOCAL_SRC_FILES:= libnative_codec17.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec18
LOCAL_SRC_FILES:= libnative_codec18.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec19
LOCAL_SRC_FILES:= libnative_codec19.so
include $(PREBUILT_SHARED_LIBRARY)

###########################
#
# GL Render ndk shared library
#
###########################
include $(CLEAR_VARS)
LOCAL_MODULE := render
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := simplest_ffmpeg_sdl_player.c \
				mediacodec_decoder.c \
				mediacodec_utils.c \
				NativeCodec.cpp
LOCAL_SHARED_LIBRARIES := ffmpeg yuv
LOCAL_LDLIBS := -lGLESv1_CM -lGLESv2 -llog
include $(BUILD_SHARED_LIBRARY)