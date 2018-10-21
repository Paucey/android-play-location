/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.location.sample.activityrecognition;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        // Now this next line is interesting... why do this?
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected static final String TAG = "MainActivity";
    
        // I appreciate that the context is being stored here, but why? Especially since it's 
        // only to be used within this class?
    private Context mContext;

    /**
     * The entry point for interacting with activity recognition.
     */
    private ActivityRecognitionClient mActivityRecognitionClient;

    // UI elements. Straightforward... but why make them Global?
    private Button mRequestActivityUpdatesButton;
    private Button mRemoveActivityUpdatesButton;

    /**
     * Adapter backed by a list of DetectedActivity objects.
     */
    private DetectedActivitiesAdapter mAdapter;


    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        // Ah, I see. So this is made global so that it can be referenced later on in methods that
        // otherwise wouldn't have the scope to 
        mContext = this;

        // Get the UI widgets.
        mRequestActivityUpdatesButton = (Button) findViewById(R.id.request_activity_updates_button);
        mRemoveActivityUpdatesButton = (Button) findViewById(R.id.remove_activity_updates_button);
        ListView detectedActivitiesListView = (ListView) findViewById(
                R.id.detected_activities_listview);

        // Enable either the Request Updates button or the Remove Updates button depending on
        // whether activity updates have been requested.
                // Want to change this to "toggle" instead of "set".
        setButtonsEnabledState();

                // Alright, so... an array list of objects which are "DetectedActivity" objects.
                // I assume each object is one of the possible activities. It's called "detectedActivities"
                // and is... sourced from the Utils class, oddly.
        ArrayList<DetectedActivity> detectedActivities = Utils.detectedActivitiesFromJson(
                PreferenceManager.getDefaultSharedPreferences(this).getString(
                        Constants.KEY_DETECTED_ACTIVITIES, ""));

        // Bind the adapter to the ListView responsible for display data for detected activities.
        mAdapter = new DetectedActivitiesAdapter(this, detectedActivities);
        detectedActivitiesListView.setAdapter(mAdapter);
        
                // Okay, this is the global ActivityRecognitionClient that was declared. A new object
                // of it is constructed here with the context "this" as the only input.
        mActivityRecognitionClient = new ActivityRecognitionClient(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
        updateDetectedActivitiesList();
    }

    @Override
    protected void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * Registers for activity recognition updates using
     * {@link ActivityRecognitionClient#requestActivityUpdates(long, PendingIntent)}.
     * Registers success and failure callbacks.
     */
    public void requestActivityUpdatesButtonHandler(View view) {
        Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent());

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(mContext,
                        getString(R.string.activity_updates_enabled),
                        Toast.LENGTH_SHORT)
                        .show();
                setUpdatesRequestedState(true);
                updateDetectedActivitiesList();
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, getString(R.string.activity_updates_not_enabled));
                Toast.makeText(mContext,
                        getString(R.string.activity_updates_not_enabled),
                        Toast.LENGTH_SHORT)
                        .show();
                setUpdatesRequestedState(false);
            }
        });
    }


    /**
     * Removes activity recognition updates using
     * {@link ActivityRecognitionClient#removeActivityUpdates(PendingIntent)}. Registers success and
     * failure callbacks.
     */
     
                // Really need to check into what Tasks<> are and why it's used here... okay, so it
                // seems like Tasks allows activities to be opened one after another in a "stack".
    public void removeActivityUpdatesButtonHandler(View view) {
        Task<Void> task = mActivityRecognitionClient.removeActivityUpdates(
                getActivityDetectionPendingIntent());
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Presents a Toast saying that Activity Updates have been removed.
                Toast.makeText(mContext,
                        getString(R.string.activity_updates_removed),
                        Toast.LENGTH_SHORT)
                        .show();
                setUpdatesRequestedState(false);
                // Reset the display.
                mAdapter.updateActivities(new ArrayList<DetectedActivity>());
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Failed to enable activity recognition.");
                Toast.makeText(mContext, getString(R.string.activity_updates_not_removed),
                        Toast.LENGTH_SHORT).show();
                setUpdatesRequestedState(true);
            }
        });
    }

    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Ensures that only one button is enabled at any time. The Request Activity Updates button is
     * enabled if the user hasn't yet requested activity updates. The Remove Activity Updates button
     * is enabled if the user has requested activity updates.
     */
    private void setButtonsEnabledState() {
        if (getUpdatesRequestedState()) {
            mRequestActivityUpdatesButton.setEnabled(false);
            mRemoveActivityUpdatesButton.setEnabled(true);
        } else {
            mRequestActivityUpdatesButton.setEnabled(true);
            mRemoveActivityUpdatesButton.setEnabled(false);
        }
    }

    /**
     * Retrieves the boolean from SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private boolean getUpdatesRequestedState() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.KEY_ACTIVITY_UPDATES_REQUESTED, false);
    }

    /**
     * Sets the boolean in SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private void setUpdatesRequestedState(boolean requesting) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(Constants.KEY_ACTIVITY_UPDATES_REQUESTED, requesting)
                .apply();
        setButtonsEnabledState();
    }

    /**
     * Processes the list of freshly detected activities. Asks the adapter to update its list of
     * DetectedActivities with new {@code DetectedActivity} objects reflecting the latest detected
     * activities.
     */
    protected void updateDetectedActivitiesList() {
        ArrayList<DetectedActivity> detectedActivities = Utils.detectedActivitiesFromJson(
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getString(Constants.KEY_DETECTED_ACTIVITIES, ""));

        mAdapter.updateActivities(detectedActivities);
    }
    
        // On shared preference changed... okay, this checks to see if the parameter given equals
        // KEY_DETECTED_ACTIVITIES from Constants and if so it runs an update on the Activities list.
        // Ugh, stupid constants. Now I gotta see what this variable is... okay, so that resolves to
        // the Package Name + .DetectedActivities. So if there's a preference change in the detected
        // activities is asks for an update...
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(Constants.KEY_DETECTED_ACTIVITIES)) {
            updateDetectedActivitiesList();
        }
    }
}
