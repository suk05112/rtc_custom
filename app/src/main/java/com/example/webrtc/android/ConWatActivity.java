package com.example.webrtc.android;

/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.appspot.apprtc.ui.SettingsActivity;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Random;

//import android.support.v4.app.ActivityCompat;
//import android.support.v7.app.AppCompatActivity;


/**
 * Handles the initial setup where the user selects which room to join.
 */
public class ConWatActivity extends AppCompatActivity {
    private static final String TAG = "ConnectActivity";
    private static final int CONNECTION_REQUEST = 1;
    private static final int REMOVE_FAVORITE_INDEX = 0;
    private static boolean commandLineRun = false;

    private ImageButton addFavoriteButton;
    private EditText roomEditText;
    private ListView roomListView;
    private SharedPreferences sharedPref;
    private String keyprefResolution;
    private String keyprefFps;
    private String keyprefVideoBitrateType;
    private String keyprefVideoBitrateValue;
    private String keyprefAudioBitrateType;
    private String keyprefAudioBitrateValue;
    private String keyprefRoomServerUrl;
    private String keyprefRoom;
    private String keyprefRoomList;
    private ArrayList<String> roomList;
    private ArrayAdapter<String> adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get setting keys.
        PreferenceManager.setDefaultValues(this, org.appspot.apprtc.R.xml.preferences, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        keyprefResolution = getString(org.appspot.apprtc.R.string.pref_resolution_key);
        keyprefFps = getString(org.appspot.apprtc.R.string.pref_fps_key);
        keyprefVideoBitrateType = getString(org.appspot.apprtc.R.string.pref_maxvideobitrate_key);
        keyprefVideoBitrateValue = getString(org.appspot.apprtc.R.string.pref_maxvideobitratevalue_key);
        keyprefAudioBitrateType = getString(org.appspot.apprtc.R.string.pref_startaudiobitrate_key);
        keyprefAudioBitrateValue = getString(org.appspot.apprtc.R.string.pref_startaudiobitratevalue_key);
        keyprefRoomServerUrl = getString(org.appspot.apprtc.R.string.pref_room_server_url_key);
        keyprefRoom = getString(org.appspot.apprtc.R.string.pref_room_key);
        keyprefRoomList = getString(org.appspot.apprtc.R.string.pref_room_list_key);

        setContentView(R.layout.activity_connect);
        //입력한 방 주소
        roomEditText = findViewById(R.id.room_edittext);
        roomEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    addFavoriteButton.performClick();
                    return true;
                }
                return false;
            }
        });
        roomEditText.requestFocus();

        roomListView = findViewById(R.id.room_listview);
        roomListView.setEmptyView(findViewById(android.R.id.empty));
        roomListView.setOnItemClickListener(roomListClickListener);
        registerForContextMenu(roomListView);
        ImageButton connectButton = findViewById(R.id.connect_button);
        connectButton.setOnClickListener(connectListener);
        addFavoriteButton = findViewById(R.id.add_favorite_button);
        addFavoriteButton.setOnClickListener(addFavoriteListener);

        // If an implicit VIEW intent is launching the app, go directly to that URL.
        // 앱끼리 연결되게 도와주는 부분
        final Intent intent = getIntent();
        if ("android.intent.action.VIEW".equals(intent.getAction()) && !commandLineRun) {
            boolean loopback = intent.getBooleanExtra(WatchActivity.EXTRA_LOOPBACK, false);
            int runTimeMs = intent.getIntExtra(WatchActivity.EXTRA_RUNTIME, 0);
            boolean useValuesFromIntent =
                    intent.getBooleanExtra(WatchActivity.EXTRA_USE_VALUES_FROM_INTENT, false);
            String room = sharedPref.getString(keyprefRoom, "");
            connectToRoom(room, true, loopback, useValuesFromIntent, runTimeMs);
            /*connectWatch(room, true, loopback, useValuesFromIntent, runTimeMs);*/
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.connect_menu, menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.room_listview) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.setHeaderTitle(roomList.get(info.position));
            String[] menuItems = getResources().getStringArray(org.appspot.apprtc.R.array.roomListContextMenu);
            for (int i = 0; i < menuItems.length; i++) {
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        } else {
            super.onCreateContextMenu(menu, v, menuInfo);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == REMOVE_FAVORITE_INDEX) {
            AdapterView.AdapterContextMenuInfo info =
                    (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            roomList.remove(info.position);
            adapter.notifyDataSetChanged();
            return true;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items.
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_loopback) {
            connectToRoom(null, false, true, false, 0);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        String room = roomEditText.getText().toString();
        String roomListJson = new JSONArray(roomList).toString();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(keyprefRoom, room);
        editor.putString(keyprefRoomList, roomListJson);
        editor.commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        String room = sharedPref.getString(keyprefRoom, "");
        roomEditText.setText(room);
        roomList = new ArrayList<>();
        String roomListJson = sharedPref.getString(keyprefRoomList, null);
        if (roomListJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(roomListJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    roomList.add(jsonArray.get(i).toString());
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to load room list: " + e.toString());
            }
        }
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roomList);
        roomListView.setAdapter(adapter);
        if (adapter.getCount() > 0) {
            roomListView.requestFocus();
            roomListView.setItemChecked(0, true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CONNECTION_REQUEST && commandLineRun) {
            Log.d(TAG, "Return: " + resultCode);
            setResult(resultCode);
            commandLineRun = false;
            finish();
        }
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used. = 환경 값가져오기 String
     */
    private String sharedPrefGetString(
            int attributeId, String intentName, int defaultId, boolean useFromIntent) {
        String defaultValue = getString(defaultId);
        if (useFromIntent) {
            String value = getIntent().getStringExtra(intentName);
            if (value != null) {
                return value;
            }
            return defaultValue;
        } else {
            String attributeName = getString(attributeId);
            return sharedPref.getString(attributeName, defaultValue);
        }
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used. = 환경 값가져오기 Boolean
     */
    private boolean sharedPrefGetBoolean(
            int attributeId, String intentName, int defaultId, boolean useFromIntent) {
        boolean defaultValue = Boolean.parseBoolean(getString(defaultId));
        if (useFromIntent) {
            return getIntent().getBooleanExtra(intentName, defaultValue);
        } else {
            String attributeName = getString(attributeId);
            return sharedPref.getBoolean(attributeName, defaultValue);
        }
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used. = 환경 값가져오기 Integer
     */
    private int sharedPrefGetInteger(
            int attributeId, String intentName, int defaultId, boolean useFromIntent) {
        String defaultString = getString(defaultId);
        int defaultValue = Integer.parseInt(defaultString);
        if (useFromIntent) {
            return getIntent().getIntExtra(intentName, defaultValue);
        } else {
            String attributeName = getString(attributeId);
            String value = sharedPref.getString(attributeName, defaultString);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Wrong setting for: " + attributeName + ":" + value);
                return defaultValue;
            }
        }
    }
    //connectToroom
    @SuppressWarnings("StringSplitter")
    private void connectToRoom(String roomId, boolean commandLineRun, boolean loopback,
                               boolean useValuesFromIntent, int runTimeMs) {
        ConWatActivity.commandLineRun = commandLineRun;

        // roomId is random for loopback.(채널 생성, 룸아이디를 랜덤으로 값 정해주는거 같은데)
        if (loopback) {
            roomId = Integer.toString((new Random()).nextInt(100000000));
        }
        //roomUrl = pref_room_server_url_default
        String roomUrl = sharedPref.getString(
                keyprefRoomServerUrl, getString(org.appspot.apprtc.R.string.pref_room_server_url_default));

        // 화상통화 활성화
        boolean videoCallEnabledwat = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_videocall_key,
                WatchActivity.EXTRA_VIDEO_CALL, org.appspot.apprtc.R.string.pref_videocall_default, useValuesFromIntent);

        // Use screencapture option. 스크린 캡쳐 옵션 사용
        boolean useScreencapture = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_screencapture_key,
                WatchActivity.EXTRA_SCREENCAPTURE, org.appspot.apprtc.R.string.pref_screencapture_default, useValuesFromIntent);

        // Use Camera2 option. 카메라2 옵션 사용
        boolean useCamera2 = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_camera2_key, WatchActivity.EXTRA_CAMERA2,
                org.appspot.apprtc.R.string.pref_camera2_default, useValuesFromIntent);

        // Get default codecs. //기본코덱 가져옴 비디오오디오 코덱
        String videoCodec = sharedPrefGetString(org.appspot.apprtc.R.string.pref_videocodec_key,
                WatchActivity.EXTRA_VIDEOCODEC, org.appspot.apprtc.R.string.pref_videocodec_default, useValuesFromIntent);
        String audioCodec = sharedPrefGetString(org.appspot.apprtc.R.string.pref_audiocodec_key,
                WatchActivity.EXTRA_AUDIOCODEC, org.appspot.apprtc.R.string.pref_audiocodec_default, useValuesFromIntent);

        // Check HW codec flag. 하드웨어 코덱
        boolean hwCodec = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_hwcodec_key,
                WatchActivity.EXTRA_HWCODEC_ENABLED, org.appspot.apprtc.R.string.pref_hwcodec_default, useValuesFromIntent);

        // Check Capture to texture.  캡쳐 화질 체크하는건가?
        boolean captureToTexture = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_capturetotexture_key,
                WatchActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, org.appspot.apprtc.R.string.pref_capturetotexture_default,
                useValuesFromIntent);

        // Check FlexFEC. FlexFEC가 뭔진 모르겠는데 webRTC에서만 쓰는 듯
        boolean flexfecEnabled = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_flexfec_key,
                WatchActivity.EXTRA_FLEXFEC_ENABLED, org.appspot.apprtc.R.string.pref_flexfec_default, useValuesFromIntent);

        // Check Disable Audio Processing flag. 오디오 처리 사용안함 플래그
        boolean noAudioProcessing = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_noaudioprocessing_key,
                WatchActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, org.appspot.apprtc.R.string.pref_noaudioprocessing_default,
                useValuesFromIntent);
        //오디오
        boolean aecDump = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_aecdump_key,
                WatchActivity.EXTRA_AECDUMP_ENABLED, org.appspot.apprtc.R.string.pref_aecdump_default, useValuesFromIntent);
        //오디오
        boolean saveInputAudioToFile =
                sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_enable_save_input_audio_to_file_key,
                        WatchActivity.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED,
                        org.appspot.apprtc.R.string.pref_enable_save_input_audio_to_file_default, useValuesFromIntent);

        // Check OpenSL ES enabled flag.
        boolean useOpenSLES = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_opensles_key,
                WatchActivity.EXTRA_OPENSLES_ENABLED, org.appspot.apprtc.R.string.pref_opensles_default, useValuesFromIntent);

        // Check Disable built-in AEC flag.
        boolean disableBuiltInAEC = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_disable_built_in_aec_key,
                WatchActivity.EXTRA_DISABLE_BUILT_IN_AEC, org.appspot.apprtc.R.string.pref_disable_built_in_aec_default,
                useValuesFromIntent);

        // Check Disable built-in AGC flag.
        boolean disableBuiltInAGC = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_disable_built_in_agc_key,
                WatchActivity.EXTRA_DISABLE_BUILT_IN_AGC, org.appspot.apprtc.R.string.pref_disable_built_in_agc_default,
                useValuesFromIntent);

        // Check Disable built-in NS flag.
        boolean disableBuiltInNS = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_disable_built_in_ns_key,
                WatchActivity.EXTRA_DISABLE_BUILT_IN_NS, org.appspot.apprtc.R.string.pref_disable_built_in_ns_default,
                useValuesFromIntent);

        // Check Disable gain control 게인제어(오디오 관련 전처리기) 사용안함 체크
        boolean disableWebRtcAGCAndHPF = sharedPrefGetBoolean(
                org.appspot.apprtc.R.string.pref_disable_webrtc_agc_and_hpf_key, WatchActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF,
                org.appspot.apprtc.R.string.pref_disable_webrtc_agc_and_hpf_key, useValuesFromIntent);

        // Get video resolution from settings. 비디오 해상도 가져옴
        int videoWidth = 0;
        int videoHeight = 0;
        if (useValuesFromIntent) {
            videoWidth = getIntent().getIntExtra(WatchActivity.EXTRA_VIDEO_WIDTH, 0);
            videoHeight = getIntent().getIntExtra(WatchActivity.EXTRA_VIDEO_HEIGHT, 0);
        }
        if (videoWidth == 0 && videoHeight == 0) {
            String resolution =
                    sharedPref.getString(keyprefResolution, getString(org.appspot.apprtc.R.string.pref_resolution_default));
            String[] dimensions = resolution.split("[ x]+");
            if (dimensions.length == 2) {
                try {
                    videoWidth = Integer.parseInt(dimensions[0]);
                    videoHeight = Integer.parseInt(dimensions[1]);
                } catch (NumberFormatException e) {
                    videoWidth = 0;
                    videoHeight = 0;
                    Log.e(TAG, "Wrong video resolution setting: " + resolution);
                }
            }
        }

        // Get camera fps from settings. 설정에서 카메라 fps 가져옴
        int cameraFps = 0;
        if (useValuesFromIntent) {
            cameraFps = getIntent().getIntExtra(WatchActivity.EXTRA_VIDEO_FPS, 0);
        }
        if (cameraFps == 0) {
            String fps = sharedPref.getString(keyprefFps, getString(org.appspot.apprtc.R.string.pref_fps_default));
            String[] fpsValues = fps.split("[ x]+");
            if (fpsValues.length == 2) {
                try {
                    cameraFps = Integer.parseInt(fpsValues[0]);
                } catch (NumberFormatException e) {
                    cameraFps = 0;
                    Log.e(TAG, "Wrong camera fps setting: " + fps);
                }
            }
        }

        // Check capture quality slider flag. 캡쳐 퀄리티 상하좌우 플래그 체크 (화질체크?)
        boolean captureQualitySlider = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_capturequalityslider_key,
                WatchActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
                org.appspot.apprtc.R.string.pref_capturequalityslider_default, useValuesFromIntent);

        // Get video and audio start bitrate. 비디오 및 오디오 시작 비트 전송률을 확인
        int videoStartBitrate = 0;
        if (useValuesFromIntent) {
            videoStartBitrate = getIntent().getIntExtra(WatchActivity.EXTRA_VIDEO_BITRATE, 0);
        }
        if (videoStartBitrate == 0) {
            String bitrateTypeDefault = getString(org.appspot.apprtc.R.string.pref_maxvideobitrate_default);
            String bitrateType = sharedPref.getString(keyprefVideoBitrateType, bitrateTypeDefault);
            if (!bitrateType.equals(bitrateTypeDefault)) {
                String bitrateValue = sharedPref.getString(
                        keyprefVideoBitrateValue, getString(org.appspot.apprtc.R.string.pref_maxvideobitratevalue_default));
                videoStartBitrate = Integer.parseInt(bitrateValue);
            }
        }

        int audioStartBitrate = 0;
        if (useValuesFromIntent) {
            audioStartBitrate = getIntent().getIntExtra(WatchActivity.EXTRA_AUDIO_BITRATE, 0);
        }
        if (audioStartBitrate == 0) {
            String bitrateTypeDefault = getString(org.appspot.apprtc.R.string.pref_startaudiobitrate_default);
            String bitrateType = sharedPref.getString(keyprefAudioBitrateType, bitrateTypeDefault);
            if (!bitrateType.equals(bitrateTypeDefault)) {
                String bitrateValue = sharedPref.getString(
                        keyprefAudioBitrateValue, getString(org.appspot.apprtc.R.string.pref_startaudiobitratevalue_default));
                audioStartBitrate = Integer.parseInt(bitrateValue);
            }
        }

        // Check statistics display option. 통계 표시 옵션을 선택
        boolean displayHud = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_displayhud_key,
                WatchActivity.EXTRA_DISPLAY_HUD, org.appspot.apprtc.R.string.pref_displayhud_default, useValuesFromIntent);

        boolean tracing = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_tracing_key, WatchActivity.EXTRA_TRACING,
                org.appspot.apprtc.R.string.pref_tracing_default, useValuesFromIntent);

        // Check Enable RtcEventLog. RtcEventLog 사용 선택
        boolean rtcEventLogEnabled = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_enable_rtceventlog_key,
                WatchActivity.EXTRA_ENABLE_RTCEVENTLOG, org.appspot.apprtc.R.string.pref_enable_rtceventlog_default,
                useValuesFromIntent);

        boolean useLegacyAudioDevice = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_use_legacy_audio_device_key,
                WatchActivity.EXTRA_USE_LEGACY_AUDIO_DEVICE, org.appspot.apprtc.R.string.pref_use_legacy_audio_device_default,
                useValuesFromIntent);

        // Get datachannel options 데이터채널 옵션 가져옴
        boolean dataChannelEnabled = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_enable_datachannel_key,
                WatchActivity.EXTRA_DATA_CHANNEL_ENABLED, org.appspot.apprtc.R.string.pref_enable_datachannel_default,
                useValuesFromIntent);
        boolean ordered = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_ordered_key, WatchActivity.EXTRA_ORDERED,
                org.appspot.apprtc.R.string.pref_ordered_default, useValuesFromIntent);
        boolean negotiated = sharedPrefGetBoolean(org.appspot.apprtc.R.string.pref_negotiated_key,
                WatchActivity.EXTRA_NEGOTIATED, org.appspot.apprtc.R.string.pref_negotiated_default, useValuesFromIntent);
        int maxRetrMs = sharedPrefGetInteger(org.appspot.apprtc.R.string.pref_max_retransmit_time_ms_key,
                WatchActivity.EXTRA_MAX_RETRANSMITS_MS, org.appspot.apprtc.R.string.pref_max_retransmit_time_ms_default,
                useValuesFromIntent);
        int maxRetr =
                sharedPrefGetInteger(org.appspot.apprtc.R.string.pref_max_retransmits_key, WatchActivity.EXTRA_MAX_RETRANSMITS,
                        org.appspot.apprtc.R.string.pref_max_retransmits_default, useValuesFromIntent);
        int id = sharedPrefGetInteger(org.appspot.apprtc.R.string.pref_data_id_key, WatchActivity.EXTRA_ID,
                org.appspot.apprtc.R.string.pref_data_id_default, useValuesFromIntent);
        String protocol = sharedPrefGetString(org.appspot.apprtc.R.string.pref_data_protocol_key,
                WatchActivity.EXTRA_PROTOCOL, org.appspot.apprtc.R.string.pref_data_protocol_default, useValuesFromIntent);

        // Start AppRTCMobile activity. 앱 엑티비티 시작 송출시작인가
        Log.d(TAG, "Connecting to room " + roomId + " at URL " + roomUrl);
        if (validateUrl(roomUrl)) {
            Uri uri = Uri.parse(roomUrl);
            Intent intent = new Intent(this, WatchActivity.class);
            intent.setData(uri);
            intent.putExtra(WatchActivity.EXTRA_ROOMID, roomId);
            intent.putExtra(WatchActivity.EXTRA_LOOPBACK, loopback);
            intent.putExtra(WatchActivity.EXTRA_VIDEO_CALL, videoCallEnabledwat);
            intent.putExtra(WatchActivity.EXTRA_SCREENCAPTURE, useScreencapture);
            intent.putExtra(WatchActivity.EXTRA_CAMERA2, useCamera2);
            intent.putExtra(WatchActivity.EXTRA_VIDEO_WIDTH, videoWidth);
            intent.putExtra(WatchActivity.EXTRA_VIDEO_HEIGHT, videoHeight);
            intent.putExtra(WatchActivity.EXTRA_VIDEO_FPS, cameraFps);
            intent.putExtra(WatchActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, captureQualitySlider);
            intent.putExtra(WatchActivity.EXTRA_VIDEO_BITRATE, videoStartBitrate);
            intent.putExtra(WatchActivity.EXTRA_VIDEOCODEC, videoCodec);
            intent.putExtra(WatchActivity.EXTRA_HWCODEC_ENABLED, hwCodec);
            intent.putExtra(WatchActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, captureToTexture);
            intent.putExtra(WatchActivity.EXTRA_FLEXFEC_ENABLED, flexfecEnabled);
            intent.putExtra(WatchActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, noAudioProcessing);
            intent.putExtra(WatchActivity.EXTRA_AECDUMP_ENABLED, aecDump);
            intent.putExtra(WatchActivity.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, saveInputAudioToFile);
            intent.putExtra(WatchActivity.EXTRA_OPENSLES_ENABLED, useOpenSLES);
            intent.putExtra(WatchActivity.EXTRA_DISABLE_BUILT_IN_AEC, disableBuiltInAEC);
            intent.putExtra(WatchActivity.EXTRA_DISABLE_BUILT_IN_AGC, disableBuiltInAGC);
            intent.putExtra(WatchActivity.EXTRA_DISABLE_BUILT_IN_NS, disableBuiltInNS);
            intent.putExtra(WatchActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, disableWebRtcAGCAndHPF);
            intent.putExtra(WatchActivity.EXTRA_AUDIO_BITRATE, audioStartBitrate);
            intent.putExtra(WatchActivity.EXTRA_AUDIOCODEC, audioCodec);
            intent.putExtra(WatchActivity.EXTRA_DISPLAY_HUD, displayHud);
            intent.putExtra(WatchActivity.EXTRA_TRACING, tracing);
            intent.putExtra(WatchActivity.EXTRA_ENABLE_RTCEVENTLOG, rtcEventLogEnabled);
            intent.putExtra(WatchActivity.EXTRA_CMDLINE, commandLineRun);
            intent.putExtra(WatchActivity.EXTRA_RUNTIME, runTimeMs);
            intent.putExtra(WatchActivity.EXTRA_USE_LEGACY_AUDIO_DEVICE, useLegacyAudioDevice);

            intent.putExtra(WatchActivity.EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled);

            if (dataChannelEnabled) {
                intent.putExtra(WatchActivity.EXTRA_ORDERED, ordered);
                intent.putExtra(WatchActivity.EXTRA_MAX_RETRANSMITS_MS, maxRetrMs);
                intent.putExtra(WatchActivity.EXTRA_MAX_RETRANSMITS, maxRetr);
                intent.putExtra(WatchActivity.EXTRA_PROTOCOL, protocol);
                intent.putExtra(WatchActivity.EXTRA_NEGOTIATED, negotiated);
                intent.putExtra(WatchActivity.EXTRA_ID, id);
            }

            if (useValuesFromIntent) {
                if (getIntent().hasExtra(WatchActivity.EXTRA_VIDEO_FILE_AS_CAMERA)) {
                    String videoFileAsCamera =
                            getIntent().getStringExtra(WatchActivity.EXTRA_VIDEO_FILE_AS_CAMERA);
                    intent.putExtra(WatchActivity.EXTRA_VIDEO_FILE_AS_CAMERA, videoFileAsCamera);
                }

                if (getIntent().hasExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)) {
                    String saveRemoteVideoToFile =
                            getIntent().getStringExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);
                    intent.putExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE, saveRemoteVideoToFile);
                }
                //넓이
                if (getIntent().hasExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH)) {
                    int videoOutWidth =
                            getIntent().getIntExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
                    intent.putExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, videoOutWidth);
                }
                //높이
                if (getIntent().hasExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT)) {
                    int videoOutHeight =
                            getIntent().getIntExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
                    intent.putExtra(WatchActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, videoOutHeight);
                }
            }

            startWatchActivity(intent);
        }
    }



    private static final String[] PERMISSIONS_START_CALL = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};//WRITE_EXTERNAL_STORAGE, CAPTURE_VIDEO_OUTPUT
    private static final int PERMISSIONS_REQUEST_START_CALL = 101;
    private Intent startCallIntent;

    private void startWatchActivity(Intent intent) {
        if(!hasPermissions(this, PERMISSIONS_START_CALL)){
            startCallIntent = intent;
            ActivityCompat.requestPermissions(this, PERMISSIONS_START_CALL, PERMISSIONS_REQUEST_START_CALL);
            return;
        }
        startActivityForResult(intent, CONNECTION_REQUEST);
    }


    private static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSIONS_REQUEST_START_CALL: {
                if (hasPermissions(this, PERMISSIONS_START_CALL)) {
                    // permission was granted, yay!
                    if (startCallIntent != null) startActivityForResult(startCallIntent, CONNECTION_REQUEST);
                } else {
                    Toast.makeText(this, "Required permissions denied.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        startCallIntent = savedInstanceState.getParcelable("startCallIntent");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("startCallIntent", startCallIntent);
    }

    private boolean validateUrl(String url) {
        if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
            return true;
        }

        new AlertDialog.Builder(this)
                .setTitle(getText(org.appspot.apprtc.R.string.invalid_url_title))
                .setMessage(getString(org.appspot.apprtc.R.string.invalid_url_text, url))
                .setCancelable(false)
                .setNeutralButton(org.appspot.apprtc.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        })
                .create()
                .show();
        return false;
    }
    //리스트에서 선택하면 읽어오는거
    private final AdapterView.OnItemClickListener roomListClickListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    String roomId = ((TextView) view).getText().toString();
                    connectToRoom(roomId, false, false, false, 0);
                }
            };

    //리스트 추가
    private final OnClickListener addFavoriteListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            String newRoom = roomEditText.getText().toString();
            if (newRoom.length() > 0 && !roomList.contains(newRoom)) {
                adapter.add(newRoom);
                adapter.notifyDataSetChanged();
            }
        }
    };
    //방 연결
    private final OnClickListener connectListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            connectToRoom(roomEditText.getText().toString(), false, false, false, 0);
        }
    };
}

