
#include <jni.h>
#include <fluidsynth.h>
#include <mutex>
#include <string>
#include <vector>

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

    if (gSettings == nullptr) return JNI_FALSE;

    fluid_settings_setnum(gSettings, "synth.sample-rate", sampleRate);
    fluid_settings_setnum(gSettings, "synth.gain", 0.8);
    fluid_settings_setint(gSettings, "synth.polyphony", 256);

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
    if (path == nullptr) return -1;

    const char* filePath = env->GetStringUTFChars(path, nullptr);
    if (filePath == nullptr) return -1;

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

    if (gSynth == nullptr || gSoundFontId < 0) return JNI_FALSE;

    return fluid_synth_noteon(gSynth, channel, key, velocity)
        == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeNoteOff(
        JNIEnv* env, jobject thiz, jint channel, jint key) {
    std::lock_guard<std::mutex> lock(gMutex);

    if (gSynth == nullptr) return JNI_FALSE;

    return fluid_synth_noteoff(gSynth, channel, key)
        == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeRenderAudio(
        JNIEnv* env, jobject thiz,
        jshortArray output, jint frames) {
    if (output == nullptr || frames <= 0) return JNI_FALSE;
    if (env->GetArrayLength(output) < frames * 2) return JNI_FALSE;

    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (samples == nullptr) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(gMutex);

    bool success = false;

    if (gSynth != nullptr) {
        success = fluid_synth_write_s16(
            gSynth, frames,
            samples, 0, 2,
            samples, 1, 2
        ) == FLUID_OK;
    }

    env->ReleaseShortArrayElements(output, samples, 0);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_samplers_android_MainActivity_nativeGetPresets(
        JNIEnv* env, jobject thiz) {

    std::vector<std::string> presets;

    {
        std::lock_guard<std::mutex> lock(gMutex);

        if (gSynth == nullptr || gSoundFontId < 0) {
            return env->NewObjectArray(
                0, env->FindClass("java/lang/String"), nullptr
            );
        }

        fluid_sfont_t* soundFont =
            fluid_synth_get_sfont_by_id(gSynth, gSoundFontId);

        if (soundFont == nullptr) {
            return env->NewObjectArray(
                0, env->FindClass("java/lang/String"), nullptr
            );
        }

        fluid_sfont_iteration_start(soundFont);

        fluid_preset_t* preset;

        while ((preset = fluid_sfont_iteration_next(soundFont)) != nullptr) {
            int bank = fluid_preset_get_banknum(preset);
            int program = fluid_preset_get_num(preset);
            const char* name = fluid_preset_get_name(preset);

            presets.push_back(
                std::to_string(bank) + "|" +
                std::to_string(program) + "|" +
                (name != nullptr ? name : "Instrumento")
            );
        }
    }

    jclass stringClass = env->FindClass("java/lang/String");

    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(presets.size()),
        stringClass,
        nullptr
    );

    for (size_t i = 0; i < presets.size(); i++) {
        jstring item = env->NewStringUTF(presets[i].c_str());
        env->SetObjectArrayElement(result, i, item);
        env->DeleteLocalRef(item);
    }

    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_samplers_android_MainActivity_nativeSelectPreset(
        JNIEnv* env, jobject thiz,
        jint channel, jint bank, jint program) {

    std::lock_guard<std::mutex> lock(gMutex);

    if (gSynth == nullptr || gSoundFontId < 0) return JNI_FALSE;

    int result = fluid_synth_program_select(
        gSynth, channel, gSoundFontId, bank, program
    );

    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
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
