/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static java.lang.Math.*;
import static org.mmarini.wheelly.apis.Utils.direction;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * MapSector keeps the presence of obstacles in the sector
 */
public class MapSector {
    public static final float MAX_SIGNAL_DISTANCE = 3F;
    public static final double THRESHOLD_SIGNAL_DISTANCE = 0.2;

    private final Point2D location;
    private long timestamp;
    private boolean filled;

    /**
     * Creates the MapSector
     *
     * @param location  the location of sector
     * @param timestamp the timestamp of filled info (0 if unknown)
     * @param filled    true if sector is filled
     */
    public MapSector(Point2D location, long timestamp, boolean filled) {
        this.location = location;
        this.timestamp = timestamp;
        this.filled = filled;
    }

    public void clean(long timeout) {
        if (timestamp <= timeout) {
            timestamp = 0;
        }
    }

    public Point2D getLocation() {
        return location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean hasObstacle() {
        return isKnown() && filled;
    }

    public boolean isFilled() {
        return filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public boolean isKnown() {
        return timestamp > 0;
    }

    public void union(MapSector other) {
        if (other.timestamp > timestamp) {
            timestamp = other.timestamp;
            filled = other.filled;
        }
    }

    /**
     * Updates the map sector with the result of a sensor signal.
     * <p>
     * The condition to update the status of a sector is that the distance of the signal is in range (> minDistance && < MAX_SIGNAL_DISTANCE)
     * And the signal direction is in range of sector (direction within the direction of sector edge)
     * And the sensor signal >= sector distance - receptive distance<br>
     * The status of sector is set to empty if no echo signal is detected or the sensor distance > sector distance + sector size / 2 - threshold
     * otherwise is set to filled
     * <p>
     *
     * @param signal            the sensor signal
     * @param receptiveDistance the receptive sector distance (distance from signal to set sector filled)
     */
    public void update(RadarMap.SensorSignal signal, float minDistance, float receptiveDistance) {
        float sectorDistance = (float) signal.sensorLocation.distance(location);
        boolean inRange = sectorDistance >= minDistance && sectorDistance <= MAX_SIGNAL_DISTANCE;
        if (inRange) {
            double sectorDirection = direction(signal.sensorLocation, location);
            double sectorDirFromSens = normalizeDegAngle(signal.sensorDirection - toDegrees(sectorDirection));
            //int a0 = (int) round(toDegrees(atan2(receptiveDistance, sectorDistance)));
            int a0 = (int) round(toDegrees(asin(receptiveDistance / sectorDistance)));
            boolean inDirection = abs(sectorDirFromSens) <= a0;
            if (inDirection) {
                if (signal.isEcho()) {
                    if (signal.distance >= sectorDistance - receptiveDistance) {
                        timestamp = signal.timestamp;
                        filled = signal.distance <= sectorDistance + receptiveDistance;
                    }
                } else {
                    timestamp = signal.timestamp;
                    filled = false;
                }
            }
        }
    }
}
