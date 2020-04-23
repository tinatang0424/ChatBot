package com.chatbot01.camerafo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.facedetector.FaceDetector;
import io.fotoapparat.facedetector.Rectangle;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.preview.FrameProcessor;
import io.fotoapparat.result.PhotoResult;
import io.fotoapparat.view.CameraView;
import io.fotoapparat.view.FocusView;
import me.itangqi.waveloadingview.WaveLoadingView;
import okhttp3.Call;
import okhttp3.OkHttpClient;


import static io.fotoapparat.log.LoggersKt.fileLogger;
import static io.fotoapparat.log.LoggersKt.logcat;
import static io.fotoapparat.log.LoggersKt.loggers;
import static io.fotoapparat.result.transformer.ResolutionTransformersKt.scaled;
import static io.fotoapparat.selector.LensPositionSelectorsKt.front;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private boolean hasAllPermission;
    private static final String LOGGING_TAG = "Fotoapparat";
    private CameraView cameraView;
    private FocusView focusView;
    private SampleFrameProcessor processor;
    private FaceDetector faceDetector;
    private View capture;
    private ImageView face;
    private ImageView emotion;
    private Fotoapparat fotoapparat;
    private File imgFile;
    private TextView tv_result;     //顯示STT結果/使用者說的話
    private TextView tv_response;   //顯示機器人回應結果
    private String serverIP = "http://" + GlobalData.serverIP + ":9999/";

    SpeechRecognizer speech;
    Intent recognizerIntent;
    String fin_result;
    TextToSpeech tts;
    String mood;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        init_SpeechRecognizer();

        cameraView = findViewById(R.id.cameraView);
        focusView = findViewById(R.id.focusView);
        capture = findViewById(R.id.capture);
        face = findViewById(R.id.face);
        emotion = findViewById(R.id.iv_mood);

        cameraView.setVisibility(View.VISIBLE);
        faceDetector = FaceDetector.create(this);
        processor = new SampleFrameProcessor();
        fotoapparat = createFotoapparat();
        takePictureOnClick();

        tv_result = findViewById(R.id.tv_result);
        tv_response = findViewById(R.id.tv_response);

        tv_result.setMovementMethod(ScrollingMovementMethod.getInstance());

        tv_response.setSelected(true);


        WaveLoadingView mWaveLoadingView = findViewById(R.id.waveLoadingView);
        mWaveLoadingView.setProgressValue(50);
        mWaveLoadingView.setAmplitudeRatio(60);
        mWaveLoadingView.setAnimDuration(1500);
        mWaveLoadingView.startAnimation();

    }

    private void init_SpeechRecognizer() {
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-TW");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    //http上傳文字
    private void uploadTxt(String text) {
        final OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .readTimeout(1000, TimeUnit.MILLISECONDS)
                .build();

        OkHttpUtils.initClient(client);

        OkHttpUtils.post()
                .addParams("UserSay", text)
                .url(serverIP)
                .build()
                .execute(new StringCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Log.d("ttt","文字傳送錯誤");
                    }

                    @Override
                    public void onResponse(String response, int id) {
                        runOnUiThread(() -> tv_response.setText(response));
                        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null);
                    }
                });
    }

    //STT&TakePic
    private void takePictureOnClick() {
        capture.setOnClickListener(v -> {
            //呼叫STT開始聆聽
            speech.startListening(recognizerIntent);
            takePicture();
        });
    }

    //拍照
    private void takePicture() {
        final PhotoResult photoResult = fotoapparat.takePicture();

        /*photoResult.saveToFile(new File(
                getExternalFilesDir("photos"),
                "photo.jpg"
        ));*/

        imgFile = new File(Environment.getExternalStorageDirectory().toString() + "/test.jpg");

        Log.e(LOGGING_TAG, "拍照成功");

        photoResult.saveToFile(imgFile);

        photoResult
                .toBitmap(scaled(0.25f))
                .whenDone(bitmapPhoto -> {
                    if (bitmapPhoto == null) {
                        Log.e(LOGGING_TAG, "Couldn't capture photo.");
                        return;
                    }

                    uploadImg();

                });

    }

    //http上傳照片
    public void uploadImg() {
        final OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(10000, TimeUnit.MILLISECONDS)
                .readTimeout(10000, TimeUnit.MILLISECONDS)
                .build();

        OkHttpUtils.initClient(client);

        Log.e("file = ", imgFile.toString());

        if(!imgFile.exists()){
            runOnUiThread(() -> Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show());
        }

        OkHttpUtils.post()
                .addFile("file", "test.jpg", imgFile)
                .url(serverIP)
                .build()
                .execute(new StringCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Log.i("errorrr",e.toString());
                    }

                    @Override
                    public void onResponse(String response, int id) {
                            mood = response;
                            Log.i("my_mood",mood);
                            if(mood.equals("happy")) {
                                Log.d("my_mood","happyhappyhappy");
                                runOnUiThread(() -> emotion.setBackgroundResource(R.drawable.ic_smile));
                            }
                            if(mood.equals("unhappy"
                            )) {
                                Log.d("my_mood","unhappyunhappyunhappy");
                                runOnUiThread(() -> emotion.setBackgroundResource(R.drawable.ic_sad));
                            }
                            if(mood.equals("normal")) {
                                Log.d("my_mood","normalnormalnormal");
                                runOnUiThread(() -> emotion.setBackgroundResource(R.drawable.ic_relax));
                            }

                    }
                });

    }

    //創建相機
    private Fotoapparat createFotoapparat() {
        return Fotoapparat
                .with(this)
                .into(cameraView)
                .focusView(focusView)
                .previewScaleType(ScaleType.CenterCrop)
                .lensPosition(front())
                .frameProcessor(processor)
                .logger(loggers(
                        logcat(),
                        fileLogger(this)
                ))
                .cameraErrorCallback(e -> {
                    Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_LONG).show();
                })
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(hasAllPermission)
            fotoapparat.start();
        else
            requestPermissions();


    }

    @Override
    protected void onStop() {
        super.onStop();
        if(hasAllPermission)
            fotoapparat.stop();
        else
            requestPermissions();

    }

    @Override
    protected void onPause() {

        if (tts!=null) {
            tts.stop();
            tts.shutdown();
        }

        if (speech!=null) {
            speech.stopListening();
            speech.cancel();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //TTS
        tts = new TextToSpeech(this, status -> {
            if(status==TextToSpeech.SUCCESS) {
                int result=tts.setLanguage(Locale.TAIWAN);
                if (result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED)
                    Log.i("TextToSpeech", "Language Not Supported");

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.i("TextToSpeech", "On Start");
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        Log.i("TextToSpeech", "On Done");

                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.i("TextToSpeech", "On Error");
                    }
                });
            }
            else
                Log.i("TextToSpeech", "Initialization Failed");
        });

    }

    //STT
    @Override
    public void onReadyForSpeech(Bundle params) {
        runOnUiThread(() -> tv_result.setText("請開始說話"));
    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int error) {
        runOnUiThread(() -> tv_result.setText("User Say"));
    }

    //STT結果
    @Override
    public void onResults(Bundle results) {
        if(results!=null){
            ArrayList matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            assert matches != null;
            fin_result = matches.get(0).toString();
            runOnUiThread(() -> tv_result.setText(fin_result));
            uploadTxt(fin_result);

        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }

    //processor
    private class SampleFrameProcessor implements FrameProcessor {
        @Override
        public void process(@NotNull Frame frame) {
            List<Rectangle> faces = faceDetector.detectFaces(frame.getImage(), frame.getSize().width, frame.getSize().height, frame.getRotation());
            setFacePic(faces);
            /*runOnUiThread(() -> rectanglesView.setRectangles(faces)
                //face.setBackgroundColor(Color.BLUE)
            );*/
        }
    }

    //臉部偵測
    private void setFacePic(List<Rectangle> faces)
    {
        if(!faces.isEmpty()) {  //偵測到臉
            Log.v("aaa","face");
            runOnUiThread(() -> face.setBackgroundResource(R.drawable.ic_face));
        }
        else {
            Log.v("aaa","noface");  //沒有測到臉
            runOnUiThread(() -> face.setBackgroundResource(R.drawable.ic_noface));
        }
    }

    //要求權限的結果
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(hasAllPermission){
            fotoapparat.start();
            cameraView.setVisibility(View.VISIBLE);
        }
    }

    //要求權限
    private void requestPermissions() {
        final RxPermissions rxPermissions = new RxPermissions(this);

        rxPermissions
                .request(Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO)
                .subscribe(granted -> {
                    if (granted) {
                        // All requested permissions are granted
                        hasAllPermission = true;
                    } else {
                        // At least one permission is denied
                        requestPermissions();
                    }
                });
    }
}
