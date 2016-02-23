package com.example.android.sunshine.app.events;

/**
 * Created by csaba.csete on 2016-02-23.
 */
public class UpdateWearableEvent {
    int iconId;
    double high;
    double low;

    public UpdateWearableEvent(int iconId, double high, double low) {
        this.iconId = iconId;
        this.high = high;
        this.low = low;
    }

    public int getIconId() {
        return iconId;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }
}
