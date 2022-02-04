#ifndef SR04_h
#define SR04_h

#include "Arduino.h"
#include "Timer.h"

/*
 * ASynchronous SR04
 */
class SR04 {
  public:
    SR04(int triggerPin, int echoPin);

    // Sets a single interval
    SR04& begin();

    // Sets a single interval
    SR04& inactivity(unsigned long interval);

    // Sets a single interval
    SR04& noSamples(int noSamples);

    // Starts the sampling
    SR04& start(void *context = NULL);

    // Stops the sampling
    SR04& stop();
  
    // Returns true if timer is sampling
    bool operator!() const {return _sampling;}

    // Sets the callback 
    SR04& onSample(void (*callback)(void* context, int distance));

    // Polls the timer
    SR04& polling();

  private:
    unsigned long _inactivity;
    int _triggerPin;
    int _echoPin;
    int _noSamples;
    void (*_onSample)(void*, int);

    bool _sampling;
    int _noMeasures;
    int _noValidSamples;
    unsigned long _totalDuration;
    void* _context;
    Timer _timer;

    static void _handleTimeout(void *, int, long);
    SR04& _send();
    SR04& _measure();
};

#endif
