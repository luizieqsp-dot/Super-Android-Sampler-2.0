
#include <jni.h>
#include <fluidsynth.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_samplers_android_MainActivity_nativeFluidSynthVersion(
        JNIEnv* env,
        jobject /* thiz */) {

    const char* version = fluid_version_str();

    return env->NewStringUTF(version);
}
