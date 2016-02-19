package com.fair18.rxlocation;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.fair18.rxlocation.utils.AddressToStringFunc;
import com.fair18.rxlocation.utils.DetectedActivityToString;
import com.fair18.rxlocation.utils.DisplayTextOnViewAction;
import com.fair18.rxlocation.utils.LocationToStringFunc;
import com.fair18.rxlocation.utils.ToMostProbableActivity;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.List;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static rx.android.app.AppObservable.bindActivity;

public class MainActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback {
    private static final int REQUEST_CHECK_SETTINGS = 0;
    private final String TAG = getClass().getSimpleName();
    private static final int REQUEST_LOCATION = 1;
    private static String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION};

    private ReactiveLocationProvider locationProvider;

    private TextView lastKnownLocationView;
    private TextView updatableLocationView;
    private TextView addressLocationView;
    private TextView currentActivityView;
    private LinearLayoutCompat mLayoutCompat;

    private Observable<Location> lastKnownLocationObservable;
    private Observable<Location> locationUpdatesObservable;
    private Observable<ActivityRecognitionResult> activityObservable;

    private Subscription lastKnownLocationSubscription;
    private Subscription updatableLocationSubscription;
    private Subscription addressSubscription;
    private Subscription activitySubscription;
    private Observable<String> addressObservable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lastKnownLocationView = (TextView) findViewById(R.id.last_known_location_view);
        updatableLocationView = (TextView) findViewById(R.id.updated_location_view);
        addressLocationView = (TextView) findViewById(R.id.address_for_location_view);
        currentActivityView = (TextView) findViewById(R.id.activity_recent_view);
        mLayoutCompat = (LinearLayoutCompat) findViewById(R.id.sample_main_layout);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
        } else {
            requestLocation();
        }
    }

    private void requestLocation() {
        locationProvider = new ReactiveLocationProvider(getApplicationContext());
        lastKnownLocationObservable = locationProvider.getLastKnownLocation();

        final LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(5)
                .setInterval(100);
        locationUpdatesObservable = locationProvider
                .checkLocationSettings(
                        new LocationSettingsRequest.Builder()
                                .addLocationRequest(locationRequest)
                                .setAlwaysShow(true)  //Refrence: http://stackoverflow.com/questions/29824408/google-play-services-locationservices-api-new-option-never
                                .build()
                )
                .doOnNext(new Action1<LocationSettingsResult>() {
                    @Override
                    public void call(LocationSettingsResult locationSettingsResult) {
                        Status status = locationSettingsResult.getStatus();
                        if (status.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                            try {
                                status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException th) {
                                Log.e("MainActivity", "Error opening settings activity.", th);
                            }
                        }
                    }
                })
                .flatMap(new Func1<LocationSettingsResult, Observable<Location>>() {
                    @Override
                    public Observable<Location> call(LocationSettingsResult locationSettingsResult) {
                        return locationProvider.getUpdatedLocation(locationRequest);
                    }
                });

        addressObservable = locationProvider.getUpdatedLocation(locationRequest)
                .flatMap(new Func1<Location, Observable<List<Address>>>() {
                    @Override
                    public Observable<List<Address>> call(Location location) {
                        return locationProvider.getReverseGeocodeObservable(location.getLatitude(), location.getLongitude(), 1);
                    }
                })
                .map(new Func1<List<Address>, Address>() {
                    @Override
                    public Address call(List<Address> addresses) {
                        return addresses != null && !addresses.isEmpty() ? addresses.get(0) : null;
                    }
                })
                .map(new AddressToStringFunc())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());

        activityObservable = locationProvider.getDetectedActivity(50);
    }

    private void requestLocationPermissions() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {

            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example, if the request has been denied previously.
            Log.i(TAG,
                    "Displaying contacts permission rationale to provide additional context.");

            // Display a SnackBar with an explanation and a button to trigger the request.
            Snackbar.make(mLayoutCompat, R.string.permission_location_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat
                                    .requestPermissions(MainActivity.this, PERMISSIONS,
                                            REQUEST_LOCATION);
                        }
                    })
                    .show();
        } else {
            // Contact permissions have not been granted yet. Request them directly.
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_LOCATION);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
        } else {

            lastKnownLocationSubscription = lastKnownLocationObservable
                    .map(new LocationToStringFunc())
                    .subscribe(new DisplayTextOnViewAction(lastKnownLocationView), new ErrorHandler());

            updatableLocationSubscription = locationUpdatesObservable
                    .map(new LocationToStringFunc())
                    .map(new Func1<String, String>() {
                        int count = 0;

                        @Override
                        public String call(String s) {
                            return s + " " + count++;
                        }
                    })
                    .subscribe(new DisplayTextOnViewAction(updatableLocationView), new ErrorHandler());


            addressSubscription = bindActivity(this, addressObservable)
                    .subscribe(new DisplayTextOnViewAction(addressLocationView), new ErrorHandler());

            activitySubscription = bindActivity(this, activityObservable)
                    .map(new ToMostProbableActivity())
                    .map(new DetectedActivityToString())
                    .subscribe(new DisplayTextOnViewAction(currentActivityView), new ErrorHandler());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (updatableLocationSubscription != null) updatableLocationSubscription.unsubscribe();
        if (addressSubscription != null) addressSubscription.unsubscribe();
        if (lastKnownLocationSubscription != null) lastKnownLocationSubscription.unsubscribe();
        if (activitySubscription != null) activitySubscription.unsubscribe();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Geofencing").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // startActivity(new Intent(MainActivity.this, GeofenceActivity.class));
                return true;
            }
        });
        menu.add("Places").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (TextUtils.isEmpty(getString(R.string.API_KEY))) {
                    Toast.makeText(MainActivity.this, "First you need to configure your API Key - see README.md", Toast.LENGTH_SHORT).show();
                } else {
                    ///        startActivity(new Intent(MainActivity.this, PlacesActivity.class));
                }
                return true;
            }
        });
        menu.add("Mock Locations").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // startActivity(new Intent(MainActivity.this, MockLocationsActivity.class));
                return true;
            }
        });
        return true;
    }

    private class ErrorHandler implements Action1<Throwable> {
        @Override
        public void call(Throwable throwable) {
            Toast.makeText(MainActivity.this, "Error occurred.", Toast.LENGTH_SHORT).show();
            Log.d("MainActivity", "Error occurred", throwable);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);//intent);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                //Refrence: https://developers.google.com/android/reference/com/google/android/gms/location/SettingsApi
                switch (resultCode) {
                    case RESULT_OK:
                        // All required changes were successfully made
                        Log.d(TAG, "User enabled location");
                        break;
                    case RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        Log.d(TAG, "User Cancelled enabling location");
                        break;
                    default:
                        break;
                }
                break;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission has been granted, preview can be displayed
                Log.i(TAG, "LOCATION permission has now been granted.");
                Snackbar.make(mLayoutCompat, R.string.permision_available_location,
                        Snackbar.LENGTH_SHORT).show();
                requestLocation();
                onStart();
            } else {
                Log.i(TAG, "LOCATION permission was NOT granted.");
                Snackbar.make(mLayoutCompat, R.string.permissions_not_granted,
                        Snackbar.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
