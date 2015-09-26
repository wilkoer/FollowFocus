package ath.lmu.de.followfocus;

import java.util.ArrayList;

/**
 * A Focus Scene has a List for speed values, direction values, a name and a status.
 * Name and status are shown in the list entry.
 */
public class FocusScene {
    private ArrayList<Integer> speedValues;
    private ArrayList<Integer> movementValues;
    private String name, status;
    private int startPosition;

    public FocusScene(String name) {
        this.name = name;
        this.status = "";
        this.movementValues = new ArrayList<>();
        this.speedValues = new ArrayList<>();
        this.startPosition = 0;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public FocusScene(String status, String name, ArrayList<Integer> speedValues, ArrayList<Integer> movementValues, int startPosition) {
        this.status = status;
        this.name = name;
        this.speedValues = speedValues;
        this.movementValues = movementValues;
        this.startPosition = startPosition;

    }

    public String getName() {
        return name;
    }

    public ArrayList<Integer> getSpeedValues() {
        return speedValues;
    }

    public ArrayList<Integer> getMovementValues() {
        return movementValues;
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
