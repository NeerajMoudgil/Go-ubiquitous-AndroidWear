package com.example.sunshinewatch;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Locale;



public class WatchFaceService extends CanvasWatchFaceService  {

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new WatchFaceEngine();
    }

    /* implement service callback methods */
    private class WatchFaceEngine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener{

        private static final String PATH = "/weather";
        private static final String OWM_MAX = "max";
        private static final String OWM_MIN = "min";
        private static final String OWM_WEATHER_ID = "id";

        private String mMAxTemp;
        private String mMinTemp;
        private int mWeatherId;


        private long time;
        private SimpleDateFormat dateFormat;
        private SimpleDateFormat timeFormat;

        private TextPaint timePaint;
        private TextPaint datePaint;
        private int backgroundColor;

        // Called when the watch face service is created for the first time
        // We will initialize our drawing components here


      private GoogleApiClient mGoogleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            time = System.currentTimeMillis();

            dateFormat = new SimpleDateFormat("EEEE MMMM dd", Locale.getDefault());
            timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            // The time paint
            timePaint = new TextPaint();
            timePaint.setColor(Color.WHITE);
            timePaint.setAntiAlias(true);
            timePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics()));

            // The date paint
            datePaint = new TextPaint();
            datePaint.setColor(Color.WHITE);
            datePaint.setAntiAlias(true);
            datePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics()));

            backgroundColor = Color.parseColor("#607D8B");

             mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        // Called every minutes
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            time = System.currentTimeMillis();
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);
            // We draw the watch face here

            Rect timeBounds = new Rect();
            String timeText = timeFormat.format(time);
            int timeX;
            int timeY;

            String dateText = dateFormat.format(time);
            Rect dateBounds = new Rect();
            int dateX;
            int dateY;

            timePaint.getTextBounds(timeText, 0, timeText.length(), timeBounds);
            timeX = Math.abs(bounds.centerX() - timeBounds.centerX());
            timeY = Math.round((Math.abs(bounds.centerY())) - (bounds.height() * 0.02f));

            datePaint.getTextBounds(dateText, 0, dateText.length(), dateBounds);
            dateX = Math.abs(bounds.centerX() - dateBounds.centerX());
            dateY = Math.round((bounds.centerY() + dateBounds.height()) + (bounds.height() * 0.02f));

            // We draw the background color
            canvas.drawColor(backgroundColor);

            // We draw the date and the time
            canvas.drawText(timeText, timeX, timeY, timePaint);
            canvas.drawText(dateText, dateX, dateY, datePaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                mGoogleApiClient.connect();


            } else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, WatchFaceEngine.this);
                    mGoogleApiClient.disconnect();
                }
            }


        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, WatchFaceEngine.this);
            Log.d("at wear", "Connected");

        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("at wear", "Connection Suspended");


        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("at wear", "Connection error");


        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            Log.d("data change called","data change called");

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