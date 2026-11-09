
#include <jni.h>
#include <fluidsynth.h>
#include <mutex>

static fluid_settings_t* gSettings = nullptr;
static fluid_synth_t* gSynth = nullptr;
static int gSoundFontId = -1;

static std::mutex gMutex;

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_samplers_android_MainActivity_nativeFluidSynthVersion(
        JNIEnv* env, jobject thiz) {

    return env->NewStringUTF(fluid_version_str());
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeInit(
        JNIEnv* env, jobject thiz, jint sampleRate) {

    std::lock_guard<std::mutex> lock(gMutex);

    if (gSynth != nullptr) {
        delete_fluid_synth(gSynth);
        gSynth = nullptr;
    }

    if (gSettings != nullptr) {
        delete_fluid_settings(gSettings);
        gSettings = nullptr;
    }

    gSoundFontId = -1;

    gSettings = new_fluid_settings();

    if (gSettings == nullptr) {
        return JNI_FALSE;
    }

    fluid_settings_setnum(
        gSettings, "synth.sample-rate", sampleRate
    );

    fluid_settings_setnum(
        gSettings, "synth.gain", 0.8
    );

    fluid_settings_setint(
        gSettings, "synth.polyphony", 256
    );

    gSynth = new_fluid_synth(gSettings);

    if (gSynth == nullptr) {
        delete_fluid_settings(gSettings);
        gSettings = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_samplers_android_MainActivity_nativeLoadSoundFont(
        JNIEnv* env, jobject thiz, jstring path) {

    if (path == nullptr) {
        return -1;
    }

    const char* filePath = env->GetStringUTFChars(path, nullptr);

    if (filePath == nullptr) {
        return -1;
    }

    std::lock_guard<std::mutex> lock(gMutex);

    int result = -1;

    if (gSynth != nullptr) {
        result = fluid_synth_sfload(gSynth, filePath, 1);
        gSoundFontId = result;
    }

    env->ReleaseStringUTFChars(path, filePath);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeNoteOn(
        JNIEnv* env, jobject thiz,
        jint channel, jint key, jint velocity) {

    std::lock_guard<std::mutex> lock(gMutex);

    if (gSynth == nullptr || gSoundFontId < 0) {
        return JNI_FALSE;
    }

    return fluid_synth_noteon(
        gSynth, channel, key, velocity
    ) == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeNoteOff(
        JNIEnv* env, jobject thiz,
        jint channel, jint key) {

    std::lock_guard<std::mutex> lock(gMutex);

    if (gSynth == nullptr) {
        return JNI_FALSE;
    }

    return fluid_synth_noteoff(
        gSynth, channel, key
    ) == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeRenderAudio(
        JNIEnv* env, jobject thiz,
        jshortArray output, jint frames) {

    if (output == nullptr || frames <= 0) {
        return JNI_FALSE;
    }

    if (env->GetArrayLength(output) < frames * 2) {
        return JNI_FALSE;
    }

    jshort* samples = env->GetShortArrayElements(output, nullptr);

    if (samples == nullptr) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(gMutex);

    bool success = false;

    if (gSynth != nullptr) {
        success = fluid_synth_write_s16(
            gSynth,
            frames,
            samples, 0, 2,
            samples, 1, 2
        ) == FLUID_OK;
    }

    env->ReleaseShortArrayElements(
        output, samples, 0
    );

    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_samplers_android_MainActivity_nativeClose(
        JNIEnv* env, jobject thiz) {

    std::lock_guard<std::mutex> lock(gMutex);

    if (gSynth != nullptr) {
        delete_fluid_synth(gSynth);
        gSynth = nullptr;
    }

    if (gSettings != nullptr) {
        delete_fluid_settings(gSettings);
        gSettings = nullptr;
    }

    gSoundFontId = -1;
}

}
