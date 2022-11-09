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

package org.mmarini.wheelly.swing;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.MapSector;
import org.mmarini.wheelly.apis.RadarMap;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The canvas with environment display
 */
public class RadarPanel extends JComponent {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    private static final float DEFAULT_WORLD_SIZE = 11;
    private static final float DEFAULT_SCALE = 400 / DEFAULT_WORLD_SIZE;
    private static final float GRID_SIZE = 1f;

    private static final Color GRID_COLOR = new Color(50, 50, 50);
    private static final Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    private static final Color FILLED_COLOR = new Color(200, 0, 0, 128);
    private static final Shape GRID_SHAPE = createGridShape();

    /**
     * Returns the transformation to draw in a world location
     *
     * @param location location in world coordinate
     */
    static AffineTransform at(Point2D location) {
        return AffineTransform.getTranslateInstance(location.getX(), location.getY());
    }

    static Shape createGridShape() {
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(0, -DEFAULT_WORLD_SIZE);
        shape.lineTo(0, DEFAULT_WORLD_SIZE);
        shape.moveTo(-DEFAULT_WORLD_SIZE, 0);
        shape.lineTo(DEFAULT_WORLD_SIZE, 0);
        for (float s = GRID_SIZE; s <= DEFAULT_WORLD_SIZE; s += GRID_SIZE) {
            shape.moveTo(s, -DEFAULT_WORLD_SIZE);
            shape.lineTo(s, DEFAULT_WORLD_SIZE);
            shape.moveTo(-s, -DEFAULT_WORLD_SIZE);
            shape.lineTo(-s, DEFAULT_WORLD_SIZE);
            shape.moveTo(-DEFAULT_WORLD_SIZE, s);
            shape.lineTo(DEFAULT_WORLD_SIZE, s);
            shape.moveTo(-DEFAULT_WORLD_SIZE, -s);
            shape.lineTo(DEFAULT_WORLD_SIZE, -s);
        }
        return shape;
    }

    private final Point2D centerLocation;
    private final float scale;
    private List<Tuple2<Point2D, Color>> radarMap;
    private Shape sectorShape;

    public RadarPanel() {
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        scale = DEFAULT_SCALE;
        centerLocation = new Point2D.Float();
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        result.scale(scale, -scale);
        result.translate(-centerLocation.getX(), -centerLocation.getY());
        return result;
    }

    /**
     * Draws the grid
     *
     * @param gr the graphic context
     */
    private void drawGrid(Graphics2D gr) {
        gr.setStroke(BORDER_STROKE);
        gr.setColor(GRID_COLOR);
        gr.draw(GRID_SHAPE);
    }

    /**
     * Draws an obstacle
     *
     * @param gr       the graphic context
     * @param location the location
     * @param color    the color
     */
    private void drawObstacle(Graphics2D gr, Point2D location, Color color) {
        if (location != null) {
            gr.transform(at(location));
            gr.setColor(color);
            gr.setStroke(BORDER_STROKE);
            gr.fill(sectorShape);
        }
    }

    private void drawRadarMap(Graphics2D gr) {
        if (radarMap != null) {
            AffineTransform base = gr.getTransform();
            gr.setStroke(BORDER_STROKE);
            for (Tuple2<Point2D, Color> t : radarMap) {
                gr.setTransform(base);
                drawObstacle(gr, t._1, t._2);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = (Graphics2D) g.create();
        gr.transform(createBaseTransform());
        AffineTransform base = gr.getTransform();
        drawGrid(gr);

        gr.setTransform(base);
        drawRadarMap(gr);
    }

    public void setRadarMap(RadarMap radarMap) {
        if (radarMap != null) {
            this.radarMap = Arrays.stream(radarMap.getMap())
                    .filter(MapSector::isKnown)
                    .map(sector -> Tuple2.of(sector.getLocation(),
                            sector.isFilled() ? FILLED_COLOR : EMPTY_COLOR))
                    .collect(Collectors.toList());
            float size = radarMap.getTopology().getGridSize();
            sectorShape = new Rectangle2D.Float(
                    -size / 2, -size / 2,
                    size, size);
        } else {
            this.radarMap = null;
        }
        repaint();
    }
}