package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by csaba.csete on 2016-02-26.
 */
public class WeatherBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, WearableUpdaterService.class);
        int iconId = intent.getIntExtra(WearableUpdaterService.EXTRA_WEATHER_ID, 0);
        double high = intent.getDoubleExtra(WearableUpdaterService.EXTRA_HIGH, 0);
        double low = intent.getDoubleExtra(WearableUpdaterService.EXTRA_LOW, 0);
        i.putExtra(WearableUpdaterService.EXTRA_WEATHER_ID, iconId);
        i.putExtra(WearableUpdaterService.EXTRA_HIGH, high);
        i.putExtra(WearableUpdaterService.EXTRA_LOW, low);
        context.startService(i);
    }

}