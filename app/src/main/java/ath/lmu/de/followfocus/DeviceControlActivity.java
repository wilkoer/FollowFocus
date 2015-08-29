/*
 * Copyright (C) 2013 The Android Open Source Project
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

package ath.lmu.de.followfocus;

import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceControlActivity extends FragmentActivity implements RecordSceneDialogFragment.NoticeDialogListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String PREFS_SCENES = "RecordedScenes";
    public static final String SCENE_LIST = "SceneList";
    private final long EXECUTION_INTERVAL = 50; // milliseconds
    private boolean isSceneSelected = false;


    private TextView mConnectionState;
    private TextView selectedSceneLabel;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private SeekBar realtimeFocusSpeedSlider;
    private Button calibrationSetMinButton;
    private Button calibrationSetMaxButton;
    private Button focusOutButton;
    private Button focusInButton;
    private ImageButton recordButton;
    private ImageButton playButton;
    private Button reconnectButton;
    private boolean mConnected = false;
    private ExpandableHeightListView recordedScenesList;
    private SceneListAdapter sceneListAdapter;
    private FocusScene currentFocusScene;
    private FocusScene selectedFocusScene;

    // current speed and direction to be sent to arduino every EXECUTION_INTERVAL seconds
    private byte currentSpeed = 0;
    private byte currentDirection = 0;
    private int currentSceneFrame = 0;

    private boolean isRecording = false;
    private boolean isPlayingScene = false;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                reconnectButton.setText(R.string.disconnect);
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

                startTimer();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                reconnectButton.setText(R.string.connect);
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                mBluetoothLeService.enableTXNotification();
                // Show all the supported services and characteristics on the user interface.
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d("ATH", "ACTION DATA AVAILABLE");
                final String txValue = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {

                            Log.d("ATH", txValue);

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connected_device_activity);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        calibrationSetMaxButton = (Button) findViewById(R.id.button_stopCalibration);
        calibrationSetMinButton = (Button) findViewById(R.id.button_startCalibration);
        focusInButton = (Button) findViewById(R.id.button_focusIn);
        focusOutButton = (Button) findViewById(R.id.button_focusOut);
        reconnectButton = (Button) findViewById(R.id.button_reconnect);
        recordedScenesList = (ExpandableHeightListView) findViewById(R.id.listView_recordedScenes);
        recordButton = (ImageButton) findViewById(R.id.button_record);
        playButton = (ImageButton) findViewById(R.id.button_play);
        selectedSceneLabel = (TextView) findViewById(R.id.textView_selectedScene);

        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        TextView activityTitle = (TextView) findViewById(R.id.textView_connectedDeviceName);
        activityTitle.setText(mDeviceName);

        if (!isSceneSelected) {
            playButton.setEnabled(false);
            selectedSceneLabel.setText(getString(R.string.selected_scene) + " " + getString(R.string.none));
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        sceneListAdapter = new SceneListAdapter(this);
        recordedScenesList.setExpanded(true);
        recordedScenesList.setAdapter(sceneListAdapter);

        realtimeFocusSpeedSlider = (SeekBar) findViewById(R.id.slider_realtimeRecording);


        /*
        * Get JSON String from shared preferences.
        * Create JSONArray Object and iterate through.
        * Retrieve data and use it with extended FocusScene constructor in order to recreate FocusScene Objects.
        * Add to List Adapter to show in List.
        * */

        SharedPreferences savedScenes = getSharedPreferences(PREFS_SCENES, 0);
        String savedScenesJson = savedScenes.getString(SCENE_LIST, null);

        Log.d("ATH", savedScenesJson);

        if (savedScenesJson != null) {
            try {
                JSONArray scenesJSON = new JSONArray(savedScenesJson);

                for (int i = 0; i < scenesJSON.length(); i++) {
                    JSONObject sceneObject = scenesJSON.getJSONObject(i);

                    ArrayList speedValues = new Gson().fromJson(sceneObject.getJSONArray("speedValues").toString(), new TypeToken<List<Integer>>(){}.getType());
                    ArrayList movementValues = new Gson().fromJson(sceneObject.getJSONArray("movementValues").toString(), new TypeToken<List<Integer>>(){}.getType());
                    String name = sceneObject.getString("name");
                    String status = sceneObject.getString("status");

                    FocusScene recoveredFocusScene = new FocusScene(status, name, speedValues, movementValues);
                    sceneListAdapter.add(recoveredFocusScene);
                }
            } catch (JSONException e) {

            }
        }

        // update list
        sceneListAdapter.notifyDataSetChanged();


        /*
        * Focus Speed Slider - Change Listener
        *
        * Map value 0-100 to a stepper motor speed range of 49 to 57.
        * Convert to byte value and save in currentSpeed.
        * */
        realtimeFocusSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                /*
                * - mapping the progess to a range from minSpeed to maxSpeed
                * - converting to byte and send to arduino via btle
                * */
                int minSpeed = 49;
                int maxSpeed = 57;
                int difference = maxSpeed - minSpeed;

                int speed = minSpeed + progress / (100 / difference);

                final BigInteger bi = BigInteger.valueOf(speed);
                final byte[] bytes = bi.toByteArray();

                currentSpeed = bytes[0];
            }
        });

        /*
        * Calibration set Minimum - Click Listener
        * */
        calibrationSetMinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte mByte = 97;

                mBluetoothLeService.writeByte(mByte);
            }
        });


        /*
        * Calibration set Maximum - Click Listener
        * */
        calibrationSetMaxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte mByte = 122;

                mBluetoothLeService.writeByte(mByte);
            }
        });


        /*
        * Reconnect
        * !!!NOT WORKING YET!!!
        * */
        reconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBluetoothLeService != null && !mConnected) {
                    final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                    Log.d(TAG, "Connect request result=" + result);
                } else if (mBluetoothLeService != null && mConnected){
                    mBluetoothLeService.disconnect();
                }
            }
        });


        /*
        * Recorded Scenes List - Click Listener
        * Select clicked element and show name of selected element
        * */
        recordedScenesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isRecording) {
                    playButton.setEnabled(true);
                    selectedSceneLabel.setText(getString(R.string.selected_scene) + " " + ((FocusScene) sceneListAdapter.getItem(position)).getName());

                    selectedFocusScene = ((FocusScene) sceneListAdapter.getItem(position));

                    Toast toast = Toast.makeText(getApplicationContext(), "Scene selected", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });


        /*
        * Focus Out - Touch Listener
        * (Touch Listener allows the button to keep triggering the event when pressed)
        * */
        focusOutButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                byte mByte = 43; // == ASCII minus
                byte nullByte = 0;


                if (event.getAction() == event.ACTION_DOWN) {
                    currentDirection = mByte; // set current direction minus
                } else if (event.getAction() == event.ACTION_UP) {
                    currentDirection = nullByte; // set current direction zero
                }

                return true;
            }
        });


        /*
        * Focus In - Touch Listener
        * (Touch Listener allows the button to keep triggering the event when pressed)
        * */
        focusInButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                byte mByte = 45; // == ASCII plus
                byte nullByte = 0;

                if (event.getAction() == event.ACTION_DOWN) {
                    currentDirection = mByte; // set current direction plus
                } else if (event.getAction() == event.ACTION_UP) {
                    currentDirection = nullByte; // set current direction zero
                }

                return true;
            }
        });

        /*
        * Record Button - Click Listener
        * (Single Click triggered)
        * */
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /*
                * If not isRecording,
                * show a newSceneDialog to create a new scene
                * */
                if (!isRecording) {
                    DialogFragment newSceneDialog = new RecordSceneDialogFragment();
                    newSceneDialog.show(getFragmentManager(), "new");
                } else {
                    /*
                    * If isRecording, stop recording.
                    * - Change drawable back to record graphic.
                    * - Set status of list entry to <scene length> seconds
                    * - Update list
                    * */
                    isRecording = false;
                    recordButton.setImageResource(R.drawable.record);
                    currentFocusScene.setStatus(currentFocusScene.getSpeedValues().size() * EXECUTION_INTERVAL / 1000 + "s");
                    sceneListAdapter.notifyDataSetChanged();

                    FocusScene recordedScene = currentFocusScene;

                    SharedPreferences savedScenes = getSharedPreferences(PREFS_SCENES, 0);
                    SharedPreferences.Editor editor = savedScenes.edit();

                    String recordedScenesJson = new Gson().toJson(sceneListAdapter.scenes);
                    editor.putString(SCENE_LIST, recordedScenesJson);

                    // Commit the edits!
                    editor.commit();
                }
            }
        });

        /*
        * Play Button - Click Listener
        * (Single Click triggered)
        * */
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                * set isPlayingScene true,
                * if a scene is selected
                * */
                if (null != selectedFocusScene) {
                    isPlayingScene = true;
                }
            }
        });

    }

    /*
    * - execute every xx ms (EXECUTION_INTERVAL)
    * - send currentDirection and currentSpeed via BTLE to Arduino device
    *
    * */
    public void startTimer (){
        final Handler handler = new Handler ();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {

                        if (!isPlayingScene) {
                            mBluetoothLeService.writeByte(currentDirection);
                            mBluetoothLeService.writeByte(currentSpeed);

                            // if scene is currently recording, write values into FocusScene object
                            if (isRecording) {
                                if (null != currentFocusScene) {
                                    currentFocusScene.addMovementValue(currentDirection);
                                    currentFocusScene.addSpeedValue(currentSpeed);
                                }
                            }
                        } else { // if recorded scene is being played, send recorded values
                            Integer speed = selectedFocusScene.getSpeedValues().get(currentSceneFrame);
                            Integer direction = selectedFocusScene.getMovementValues().get(currentSceneFrame);

                            final BigInteger bi_speed = BigInteger.valueOf(speed);
                            final byte[] byte_speed = bi_speed.toByteArray();

                            final BigInteger bi_direction = BigInteger.valueOf(direction);
                            final byte[] byte_direction = bi_direction.toByteArray();

                            mBluetoothLeService.writeByte(byte_speed[0]);
                            mBluetoothLeService.writeByte(byte_direction[0]);

                            Log.d("ATH", speed.toString());

                            if (currentSceneFrame < selectedFocusScene.getSpeedValues().size()-1) {
                                currentSceneFrame++;
                            } else {
                                currentSceneFrame = 0;
                                isPlayingScene = false;
                            }
                        }
                    }
                });
            }
        }, 0, EXECUTION_INTERVAL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onNewScenePositiveClick(String name) {
        //add new scene
        currentFocusScene = new FocusScene(name);
        currentFocusScene.setStatus(getString(R.string.currently_recording));
        sceneListAdapter.add(currentFocusScene);
        sceneListAdapter.notifyDataSetChanged();

        isRecording = true;
        recordButton.setImageResource(R.drawable.stop);
    }
}
