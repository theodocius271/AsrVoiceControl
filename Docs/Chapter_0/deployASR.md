# Deploying sherpa-onnx ASR on Android from Java

This guide is based on the Android, Java, Kotlin API, JNI, and APK build files in this checkout of `sherpa-onnx`. The checkout currently uses sherpa-onnx version `1.13.3` in the Android demos.

The practical recommendation is:

1. Use the Android AAR dependency, not the desktop `sherpa-onnx/java-api` Maven/JAR module.
2. Put the ASR model files under `app/src/main/assets/`.
3. In Java, call the compiled Android API classes under `com.k2fsa.sherpa.onnx`.
4. Use `OnlineRecognizer` for true streaming microphone ASR.
5. Use `Vad` + `OfflineRecognizer` when you want VAD-segmented utterance recognition.

## 1. What parts of the repo matter

The repo contains many platforms and features. For Android ASR in a Java app, these are the important parts:

| Path | Why it matters |
|---|---|
| `android/SherpaOnnxJavaDemo` | Plain Java Android demo. It uses `OnlineRecognizer` from the Android artifact and records microphone audio with `AudioRecord`. Start here if you want streaming ASR in Java. |
| `android/SherpaOnnx` | Kotlin streaming ASR demo. It uses `OnlineRecognizer` and shows endpoint handling, model config choices, and optional QNN file-copy handling. |
| `android/SherpaOnnxVadAsr` | Kotlin VAD + offline ASR demo. It records microphone audio, segments speech with `Vad`, then sends each segment to `OfflineRecognizer`. |
| `android/SherpaOnnxAar` | AAR packaging project. Use it if you want to build a local AAR from this repo instead of depending on JitPack. |
| `sherpa-onnx/kotlin-api` | Android API classes. They are written in Kotlin, but they compile to JVM bytecode and are callable from Java. |
| `sherpa-onnx/jni` | JNI bridge. The API classes call native methods implemented here. |
| `build-android-arm64-v8a.sh`, `build-android-armv7-eabi.sh`, `build-android-x86-64.sh`, `build-android-x86.sh` | Native Android build scripts. Use these only if you need to build your own `.so` files. |
| `scripts/apk/build-apk-asr.sh.in` | How official streaming-ASR APKs are assembled. It builds native libs, downloads models, copies model files to assets, copies `.so` files to `jniLibs`, and runs Gradle. |
| `scripts/apk/build-apk-vad-asr.sh.in` | Same idea for VAD + offline ASR APKs. |

Important distinction:

`sherpa-onnx/java-api` is a desktop JVM API module. Its README talks about `java.library.path`, desktop `.dll`/`.so`/`.dylib`, and Maven coordinates `com.litongjava:sherpa-onnx-java-api`. That is not the normal Android path. For Android, use the Android AAR or build your own AAR.

## 2. Runtime architecture

Your Java Android app calls this stack:

```text
Your Activity/Service Java code
  -> com.k2fsa.sherpa.onnx.OnlineRecognizer / OfflineRecognizer / Vad
  -> libsherpa-onnx-jni.so
  -> sherpa-onnx C++ runtime
  -> ONNX Runtime
  -> ASR model files in assets or internal storage
```

The Android API classes load the native library with:

```java
System.loadLibrary("sherpa-onnx-jni");
```

So either your AAR must contain `libsherpa-onnx-jni.so`, or your app must package it under `app/src/main/jniLibs/<abi>/`.

## 3. Choose your ASR style

### Option A: Streaming ASR

Use `OnlineRecognizer`.

Use this when:

- You want partial text while the user is speaking.
- You want low latency microphone recognition.
- Your model package contains streaming model files, for example encoder/decoder/joiner files.

Repo examples:

- Java: `android/SherpaOnnxJavaDemo`
- Kotlin: `android/SherpaOnnx`

Typical model file pattern:

```text
app/src/main/assets/
  sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/
    encoder-epoch-99-avg-1.int8.onnx
    decoder-epoch-99-avg-1.onnx
    joiner-epoch-99-avg-1.int8.onnx
    tokens.txt
```

### Option B: VAD + offline ASR

Use `Vad` to cut microphone audio into speech segments, then pass each segment to `OfflineRecognizer`.

Use this when:

- You want simpler utterance-level recognition.
- You have a non-streaming ASR model such as Paraformer, Whisper, SenseVoice, Zipformer CTC, etc.
- You can accept results after each speech segment instead of live partial text.

Repo example:

- Kotlin: `android/SherpaOnnxVadAsr`

Typical file pattern:

```text
app/src/main/assets/
  silero_vad.onnx
  sherpa-onnx-paraformer-zh-2023-09-14/
    model.int8.onnx
    tokens.txt
```

## 4. Recommended project structure

Use your own application package. Do not copy the demo package name `com.k2fsa.sherpa.onnx` unless you are directly modifying the demo. The library package is already `com.k2fsa.sherpa.onnx`; your app can import it from any package.

Example app structure:

```text
MyAsrApp/
  settings.gradle
  build.gradle
  app/
    build.gradle
    src/main/
      AndroidManifest.xml
      java/com/example/myasr/
        MainActivity.java
        SherpaAsrManager.java
        AudioRecorder.java
      assets/
        sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/
          encoder-epoch-99-avg-1.int8.onnx
          decoder-epoch-99-avg-1.onnx
          joiner-epoch-99-avg-1.int8.onnx
          tokens.txt
      jniLibs/
        arm64-v8a/
          libsherpa-onnx-jni.so       # only needed if not using an AAR that already packages native libs
          libonnxruntime.so           # only needed for shared native builds
```

For most apps, keep sherpa-onnx behind one Java class such as `SherpaAsrManager`. That class should own recognizer initialization, stream creation/release, and model configuration. Keep Android UI and microphone code separate.

## 5. Add the Android dependency

### Recommended: use the published Android artifact

The Java demo uses this dependency:

```gradle
implementation 'com.github.k2-fsa:sherpa-onnx:v1.13.3'
```

Your top-level repositories must include JitPack:

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

For a Java app module:

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.example.myasr'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.myasr"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"

        ndk {
            // Keep only the ABIs you actually ship.
            // arm64-v8a is usually enough for modern production phones.
            abiFilters 'arm64-v8a'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'com.github.k2-fsa:sherpa-onnx:v1.13.3'

    // App UI dependencies are optional; keep only what your app uses.
    implementation 'androidx.appcompat:appcompat:1.7.0'
}
```

If you use the published artifact, you normally do not copy `sherpa-onnx/kotlin-api` files or native `.so` files manually.

### Alternative: build a local AAR

Use this when you need a patched repo, custom native build flags, or an offline dependency.

The repo provides `android/SherpaOnnxAar/README.md`. It says to download the Android native package, copy its `jniLibs` into the AAR module, then assemble:

```bash
cd /work1/AsrToy/sherpa-onnx

wget https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.3/sherpa-onnx-v1.13.3-android.tar.bz2
tar xvf sherpa-onnx-v1.13.3-android.tar.bz2

cp -v jniLibs/arm64-v8a/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/arm64-v8a/
cp -v jniLibs/armeabi-v7a/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/armeabi-v7a/
cp -v jniLibs/x86/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/x86/
cp -v jniLibs/x86_64/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/x86_64/

cd android/SherpaOnnxAar
./gradlew :sherpa_onnx:assembleRelease
```

The output is:

```text
android/SherpaOnnxAar/sherpa_onnx/build/outputs/aar/sherpa_onnx-release.aar
```

Then copy that AAR to your app:

```text
app/libs/sherpa_onnx-release.aar
```

and add:

```gradle
dependencies {
    implementation files('libs/sherpa_onnx-release.aar')

    // Needed if your local file AAR does not bring transitive Kotlin runtime dependencies.
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.7.20'
}
```

### Alternative: copy native libs directly

Only do this if you are not using an AAR that already packages native libs.

The native build scripts explain two modes:

- `BUILD_SHARED_LIBS=ON`: copy both `libsherpa-onnx-jni.so` and `libonnxruntime.so`.
- `BUILD_SHARED_LIBS=OFF`: ONNX Runtime is statically linked into `libsherpa-onnx-jni.so`, so copy only `libsherpa-onnx-jni.so`.

For a smaller ASR-only native build, disable unused features:

```bash
cd /work1/AsrToy/sherpa-onnx
export ANDROID_NDK=/path/to/android/sdk/ndk/<version>
export SHERPA_ONNX_ENABLE_TTS=OFF
export SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF

# Shared build: produces libsherpa-onnx-jni.so and uses libonnxruntime.so.
BUILD_SHARED_LIBS=ON ./build-android-arm64-v8a.sh

# Static build: usually easier to package, but the one .so is larger.
BUILD_SHARED_LIBS=OFF ./build-android-arm64-v8a.sh
```

Copy the generated libraries:

```text
build-android-arm64-v8a/install/lib/*.so
  -> app/src/main/jniLibs/arm64-v8a/
```

Repeat with the other ABI scripts only if you plan to ship those ABIs:

- `build-android-armv7-eabi.sh` -> `armeabi-v7a`
- `build-android-x86-64.sh` -> `x86_64`
- `build-android-x86.sh` -> `x86`

## 6. Prepare model assets

### Streaming Zipformer example

This is the exact model used by `android/SherpaOnnxJavaDemo`.

Download and extract:

```bash
cd app/src/main/assets
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

Keep only the files you configure:

```text
app/src/main/assets/
  sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/
    encoder-epoch-99-avg-1.int8.onnx
    decoder-epoch-99-avg-1.onnx
    joiner-epoch-99-avg-1.int8.onnx
    tokens.txt
```

Delete unused files such as test waves, readmes, scripts, and alternative float/int8 models to reduce APK size.

### VAD + offline Paraformer example

Download VAD:

```bash
cd app/src/main/assets
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

Download the offline ASR model:

```bash
cd app/src/main/assets
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2
tar xvf sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2
rm sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2
```

Keep:

```text
app/src/main/assets/
  silero_vad.onnx
  sherpa-onnx-paraformer-zh-2023-09-14/
    model.int8.onnx
    tokens.txt
```

### Optional Chinese number normalization

Several APK scripts download `itn_zh_number.fst`. If you want Chinese number inverse text normalization, put it in assets and set `ruleFsts`.

```text
app/src/main/assets/
  itn_zh_number.fst
```

In Java:

```java
config.setRuleFsts("itn_zh_number.fst");
```

### Assets mode vs filesystem mode

This is important.

If you create a recognizer like this:

```java
new OnlineRecognizer(getAssets(), config);
new OfflineRecognizer(getAssets(), config);
new Vad(getAssets(), config);
```

then all model paths in the config must be relative to `app/src/main/assets`. Example:

```java
modelConfig.setTokens("sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/tokens.txt");
```

Do not use absolute paths with an `AssetManager`. The native `ReadFile(AAssetManager*, filename)` path prints an error if a path starts with `/`.

If you copy models to internal storage or external storage, pass `null` as the asset manager and use absolute filesystem paths:

```java
OnlineRecognizer recognizer =
        new OnlineRecognizer((android.content.res.AssetManager) null, config);
```

Use filesystem mode for QNN or other providers that require real filesystem files. The Kotlin streaming demo copies assets to internal storage before QNN initialization.

## 7. AndroidManifest permissions

For microphone ASR:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

If you record in a foreground service, also add:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

For Android 9+ foreground-service microphone work, you may also need the correct foreground service type depending on your target SDK and service design.

Example service declaration:

```xml
<application ...>
    <service
        android:name=".SpeechSherpaRecognitionService"
        android:exported="false" />
</application>
```

Always request `RECORD_AUDIO` at runtime before creating/starting `AudioRecord`.

## 8. Java streaming ASR implementation

This section translates the repo's Java demo into a reusable manager-style implementation.

### Initialize `OnlineRecognizer`

```java
import android.content.res.AssetManager;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

public final class StreamingSherpaAsr {
    private static final int SAMPLE_RATE = 16000;

    private final OnlineRecognizer recognizer;

    public StreamingSherpaAsr(AssetManager assets) {
        String modelDir = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20";

        OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
        transducer.setEncoder(modelDir + "/encoder-epoch-99-avg-1.int8.onnx");
        transducer.setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx");
        transducer.setJoiner(modelDir + "/joiner-epoch-99-avg-1.int8.onnx");

        OnlineModelConfig model = new OnlineModelConfig();
        model.setTransducer(transducer);
        model.setTokens(modelDir + "/tokens.txt");
        model.setModelType("zipformer");
        model.setNumThreads(2);
        model.setDebug(false);
        model.setProvider("cpu");

        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(SAMPLE_RATE);
        feat.setFeatureDim(80);
        feat.setDither(0.0f);

        OnlineRecognizerConfig config = new OnlineRecognizerConfig();
        config.setFeatConfig(feat);
        config.setModelConfig(model);
        config.setEnableEndpoint(true);
        config.setDecodingMethod("greedy_search");

        recognizer = new OnlineRecognizer(assets, config);
    }

    public OnlineRecognizer getRecognizer() {
        return recognizer;
    }

    public void release() {
        recognizer.release();
    }
}
```

Notes:

- The Kotlin API data classes generate Java setters such as `setEncoder`, `setTokens`, and `setModelConfig`.
- Java cannot use Kotlin default arguments directly, so call `recognizer.createStream("")` for an online stream.
- Keep `debug` off in production. Set it to `true` only when checking paths and config in logcat.

### Record microphone audio

The demos use:

- sample rate: `16000`
- channel: mono
- encoding: PCM 16-bit
- read interval: about 100 ms for streaming ASR

```java
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineStream;

public final class StreamingMicLoop {
    private static final int SAMPLE_RATE = 16000;

    private final android.content.Context context;
    private final OnlineRecognizer recognizer;
    private volatile boolean recording;
    private AudioRecord audioRecord;
    private Thread worker;

    public interface ResultCallback {
        void onPartialOrFinalText(String text);
    }

    public StreamingMicLoop(android.content.Context context, OnlineRecognizer recognizer) {
        this.context = context.getApplicationContext();
        this.recognizer = recognizer;
    }

    public void start(ResultCallback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("RECORD_AUDIO permission is not granted");
        }

        int minBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBytes * 2);

        recording = true;
        audioRecord.startRecording();

        worker = new Thread(() -> runLoop(callback), "sherpa-asr-mic");
        worker.start();
    }

    public void stop() {
        recording = false;

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void runLoop(ResultCallback callback) {
        OnlineStream stream = recognizer.createStream("");
        int bufferSize = (int) (0.1 * SAMPLE_RATE);
        short[] buffer = new short[bufferSize];

        String lastText = "";
        int segmentIndex = 0;

        while (recording) {
            int n = audioRecord.read(buffer, 0, buffer.length);
            if (n <= 0) {
                continue;
            }

            float[] samples = new float[n];
            for (int i = 0; i < n; ++i) {
                samples[i] = buffer[i] / 32768.0f;
            }

            stream.acceptWaveform(samples, SAMPLE_RATE);

            while (recognizer.isReady(stream)) {
                recognizer.decode(stream);
            }

            boolean isEndpoint = recognizer.isEndpoint(stream);
            String text = recognizer.getResult(stream).getText();

            String display = lastText;
            if (text != null && !text.isEmpty()) {
                display = lastText.isEmpty()
                        ? segmentIndex + ": " + text
                        : lastText + "\n" + segmentIndex + ": " + text;
            }

            if (isEndpoint) {
                recognizer.reset(stream);
                if (text != null && !text.isEmpty()) {
                    lastText = display;
                    segmentIndex += 1;
                }
            }

            callback.onPartialOrFinalText(display);
        }

        stream.release();
    }
}
```

For streaming Paraformer models, the Kotlin demo adds about `0.8` seconds of zero padding at endpoint before getting the final segment text. Do that if your configured model uses `OnlineParaformerModelConfig`.

## 9. Java VAD + offline ASR implementation

This is the Java equivalent of `android/SherpaOnnxVadAsr`.

Use this for non-streaming models such as Paraformer, Whisper, SenseVoice, Zipformer CTC, Nemo CTC, etc.

### Initialize VAD

```java
import android.content.res.AssetManager;

import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

public final class SherpaVadFactory {
    public static Vad createVad(AssetManager assets) {
        SileroVadModelConfig silero = new SileroVadModelConfig();
        silero.setModel("silero_vad.onnx");
        silero.setThreshold(0.5f);
        silero.setMinSilenceDuration(0.25f);
        silero.setMinSpeechDuration(0.25f);
        silero.setWindowSize(512);
        silero.setMaxSpeechDuration(5.0f);

        VadModelConfig config = new VadModelConfig();
        config.setSileroVadModelConfig(silero);
        config.setSampleRate(16000);
        config.setNumThreads(1);
        config.setProvider("cpu");
        config.setDebug(false);

        return new Vad(assets, config);
    }
}
```

### Initialize `OfflineRecognizer`

Example for `sherpa-onnx-paraformer-zh-2023-09-14`:

```java
import android.content.res.AssetManager;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;

public final class OfflineSherpaAsrFactory {
    public static OfflineRecognizer createParaformer(AssetManager assets) {
        String modelDir = "sherpa-onnx-paraformer-zh-2023-09-14";

        OfflineParaformerModelConfig paraformer = new OfflineParaformerModelConfig();
        paraformer.setModel(modelDir + "/model.int8.onnx");

        OfflineModelConfig model = new OfflineModelConfig();
        model.setParaformer(paraformer);
        model.setTokens(modelDir + "/tokens.txt");
        model.setModelType("paraformer");
        model.setNumThreads(2);
        model.setDebug(false);
        model.setProvider("cpu");

        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(16000);
        feat.setFeatureDim(80);
        feat.setDither(0.0f);

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(feat);
        config.setModelConfig(model);

        // Optional if you copied itn_zh_number.fst into assets.
        // config.setRuleFsts("itn_zh_number.fst");

        return new OfflineRecognizer(assets, config);
    }
}
```

For another model type, change only the model-specific sub-config:

| Model family | Config class and files |
|---|---|
| Offline transducer | `OfflineTransducerModelConfig`: `encoder`, `decoder`, `joiner`, plus `tokens`, `modelType="transducer"` or `modelType="nemo_transducer"` |
| Paraformer | `OfflineParaformerModelConfig`: `model`, plus `tokens`, `modelType="paraformer"` |
| Whisper | `OfflineWhisperModelConfig`: `encoder`, `decoder`, plus `tokens`, `modelType="whisper"` |
| SenseVoice | `OfflineSenseVoiceModelConfig`: `model`, plus `tokens` |
| Zipformer CTC | `OfflineZipformerCtcModelConfig`: `model`, plus `tokens` |
| Nemo CTC | `OfflineNemoEncDecCtcModelConfig`: `model`, plus `tokens` |

The helper `getOfflineModelConfig(type)` in `sherpa-onnx/kotlin-api/OfflineRecognizer.kt` is a useful catalog of known model directories and file names. You do not have to call it from Java; you can create the config directly as shown above.

### Run VAD and recognize segments

```java
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;

public final class VadOfflinePipeline {
    private static final int SAMPLE_RATE = 16000;

    private final Vad vad;
    private final OfflineRecognizer recognizer;

    public interface SegmentCallback {
        void onText(String text);
    }

    public VadOfflinePipeline(Vad vad, OfflineRecognizer recognizer) {
        this.vad = vad;
        this.recognizer = recognizer;
    }

    public void acceptAudio(float[] samples, SegmentCallback callback) {
        vad.acceptWaveform(samples);

        while (!vad.empty()) {
            SpeechSegment segment = vad.front();
            String text = recognize(segment.getSamples());
            vad.pop();

            if (text != null && !text.isEmpty()) {
                callback.onText(text);
            }
        }
    }

    public String recognize(float[] samples) {
        OfflineStream stream = recognizer.createStream();
        stream.acceptWaveform(samples, SAMPLE_RATE);
        recognizer.decode(stream);
        String text = recognizer.getResult(stream).getText();
        stream.release();
        return text;
    }

    public void reset() {
        vad.reset();
    }

    public void release() {
        vad.release();
        recognizer.release();
    }
}
```

In a real app, run `recognize()` on a background executor. Offline recognition can take noticeable time on large models.

## 10. Model config examples

### Streaming transducer

Required assets:

```text
<modelDir>/encoder*.onnx
<modelDir>/decoder*.onnx
<modelDir>/joiner*.onnx
<modelDir>/tokens.txt
```

Java config:

```java
OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
transducer.setEncoder(modelDir + "/encoder.int8.onnx");
transducer.setDecoder(modelDir + "/decoder.onnx");
transducer.setJoiner(modelDir + "/joiner.int8.onnx");

OnlineModelConfig model = new OnlineModelConfig();
model.setTransducer(transducer);
model.setTokens(modelDir + "/tokens.txt");
model.setModelType("zipformer2");
```

### Streaming CTC

Required assets:

```text
<modelDir>/model.int8.onnx
<modelDir>/tokens.txt
```

Java config:

```java
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig;

OnlineZipformer2CtcModelConfig ctc = new OnlineZipformer2CtcModelConfig();
ctc.setModel(modelDir + "/model.int8.onnx");

OnlineModelConfig model = new OnlineModelConfig();
model.setZipformer2Ctc(ctc);
model.setTokens(modelDir + "/tokens.txt");
```

### Offline SenseVoice

Required assets:

```text
<modelDir>/model.int8.onnx
<modelDir>/tokens.txt
```

Java config:

```java
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;

OfflineSenseVoiceModelConfig senseVoice = new OfflineSenseVoiceModelConfig();
senseVoice.setModel(modelDir + "/model.int8.onnx");
senseVoice.setLanguage("");
senseVoice.setUseInverseTextNormalization(true);

OfflineModelConfig model = new OfflineModelConfig();
model.setSenseVoice(senseVoice);
model.setTokens(modelDir + "/tokens.txt");
```

### Offline Whisper

Required assets:

```text
<modelDir>/tiny.en-encoder.int8.onnx
<modelDir>/tiny.en-decoder.int8.onnx
<modelDir>/tiny.en-tokens.txt
```

Java config:

```java
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
whisper.setEncoder(modelDir + "/tiny.en-encoder.int8.onnx");
whisper.setDecoder(modelDir + "/tiny.en-decoder.int8.onnx");
whisper.setLanguage("en");
whisper.setTask("transcribe");

OfflineModelConfig model = new OfflineModelConfig();
model.setWhisper(whisper);
model.setTokens(modelDir + "/tiny.en-tokens.txt");
model.setModelType("whisper");
```

## 11. Packaging and APK size

Models can be much larger than ordinary Android resources. For a first working app, packaging models in `assets/` is simplest. For production, consider downloading models after install or using Play Asset Delivery.

Keep APK size under control:

- Prefer int8 models on mobile unless accuracy demands float models.
- Keep only one model variant in assets.
- Remove `test_wavs`, `README.md`, scripts, Python files, YAML files, and unused ONNX variants.
- Restrict `abiFilters` to shipped device ABIs.
- Disable unused native features if you build sherpa-onnx yourself, for example TTS and speaker diarization.

## 12. Threading and lifecycle rules

Follow these rules in your Android app:

1. Initialize recognizers on a background thread. Large ONNX models can block the UI.
2. Do not record without runtime `RECORD_AUDIO` permission.
3. Keep microphone read and decode work off the UI thread.
4. Convert PCM16 samples to float samples in `[-1.0, 1.0]`.
5. Release every `OnlineStream` or `OfflineStream` after use.
6. Release `OnlineRecognizer`, `OfflineRecognizer`, and `Vad` in `onDestroy()` or when the ASR feature is no longer needed.
7. Reset the online stream after endpoint detection.
8. For VAD + offline ASR, avoid launching unlimited concurrent recognition jobs. Use a single-thread executor or bounded queue.

## 13. Step-by-step deployment plan

### Step 1: Start from a normal Android Java app

Create or open your Android app. Use your own package, for example:

```text
com.example.myasr
```

Do not copy the whole `android/` directory from sherpa-onnx.

### Step 2: Add JitPack and the sherpa-onnx Android dependency

Add JitPack to `settings.gradle`, then add:

```gradle
implementation 'com.github.k2-fsa:sherpa-onnx:v1.13.3'
```

Build once to confirm Gradle resolves the artifact.

### Step 3: Pick one model path

For live partial results, choose a streaming model and `OnlineRecognizer`.

For utterance-level results, choose an offline model and optionally add VAD.

Start with the same models as the repo examples before changing to a custom model.

### Step 4: Copy only required model files

Put model files under:

```text
app/src/main/assets/
```

Use the exact file names from the config. If you rename a file, update the Java config.

### Step 5: Add microphone permission

Add `RECORD_AUDIO` to the manifest and request it at runtime.

### Step 6: Implement a recognizer manager

For streaming ASR, create:

```text
StreamingSherpaAsr.java
StreamingMicLoop.java
```

For VAD + offline ASR, create:

```text
SherpaVadFactory.java
OfflineSherpaAsrFactory.java
VadOfflinePipeline.java
```

Keep UI code out of these classes except for callbacks.

### Step 7: Initialize in the app

Do recognizer initialization on an executor:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.execute(() -> {
    StreamingSherpaAsr asr = new StreamingSherpaAsr(getAssets());
    runOnUiThread(() -> {
        // Enable record button.
    });
});
```

### Step 8: Feed microphone samples

Use `AudioRecord` at 16 kHz mono PCM16. Convert to float:

```java
float[] samples = new float[n];
for (int i = 0; i < n; ++i) {
    samples[i] = buffer[i] / 32768.0f;
}
```

Then send samples to `OnlineStream.acceptWaveform()` or `Vad.acceptWaveform()`.

### Step 9: Build and inspect the APK

Build:

```bash
./gradlew assembleDebug
```

Check that assets are packaged:

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'assets/.*onnx'
```

If you package native libraries yourself, also check:

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'lib/.*/libsherpa-onnx-jni.so'
```

### Step 10: Test on a real device

Use a real Android device first. Emulator microphone setup can require:

```bash
adb emu avd hostmicon
```

Watch logs:

```bash
adb logcat | grep sherpa-onnx
```

Set `model.setDebug(true)` temporarily if you need to inspect the resolved config in logcat.

## 14. Troubleshooting

### `UnsatisfiedLinkError: no sherpa-onnx-jni`

Cause:

- The AAR/native libs are not packaged.
- The device ABI is not included.
- You copied native libs to the wrong `jniLibs` folder.

Fix:

- If using JitPack AAR, remove manual conflicting native packaging and rebuild.
- If using manual libs, verify `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so`.
- If `BUILD_SHARED_LIBS=ON`, also package `libonnxruntime.so`.
- Match `abiFilters` with the `.so` folders you ship.

### Native error: failed to load model file

Cause:

- Path mismatch.
- Asset directory name does not match config.
- Absolute path used with `AssetManager`.
- Relative asset path used with filesystem mode.

Fix:

- In assets mode, use `new OnlineRecognizer(getAssets(), config)` and relative paths.
- In filesystem mode, use `new OnlineRecognizer((AssetManager) null, config)` and absolute paths.
- Enable debug config and inspect logcat.

### App freezes during startup

Cause:

- Recognizer initialized on the UI thread.

Fix:

- Initialize in `ExecutorService`, coroutine, or another background mechanism.

### Recognition is empty or poor

Check:

- `AudioRecord` really runs at 16 kHz mono.
- PCM16 samples are divided by `32768.0f`.
- Runtime microphone permission is granted.
- You are using the right model files for the config.
- You did not keep a float file name in code while only packaging an int8 file, or vice versa.
- For streaming endpointing, call `recognizer.reset(stream)` after finalizing a segment.

### Duplicate class or package confusion

Cause:

- You copied `sherpa-onnx/kotlin-api` source files into your app and also depend on the AAR.

Fix:

- Prefer the AAR. Do not copy Kotlin API source files unless you are deliberately maintaining a custom library module.

## 15. Minimal file-copy checklist

For the recommended JitPack AAR + streaming ASR setup, copy only:

```text
app/src/main/assets/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/
  encoder-epoch-99-avg-1.int8.onnx
  decoder-epoch-99-avg-1.onnx
  joiner-epoch-99-avg-1.int8.onnx
  tokens.txt
```

Add only:

```gradle
implementation 'com.github.k2-fsa:sherpa-onnx:v1.13.3'
```

and the JitPack repository.

For VAD + offline Paraformer, copy:

```text
app/src/main/assets/silero_vad.onnx
app/src/main/assets/sherpa-onnx-paraformer-zh-2023-09-14/
  model.int8.onnx
  tokens.txt
```

Use:

```java
new Vad(getAssets(), vadConfig);
new OfflineRecognizer(getAssets(), offlineConfig);
```

For a local/manual native setup, additionally package:

```text
app/src/main/jniLibs/<abi>/libsherpa-onnx-jni.so
app/src/main/jniLibs/<abi>/libonnxruntime.so       # only when BUILD_SHARED_LIBS=ON
```

## 16. Best first implementation

If your goal is to ship a Java Android ASR feature quickly:

1. Create your own Java Android app.
2. Add JitPack and `implementation 'com.github.k2-fsa:sherpa-onnx:v1.13.3'`.
3. Copy the streaming Zipformer model files used by `android/SherpaOnnxJavaDemo`.
4. Implement the `StreamingSherpaAsr` and `StreamingMicLoop` pattern above.
5. Test on `arm64-v8a` first.
6. After it works, switch to your target model by changing only the assets and model config.

This avoids copying the whole repo, avoids the desktop Java API, and keeps the Android integration surface small.
