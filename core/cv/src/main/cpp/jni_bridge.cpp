// JNI entry points for com.nevonai.smartshape.cv.NativeCv. Results are returned as flat float
// arrays to avoid constructing JVM objects per point; NativeCv.kt documents the layouts.

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <opencv2/core/version.hpp>
#include <opencv2/imgproc.hpp>

#include "core/image_quality.hpp"
#include "core/marker_detector.hpp"
#include "core/shape_detector.hpp"

#define LOG_TAG "SmartShapeCv"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using smartshape::Marker;
using smartshape::Shape;

bool bitmapToGray(JNIEnv *env, jobject bitmap, cv::Mat &gray) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format %d (need ARGB_8888)", info.format);
        return false;
    }
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
    const cv::Mat rgba(static_cast<int>(info.height), static_cast<int>(info.width), CV_8UC4, pixels, info.stride);
    cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// Wraps a camera Y plane without copying. The buffer must stay valid while the Mat is used.
bool bufferToGray(JNIEnv *env, jobject buffer, jint width, jint height, jint rowStride, cv::Mat &gray) {
    auto *data = static_cast<uchar *>(env->GetDirectBufferAddress(buffer));
    if (data == nullptr) return false;
    gray = cv::Mat(height, width, CV_8UC1, data, static_cast<size_t>(rowStride));
    return true;
}

jfloatArray toArray(JNIEnv *env, const std::vector<float> &v) {
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (out != nullptr && !v.empty()) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(v.size()), v.data());
    return out;
}

jfloatArray encodeShapes(JNIEnv *env, const std::vector<Shape> &shapes) {
    std::vector<float> v{static_cast<float>(shapes.size())};
    for (const auto &s : shapes) {
        v.push_back(static_cast<float>(static_cast<int>(s.type)));
        v.push_back(s.confidence);
        v.push_back(static_cast<float>(s.points.size()));
        for (const auto &p : s.points) {
            v.push_back(p.x);
            v.push_back(p.y);
        }
    }
    return toArray(env, v);
}

jfloatArray encodeMarkers(JNIEnv *env, const std::vector<Marker> &markers) {
    std::vector<float> v{static_cast<float>(markers.size())};
    for (const auto &m : markers) {
        v.push_back(static_cast<float>(m.id));
        for (const auto &p : m.corners) {
            v.push_back(p.x);
            v.push_back(p.y);
        }
    }
    return toArray(env, v);
}

smartshape::DetectorParams params(jint maxDim, jboolean refine) {
    smartshape::DetectorParams p;
    p.maxDim = maxDim;
    p.refineCorners = refine;
    return p;
}

template <typename F>
jfloatArray guarded(JNIEnv *env, F &&f) {
    try {
        return f();
    } catch (const cv::Exception &e) {
        LOGE("OpenCV error: %s", e.what());
    } catch (const std::exception &e) {
        LOGE("Native error: %s", e.what());
    }
    return toArray(env, {0.0f});
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_nevonai_smartshape_cv_NativeCv_openCvVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF(CV_VERSION);
}

JNIEXPORT jfloatArray JNICALL
Java_com_nevonai_smartshape_cv_NativeCv_detectShapesBitmap(JNIEnv *env, jobject, jobject bitmap, jint maxDim,
                                                          jboolean refine) {
    return guarded(env, [&] {
        cv::Mat gray;
        if (!bitmapToGray(env, bitmap, gray)) return toArray(env, {0.0f});
        return encodeShapes(env, smartshape::detectShapes(gray, params(maxDim, refine)));
    });
}

JNIEXPORT jfloatArray JNICALL
Java_com_nevonai_smartshape_cv_NativeCv_detectShapesLuma(JNIEnv *env, jobject, jobject buffer, jint width, jint height,
                                                        jint rowStride, jint maxDim) {
    return guarded(env, [&] {
        cv::Mat gray;
        if (!bufferToGray(env, buffer, width, height, rowStride, gray)) return toArray(env, {0.0f});
        return encodeShapes(env, smartshape::detectShapes(gray, params(maxDim, JNI_FALSE)));
    });
}

JNIEXPORT jfloatArray JNICALL
Java_com_nevonai_smartshape_cv_NativeCv_detectMarkersBitmap(JNIEnv *env, jobject, jobject bitmap) {
    return guarded(env, [&] {
        cv::Mat gray;
        if (!bitmapToGray(env, bitmap, gray)) return toArray(env, {0.0f});
        return encodeMarkers(env, smartshape::detectMarkers(gray));
    });
}

JNIEXPORT jfloatArray JNICALL
Java_com_nevonai_smartshape_cv_NativeCv_qualityLuma(JNIEnv *env, jobject, jobject buffer, jint width, jint height,
                                                   jint rowStride) {
    return guarded(env, [&] {
        cv::Mat gray;
        if (!bufferToGray(env, buffer, width, height, rowStride, gray)) return toArray(env, {0.0f, 0.0f});
        const auto q = smartshape::measureQuality(gray);
        return toArray(env, {static_cast<float>(q.sharpness), static_cast<float>(q.brightness)});
    });
}

}  // extern "C"
