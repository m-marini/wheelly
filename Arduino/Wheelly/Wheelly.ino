//#include <Streaming.h>

#include <CommandParser.h>

#include "debug.h"
#include "Timer.h"
#include "Multiplexer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "MotorCtrl.h"
#include "AsyncSerial.h"

/*
 * Pins
 */
#define LATCH_PIN 8
#define CLOCK_PIN 7
#define DATA_PIN 12

#define TRIGGER_PIN 9
#define ECHO_PIN 4

#define SERVO_PIN 10

#define RECEIVER_PIN 11

#define ENABLE_LEFT_PIN 5
#define ENABLE_RIGHT_PIN 6

#define VOLTAGE_PIN A3

/*
 * Serial config
 */
#define SERIAL_BPS  115200

/*
 * Multiplexer outputs
 */
#define LEFT_BACK   0
#define LEFT_FORW   1
#define RIGHT_FORW  2
#define RIGHT_BACK  3
#define GREEN        4
#define YELLOW        5
#define RED        6
#define BLOCK_LED        7

/*
 * Motor speeds
 */
#define MAX_FORWARD 255
#define MAX_BACKWARD -255

/*
 * Distances
 */
#define STOP_DISTANCE  30
#define WARNING_DISTANCE  50
#define INFO_DISTANCE  70

/*
 * Directions
 */
#define FORWARD_LEFT    0
#define FORWARD         1
#define FORWARD_RIGHT   2
#define LEFT            3
#define STOP            4
#define RIGHT           5
#define BACKWARD_LEFT   6
#define BACKWARD        7
#define BACKWARD_RIGHT  8
#define NO_DIRECTIONS (sizeof(speedsByDirection) / sizeof(speedsByDirection[0]))

#define NO_COMMAND      -1

/*
 * Intervals
 */
#define SCAN_INTERVAL   10000ul
#define MOVE_INTERVAL   750ul

/*
 * Scanner constants
 */
#define NO_SAMPLES  3
#define FRONT_SCAN_INDEX  (NO_SCAN_DIRECTIONS / 2)
#define NO_SCAN_DIRECTIONS (sizeof(scanDirections) / sizeof(scanDirections[0]))
#define SERVO_OFFSET  -7

#define COMMANDS            3
#define COMMAND_ARGS        4
#define COMMAND_NAME_LENGTH 2
#define COMMAND_ARG_SIZE    64
#define RESPONSE_SIZE       64

typedef CommandParser<COMMANDS, COMMAND_ARGS, COMMAND_NAME_LENGTH, COMMAND_ARG_SIZE, RESPONSE_SIZE> Parser;

Parser parser;

/*
 * Global variables
 */

const Timer ledTimer;
const Timer statsTimer;
const Timer motorsTimer;

Multiplexer multiplexer(LATCH_PIN, CLOCK_PIN, DATA_PIN);

const SR04 sr04(TRIGGER_PIN, ECHO_PIN);

const AsyncServo servo;

const MotorCtrl leftMotor(ENABLE_LEFT_PIN, LEFT_FORW, LEFT_BACK);
const MotorCtrl rightMotor(ENABLE_RIGHT_PIN, RIGHT_FORW, RIGHT_BACK);

const AsyncSerial asyncSerial;

/*
 * Stats
 */
long counter;
unsigned long started;

/*
 * Obstacle distance scanner
 */
const int scanDirections[] = {
  0, 30, 60, 90, 120, 150, 180
};
bool isFullScanning;
int scanIndex;  // Scan index
int distances[NO_SCAN_DIRECTIONS];
unsigned long scanTimes[NO_SCAN_DIRECTIONS];

/*
 * Motor speeds [left, right] by direction
 */
int currentDirection;
const int speedsByDirection[][2] = {
  {0, MAX_FORWARD},             // FORWARD_LEFT
  {MAX_FORWARD, MAX_FORWARD},   // FORWARD
  {MAX_FORWARD, 0},             // FORWARD_RIGHT
  {MAX_BACKWARD, MAX_FORWARD},  // LEFT
  {0, 0},                       // STOP
  {MAX_FORWARD, MAX_BACKWARD},  // RIGHT
  {MAX_BACKWARD, 0},            // BACKWARD_LEFT
  {MAX_BACKWARD, MAX_BACKWARD}, // BACKWARD
  {0, MAX_BACKWARD},            // BACKWARD_RIGHT
};

const static unsigned long standbyTime[] = {50, 1450};

/*
 * Voltage sensor
 */
unsigned long voltageTime;
int voltageValue;
 

/*
 * Set up
 */
void setup() {
  Serial.begin(SERIAL_BPS);
  pinMode(LED_BUILTIN, OUTPUT);
  multiplexer.begin();
  sr04.begin();
  servo.attach(SERVO_PIN);
  leftMotor.begin(multiplexer);
  rightMotor.begin(multiplexer);
  
  multiplexer.reset().flush();

  digitalWrite(LED_BUILTIN, LOW);
  for (int i = 0; i < 3; i++) {
    delay(100);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(50);
    digitalWrite(LED_BUILTIN, LOW);
  }
  
  Serial.println(F("ha"));

  /*
   * Init led timer
   */
  ledTimer.onNext([](void *, int i, long) {
    /*
     * Handles timeout event from LED blink timer
     */
    digitalWrite(LED_BUILTIN, i == 0 ? LOW : HIGH);
  })
  .intervals(2, standbyTime)
  .continuous(true)
  .start();

  /*
   * Init stats timer
   */
  statsTimer.onNext([](void *context, int i, long j) {
    /*
    * Handle timeout event from statistics timer
    */

#if DEBUG
    Serial.print("Stats: ");
    Serial.println (counter * 1000 / (millis() - started));
#endif

    started = millis();
    counter = 0;
  })
    .interval(10000)
    .continuous(true)
    .start();

  motorsTimer.interval(1)
    .onNext([](void *, int i, long) {
      moveTo(STOP);
    });

  /*
   * Init distance sensor
   */
  sr04.noSamples(NO_SAMPLES)
    .onSample([](void *, int distance) {
      /*
       * Handles sample event from distance sensor
       */

#if DEBUG
      Serial << "handleSample: dir=" << scanDirections[scanIndex] << ", distance=" << distance << endl;
#endif

      distances[scanIndex] = distance;
      scanTimes[scanIndex] = millis();

      showDistance(distance);

      if (isForward(currentDirection) && !canMoveForward()) {
        moveTo(STOP);
        startFullScanning();
      } else {
        if (isFullScanning) {
          scanIndex++;
          if (scanIndex >= NO_SCAN_DIRECTIONS) {
            scanIndex = FRONT_SCAN_INDEX;
            isFullScanning = false;
            sendStatus();
          }
        }
        servo.angle(scanDirections[scanIndex]);
      }
    });

  /*
   * Init sensor servo
   */
  servo
    .offset(SERVO_OFFSET)
    .onReached([](void *, int angle) {
    /*
     * Handles position reached event from scan servo
     */
#if DEBUG
    Serial << "handleReached: dir=" << angle << endl;
#endif

    sr04.start();
  }).angle(scanDirections[FRONT_SCAN_INDEX]);

  /*
   * Init async serial port
   */
  asyncSerial.onData([](void *, const char *line, const Timing& timing) {
    processCommand(line, timing);
  });

  /*
   * Init parser
   */
  parser.registerCommand("mt", "ui", [](Parser::Argument *args, char *response) {
    unsigned long timeout = args[0].asUInt64;
    int dir = args[1].asInt64;
    if (dir == STOP) {
      moveTo(dir);
      motorsTimer.stop();
    } else if (isValidDirection(dir)
      && timeout > millis()
      && (!isForward(dir) || canMoveForward())) {
      moveTo(dir);
      motorsTimer.start(timeout);
    }
  });
  parser.registerCommand("sc", "", [](Parser::Argument *args, char *response) {
    startFullScanning();
  });
  parser.registerCommand("qs", "", [](Parser::Argument *args, char *response) {
    sendStatus();
  });

  started = millis();
  
  scanIndex = FRONT_SCAN_INDEX;
  servo.angle(scanDirections[scanIndex]);
  moveTo(STOP);
}

/*
 * Main loop
 */
void loop() {
  counter++;
  voltageTime = millis();
  voltageValue = analogRead(VOLTAGE_PIN);
  ledTimer.polling();
  statsTimer.polling();
  motorsTimer.polling();
  servo.polling();
  sr04.polling();
  asyncSerial.polling();
  multiplexer.flush();
}

/*
 * 
 */
void processCommand(const char *line, const Timing& timing) {

#if DEBUG
  Serial << "processCommand: " << line << endl;
#endif

  char response[Parser::MAX_RESPONSE_SIZE];
  char cmd[BUFFER_SIZE + 1];
  strtrim(cmd, line);

  if (strncmp(cmd, "ck ", 3) == 0) {
    Serial.print(cmd);
    Serial.print(F(" "));
    Serial.print(timing.millis);
    Serial.print(F(" "));
    Serial.print(timing.micros);
    Serial.print(F(" "));
    unsigned long ms = millis();
    unsigned long us = micros();
    Serial.print(ms);
    Serial.print(F(" "));
    Serial.println(us);
  } else {
    parser.processCommand(cmd, response);
#if DEBUG
    Serial << "  response: " << response << endl;
#endif

  }
}

char *strtrim(char *out, const char *from) {
  while (isSpace(*from)) {
    from++;
  }
  const char *to = from + strlen(from) - 1;
  while (to >= from && isSpace(*to)) {
      to--;
  }
  char *s = out;
  while (from <= to) {
    *s++ = *from++;
  }
  *s = '\0';
  return out;
}

/*
 * Returns true if forward direction
 */
bool isForward(int direction) {
  switch (direction) {
    case FORWARD_LEFT:
    case FORWARD:
    case FORWARD_RIGHT:
      return true;
    default:
      return false;
  }
}

bool isValidDirection(int direction) {
  return direction >= 0 && direction < NO_DIRECTIONS;
}

/*
 * Returns true if can move forward 
 */
bool canMoveForward() {
  for (int i = 1; i < NO_SCAN_DIRECTIONS - 1; i++) {
    int d = distances[i];
    if (d > 0 && d <= STOP_DISTANCE) {
      return false;
    }
  }
  return true;
}

/*
 * Move Wheelly to direction
 */
void moveTo(int direction) {
  if (direction < 0 || direction >= NO_DIRECTIONS) {
    direction = STOP;
  }
  int left = speedsByDirection[direction][0];
  int right = speedsByDirection[direction][1];
  leftMotor.speed(left);
  rightMotor.speed(right);
  int prevDir = currentDirection;
  currentDirection = direction;
  if (prevDir != currentDirection) {    
    sendStatus();
  }
}

/*
 * Show distances
 */
void showDistance(int distance) {
  multiplexer.set(GREEN, distance > 0 && distance <= INFO_DISTANCE);
  multiplexer.set(YELLOW, distance > 0 && distance <= WARNING_DISTANCE);
  multiplexer.set(RED, distance > 0 && distance <= STOP_DISTANCE);
  multiplexer.set(BLOCK_LED, !canMoveForward());
}

void startFullScanning() {
  sr04.stop();

#if DEBUG
  Serial << "startFullScanning" << endl;
#endif

  isFullScanning = true;
  scanIndex = 0;
  servo.angle(scanDirections[scanIndex]);
}

void sendStatus() {
  Serial.print(F("st "));
  Serial.print(millis());
  Serial.print(F(" "));
  Serial.print(currentDirection);
  Serial.print(F(" "));
  for (int i = 0; i < NO_SCAN_DIRECTIONS; i++) {
    Serial.print(scanTimes[i]);
    Serial.print(F(" "));
    Serial.print(scanDirections[i]);
    Serial.print(F(" "));
    Serial.print(distances[i]);
    Serial.print(F(" "));    
  }
  Serial.print(voltageTime);
  Serial.print(F(" "));
  Serial.print(voltageValue);
  Serial.println();
}

/*
 * Process serial event 
 */
void serialEvent() {
  asyncSerial.serialEvent();
}
