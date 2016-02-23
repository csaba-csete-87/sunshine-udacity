/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nordlogic.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "MyWatchFace";

    private static final Typeface BOLD_TYPEFACE =
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
        Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredWeatherReceiver = false;
        Paint mBackgroundPaint;
        Paint mWatchTextPaint;
        Paint mDateTextPaint;
        Paint mSeparatorPaint;
        Paint mWeatherTextPaint;
        Calendar mCalendar;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(Wearable.API)
            .build();

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        float mXOffsetWatch;
        float mYOffsetWatch;
        float mXOffsetDate;
        float mYOffsetDate;
        float mYOffsetSeparator;
        float mYOffsetWeather;
        float mSeparatorLength;
        float mXOffsetWeather;
        private float mYAllOffset;
        private SimpleDateFormat mDayOfWeekFormat;
        private java.text.DateFormat mDateFormat;
        private Date mDate;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                .setShowSystemUiTime(false)
                .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffsetWatch = resources.getDimension(R.dimen.digital_y_offset);
            mYOffsetDate = resources.getDimension(R.dimen.date_y_offset);
            mYOffsetSeparator = resources.getDimension(R.dimen.separator_y_offset);
            mYOffsetWeather = resources.getDimension(R.dimen.weather_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mWatchTextPaint = new Paint();
            mWatchTextPaint = createTextPaint(resources.getColor(R.color.main));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.secondary));

            mSeparatorPaint = new Paint();
            mSeparatorPaint = createTextPaint(resources.getColor(R.color.secondary));

            mWeatherTextPaint = new Paint();
            mWeatherTextPaint = createTextPaint(resources.getColor(R.color.main));

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            return createTextPaint(textColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = new SimpleDateFormat("MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffsetWatch = resources.getDimension(isRound
                ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float digitalTextSize = resources.getDimension(isRound
                ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            float weatherTextSize = resources.getDimension(isRound
                ? R.dimen.weather_text_size_round : R.dimen.weather_text_size);

            mXOffsetDate = resources.getDimension(isRound
                ? R.dimen.date_x_offset_round : R.dimen.date_x_offset);
            mSeparatorLength = resources.getDimension(R.dimen.separator_length);
            mXOffsetWeather = resources.getDimension(isRound
                ? R.dimen.weather_x_offset_round : R.dimen.weather_x_offset);
            mYAllOffset = resources.getDimension(R.dimen.all_y_offset_round);

            if (isRound) {
                mYOffsetWatch += mYAllOffset;
                mYOffsetDate += mYAllOffset;
                mYOffsetSeparator += mYAllOffset;
                mYOffsetWeather += mYAllOffset;
            }

            mWatchTextPaint.setTextSize(digitalTextSize);
            mDateTextPaint.setTextSize(dateTextSize);
            mWeatherTextPaint.setTextSize(weatherTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mWatchTextPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                    + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mLowBitAmbient) {
                mWatchTextPaint.setAntiAlias(!inAmbientMode);
            }
            invalidate();
            updateTimer();
//            super.onAmbientModeChanged(inAmbientMode);
//            if (Log.isLoggable(TAG, Log.DEBUG)) {
//                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
//            }
//            adjustPaintColorToCurrentMode(mBackgroundPaint, mInteractiveBackgroundColor,
//                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
//            adjustPaintColorToCurrentMode(mHourPaint, mInteractiveHourDigitsColor,
//                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
//            adjustPaintColorToCurrentMode(mMinutePaint, mInteractiveMinuteDigitsColor,
//                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
//            // Actually, the seconds are not rendered in the ambient mode, so we could pass just any
//            // value as ambientColor here.
//            adjustPaintColorToCurrentMode(mSecondPaint, mInteractiveSecondDigitsColor,
//                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);
//
//            if (mLowBitAmbient) {
//                boolean antiAlias = !inAmbientMode;
//                mDatePaint.setAntiAlias(antiAlias);
//                mHourPaint.setAntiAlias(antiAlias);
//                mMinutePaint.setAntiAlias(antiAlias);
//                mSecondPaint.setAntiAlias(antiAlias);
//                mAmPmPaint.setAntiAlias(antiAlias);
//                mColonPaint.setAntiAlias(antiAlias);
//            }
//            invalidate();
//
//            // Whether the timer should be running depends on whether we're in ambient mode (as well
//            // as whether we're visible), so we may need to start or stop the timer.
//            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor, int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            String hourText = String.format("%d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE));
            String dateText = String.format("%s, %s", mDayOfWeekFormat.format(mDate), mDateFormat.format(mDate));

            drawCenteredText(canvas, mWatchTextPaint, hourText, mYOffsetWatch);
            drawCenteredText(canvas, mDateTextPaint, dateText.toUpperCase(), mYOffsetDate);
            drawCenteredLine(canvas, mSeparatorPaint, mSeparatorLength, mYOffsetSeparator);
            drawCenteredText(canvas, mWeatherTextPaint, "25° 26°", mYOffsetWeather);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void updateConfigDataItemAndUiOnStartup() {
            DigitalWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                new DigitalWatchFaceUtil.FetchConfigDataMapCallback() {
                    @Override
                    public void onConfigDataMapFetched(DataMap startupConfig) {
                        // If the DataItem hasn't been created yet or some keys are missing,
                        // use the default values.
                        setDefaultValuesForMissingConfigKeys(startupConfig);
                        DigitalWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                        updateUiForConfigDataMap(startupConfig);
                    }
                }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_BACKGROUND_COLOR,
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_HOURS_COLOR,
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_MINUTES_COLOR,
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            addIntKeyIfMissing(config, DigitalWatchFaceUtil.KEY_SECONDS_COLOR,
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        public void drawCenteredText(Canvas canvas, Paint paint, String text, float offsetY) {
            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);
            int x = (canvas.getWidth() / 2) - (bounds.width() / 2);
            canvas.drawText(text, x, offsetY, paint);
        }

        private void drawCenteredLine(Canvas canvas, Paint paint, float length, float offsetY) {
            float x = (canvas.getWidth() / 2) - (length / 2);
            canvas.drawLine(x, offsetY, x + length, offsetY, paint);
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int color = config.getInt(configKey);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                        + Integer.toHexString(color));
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.e(TAG, "" + dataEventBuffer);
            Log.e(TAG, "" + dataEventBuffer);
            Log.e(TAG, "" + dataEventBuffer);
            Log.e(TAG, "" + dataEventBuffer);
            Log.e(TAG, "" + dataEventBuffer);
            Log.d("LiveSessionDataFragment", "ON DATA CHANGED");
            for (DataEvent event : dataEventBuffer) {
                Log.d(TAG, "ITERATING EVENTS");
                if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains("/weather")) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    Log.e("test", "" + dataMapItem.getDataMap().get("high"));
                    Log.e("test", "" + dataMapItem.getDataMap().get("low"));
                    Log.e("test", "" + dataMapItem.getDataMap().get("iconId"));
                }
            }

            dataEventBuffer.release();
//            for (DataEvent dataEvent : dataEvents) {
//                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
//                    continue;
//                }
//
//                DataItem dataItem = dataEvent.getDataItem();
//                if (!dataItem.getUri().getPath().equals(
//                    DigitalWatchFaceUtil.PATH_WITH_FEATURE)) {
//                    continue;
//                }
//
//                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
//                DataMap config = dataMapItem.getDataMap();
//                if (Log.isLoggable(TAG, Log.DEBUG)) {
//                    Log.d(TAG, "Config DataItem updated:" + config);
//                }
//                updateUiForConfigDataMap(config);
//            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + bundle);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }
    }
}
