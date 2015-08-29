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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceControlActivity extends FragmentActivity implements RecordSceneDialogFragment.NoticeDialogListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private final long EXECUTION_INTERVAL = 50; // milliseconds


    private TextView mConnectionState;
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
    private FocusScene currentScene;

    // current speed to be sent to arduino every EXECUTION_INTERVAL seconds
    private byte currentSpeed = 0;
    private byte currentDirection = 0;

    private Boolean recording = false;

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

        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        TextView activityTitle = (TextView) findViewById(R.id.textView_connectedDeviceName);
        activityTitle.setText(mDeviceName);


        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        sceneListAdapter = new SceneListAdapter(this);
        recordedScenesList.setExpanded(true);
        recordedScenesList.setAdapter(sceneListAdapter);

        realtimeFocusSpeedSlider = (SeekBar) findViewById(R.id.slider_realtimeRecording);
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

        calibrationSetMinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte mByte = 97;

                mBluetoothLeService.writeByte(mByte);
            }
        });

        calibrationSetMaxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte mByte = 122;

                mBluetoothLeService.writeByte(mByte);
            }
        });

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

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!recording) {

                    DialogFragment newSceneDialog = new RecordSceneDialogFragment();
                    newSceneDialog.show(getFragmentManager(), "new");


                } else {
                    recording = false;
                    recordButton.setImageResource(R.drawable.record);
                    currentScene.setStatus(currentScene.getSpeedValues().size() * EXECUTION_INTERVAL / 1000 + "s");
                    sceneListAdapter.notifyDataSetChanged();

                    FocusScene recordedScene = currentScene;

                    // stop recording
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
                        mBluetoothLeService.writeByte(currentDirection);
                        mBluetoothLeService.writeByte(currentSpeed);

                        // if recording, write values into FocusScene object
                        if (recording) {
                            if (null != currentScene) {
                                currentScene.addMovementValue(currentDirection);
                                currentScene.addSpeedValue(currentSpeed);
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
        currentScene = new FocusScene(name);
        currentScene.setStatus("recording");
        sceneListAdapter.add(currentScene);
        sceneListAdapter.notifyDataSetChanged();

        recording = true;
        recordButton.setImageResource(R.drawable.stop);


    }
}
