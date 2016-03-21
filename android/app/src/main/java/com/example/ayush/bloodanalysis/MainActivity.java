package com.example.ayush.bloodanalysis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ConcentrationEstimatorService cEstimator;
    private ImageView imageView;

    private TextView tv1, tv2, tv3;

    public boolean isStarted, isFinished;

    // data of the standard curve and results
    double[] results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            isStarted = savedInstanceState.getBoolean(Constants.ISSTARTED_KEY, false);
            isFinished = savedInstanceState.getBoolean(Constants.ISFINISHED_KEY, false);
            results = savedInstanceState.getDoubleArray(Constants.RESULTS_TAG);
        } else {
            isFinished = false;
            isStarted = false;
            results = new double[7];
        }

        /*
         * Add receivers for handling broadcasts
         */
        // The filter's action is BROADCAST_ACTION
        IntentFilter mStatusIntentFilter = new IntentFilter(
                Constants.BROADCAST_ACTION);

        // Instantiate a new ResponseReceiver
        ResponseReceiver mResponseReceiver = new ResponseReceiver();
        // Register the receiver and its intent
        LocalBroadcastManager.getInstance(this).registerReceiver(mResponseReceiver, mStatusIntentFilter);


        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        Log.i("On create", "is finished = " + isFinished);
        imageView = (ImageView) findViewById(R.id.image_display);

        tv1 = (TextView) findViewById(R.id.results_qc1);
        tv2 = (TextView) findViewById(R.id.results_qc2);
        tv3 = (TextView) findViewById(R.id.results_sample);

        // load input image in cache dir
        getImage();

        // display extracted image, if we have finished
        if (isFinished) {
            displayImage();
            showResults();
        }
    }

    public void getImage() {
        File f = new File(getCacheDir() + "/image072915B.jpeg");
        if (!f.exists()) try {

            InputStream is = getAssets().open("image072915B.jpeg");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();


            FileOutputStream fos = new FileOutputStream(f);
            fos.write(buffer);
            fos.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("CallBack", "OpenCV loaded successfully");

                    startEstimation();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i("On save", "is finished = " + isFinished);
        outState.putBoolean(Constants.ISSTARTED_KEY, isStarted);
        outState.putBoolean(Constants.ISFINISHED_KEY, isFinished);
        outState.putDoubleArray(Constants.RESULTS_TAG, results);
    }

    public void startEstimation() {
        // Starts the concentration estimation intent

        if (!isStarted && !isFinished) {
            String basePath = getCacheDir().toString();
            String inputFileName = "image072915B.jpeg";
            String extractedFileName = "extractedImage.jpeg";
            Intent estimatorIntent = new Intent(this, ConcentrationEstimatorService.class);
            estimatorIntent.putExtra(Constants.BASEPATH_TAG, basePath);
            estimatorIntent.putExtra(Constants.INPUTNAME_TAG, inputFileName);
            estimatorIntent.putExtra(Constants.EXTRACTEDNAME_TAG, extractedFileName);

            startService(estimatorIntent);
            isStarted = true;
            isFinished = false;
        }
    }

    public void displayImage() {
        String filePath = getCacheDir() + "/extractedImage.jpeg";
        Mat extractedImg = Imgcodecs.imread(filePath, Imgcodecs.CV_LOAD_IMAGE_COLOR);

        Bitmap bm = Bitmap.createBitmap(extractedImg.cols(), extractedImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(extractedImg, bm);

        imageView.setImageBitmap(bm);
    }

    public void showResults() {
        tv1.setText("Calculated concentration QC1= " + results[2]);
        tv2.setText("Calculated concentration QC2= " + results[3]);
        tv3.setText("Calculated concentration sample= " + results[6]);
    }

    private class ResponseReceiver extends BroadcastReceiver {
        // prevents instantiation
        private ResponseReceiver() {

        }

        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            int status = extras.getInt(Constants.EXTENDED_DATA_STATUS, 0);

            if (status != 1) {
                return;
            }
            results = extras.getDoubleArray(Constants.RESULTS_TAG);
            isFinished = true;
            isStarted = false;

            showResults();
            displayImage();
        }
    }
}


