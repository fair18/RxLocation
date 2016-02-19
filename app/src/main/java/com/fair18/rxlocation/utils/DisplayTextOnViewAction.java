package com.fair18.rxlocation.utils;

import android.widget.TextView;

import rx.functions.Action1;

/**
 * Created by Yuriy on 2016-02-18.
 * RxLocation.
 */
public class DisplayTextOnViewAction implements Action1<String> {
    private final TextView target;

    public DisplayTextOnViewAction(TextView target) {
        this.target = target;
    }

    @Override
    public void call(String s) {
        target.setText(s);
    }
}
