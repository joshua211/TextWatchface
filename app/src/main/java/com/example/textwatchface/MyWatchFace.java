package com.example.textwatchface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final int COMPLICATION_ID = 0;
    private static final int[] COMPLICATION_IDS = {COMPLICATION_ID};

    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            }
    };

    static int getComplicationId() {
        return COMPLICATION_ID;
    }

    static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    static int[] getSupportedComplicationTypes() {
        return COMPLICATION_SUPPORTED_TYPES[COMPLICATION_ID];
    }

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
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        /* Handler to update the time once a second in interactive mode. */
        private final Handler updateTimeHandler = new EngineHandler(this);
        private Calendar calendar;
        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                battery = (int)(level * 100 / (float)scale);
            }
        };
        private boolean registeredTimeZoneReceiver = false;
        private boolean registeredBatteryReceiver = false;
        private boolean muteMode;
        private boolean showDate;
        private float centerX;
        private float height;
        private float width;
        private float centerY;
        private int battery;
        private int textSize = 55;
        private int textColor = Color.WHITE;
        private int hourColor = Color.RED;
        private int miscColor = Color.WHITE;
        private Paint textPaint;
        private Paint hourPaint;
        private Paint miscPaint;
        private boolean isAmbient;
        private boolean isLowBitAmbient;
        private boolean isBurnInProtecrion;

        private SparseArray<ComplicationData> activeComplicationDataSparseArray;
        private SparseArray<ComplicationDrawable> complicationDrawableSparseArray;

        private String[] hourNames = {
                "zw??lf",
                "eins",
                "zwei",
                "drei",
                "vier",
                "f??nf",
                "sechs",
                "sieben",
                "acht",
                "neun",
                "zehn",
                "elf",
                "zw??lf"
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .setHideStatusBar(true)
                    .build());

            calendar = Calendar.getInstance();

            initializeComplications();
            initializePaint();
        }

        private void initializeComplications() {
            activeComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            ComplicationDrawable drawable = (ComplicationDrawable) getDrawable(R.drawable.custom_complication_styles);
            drawable.setContext(getApplicationContext());

            complicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            complicationDrawableSparseArray.put(COMPLICATION_ID, drawable);

            setActiveComplications(COMPLICATION_IDS);
        }

        private void initializePaint() {
            textPaint = new Paint();
            textPaint.setColor(textColor);
            textPaint.setTypeface(getResources().getFont(R.font.montserrat));
            textPaint.setTextSize(textSize);
            textPaint.setAntiAlias(true);

            hourPaint = new Paint();
            hourPaint.setColor(hourColor);
            hourPaint.setTypeface(getResources().getFont(R.font.montserrat));
            hourPaint.setTextSize(textSize);
            hourPaint.setAntiAlias(true);

            miscPaint = new Paint();
            miscPaint.setColor(miscColor);
            miscPaint.setTypeface(getResources().getFont(R.font.baloo));
            miscPaint.setTextSize(textSize/2);
            miscPaint.setAntiAlias(true);
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            isLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            isBurnInProtecrion = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onComplicationDataUpdate(int watchFaceComplicationId, ComplicationData data) {
            activeComplicationDataSparseArray.put(watchFaceComplicationId, data);

            ComplicationDrawable complicationDrawable =
                    complicationDrawableSparseArray.get(watchFaceComplicationId);
            complicationDrawable.setComplicationData(data);

            invalidate();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            isAmbient = inAmbientMode;
            updateTimer();
            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationDrawable = complicationDrawableSparseArray.get(COMPLICATION_IDS[i]);
                complicationDrawable.setInAmbientMode(isAmbient);
            }
        }


        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            centerX = width / 2f;
            centerY = height / 2f;
            this.width = width;
            this.height = height;

            int complicationSize = width / 4;

            Rect bounds = new Rect((int)(centerX - complicationSize/2), height - complicationSize, (int)(centerX + complicationSize/2), height-15);
            complicationDrawableSparseArray.get(COMPLICATION_ID).setBounds(bounds);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TAP:
                    int tappedComplicationId = getTappedComplicationId(x, y);
                    if (tappedComplicationId != -1) {
                        onComplicationTap(tappedComplicationId);
                        break;
                    }

                    showDate = true;
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            showDate = false;
                        }
                    }, 3000);
                    break;
            }
            invalidate();
        }

        private int getTappedComplicationId(int x, int y) {

            int complicationId;
            ComplicationData complicationData;
            ComplicationDrawable complicationDrawable;

            long currentTimeMillis = System.currentTimeMillis();

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationData = activeComplicationDataSparseArray.get(complicationId);

                if ((complicationData != null)
                        && (complicationData.isActive(currentTimeMillis))
                        && (complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED)
                        && (complicationData.getType() != ComplicationData.TYPE_EMPTY)) {

                    complicationDrawable = complicationDrawableSparseArray.get(complicationId);
                    Rect complicationBoundingRect = complicationDrawable.getBounds();

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return complicationId;
                        }
                    } else {
                        Log.e("WATCHFACE", "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        // Fires PendingIntent associated with complication (if it has one).
        private void onComplicationTap(int complicationId) {
            Log.d("WATCHFACE", "onComplicationTap()");

            ComplicationData complicationData =
                    activeComplicationDataSparseArray.get(complicationId);

            if (complicationData != null) {

                if (complicationData.getTapAction() != null) {
                    try {
                        complicationData.getTapAction().send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e("WATCHFACE", "onComplicationTap() tap action error: " + e);
                    }

                } else if (complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {
                    ComponentName componentName = new ComponentName(
                            getApplicationContext(),
                            MyWatchFace.class);

                    Intent permissionRequestIntent =
                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                    getApplicationContext(), componentName);

                    startActivity(permissionRequestIntent);
                }

            } else {
                Log.d("WATCHFACE", "No PendingIntent for complication " + complicationId + ".");
            }
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);
            canvas.drawColor(Color.BLACK);
            if(showDate)
                drawDate(canvas);
            else
                drawWatchFace(canvas);
            drawMisc(canvas);
            drawComplications(canvas, now);
        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            int complicationId;
            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationDrawable = complicationDrawableSparseArray.get(complicationId);

                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }

        private void drawDate(Canvas canvas) {
            Date date = calendar.getTime();
            String dateString = new SimpleDateFormat("dd.MM.yyyy").format(date);
            String weekday = new SimpleDateFormat("EEEE", Locale.GERMAN).format(date);
            float width = textPaint.measureText(weekday);
            canvas.drawText(weekday, centerX - width/2, centerY - textSize, hourPaint);
            width = textPaint.measureText(dateString);
            canvas.drawText(dateString, centerX - width/2, centerY +textSize, textPaint);
        }

        private void drawMisc(Canvas canvas) {
            Date time = calendar.getTime();
            String timeString = new SimpleDateFormat("HH:mm").format(time);
            String text = timeString + "         " + String.valueOf(battery) + "%";
            float textWidth = miscPaint.measureText(text);
            canvas.drawText(text, centerX - textWidth/2, textSize + 10, miscPaint);
        }

        private String[] getTimeString(Calendar calendar) {
            int hour = calendar.get(Calendar.HOUR);
            int minute = calendar.get(Calendar.MINUTE);

            if(minute >= 3 && minute <=6 )
                return new String[] {"f??nf", "nach", getHourString(hour)};
            if(minute >= 7 && minute <= 12)
                return new String[] {"zehn", "nach", getHourString(hour)};
            if(minute >= 13 && minute <= 17)
                return new String[] {"viertel", "nach", getHourString(hour)};
            if(minute >= 18 && minute <= 22)
                return new String[] {"zwanzig", "nach", getHourString(hour)};
            if(minute >= 23 && minute <= 27)
                return new String[] {"kurz", "vor", "halb", getHourString(hour +1)};
            if(minute >= 28 && minute <= 32)
                return new String[] {"halb", getHourString(hour +1)};
            if(minute >= 33 && minute <= 37)
                return new String[] {"kurz" ,"nach", "halb", getHourString(hour +1)};
            if(minute >= 38 && minute <= 42)
                return new String[] {"zwanzig", "vor", getHourString(hour +1)};
            if(minute >= 43 && minute <= 47)
                return new String[] {"viertel", "vor", getHourString(hour +1)};
            if(minute >= 48 && minute <= 52)
                return new String[] {"zehn", "vor", getHourString(hour +1)};
            if(minute >= 53 && minute <= 57)
                return new String[] {"f??nf", "vor", getHourString(hour +1)};

            return new String[] {getHourString(hour), "uhr"};
        }

        private String getHourString(int hour) {
                int newHour = hour > 12 ? hour-12 : hour;
                return hourNames[newHour];
        }


        private void drawWatchFace(Canvas canvas) {
            String[] textArray = getTimeString(calendar);
            float width;
            float padding;
            String text;
            switch (textArray.length)
            {
                case 2:
                    text = textArray[0] + " " + textArray[1];
                    width = textPaint.measureText(text);
                    padding = textPaint.measureText(textArray[0]);
                    if(textArray[1].equals("uhr"))
                    {
                        canvas.drawText(textArray[0], centerX - width/2, centerY + textSize/2, hourPaint);
                        canvas.drawText(" " + textArray[1], (centerX - width/2) + padding, centerY + textSize/2, textPaint);
                    }
                    else {
                        canvas.drawText(textArray[0], centerX - width/2, centerY + textSize/2, textPaint);
                        canvas.drawText(" " + textArray[1], (centerX - width/2) + padding, centerY + textSize/2, hourPaint);
                    }
                    break;
                case 3:
                    text = textArray[0] + " " + textArray[1];
                    width = textPaint.measureText(text);
                    canvas.drawText(text, centerX - width/2, centerY - textSize/2, textPaint);
                    text = textArray[2];
                    width = textPaint.measureText(text);
                    canvas.drawText(text, centerX - width/2, centerY + textSize, hourPaint);
                    break;
                case 4:
                    text = textArray[0] + " " + textArray[1];
                    width = textPaint.measureText(text);
                    canvas.drawText(text, centerX - width/2, centerY - textSize/2, textPaint);
                    text = textArray[2] + " " + textArray[3];
                    width = textPaint.measureText(text);
                    padding = textPaint.measureText(textArray[2]);
                    canvas.drawText(textArray[2], centerX - width/2, centerY + textSize, textPaint);
                    canvas.drawText(" " + textArray[3], (centerX - width/2) + padding, centerY + textSize, hourPaint);
                    break;
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren"t visible. */
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter timeZoneFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            MyWatchFace.this.registerReceiver(timeZoneReceiver, timeZoneFilter);
            Intent intent = MyWatchFace.this.registerReceiver(batteryReceiver, batteryFilter);
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            battery = (int)(level * 100 / (float)scale);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver && !registeredBatteryReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            registeredBatteryReceiver = false;
            MyWatchFace.this.unregisterReceiver(timeZoneReceiver);
            MyWatchFace.this.unregisterReceiver(batteryReceiver);
        }

        /**
         * Starts/stops the {@link #updateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isAmbient;
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
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}