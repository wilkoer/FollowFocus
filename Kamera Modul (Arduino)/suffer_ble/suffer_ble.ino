/*********************************************************************
This is an example for our nRF8001 Bluetooth Low Energy Breakout

  Pick one up today in the adafruit shop!
  ------> http://www.adafruit.com/products/1697

Adafruit invests time and resources providing this open source code,
please support Adafruit and open-source hardware by purchasing
products from Adafruit!

Written by Kevin Townsend/KTOWN  for Adafruit Industries.
MIT license, check LICENSE for more information
All text above, and the splash screen below must be included in any redistribution
*********************************************************************/
// This version uses the internal data queing so you can treat it like Serial (kinda)!


// Movement of Optical encoder is translated to moving stepper
// THIS IS A WORKING VERSION 22.1.13
//      Adi Soffer  2013       //
//    for more info visit      //
// http://adisoffer.tumblr.com //


#include <SPI.h>
#include <math.h>
#include "Adafruit_BLE_UART.h"
#include <AccelStepper.h>
#include <stdlib.h>

// Connect CLK/MISO/MOSI to hardware SPI
// e.g. On UNO & compatible: CLK = 13, MISO = 12, MOSI = 11
#define ADAFRUITBLE_REQ 10
#define ADAFRUITBLE_RDY 2     // This should be an interrupt pin, on Uno thats #2 or #3
#define ADAFRUITBLE_RST 9

Adafruit_BLE_UART BTLEserial = Adafruit_BLE_UART(ADAFRUITBLE_REQ, ADAFRUITBLE_RDY, ADAFRUITBLE_RST);


AccelStepper stepper(1, 8, 7);
int enablePin = 3;
int MinPulseWidth = 50; //too low and the motor will stall, too high and it will slow it down
int minSpeedValue = 50;
int maxSpeedValue = 2000;

//Sleep to save energy
long previousMillis = 0;
int sleepTimer = 5000;

//values will change
volatile long encoderValue = 0; 
byte dataReceive = 0;
int speedValue = 1;
int highEndMark = 500000;
int lowEndMark = -500000;
int stepSize = 200;
bool waitForStartPos = false;


void setup(void)
{
  Serial.begin(9600);  
  stepper.setMinPulseWidth(MinPulseWidth); 
  stepper.setMaxSpeed(speedValue);             //variable to later determine speed play/rewind
  stepper.setAcceleration(100000); 
  stepper.setSpeed(50000); 
  pinMode(enablePin, OUTPUT); 
  
  while (!Serial); // Leonardo/Micro should wait for serial init
  Serial.println(F("Adafruit Bluefruit Low Energy nRF8001 Print echo demo"));
  BTLEserial.setDeviceName("ATH_BLE"); /* 7 characters max! */
  BTLEserial.begin();
}

aci_evt_opcode_t laststatus = ACI_EVT_DISCONNECTED;

void loop()
{  
  BTLEserial.pollACI(); // Tell the nRF8001 to do whatever it should be working on.
  aci_evt_opcode_t status = BTLEserial.getState();

  // If the status changed print it out!
  if (status != laststatus) 
  {
    if (status == ACI_EVT_DEVICE_STARTED)
      Serial.println(F("* Advertising started"));
    if (status == ACI_EVT_CONNECTED) 
      Serial.println(F("* Connected!"));
    if (status == ACI_EVT_DISCONNECTED) 
      Serial.println(F("* Disconnected or advertising timed out"));
    // OK set the last status change to this one
    laststatus = status;
  }
  
  if (status == ACI_EVT_CONNECTED) 
  {
    stepper.enableOutputs();
    stepper.run();
    if (BTLEserial.available()>0) 
    {
      digitalWrite (enablePin, LOW);
      previousMillis = millis();
      dataReceive = BTLEserial.read();
      //Serial.println(dataReceive);
      react(); //function to react to input   

      stepper.run();
      stepper.moveTo(encoderValue);
    }
    else
    {
      //Stepper sleep after 5sec of no data
      unsigned long currentMillis = millis ();
      if (currentMillis - previousMillis > sleepTimer)
      {
        digitalWrite (enablePin, HIGH);
        stepper.disableOutputs();
      }
    } 
  }
}

void react()
{
  if (dataReceive > 48 && dataReceive < 58)  //[1-9]-Key
  {
    speedValue = map(dataReceive, 49, 57, minSpeedValue, maxSpeedValue);
    stepper.setMaxSpeed(speedValue);
    Serial.println(speedValue);
  } 
  else if (dataReceive == 43) //[+]-Key
  {
    encoderValue += stepSize;
    if (encoderValue > highEndMark){
        encoderValue = highEndMark;
      }
  }
  else if (dataReceive == 45)  //[-]-Key
  {
    encoderValue -= stepSize;
    if (encoderValue < lowEndMark){
        encoderValue = lowEndMark;
      }
  }    
  else if (dataReceive == 0) {
    encoderValue = stepper.currentPosition();
  }
  else if (dataReceive == 97 || dataReceive == 122 || dataReceive == 115)
  {
    String s = "EndMark";
    if (dataReceive == 97)  //[a]-Key
    {
      lowEndMark = stepper.currentPosition(); 
      s = "LowEndMark" + String(lowEndMark);
    }    
    else if (dataReceive == 122)  //[z]-Key
    {
      highEndMark = stepper.currentPosition();
      s = "HighEndMark" + String(highEndMark);
    } 
    else if (dataReceive == 115) { //[s] for save
       s = "StartPosition" + String(stepper.currentPosition();
    }
    // We need to convert the line to bytes, no more than 20 at this time
    uint8_t sendbuffer[20];
    s.getBytes(sendbuffer, 20);
    char sendbuffersize = min(20, s.length());
    Serial.print(F("\n* Sending -> \"")); Serial.print((char *)sendbuffer); Serial.println("\"");
    BTLEserial.write(sendbuffer, sendbuffersize);
  }
  else if (dateReceive == 112) {  //[r] for rewind
     waitForStartPos = true;
  } else if (waitForStartPos){
     encoderValue = dataReceive;
     waitForStartPos = false;
  }
} 























