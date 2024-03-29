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

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceControlActivity extends FragmentActivity implements RecordSceneDialogFragment.NoticeDialogListener, DeleteSceneDialogFragment.NoticeDialogListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String PREFS_SCENES = "RecordedScenes";
    public static final String SCENE_LIST = "SceneList";
    private final long EXECUTION_INTERVAL = 50; // milliseconds
    private final int STEP_SIZE = 200;
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
    private byte currentSpeed = 49;
    private byte currentDirection = 0;
    private int currentSceneFrame = 0;

    private boolean isRecording = false;
    private boolean isPlayingScene = false;

    private int lowEndMark = -1;
    private int highEndMark = -1;
    private int currentStep = 0;
    private int startPosition = 0;

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
                            String value = null;

                            if (txValue.contains("StartPosition")) {
                                if (txValue.contains("\n")) {
                                    value = txValue.substring(0, txValue.indexOf("\n"));
                                }
                                value = value.replace("StartPosition", "");

                                startPosition = Integer.parseInt(value);

                                if (currentFocusScene != null) {
                                    currentFocusScene.setStartPosition(startPosition);
                                }
                            }

                            if (txValue.contains("HighEndMark")) {
                                if (txValue.contains("\n")) {
                                    value = txValue.substring(0, txValue.indexOf("\n"));
                                }
                                value = value.replace("HighEndMark", "");

                                highEndMark = Integer.parseInt(value);
                                currentStep = highEndMark;
                                Log.d("ATH", highEndMark + "");

                            } else if (txValue.contains("LowEndMark")) {
                                if (txValue.contains("\n")) {
                                    value = txValue.substring(0, txValue.indexOf("\n"));
                                }
                                value = value.replace("LowEndMark", "");
                                lowEndMark = Integer.parseInt(value);
                                currentStep = lowEndMark;
                                Log.d("ATH", lowEndMark + "");
                            }

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

        // get device name data & address from DeviceScanActivity
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references
        calibrationSetMaxButton = (Button) findViewById(R.id.button_stopCalibration);
        calibrationSetMinButton = (Button) findViewById(R.id.button_startCalibration);
        focusInButton = (Button) findViewById(R.id.button_focusIn);
        focusOutButton = (Button) findViewById(R.id.button_focusOut);
        reconnectButton = (Button) findViewById(R.id.button_reconnect);
        recordedScenesList = (ExpandableHeightListView) findViewById(R.id.listView_recordedScenes);
        recordButton = (ImageButton) findViewById(R.id.button_record);
        playButton = (ImageButton) findViewById(R.id.button_play);
        selectedSceneLabel = (TextView) findViewById(R.id.textView_selectedScene);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        TextView activityTitle = (TextView) findViewById(R.id.textView_connectedDeviceName);
        realtimeFocusSpeedSlider = (SeekBar) findViewById(R.id.slider_realtimeRecording);

        // set device name as Activity title
        activityTitle.setText(mDeviceName);

        // disable playButton and set selected Scene to "none"
        if (!isSceneSelected) {
            playButton.setEnabled(false);
            selectedSceneLabel.setText(getString(R.string.selected_scene) + " " + getString(R.string.none));
        }

        // BTLE service
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Recorded Scenes List
        sceneListAdapter = new SceneListAdapter(this);
        recordedScenesList.setExpanded(true);
        recordedScenesList.setAdapter(sceneListAdapter);


        /*
        * Get JSON String from shared preferences.
        * Create JSONArray Object and iterate through.
        * Retrieve data and use it with extended FocusScene constructor in order to recreate FocusScene Objects.
        * Add to List Adapter to show in List.
        * */
        SharedPreferences savedScenes = getSharedPreferences(PREFS_SCENES, 0);
        String savedScenesJson = savedScenes.getString(SCENE_LIST, null);

        if (savedScenesJson != null) {
            try {
                JSONArray scenesJSON = new JSONArray(savedScenesJson);

                for (int i = 0; i < scenesJSON.length(); i++) {
                    JSONObject sceneObject = scenesJSON.getJSONObject(i);

                    ArrayList speedValues = new Gson().fromJson(sceneObject.getJSONArray("speedValues").toString(), new TypeToken<List<Integer>>(){}.getType());
                    ArrayList movementValues = new Gson().fromJson(sceneObject.getJSONArray("movementValues").toString(), new TypeToken<List<Integer>>(){}.getType());
                    String name = sceneObject.getString("name");
                    String status = sceneObject.getString("status");

                    FocusScene recoveredFocusScene = new FocusScene(status, name, speedValues, movementValues, startPosition);
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
                byte mByte = 97; // ASCII "a"

                mBluetoothLeService.writeByte(mByte);
            }
        });


        /*
        * Calibration set Maximum - Click Listener
        * */
        calibrationSetMaxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte mByte = 122; // ASCII "z"

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

                    // send signal to rewind
                    byte rewindByte = 112;
                    mBluetoothLeService.writeByte(rewindByte);

                    // convert startPosition to byte and send position to rewind to
                    final BigInteger bi = BigInteger.valueOf(selectedFocusScene.getStartPosition());
                    final byte[] bytes = bi.toByteArray();
                    byte startPositionByte = bytes[0];
                    mBluetoothLeService.writeByte(startPositionByte);

                    Toast toast = Toast.makeText(getApplicationContext(), "Scene selected", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });

         /*
        * Recorded Scenes List - LongClick Listener
        * Select clicked element and show delete dialog
        * */
        recordedScenesList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                DialogFragment deleteDialog = new DeleteSceneDialogFragment();
                Bundle args = new Bundle();

                args.putInt("index", position);
                deleteDialog.setArguments(args);
                deleteDialog.show(getFragmentManager(), "deleteSequence");

                return true;
            }
        });


        /*
        * Focus Out - Touch Listener
        * (Touch Listener allows the button to keep triggering the event when pressed)
        * */
        focusOutButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                byte mByte = 43; // ASCII "+"
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
                byte mByte = 45; // ASCII "-"
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
    * Timer Thread
    * - executed every <EXECUTION_INTERVAL> ms
    * - sends currentDirection and currentSpeed via BTLE to Arduino device
    * */
    public void startTimer (){
        final Handler handler = new Handler ();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {

                        if (!isPlayingScene) { // if not playing a recorded scene

                            if (currentDirection == 43) {
                                currentStep += STEP_SIZE;
                            } else if (currentDirection == 45) {
                                currentStep -= STEP_SIZE;
                            }

                            // calibration boundaries
                            if (lowEndMark != -1 && highEndMark != -1) {
                                if (currentStep >= lowEndMark || currentStep <= highEndMark) {
                                    //currentDirection = 0;
                                }
                            }


                            try {
                                mBluetoothLeService.writeByte(currentDirection);
                                mBluetoothLeService.writeByte(currentSpeed);
                            } catch(Exception e) {

                            }


                            // if scene is currently recording, write values into FocusScene object
                            if (isRecording) {
                                if (null != currentFocusScene) {
                                    currentFocusScene.addMovementValue(currentDirection);
                                    currentFocusScene.addSpeedValue(currentSpeed);
                                }
                            }
                        } else { // if recorded scene is being played, send recorded values as byte values
                            Integer speed = selectedFocusScene.getSpeedValues().get(currentSceneFrame);
                            Integer direction = selectedFocusScene.getMovementValues().get(currentSceneFrame);

                            final BigInteger bi_speed = BigInteger.valueOf(speed);
                            final byte[] byte_speed = bi_speed.toByteArray();

                            final BigInteger bi_direction = BigInteger.valueOf(direction);
                            final byte[] byte_direction = bi_direction.toByteArray();

                            mBluetoothLeService.writeByte(byte_speed[0]);
                            mBluetoothLeService.writeByte(byte_direction[0]);

                            // stop playing, when max frames reached
                            if (currentSceneFrame < selectedFocusScene.getSpeedValues().size()-1) {
                                currentSceneFrame++;
                            } else {
                                currentSceneFrame = 0;
                                isPlayingScene = false;

                                // send signal to Arduino to save current position before recording.
                                byte rewindByte = 114; // ASCII "r"
                                mBluetoothLeService.writeByte(rewindByte);

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

    /*
    * execute, when new scene dialog was confirmed
    * - create new FocusScene object and add it to sceneListAdapter
    * - set recording true
    * - change recordButton image
    * */
    public void onNewScenePositiveClick(String name) {
        //add new scene
        currentFocusScene = new FocusScene(name);
        currentFocusScene.setStatus(getString(R.string.currently_recording));
        sceneListAdapter.add(currentFocusScene);
        sceneListAdapter.notifyDataSetChanged();

        isRecording = true;
        recordButton.setImageResource(R.drawable.stop);

        // send signal to Arduino to save current position before playing.
        byte savePositionByte = 115; // ASCII "s"
        mBluetoothLeService.writeByte(savePositionByte);
    }

    /*
    * execute, when delete scene dialog was confirmed
    * - delete scene from list
    * - save to shared preferences
    * */
    public void onDeleteScenePositiveClick(int position) {
        sceneListAdapter.remove(position);
        sceneListAdapter.notifyDataSetChanged();

        // save edited list
        SharedPreferences savedScenes = getSharedPreferences(PREFS_SCENES, 0);
        SharedPreferences.Editor editor = savedScenes.edit();

        String recordedScenesJson = new Gson().toJson(sceneListAdapter.scenes);
        editor.putString(SCENE_LIST, recordedScenesJson);

        // Commit the edits!
        editor.commit();
    }
}
