if (status == ACI_EVT_CONNECTED) 
  {
    stepper.enableOutputs();
    stepper.run();
    if (BLEserial.available()>0) 
    {
      digitalWrite (enablePin, LOW);
      previousMillis = millis();
      dataReceive = BLEserial.read();
      react(); //function to react to input   
      stepper.run();
      stepper.moveTo(encoderValue);
    } 
	...

void react()
{
  if (dataReceive > 48 && dataReceive < 58)  //[1-9]-Key
  {
    speedValue = map(dataReceive, 49, 57,
    	minSpeedValue, maxSpeedValue);
    stepper.setMaxSpeed(speedValue);
    Serial.println(speedValue);
  } 
  else if (dataReceive == 43) //[+]-Key
  {
    encoderValue += stepSize;
  }
  else if (dataReceive == 45)  //[-]-Key
  {
    encoderValue -= stepSize;
  }    
  else if (dataReceive == 0) {
    encoderValue = stepper.currentPosition();
  }
  ...