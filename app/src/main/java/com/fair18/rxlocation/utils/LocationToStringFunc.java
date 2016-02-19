package com.fair18.rxlocation.utils;

import android.location.Location;

import rx.functions.Func1;

/**
 * Created by Yuriy on 2016-02-18.
 * RxLocation.
 */
public class LocationToStringFunc implements Func1<Location, String> {
    @Override
    public String call(Location location) {
        if (location != null)
            return location.getLatitude() + " " + location.getLongitude() + " (" + location.getAccuracy() + ")";
        return "no location available";
    }
}
