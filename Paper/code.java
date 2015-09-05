/*
* Timer Thread
* - executed every <EXECUTION_INTERVAL> ms
* - sends currentDirection and currentSpeed 
*   via BLE to Arduino device
* */
public void startTimer (){
  final Handler handler = new Handler ();
  Timer timer = new Timer();
  timer.scheduleAtFixedRate(new TimerTask() {
    public void run() {
      handler.post(new Runnable() {
        public void run() {

// if not playing a recorded scene:
          if (!isPlayingScene) { 

// send data via BLE

            if (isRecording) {
// if scene is currently recording, 
// write values into FocusScene object
            }
          } else { 
// if recorded scene is being 
// played back, send recorded
// values as byte values.
// convert slide value (Integer)
// to byte values and send via BLE

// stop playing, when max frames reached:
            if (currentSceneFrame < 
                selectedFocusScene
                .getSpeedValues().size()-1) {
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