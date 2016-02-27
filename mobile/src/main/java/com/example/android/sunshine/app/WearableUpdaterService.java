package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearableUpdaterService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String EXTRA_WEATHER_ID = "iconId";
    public static final String EXTRA_HIGH = "high";
    public static final String EXTRA_LOW = "low";
    private static final String EXTRA_TIME_STAMP = "timestamp";

    private GoogleApiClient mGoogleApiClient;
    private int mIconId;
    private double mHigh;
    private double mLow;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mIconId = intent.getIntExtra(EXTRA_WEATHER_ID, 0);
            mHigh = intent.getDoubleExtra(EXTRA_HIGH, 0);
            mLow = intent.getDoubleExtra(EXTRA_LOW, 0);
            initGoogleApiClient();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onConnected(Bundle bundle) {
        sendDataToWearable();
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (i != -999) {
            Log.d("onConnectionSuspended", "" + i);
        }
    }

    private void initGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(Wearable.API)
            .build();
        mGoogleApiClient.connect();

    }

    public void sendDataToWearable() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather");

        putDataMapRequest.getDataMap().putInt(EXTRA_WEATHER_ID, mIconId);
        putDataMapRequest.getDataMap().putDouble(EXTRA_HIGH, mHigh);
        putDataMapRequest.getDataMap().putDouble(EXTRA_LOW, mLow);
        putDataMapRequest.getDataMap().putLong(EXTRA_TIME_STAMP, System.currentTimeMillis());
        putDataMapRequest.setUrgent();

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                if (dataItemResult != null) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap();
                    Log.d("iconId", "" + dataMap.getInt(EXTRA_WEATHER_ID));
                    Log.d("high", "" + dataMap.getDouble(EXTRA_HIGH));
                    Log.d("low", "" + dataMap.getDouble(EXTRA_LOW));
                }
                stopSelf();
            }
        });
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("onConnectionFailed", "" + connectionResult.getErrorMessage());
    }
}
