package com.example.android.sunshine.app.sync;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.example.android.sunshine.app.events.UpdateWearableEvent;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.greenrobot.eventbus.EventBus;

public class SunshineSyncService extends Service implements GoogleApiClient.ConnectionCallbacks {
    private static final Object sSyncAdapterLock = new Object();
    private static SunshineSyncAdapter sSunshineSyncAdapter = null;
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        initGoogleApiClient();
        Log.d("SunshineSyncService", "onCreate - SunshineSyncService");
        synchronized (sSyncAdapterLock) {
            if (sSunshineSyncAdapter == null) {
                sSunshineSyncAdapter = new SunshineSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSunshineSyncAdapter.getSyncAdapterBinder();
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    public void onEvent(UpdateWearableEvent e) {
        if (mGoogleApiClient.isConnected()) {
            sendDataToWearable(e);
        } else {
            Log.e("onEVENT", "NOT CONNECTED TO GOOGLE API CLIENT");
        }
    }

    private void initGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addApi(Wearable.API)
            .build();
    }

    public void sendDataToWearable(UpdateWearableEvent e) {
        Log.d("onEvent", "Sending data to wearable");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather");

        putDataMapRequest.getDataMap().putInt("iconId", e.getIconId());
        putDataMapRequest.getDataMap().putDouble("high", e.getHigh());
        putDataMapRequest.getDataMap().putDouble("low", e.getLow());

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request).await();
    }

}