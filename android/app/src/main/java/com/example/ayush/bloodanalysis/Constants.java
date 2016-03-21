package com.example.ayush.bloodanalysis;

/**
 * Created by ayush on 16/3/16.
 */
public final class Constants {
    // Defines a custom Intent action
    public static final String BROADCAST_ACTION =
            "com.example.ayush.bloodanalysis.BROADCAST";

    // Defines the key for the status "extra" in an Intent
    public static final String EXTENDED_DATA_STATUS =
            "com.example.ayush.bloodanalysis.STATUS";

    // Defines the key to pass base of the path for images
    public static final String BASEPATH_TAG = "com.example.ayush.bloodanalysis.BASEPATH";

    // Defines the key to pass filename of the input image
    public static final String INPUTNAME_TAG = "com.example.ayush.bloodanalysis.INPUTNAME";

    // Defines the key to pass filename of the input image
    public static final String EXTRACTEDNAME_TAG = "com.example.ayush.bloodanalysis.EXTRACTEDNAME";

    // Define the keys for the state of intentservice
    public static final String ISSTARTED_KEY = "com.example.ayush.bloodanalysis.ISSTARTED";
    public static final String ISFINISHED_KEY = "com.example.ayush.bloodanalysis.ISFINISHED";

    // Defines the key for results
    public static final String RESULTS_TAG = "com.example.ayush.bloodanalysis.RESULTS";
}
