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

import com.fasterxml.jackson.databind.JsonNode;
import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.WorldManifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.contacts.Contact;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_SCALE;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * Simulated robot
 */
public class SimRobot implements RobotApi {
    public static final double GRID_SIZE = 0.2;
    public static final double WORLD_SIZE = 10;
    public static final double X_CENTER = 0;
    public static final double Y_CENTER = 0;
    public static final double MAX_OBSTACLE_DISTANCE = 3;
    public static final double MAX_DISTANCE = 3;
    public static final double MAX_VELOCITY = MAX_PPS * DISTANCE_PER_PULSE;
    public static final double MAX_ANGULAR_PPS = 20;
    public static final double ROBOT_TRACK = 0.136;
    public static final double MAX_ANGULAR_VELOCITY = MAX_ANGULAR_PPS * DISTANCE_PER_PULSE / ROBOT_TRACK * 2; // RAD/s
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/sim-robot-schema-0.1";
    private static final int DEFAULT_MAX_ANGULAR_SPEED = 5;
    private static final long DEFAULT_MOTION_INTERVAL = 500;
    private static final long DEFAULT_PROXY_INTERVAL = 500;
    private static final double MIN_OBSTACLE_DISTANCE = 1;
    private static final Vec2 GRAVITY = new Vec2();
    private static final int VELOCITY_ITER = 10;
    private static final int POSITION_ITER = 10;
    private static final double RAD_10 = toRadians(10);
    private static final double RAD_30 = toRadians(30);
    private static final double ROBOT_MASS = 0.785;
    private static final double ROBOT_FRICTION = 1;
    private static final double ROBOT_RESTITUTION = 0;
    private static final double SAFE_DISTANCE = 0.2;
    private static final double MAX_ACC = 1;
    private static final double MAX_FORCE = MAX_ACC * ROBOT_MASS;
    private static final double MAX_TORQUE = 0.7;
    private static final Logger logger = LoggerFactory.getLogger(SimRobot.class);
    private static final float ROBOT_RADIUS = 0.15f;
    private static final double ROBOT_DENSITY = ROBOT_MASS / (ROBOT_RADIUS * ROBOT_RADIUS * PI);
    private static final int DEFAULT_SENSOR_RECEPTIVE_ANGLE = 15;
    private static final double DEG89_5_EPSILON = sin(toDegrees(89.5));

    public static SimRobot create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long mapSeed = locator.path("mapSeed").getNode(root).asLong(0);
        long robotSeed = locator.path("robotSeed").getNode(root).asLong(0);
        int numObstacles = locator.path("numObstacles").getNode(root).asInt();
        Complex sensorReceptiveAngle = Complex.fromDeg(locator.path("sensorReceptiveAngle").getNode(root).asInt(DEFAULT_SENSOR_RECEPTIVE_ANGLE));
        Random mapRandom = mapSeed > 0L ? new Random(mapSeed) : new Random();
        Random robotRandom = robotSeed > 0L ? new Random(robotSeed) : new Random();
        ObstacleMap obstacleMap = MapBuilder.create(GRID_SIZE)
                .rect(-WORLD_SIZE / 2,
                        -WORLD_SIZE / 2, WORLD_SIZE / 2, WORLD_SIZE / 2)
                .rand(numObstacles, X_CENTER, Y_CENTER, MIN_OBSTACLE_DISTANCE, MAX_OBSTACLE_DISTANCE, mapRandom)
                .build();
        double errSigma = locator.path("errSigma").getNode(root).asDouble();
        double errSensor = locator.path("errSensor").getNode(root).asDouble();
        int maxAngularSpeed = locator.path("maxAngularSpeed").getNode(root).asInt(DEFAULT_MAX_ANGULAR_SPEED);
        long motionInterval = locator.path("motionInterval").getNode(root).asLong(DEFAULT_MOTION_INTERVAL);
        long proxyInterval = locator.path("proxyInterval").getNode(root).asLong(DEFAULT_PROXY_INTERVAL);
        return new SimRobot(obstacleMap,
                robotRandom,
                errSigma, errSensor,
                sensorReceptiveAngle, maxAngularSpeed, motionInterval, proxyInterval);
    }

    /**
     * Creates obstacle in the world
     *
     * @param world    thr world
     * @param location the obstacle location
     */
    protected static void createObstacle(World world, Point2D location) {
        PolygonShape obsShape = new PolygonShape();
        obsShape.setAsBox(RobotStatus.OBSTACLE_SIZE / 2, RobotStatus.OBSTACLE_SIZE / 2);

        FixtureDef obsFixDef = new FixtureDef();
        obsFixDef.shape = obsShape;

        BodyDef obsDef = new BodyDef();
        obsDef.type = BodyType.STATIC;

        obsDef.position.x = (float) location.getX();
        obsDef.position.y = (float) location.getY();
        Body obs = world.createBody(obsDef);
        obs.createFixture(obsFixDef);
    }

    private final double errSensor;
    private final double errSigma;
    private final int maxAngularSpeed;
    private final long motionInterval;
    private final ObstacleMap obstacleMap;
    private final long proxyInterval;
    private final Random random;
    private final Body robot;
    private final Fixture robotFixture;
    private final Complex sensorReceptiveAngle;
    private final World world;
    private Complex direction;
    private double echoDistance;
    private boolean frontSensor;
    private double leftPps;
    private long motionTimeout;
    private Consumer<ClockSyncEvent> onClock;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellyMotionMessage> onMotion;
    private Consumer<WheellyProxyMessage> onProxy;
    private long proxyTimeout;
    private boolean rearSensor;
    private double rightPps;
    private Complex sensorDirection;
    private long simulationTime;
    private int speed;

    /**
     * Creates a simulated robot
     *
     * @param obstacleMap          the obstacle map
     * @param random               the random generator
     * @param errSigma             sigma of errors in physic simulation (U)
     * @param errSensor            sensor error (m)
     * @param sensorReceptiveAngle sensor receptive angle (DEG)
     * @param maxAngularSpeed      the maximum angular speed
     * @param motionInterval       the interval between motion messages
     * @param proxyInterval        the interval between proxy messages
     */
    public SimRobot(ObstacleMap obstacleMap, Random random, double errSigma, double errSensor, Complex sensorReceptiveAngle, int maxAngularSpeed, long motionInterval, long proxyInterval) {
        logger.atDebug().log("Created");
        this.random = requireNonNull(random);
        this.errSigma = errSigma;
        this.errSensor = errSensor;
        this.obstacleMap = requireNonNull(obstacleMap);
        this.sensorReceptiveAngle = requireNonNull(sensorReceptiveAngle);
        this.sensorDirection = Complex.DEG0;
        this.direction = Complex.DEG0;
        this.maxAngularSpeed = maxAngularSpeed;
        this.motionInterval = motionInterval;
        this.proxyInterval = proxyInterval;
        this.frontSensor = this.rearSensor = true;

        // Creates the jbox2 physic world
        this.world = new World(GRAVITY);
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                SimRobot.this.handleBeginContact(contact);
            }

            @Override
            public void endContact(Contact contact) {
                SimRobot.this.handleEndContact(contact);
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse contactImpulse) {
            }

            @Override
            public void preSolve(Contact contact, Manifold manifold) {
            }
        });

        // Creates the jbox2 physic robot body
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.angle = (float) (PI / 2);
        this.robot = world.createBody(bodyDef);

        CircleShape circleShape = new CircleShape();
        circleShape.setRadius(ROBOT_RADIUS);
        FixtureDef fixDef = new FixtureDef();
        fixDef.shape = circleShape;
        fixDef.friction = (float) ROBOT_FRICTION;
        fixDef.density = (float) ROBOT_DENSITY;
        fixDef.restitution = (float) ROBOT_RESTITUTION;
        this.robotFixture = robot.createFixture(fixDef);

        // Create obstacle fixture
        for (Point2D point : obstacleMap.points()) {
            createObstacle(world, point);
        }
    }

    /**
     * Returns true if robot can move backward
     */
    boolean canMoveBackward() {
        return rearSensor;
    }

    /**
     * Returns true if robot can move forward
     */
    boolean canMoveForward() {
        return frontSensor && (echoDistance == 0 || echoDistance > SAFE_DISTANCE);
    }

    /**
     * Halt the robot if it is moving in forbidden directionDeg
     */
    private void checkForSpeed() {
        if (((speed > 0 || leftPps > 0 || rightPps > 0) && !canMoveForward())
                || ((speed < 0 || leftPps < 0 || rightPps < 0) && !canMoveBackward())) {
            halt();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void configure() {
        if (onClock != null) {
            onClock.accept(new ClockSyncEvent(simulationTime, simulationTime, simulationTime, simulationTime));
        }
        sendMotion();
        sendProxy();
        sendContacts();
    }

    @Override
    public void connect() {
    }

    /**
     * Returns the contact directionDeg relative to the robot (RAD)
     *
     * @param contact the contact
     */
    private Complex contactRelativeDirection(Contact contact) {
        WorldManifold worldManifold = new WorldManifold();
        contact.getWorldManifold(worldManifold);
        Point2D contactAt = new Point2D.Double(worldManifold.points[0].x,
                worldManifold.points[0].y);
        Point2D location = this.location();
        Complex contactDirection = Complex.direction(location, contactAt);
        return contactDirection.sub(direction());
    }

    /**
     * @param dt the localTime interval
     */
    private void controller(double dt) {
        // Direction difference
        double dAngle = direction().sub(direction).toRad();

        // Relative angular speed to fix the directionDeg
        double angularVelocityPps = Utils.clip(Utils.linear(dAngle, -RAD_10, RAD_10, -maxAngularSpeed, maxAngularSpeed), -maxAngularSpeed, maxAngularSpeed);
        // Relative linear speed to fix the speed

        double linearVelocityPps = speed * Utils.clip(Utils.linear(abs(dAngle), 0, RAD_30, 1, 0), 0, 1);

        // Relative left-right motor speeds
        leftPps = Utils.clip((linearVelocityPps - angularVelocityPps), -MAX_PPS, MAX_PPS);
        rightPps = Utils.clip((linearVelocityPps + angularVelocityPps), -MAX_PPS, MAX_PPS);

        // Real left-right motor speeds
        double left = leftPps * DISTANCE_PER_PULSE;
        double right = rightPps * DISTANCE_PER_PULSE;

        // Real forward velocity
        double forwardVelocity = (left + right) / 2;

        // target real speed
        Vec2 targetVelocity = robot.getWorldVector(Utils.vec2(forwardVelocity, 0));
        // Difference of speed
        Vec2 dv = targetVelocity.sub(robot.getLinearVelocity());
        // Impulse to fix the speed
        Vec2 dq = dv.mul(robot.getMass());
        // Force to fix the speed
        Vec2 force = dq.mul((float) (1 / dt));
        // Robot relative force
        Vec2 localForce = robot.getLocalVector(force);
        // add a random factor to force
        localForce = localForce.mul((float) (1 + random.nextGaussian() * errSensor));

        // Clip the local force to physic constraints
        localForce.x = (float) Utils.clip(localForce.x, -MAX_FORCE, MAX_FORCE);
        force = robot.getWorldVector(localForce);

        // Angle rotation due to differential motor speeds
        double angularVelocity1 = (right - left) / ROBOT_TRACK;
        // Limits rotation to max allowed rotation
        double angularVelocity = clip(angularVelocity1, -MAX_ANGULAR_VELOCITY, MAX_ANGULAR_VELOCITY);
        // Angular impulse to fix directionDeg
        double robotAngularVelocity = robot.getAngularVelocity();
        double angularTorque = (angularVelocity - robotAngularVelocity) * robot.getInertia() / dt;
        // Add a random factor to angular impulse
        angularTorque *= (1 + random.nextGaussian() * errSigma);
        // Clip the angular torque
        angularTorque = Utils.clip(angularTorque, -MAX_TORQUE, MAX_TORQUE);
        world.clearForces();
        robot.applyForceToCenter(force);
        robot.applyTorque((float) angularTorque);
        world.step((float) dt, VELOCITY_ITER, POSITION_ITER);

        // Update robot status
        updateMotion();
    }

    /**
     * Returns the robot directionDeg (DEG)
     */
    public Complex direction() {
        return Complex.fromRad(PI / 2 - robot.getAngle());
    }

    /**
     * Returns the echo distance (m)
     */
    public double echoDistance() {
        return echoDistance;
    }

    /**
     * Returns true if front sensor is clear
     */
    boolean frontSensor() {
        return frontSensor;
    }

    @Override
    public void halt() {
        speed = 0;
        direction = direction();
        leftPps = 0;
        rightPps = 0;
    }

    private void handleBeginContact(Contact contact) {
        if (contact.m_fixtureA.equals(robotFixture) || contact.m_fixtureB.equals(robotFixture)) {
            // Contact with robot fixture
            Complex contactDirection = contactRelativeDirection(contact);
            logger.atDebug().setMessage("Begin contact at {}")
                    .addArgument(() -> {
                        WorldManifold worldManifold = new WorldManifold();
                        contact.getWorldManifold(worldManifold);
                        return new Point2D.Double(worldManifold.points[0].x,
                                worldManifold.points[0].y);
                    }).log();
            logger.atDebug().setMessage("        robot at {} {} DEG")
                    .addArgument(this::location)
                    .addArgument(() -> direction().toIntDeg())
                    .log();
            logger.atDebug().setMessage("      contact at {} DEG")
                    .addArgument(contactDirection::toIntDeg)
                    .log();
            if (contactDirection.isFront(DEG89_5_EPSILON)) {
                // contact at +-89.5 DEG from the front
                frontSensor = false;
                halt();
            }
            if (contactDirection.isRear(DEG89_5_EPSILON)) {
                // contact at +-89.5 DEG from the rear
                rearSensor = false;
                halt();
            }
        }
        sendContacts();
    }

    private void handleEndContact(Contact contact) {
        logger.atDebug().log("End contact");
        WorldManifold worldManifold = new WorldManifold();
        contact.getWorldManifold(worldManifold);
        frontSensor = true;
        rearSensor = true;
        sendContacts();
    }

    /**
     * Returns the robot location (m)
     */
    public Point2D location() {
        Vec2 pos = robot.getPosition();
        return new Point2D.Double(pos.x, pos.y);
    }

    @Override
    public void move(Complex dir, int speed) {
        this.direction = dir;
        this.speed = min(max(speed, -MAX_PPS), MAX_PPS);
        checkForSpeed();
    }

    /**
     * Returns the obstacles map
     */
    public Optional<ObstacleMap> obstaclesMap() {
        return Optional.ofNullable(obstacleMap);
    }

    /**
     * Returns true if rear sensor is clear
     */
    boolean rearSensor() {
        return rearSensor;
    }

    @Override
    public void scan(Complex dir) {
        this.sensorDirection = dir.y() >= 0
                ? dir
                : dir.x() >= 0
                ? Complex.DEG90 : Complex.DEG270;
    }

    /**
     * Sends the contacts message
     */
    private void sendContacts() {
        if (onContacts != null) {
            WheellyContactsMessage msg = new WheellyContactsMessage(
                    System.currentTimeMillis(), simulationTime, simulationTime,
                    frontSensor, rearSensor,
                    canMoveForward(),
                    canMoveBackward()
            );
            onContacts.accept(msg);
            logger.atDebug().log("On contacts {}", msg);
        }
    }

    /**
     * Sends the motion message
     */
    private void sendMotion() {
        if (onMotion != null) {
            Vec2 pos = robot.getPosition();
            double xPulses = pos.x / DISTANCE_PER_PULSE;
            double yPulses = pos.y / DISTANCE_PER_PULSE;
            Complex robotDir = direction();
            WheellyMotionMessage msg = new WheellyMotionMessage(
                    System.currentTimeMillis(), simulationTime, simulationTime,
                    xPulses, yPulses, robotDir.toIntDeg(),
                    leftPps, rightPps,
                    0, speed == 0,
                    (int) round(leftPps), (int) round(rightPps),
                    0, 0);
            onMotion.accept(msg);
        }
        motionTimeout = simulationTime + motionInterval;
    }

    /**
     * Sends the proxy message
     */
    private void sendProxy() {
        if (onProxy != null) {
            Vec2 pos = robot.getPosition();
            double xPulses = pos.x / DISTANCE_PER_PULSE;
            double yPulses = pos.y / DISTANCE_PER_PULSE;
            Complex echoYaw = direction();
            long echoDelay = round(echoDistance / DISTANCE_SCALE);
            WheellyProxyMessage msg = new WheellyProxyMessage(
                    System.currentTimeMillis(), simulationTime, simulationTime,
                    sensorDirection.toIntDeg(), echoDelay, xPulses, yPulses, echoYaw.toIntDeg());
            onProxy.accept(msg);
        }
        proxyTimeout = simulationTime + proxyInterval;
    }

    /**
     * Returns the sensor directionDeg (DEG)
     */
    public Complex sensorDirection() {
        return sensorDirection;
    }

    @Override
    public void setOnClock(Consumer<ClockSyncEvent> callback) {
        onClock = callback;
    }

    @Override
    public void setOnContacts(Consumer<WheellyContactsMessage> callback) {
        this.onContacts = callback;
    }

    @Override
    public void setOnMotion(Consumer<WheellyMotionMessage> callback) {
        this.onMotion = callback;
    }

    @Override
    public void setOnProxy(Consumer<WheellyProxyMessage> callback) {
        this.onProxy = callback;
    }

    @Override
    public void setOnSupply(Consumer<WheellySupplyMessage> callback) {
    }

    /**
     * Sets the robot directionDeg
     *
     * @param direction the directionDeg in DEG
     */
    public void setRobotDir(Complex direction) {
        this.direction = direction;
        robot.setTransform(robot.getPosition(),
                (float) Complex.DEG90.sub(direction).toRad());
    }

    /**
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void setRobotPos(double x, double y) {
        Vec2 pos = new Vec2();
        pos.x = (float) x;
        pos.y = (float) y;
        robot.setTransform(pos, robot.getAngle());
    }

    @Override
    public long simulationTime() {
        return simulationTime;
    }

    @Override
    public void tick(long dt) {
        //long t0 = System.currentTimeMillis();
        this.simulationTime += dt;

        // Simulate robot motion
        controller(dt * 1e-3F);

        // Check for sensor
        Vec2 pos = robot.getPosition();
        double x = pos.x;
        double y = pos.y;
        Point2D position = new Point2D.Double(x, y);
        Complex sensorRad = direction().add(sensorDirection);
        boolean prevEchoAlarm = echoDistance > 0 && echoDistance <= SAFE_DISTANCE;
        this.echoDistance = 0;
        // Finds the nearest obstacle in proxy sensor range
        Point2D obs = obstacleMap.nearest(x, y, sensorRad, sensorReceptiveAngle);
        if (obs != null) {
            // Computes the distance of obstacles
            double dist = obs.distance(position) - obstacleMap.gridSize() / 2
                    + random.nextGaussian() * errSensor;
            echoDistance = dist > 0 && dist < MAX_DISTANCE ? dist : 0;
        }
        boolean echoAlarm = echoDistance > 0 && echoDistance <= SAFE_DISTANCE;
        if (echoAlarm != prevEchoAlarm) {
            sendContacts();
        }
        // Check for movement constraints
        checkForSpeed();
        updateProxy();
        //logger.atDebug().log("Tick elapsed {} ms", System.currentTimeMillis() - t0);
    }

    /**
     * Sends the motion message if interval has elapsed
     */
    private void updateMotion() {
        if (simulationTime >= motionTimeout) {
            sendMotion();
        }
    }

    /**
     * Sends the proxy message if interval has elapsed
     */
    private void updateProxy() {
        if (simulationTime >= proxyTimeout) {
            sendProxy();
        }
    }
}
