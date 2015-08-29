package ath.lmu.de.followfocus;

import android.util.Log;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by alexander on 24.07.15.
 */
public class FocusScene {
    private ArrayList<Integer> speedValues;
    private ArrayList<Integer> movementValues;
    private String name, status;
    private boolean isRecording = false;


    private Timer timer;



    public String getName() {
        return name;
    }

    public ArrayList<Integer> getSpeedValues() {
        return speedValues;
    }

    public ArrayList<Integer> getMovementValues() {
        return movementValues;
    }

    public FocusScene(String name) {
        this.name = name;
        this.status = "";
        this.movementValues = new ArrayList<>();
        this.speedValues = new ArrayList<>();

    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.status;
    }

    public void addSpeedValue(int speed) {
        this.speedValues.add(speed);
    }

    public void addMovementValue(int direction) {
        this.movementValues.add(direction);
    }


}
