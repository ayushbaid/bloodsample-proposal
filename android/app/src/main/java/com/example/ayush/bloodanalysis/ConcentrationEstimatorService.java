package com.example.ayush.bloodanalysis;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ayush on 10/3/16.
 */
public class ConcentrationEstimatorService extends IntentService {

    private static final String TAG = "IntensityEstimator";

    private Mat inputImg; // input RGB image
    private Mat aImg; // input image in "a" of l*a*b* space
    private Mat extractedImg; // image showing extracted regions
    private Mat grayImg; // grayscale image for intensity

    public ConcentrationEstimatorService() {
        super("ConcentrationEstimatorService");
    }

    public void loadImage(String path) {
        /*
         * Loads the image, and converts it into grayscale and lab color space
         */
        Log.i(TAG, "Image path = " + path);
        inputImg = Imgcodecs.imread(path, Imgcodecs.CV_LOAD_IMAGE_COLOR);

        grayImg = new Mat();
        Mat labImage = new Mat();

        // Log.i(TAG, "Image size = " + inputImg.height() + "*" + inputImg.width());
        // Log.i(TAG, "Image type = " + inputImg.type());

        Imgproc.cvtColor(inputImg, inputImg, Imgproc.COLOR_BGR2RGB);
        // Coverting to l*a*b* and grayscale colorspace
        Imgproc.cvtColor(inputImg, grayImg, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(inputImg, labImage, Imgproc.COLOR_RGB2Lab);

        // Extracting "a" image
        aImg = new Mat(labImage.rows(), labImage.cols(), grayImg.type());

        int ch[] = {1, 0};
        MatOfInt from_to = new MatOfInt(ch);
        List<Mat> list1 = new ArrayList<Mat>();
        List<Mat> list2 = new ArrayList<Mat>();
        list1.add(labImage);
        list2.add(aImg);
        // Log.i(TAG, "A-Image channels = " + aImg.channels());
        Core.mixChannels(list1, list2, from_to);
        // Log.i(TAG, "A-Image channels = " + aImg.channels());
    }

    public Bitmap getOriginalImage() {
        // convert to bitmap:
        Bitmap bm = Bitmap.createBitmap(inputImg.cols(), inputImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputImg, bm);

        return bm;
    }

    public Bitmap getAImage() {
        // convert from grayscale to rbg image
        Mat output = new Mat();
        Imgproc.cvtColor(aImg, output, Imgproc.COLOR_GRAY2RGB, 4);
        // convert to bitmap:
        Bitmap bm = Bitmap.createBitmap(aImg.cols(), aImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, bm);

        return bm;
    }

    public Bitmap getExtractedImage() {
        Bitmap bm = Bitmap.createBitmap(extractedImg.cols(), extractedImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(extractedImg, bm);

        return bm;
    }

    public void writeExtractedImage(String path) {
        Imgcodecs.imwrite(path, extractedImg);
    }

    public double[] processImage() {
        // Does all the core processing

        /* *********************************************************************
         * Getting blob centers
         * *********************************************************************
         */

        // ** Perform Otsu's thresholding **
        Mat thresholdImage = aImg;
        Imgproc.threshold(aImg, thresholdImage, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

        // ** getting connected components **
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        Imgproc.connectedComponentsWithStats(thresholdImage, labels, stats, centroids);


        // converting stats to java array
        stats.convertTo(stats, CvType.CV_64F);
        int numRows = stats.rows();
        int numCols = stats.cols();
        double[] statsArray = new double[numRows * numCols];
        stats.get(0, 0, statsArray);

        // extracting areas from stats
        double[] areas = new double[numRows];
        for (int i = 0; i < numRows; i++) {
            areas[i] = statsArray[i * numCols + numCols - 1];
        }

        // ** filtering areas **
        double lowerThres = Math.pow(10, 4); // heuristic estimate
        double upperThres = Math.pow(10, 6);

        int validCount = 0;
        boolean[] validBlob = new boolean[numRows];
        for (int i = 0; i < numRows; i++) {
            if (areas[i] > lowerThres && areas[i] < upperThres) {
                validBlob[i] = true;
                validCount++;
            } else {
                validBlob[i] = false;
            }
        }

        // ** getting centers of blobs **
        // Note: x axis is horizontal axis for now
        double[] centerX = new double[validCount];
        double[] centerY = new double[validCount];

        double[] centroidsArray = new double[numRows * 2];
        centroids.get(0, 0, centroidsArray);

        int count = 0;
        for (int i = 0; i < numRows; i++) {
            if (validBlob[i]) {
                centerX[count] = centroidsArray[2 * i];
                centerY[count] = centroidsArray[2 * i + 1];
                count++;
            }
        }

        // finding mean of the centers, which will roughly be the center of the grid
        double gridCenterX = findMean(centerX);
        double gridCenterY = findMean(centerY);

        // Log.i(TAG, "grid center x = " + gridCenterX);

        // shifting center of blobs relative to the grid center
        double[] relativeCenterX = new double[validCount];
        double[] relativeCenterY = new double[validCount];
        for (int i = 0; i < validCount; i++) {
            relativeCenterX[i] = centerX[i] - gridCenterX;
            relativeCenterY[i] = centerY[i] - gridCenterY;
        }

        double varianceX = findVariance(centerX);
        double varianceY = findVariance(centerY);

        // assigning row and column index to each blob
        // Assumption: roughly circular blobs
        double thresholdX = 0.6 * Math.sqrt(varianceX);
        double thresholdY = 0.6 * Math.sqrt(varianceY);

        // Log.i(TAG, "thresholdX = " + thresholdX);

        int[] blobIndicesRow = new int[validCount];
        int[] blobIndicesCol = new int[validCount];

        double[][][] gridCenters = new double[3][3][2];

        /* ************************************************************************
         * Associating each blob with its position in the grid
         * ************************************************************************
         */

        double xVal, yVal;
        for (int i = 0; i < validCount; i++) {
            xVal = relativeCenterX[i];
            yVal = relativeCenterY[i];

            if (xVal < -thresholdX) {
                blobIndicesCol[i] = 0;
            } else if (xVal > thresholdX) {
                blobIndicesCol[i] = 2;
            } else {
                blobIndicesCol[i] = 1;
            }

            if (yVal < -thresholdY) {
                blobIndicesRow[i] = 0;
            } else if (yVal > thresholdY) {
                blobIndicesRow[i] = 2;
            } else {
                blobIndicesRow[i] = 1;
            }

            gridCenters[blobIndicesRow[i]][blobIndicesCol[i]][0] = centerX[i];
            gridCenters[blobIndicesRow[i]][blobIndicesCol[i]][1] = centerY[i];
        }


        /* ************************************************
         * Estimating the center for the unrecognized blob
         * *************************************************
         * Using the property that the blobs are in a rectangular grid
         */
        double slope1X = gridCenters[0][2][0] - gridCenters[0][1][0];
        double slope1Y = gridCenters[0][2][1] - gridCenters[0][1][1];
        double intercept1X = gridCenters[0][1][0] - slope1X;
        double intercept1Y = gridCenters[0][1][1] - slope1Y;

        double slope2X = gridCenters[2][0][0] - gridCenters[1][0][0];
        double slope2Y = gridCenters[2][0][1] - gridCenters[1][0][1];
        double intercept2X = gridCenters[1][0][0] - slope2X;
        double intercept2Y = gridCenters[1][0][1] - slope2Y;

        gridCenters[0][0][0] = 0.5 * (intercept1X + intercept2X);
        gridCenters[0][0][1] = 0.5 * (intercept1Y + intercept2Y);


        /* ***********************************************************
         * Extracting mean intensity for each blob
         * ***********************************************************
         * Square mask is used for each blob
         */

        // get minimum area of the detected blobs
        double minArea = Double.MAX_VALUE;
        for (int i = 0; i < numRows; i++) {
            if (validBlob[i] && areas[i] < minArea) {
                minArea = areas[i];
            }
        }

        // setting mask area to be minArea/3.2 (approximation obtained by geometry)
        double maskAreas = minArea / 3.2;
        int maskEdge = (int) Math.sqrt(maskAreas); // square mask
        int maskTravel = maskEdge / 2;

        // getting the masking representation and mean intensity for each blob
        extractedImg = inputImg.clone();

        double[][] meanIntensity = new double[3][3]; // mean intensity for each blob

        int x, y;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                x = (int) Math.round(gridCenters[i][j][0]) - maskTravel;
                y = (int) Math.round(gridCenters[i][j][1]) - maskTravel;

                // System.out.println("x = " + x + " , y = " + y);
                Rect roi = new Rect(x, y, maskEdge, maskEdge);
                Mat blob = extractedImg.submat(roi);
                blob.setTo(new Scalar(0));

                Mat intensityBlob = grayImg.submat(roi);
                Scalar mean = Core.mean(intensityBlob);

                meanIntensity[i][j] = mean.val[0];
                // System.out.println(meanIntensity[i][j]);
            }
        }

        /* ***********************************************************************
         * Estimating standard curve
         * ***********************************************************************
         * Using log-linear fit
         */
        int numData = 5;
        double[] concArray = {62.5, 125, 250, 500, 1000};
        double[] logConc = new double[numData];
        for (int i = 0; i < 5; i++) {
            logConc[i] = Math.log(concArray[i]);
        }

        double[] intensityArray = {meanIntensity[1][0], meanIntensity[2][0], meanIntensity[2][2], meanIntensity[1][2], meanIntensity[0][2]};

        double[] params = linearRegression(logConc, intensityArray);
        double slope = params[0];
        double intercept = params[1];

        Log.i(TAG, "slope: " + slope + ", intercept: " + intercept);

        /* ***********************************************************************
         * Estimate concentration using standard curve
         * ***********************************************************************
         */

        // For QC1
        double expConc_QC1 = 156;
        double intensity_QC1 = meanIntensity[2][1];
        double calcConc_QC1 = Math.exp((intensity_QC1 - intercept) / slope);

        // For QC2
        double expConc_QC2 = 750;
        double intensity_QC2 = meanIntensity[0][1];
        double calcConc_QC2 = Math.exp((intensity_QC2 - intercept) / slope);

        // Calculating relative errors
        double error_QC1 = Math.abs((expConc_QC1 - calcConc_QC1) / expConc_QC1);
        double error_QC2 = Math.abs((expConc_QC2 - calcConc_QC2) / expConc_QC2);


        // Intensity estimate for the required sample
        double intensity_req = meanIntensity[1][1];
        double calcConc_req = Math.exp((intensity_req - intercept) / slope);

        double[] returnArray = new double[7];

        returnArray[0] = slope;
        returnArray[1] = intercept;
        returnArray[2] = calcConc_QC1;
        returnArray[3] = calcConc_QC2;
        returnArray[4] = error_QC1;
        returnArray[5] = error_QC2;
        returnArray[6] = calcConc_req;

        return returnArray;

    }

    public double[] linearRegression(double[] x, double[] y) {
        // Least-square linear regression y=a*x+b

        int length = x.length;

        double xBar = findMean(x);
        double yBar = findMean(y);


        double xxbar = 0, yybar = 0, xybar = 0;

        for (int i = 0; i < length; i++) {
            xxbar += (x[i] - xBar) * (x[i] - xBar);
            yybar += (y[i] - yBar) * (y[i] - yBar);
            xybar += (x[i] - xBar) * (y[i] - yBar);
        }
        double slope = xybar / xxbar;
        double intercept = yBar - slope * xBar;

        double[] val = {slope, intercept};
        return val;
    }


    public double findMean(double[] input) {
        int length = input.length;
        double sum = 0;

        for (double val : input) {
            sum = sum + val;
        }
        return (sum / length);
    }

    public double findVariance(double[] input) {
        int length = input.length;
        double mean = findMean(input);

        double sum = 0;
        for (double val : input) {
            sum = sum + Math.pow(val - mean, 2);
        }
        return (sum / (length - 1));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Gets data from the incoming Intent
        Bundle extras = intent.getExtras();

        // Do stuff
        String basePath = extras.getString(Constants.BASEPATH_TAG);
        String inputFileName = extras.getString(Constants.INPUTNAME_TAG);
        String extractedFileName = extras.getString(Constants.EXTRACTEDNAME_TAG);


        loadImage(basePath + "/" + inputFileName);
        double[] results = processImage();
        writeExtractedImage(basePath + "/" + extractedFileName);
        sendStatus(results);

    }


    public void sendStatus(double[] results) {
        /*
     * Creates a new Intent containing a Uri object
     * BROADCAST_ACTION is a custom Intent action
     */
        int status = 1;
        Intent localIntent = new Intent(Constants.BROADCAST_ACTION);
        localIntent.putExtra(Constants.EXTENDED_DATA_STATUS, status);
        localIntent.putExtra(Constants.RESULTS_TAG, results);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }
}

