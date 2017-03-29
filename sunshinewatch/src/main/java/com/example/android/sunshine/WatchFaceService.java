package com.example.android.sunshine;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

//taken help from digitalwatchface sample and https://www.codeproject.com/Articles/1031696/Creating-the-watch-faces-for-Android-Wear

public class WatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = "sunWatchFaceService";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new WatchFaceEngine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WatchFaceEngine> mWeakReference;

        public EngineHandler(WatchFaceService.WatchFaceEngine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchFaceService.WatchFaceEngine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    /* implement service callback methods */
    private class WatchFaceEngine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {

        private static final String PATH = "/weather";
        private static final String OWM_MAX = "max";
        private static final String OWM_MIN = "min";
        private static final String OWM_WEATHER_ID = "id";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mAmbient;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean mRegisteredTimeZoneReceiver = false;
        float mYOffsetTime, mYOffsetDate, mYOffsetDivider, mYOffsetWeather, mxOffsetTime, mXOffsetDate;
        private String mMAxTemp;
        private String mMinTemp;
        private int mWeatherId;
        private Calendar calendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar = Calendar.getInstance();
            }
        };
        private Paint mBackgroundPaint, mTextTimePaint, mTextDatePaint, mTextMaxTempPaint, mTextMinPaint;


        // Called when the watch face service is created for the first time
        // We will initialize our drawing components here
        private GoogleApiClient mGoogleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());


            mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            calendar = Calendar.getInstance();

            Resources resources = WatchFaceService.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.colorPrimary));
            mTextTimePaint = createTextPaint(resources.getColor(R.color.time_text), resources.getDimension(R.dimen.timeTextSize), NORMAL_TYPEFACE);
            mTextDatePaint = createTextPaint(resources.getColor(R.color.time_text), resources.getDimension(R.dimen.dateTextSize), NORMAL_TYPEFACE);
            mTextMaxTempPaint = createTextPaint(resources.getColor(R.color.time_text), resources.getDimension(R.dimen.tempTextSize), NORMAL_TYPEFACE);
            mTextMinPaint = createTextPaint(resources.getColor(R.color.time_text), resources.getDimension(R.dimen.tempTextSize), NORMAL_TYPEFACE);

            boolean is24Hour = DateFormat.is24HourFormat(WatchFaceService.this);
            int minute = calendar.get(Calendar.MINUTE);


            String timeText = getTime();

            float timeTextLen = mTextTimePaint.measureText(timeText);
            mxOffsetTime = timeTextLen / 2;


            String dateText = getDate();

            mXOffsetDate = mTextDatePaint.measureText(dateText) / 2;

            mYOffsetTime = resources.getDimension(R.dimen.y_offset_time);
            mYOffsetDate = resources.getDimension(R.dimen.y_offset_date);
            mYOffsetDivider = resources.getDimension(R.dimen.y_offset_divider);
            mYOffsetWeather = resources.getDimension(R.dimen.y_offset_weather);


        }


        private Paint createTextPaint(int textColor, float textSize, Typeface typeface) {
            Paint paint = new Paint();

            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            paint.setTextSize(textSize);

            return paint;
        }

        private String getTime() {

            boolean is24Hour = DateFormat.is24HourFormat(WatchFaceService.this);
            int minute = calendar.get(Calendar.MINUTE);


            String timeText;
            if (is24Hour) {
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                timeText = String.format("%02d:%02d", hour, minute);
            } else {
                int hour = calendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                timeText = String.format("%d:%02d", hour, minute);
            }

            return timeText;
        }

        private String getDate() {


            String dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
            String monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            int year = calendar.get(Calendar.YEAR);
            String dateText = String.format("%s, %s %d, %d", dayName.toUpperCase(), monthName.toUpperCase(), dayOfMonth, year);

            return dateText;

        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        // Called every minutes
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                if (mLowBitAmbient) {
                    mBackgroundPaint.setAntiAlias(!inAmbientMode);
                    mTextTimePaint.setAntiAlias(!inAmbientMode);
                    mTextDatePaint.setAntiAlias(!inAmbientMode);
                    mTextMinPaint.setAntiAlias(!inAmbientMode);
                    mTextMaxTempPaint.setAntiAlias(!inAmbientMode);

                    invalidate();
                }

                // Whether the timer should be running depends on whether we're visible (as well as
                // whether we're in ambient mode), so we may need to start or stop the timer.
                updateTimer();
            }
        }

        private void updateTimer() {
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

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);
// Draw background color
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            calendar.setTimeInMillis(System.currentTimeMillis());

            // Draw time text

            String timeText = getTime();
            canvas.drawText(timeText, bounds.centerX() - mxOffsetTime, mYOffsetTime, mTextTimePaint);

            // Draw date text
            String dateText = getDate();
            if (isInAmbientMode()) {
                mTextDatePaint.setColor(getResources().getColor(R.color.time_text));
            } else {
                mTextDatePaint.setColor(getResources().getColor(R.color.colorPrimaryLight));
            }
            canvas.drawText(dateText, bounds.centerX() - mXOffsetDate, mYOffsetDate, mTextDatePaint);

            if (mMAxTemp != null && mMinTemp != null) {
                // divider
                canvas.drawLine(bounds.centerX() - 25, mYOffsetDivider, bounds.centerX() + 25, mYOffsetDivider, mTextDatePaint);

                float highTextSize = mTextMaxTempPaint.measureText(mMAxTemp);
                if (mAmbient) {
                    mTextMinPaint.setColor(getResources().getColor(R.color.time_text));
                    float lowTextSize = mTextMinPaint.measureText(mMinTemp);
                    float xOffset = bounds.centerX() - ((highTextSize + lowTextSize + 20) / 2);
                    canvas.drawText(mMAxTemp, xOffset, mYOffsetWeather, mTextMaxTempPaint);
                    canvas.drawText(mMinTemp, xOffset + highTextSize + 20, mYOffsetWeather, mTextMinPaint);
                } else {
                    mTextMinPaint.setColor(getResources().getColor(R.color.colorPrimaryLight));
                    float xOffset = bounds.centerX() - (highTextSize / 2);
                    canvas.drawText(mMAxTemp, xOffset, mYOffsetWeather, mTextMaxTempPaint);
                    canvas.drawText(mMinTemp, bounds.centerX() + (highTextSize / 2) + 20, mYOffsetWeather, mTextMinPaint);

                    // Draw weather icon
                    Drawable b = getResources().getDrawable(IconUtility.getSmallArtResourceIdForWeatherCondition(mWeatherId));
                    Bitmap icon = ((BitmapDrawable) b).getBitmap();
                    float scaledWidth = (mTextMaxTempPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
                    Bitmap weatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTextMaxTempPaint.getTextSize(), true);
                    float iconXOffset = bounds.centerX() - ((highTextSize / 2) + weatherIcon.getWidth() + 25);
                    canvas.drawBitmap(weatherIcon, iconXOffset, mYOffsetWeather - weatherIcon.getHeight(), null);
                }
            }


        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                calendar = Calendar.getInstance();


            } else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, WatchFaceEngine.this);
                    mGoogleApiClient.disconnect();
                }
                unregisterReceiver();

            }
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();


        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, WatchFaceEngine.this);
            Log.d(TAG, "Connected");

        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Connection Suspended");


        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "Connection error");


        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            Log.d(TAG, "data change called");

            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mMAxTemp = dataMap.getString(OWM_MAX);
                        mMinTemp = dataMap.getString(OWM_MIN);
                        mWeatherId = dataMap.getInt(OWM_WEATHER_ID);
                        Log.d("got data", "\nMax: " + mMAxTemp + "\nMin : " + mMinTemp + "\nweathrID: " + mWeatherId);
                        invalidate();
                    }
                }
            }

        }


    }


}