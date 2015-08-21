package ath.lmu.de.followfocus;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by alexander on 24.07.15.
 */
public class FocusScene {
    private ArrayList<Integer> speedValues;
    private ArrayList<Integer> movementValues;
    private String name;


    private Timer timer;


    public String getName() {
        return name;
    }

    public FocusScene(String name) {
        this.name = name;

    }

    public void addSpeedValue(int speed) {
        this.speedValues.add(speed);
    }

    public void addMovementValue(int direction) {
        this.movementValues.add(direction);
    }


}
