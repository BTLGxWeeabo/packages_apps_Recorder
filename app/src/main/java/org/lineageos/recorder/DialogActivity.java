/*
 * Copyright (C) 2017 The LineageOS Project
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
package org.lineageos.recorder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

public class DialogActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "dialogTitle";
    public static final String EXTRA_LAST_SCREEN = "lastScreenItem";
    public static final String EXTRA_LAST_SOUND = "lastSoundItem";
    public static final String EXTRA_SETTINGS_SCREEN = "settingsScreen";
    public static final String EXTRA_DELETE_LAST_RECORDING = "deleteLastItem";
    private static final int REQUEST_RECORD_AUDIO_PERMS = 213;
    private static final String TYPE_AUDIO = "audio/wav";
    private static final String TYPE_VIDEO = "video/mp4";

    private LinearLayout mRootView;
    private FrameLayout mContent;
    private Spinner mAudioSource;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.dialog_base);
        setFinishOnTouchOutside(true);

        mRootView = findViewById(R.id.dialog_root);
        TextView title = findViewById(R.id.dialog_title);
        mContent = findViewById(R.id.dialog_content);

        mPrefs = getSharedPreferences(Utils.PREFS, 0);

        Intent intent = getIntent();
        int dialogTitle = intent.getIntExtra(EXTRA_TITLE, 0);
        boolean isLastScreen = intent.getBooleanExtra(EXTRA_LAST_SCREEN, false);
        boolean isLastSound = intent.getBooleanExtra(EXTRA_LAST_SOUND, false);
        boolean isSettingsScreen = intent.getBooleanExtra(EXTRA_SETTINGS_SCREEN, false);

        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        if (dialogTitle != 0) {
            title.setText(dialogTitle);
        }

        if (isLastScreen) {
            setupAsLastItem(false);
        } else if (isLastSound) {
            setupAsLastItem(true);
        } else if (isSettingsScreen) {
            setupAsSettingsScreen();
        }

        animateAppearance();

        boolean deleteLastRecording = intent.getBooleanExtra(EXTRA_DELETE_LAST_RECORDING, false);
        if (deleteLastRecording) {
            deleteLastItem(isLastSound);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        if (requestCode != REQUEST_RECORD_AUDIO_PERMS) {
            return;
        }
        if (!hasAudioPermission()){
            setScreenWithAudio(Utils.PREF_AUDIO_RECORDING_SOURCE_DISABLED);
            mAudioSource.setSelection(Utils.PREF_AUDIO_RECORDING_SOURCE_DISABLED);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void animateAppearance() {
        mRootView.setAlpha(0f);
        mRootView.animate()
                .alpha(1f)
                .setStartDelay(250)
                .start();
    }

    private void setupAsLastItem(boolean isSound) {
        View view = createContentView(R.layout.dialog_content_last_item);
        TextView description = view.findViewById(R.id.dialog_content_last_description);
        ImageView play = view.findViewById(R.id.dialog_content_last_play);
        ImageView delete = view.findViewById(R.id.dialog_content_last_delete);
        ImageView share = view.findViewById(R.id.dialog_content_last_share);

        description.setText(LastRecordHelper.getLastItemDescription(this, isSound));

        play.setOnClickListener(v -> playLastItem(isSound));
        delete.setOnClickListener(v -> deleteLastItem(isSound));
        share.setOnClickListener(v -> shareLastItem(isSound));
    }

    private void playLastItem(boolean isSound) {
        String type = isSound ? TYPE_AUDIO : TYPE_VIDEO;
        Uri uri = LastRecordHelper.getLastItemUri(this, isSound);
        Intent intent = LastRecordHelper.getOpenIntent(uri, type);
        if (intent != null) {
            startActivityForResult(intent, 0);
        }
    }

    private void deleteLastItem(boolean isSound) {
        Uri uri = LastRecordHelper.getLastItemUri(this, isSound);
        AlertDialog dialog = LastRecordHelper.deleteFile(this, uri, isSound);
        dialog.setOnDismissListener(d -> finish());
        dialog.show();
    }

    private void shareLastItem(boolean isSound) {
        String type = isSound ? TYPE_AUDIO : TYPE_VIDEO;
        Uri uri = LastRecordHelper.getLastItemUri(this, isSound);
        startActivity(LastRecordHelper.getShareIntent(uri, type));
    }

    private void setupAsSettingsScreen() {
        View view = createContentView(R.layout.dialog_content_screen_settings);
        mAudioSource = view.findViewById(R.id.dialog_content_screen_settings_audio_source);
        mAudioSource.setSelection(getScreenWithAudio());
        mAudioSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setScreenWithAudio(position);
                if (!hasAudioPermission() && position != Utils.PREF_AUDIO_RECORDING_SOURCE_DISABLED) {
                    askAudioPermission();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mAudioSource.setSelection(getScreenWithAudio());

        if (Utils.isScreenRecording(this)) {
            mAudioSource.setEnabled(false);
        }
    }

    private View createContentView(@LayoutRes int layout) {
        LayoutInflater inflater = getLayoutInflater();
        return inflater.inflate(layout, mContent);
    }

    private boolean hasAudioPermission() {
        int result = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void askAudioPermission() {
        requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO },
                REQUEST_RECORD_AUDIO_PERMS);
    }

    private void setScreenWithAudio(int source) {
        mPrefs.edit().putInt(Utils.PREF_AUDIO_RECORDING_SOURCE, source).apply();
    }

    private int getScreenWithAudio() {
        return mPrefs.getInt(Utils.PREF_AUDIO_RECORDING_SOURCE, Utils.PREF_AUDIO_RECORDING_SOURCE_DEFAULT);
    }
}