/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.model;

import java.awt.geom.Point2D;
import java.util.StringJoiner;

import static java.lang.Math.toRadians;

/**
 *
 */
public class RobotAsset {

    /**
     * @param x         x coordinate
     * @param y         y coordinate
     * @param direction direction (DEG)
     */
    public static RobotAsset create(float x, float y, int direction) {
        return new RobotAsset(x, y, direction);
    }

    public final int direction;
    public final float x;
    public final float y;

    /**
     * @param x         x coordinate
     * @param y         y coordinate
     * @param direction direction (DEG)
     */
    protected RobotAsset(float x, float y, int direction) {
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    public float getAngle() {
        return (float) toRadians(direction);
    }

    public int getDirection() {
        return direction;
    }

    public Point2D getLocation() {
        return new Point2D.Float(x, y);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RobotAsset.class.getSimpleName() + "[", "]")
                .add("x=" + x)
                .add("y=" + y)
                .add("direction=" + direction)
                .toString();
    }
}
