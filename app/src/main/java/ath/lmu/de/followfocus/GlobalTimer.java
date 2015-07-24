package ath.lmu.de.followfocus;

import android.util.Log;

import java.util.TimerTask;

/**
 * Created by alexander on 24.07.15.
 */
public class GlobalTimer extends TimerTask {

    private int currentSpeed, currentDirection;
    private BluetoothLeService mBluetoothLeService;
    private FocusScene focusScene;

    public GlobalTimer(int currentSpeed, int currentDirection) {
        this.currentSpeed = currentSpeed;
        this.currentDirection = currentDirection;
        this.mBluetoothLeService = mBluetoothLeService;
    }

    public void setCurrentSpeed(int speed) {
        this.currentSpeed = speed;
    }

    public void setCurrentDirection(int direction) {
        this.currentDirection = direction;
    }

    @Override
    public void run() {
        Log.d("Alex", "Speed: " + currentSpeed + " Direction: " + currentDirection);

    }
}
