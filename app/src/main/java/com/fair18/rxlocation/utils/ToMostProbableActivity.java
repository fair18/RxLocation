package com.fair18.rxlocation.utils;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import rx.functions.Func1;

/**
 * Created by Yuriy on 2016-02-18.
 * RxLocation.
 */
public class ToMostProbableActivity implements Func1<ActivityRecognitionResult, DetectedActivity> {
    @Override
    public DetectedActivity call(ActivityRecognitionResult activityRecognitionResult) {
        return activityRecognitionResult.getMostProbableActivity();
    }
}

