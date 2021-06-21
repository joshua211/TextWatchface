package com.example.textwatchface;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.util.concurrent.Executors;

public class ComplicationConfigActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "ConfigActivity";
    static final int COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private int complicationId;
    private int selectedComplicationId;
    private ComponentName watchFaceComponentName;
    private ProviderInfoRetriever providerInfoRetriever;
    private ImageView complicationBackground;
    private ImageButton complication;
    private Drawable defaultlAddComplicationDrawable;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_config);
        defaultlAddComplicationDrawable = getDrawable(R.drawable.add_complication);

        selectedComplicationId = -1;
        complicationId = MyWatchFace.getComplicationId();
        watchFaceComponentName =
                new ComponentName(getApplicationContext(), MyWatchFace.class);

        complicationBackground = (ImageView) findViewById(R.id.complication_background);
        complication = (ImageButton) findViewById(R.id.left_complication);
        complication.setOnClickListener(this);

        // Sets default as "Add Complication" icon.
        complication.setImageDrawable(defaultlAddComplicationDrawable);
        complicationBackground.setVisibility(View.INVISIBLE);

        providerInfoRetriever =
                new ProviderInfoRetriever(getApplicationContext(), Executors.newCachedThreadPool());
        providerInfoRetriever.init();

        retrieveInitialComplicationsData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        providerInfoRetriever.release();
    }

    public void retrieveInitialComplicationsData() {

        final int[] complicationIds = MyWatchFace.getComplicationIds();

        providerInfoRetriever.retrieveProviderInfo(
                new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    @Override
                    public void onProviderInfoReceived(
                            int watchFaceComplicationId,
                            @Nullable ComplicationProviderInfo complicationProviderInfo) {
                        updateComplicationViews(watchFaceComplicationId, complicationProviderInfo);
                    }
                },
                watchFaceComponentName,
                complicationIds);
    }

    @Override
    public void onClick(View v) {
        if(v.equals(complication))
            launchComplicationHelperActivity();
    }

    private void launchComplicationHelperActivity() {
        selectedComplicationId = MyWatchFace.getComplicationId();

        if (selectedComplicationId >= 0) {

            int[] supportedTypes =
                    MyWatchFace.getSupportedComplicationTypes();

            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            getApplicationContext(),
                            watchFaceComponentName,
                            selectedComplicationId,
                            supportedTypes),
                    ComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE);

        }
    }

    public void updateComplicationViews(
            int watchFaceComplicationId, ComplicationProviderInfo complicationProviderInfo) {

        if (watchFaceComplicationId == complicationId) {
            if (complicationProviderInfo != null) {
                complication.setImageIcon(complicationProviderInfo.providerIcon);
                complicationBackground.setVisibility(View.VISIBLE);

            } else {
                complication.setImageDrawable(defaultlAddComplicationDrawable);
                complicationBackground.setVisibility(View.INVISIBLE);
            }

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {

            // Retrieves information for selected Complication provider.
            ComplicationProviderInfo complicationProviderInfo =
                    data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);

            if (selectedComplicationId >= 0) {
                updateComplicationViews(selectedComplicationId, complicationProviderInfo);
            }
        }
    }
}
