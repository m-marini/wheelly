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

package org.mmarini.wheelly.apps;

import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.KpiCSVSubscriber;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.PolarRobotEnv;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.envs.WithPolarMap;
import org.mmarini.wheelly.envs.WithRadarMap;
import org.mmarini.wheelly.swing.ComMonitor;
import org.mmarini.wheelly.swing.EnvironmentPanel;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.PolarPanel;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class Wheelly {
    public static final Dimension DEFAULT_RADAR_DIMENSION = new Dimension(400, 400);
    public static final String[] DEFAULT_KPIS = {
            "^reward$",
            "^avgReward$",
            "^delta$",
            "^v0$",
            "^trainedCritic.output$",
    };
    private static final Logger logger = LoggerFactory.getLogger(Wheelly.class);
    private static final Validator BASE_CONFIG = objectPropertiesRequired(Map.of(
            "version", string(values("0.4")),
            "active", string(),
            "configurations", object()
    ), List.of("version", "active", "configurations"));

    /**
     * Creates kpis process
     *
     * @param agent   the agent
     * @param actions the action spec
     * @param file    the path of kpis
     * @param labels  the key labels to filter
     */
    private static void createKpis(Agent agent, Map<String, SignalSpec> actions, File file, String labels) {
        KpiCSVSubscriber sub;

        if (labels.length() == 0) {
            // Default kpis
            String[] labs = Stream.concat(Stream.of(DEFAULT_KPIS),
                    actions.keySet().stream()
                            .flatMap(n -> Stream.of("^policy", "^trainedPolicy", "^gradPolicy").map(k -> k + "." + n + "$")
                            )
            ).toArray(String[]::new);
            sub = KpiCSVSubscriber.create(file, labs);
        } else if ("all".equals(labels)) {
            // full kpis
            sub = KpiCSVSubscriber.create(file);
        } else {
            // filtered kpis
            sub = KpiCSVSubscriber.create(file, labels.split(","));
        }
        agent.readKpis().subscribe(sub);
    }

    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(Wheelly.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-r", "--robot")
                .setDefault("robot.yml")
                .help("specify robot yaml configuration file");
        parser.addArgument("-c", "--controller")
                .setDefault("controller.yml")
                .help("specify controller yaml configuration file");
        parser.addArgument("-e", "--env")
                .setDefault("env.yml")
                .help("specify environment yaml configuration file");
        parser.addArgument("-a", "--agent")
                .setDefault("agent.yml")
                .help("specify agent yaml configuration file");
        parser.addArgument("-k", "--kpis")
                .setDefault("")
                .help("specify kpis path");
        parser.addArgument("-l", "--labels")
                .setDefault("")
                .help("specify kpi labels comma separated (all for all kpi)");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-t", "--time")
                .setDefault(43200L)
                .type(Long.class)
                .help("specify number of seconds of session duration");
        return parser;
    }

    /**
     * Returns an object instance from configuration file
     *
     * @param <T>        the returned object class
     * @param file       the filename
     * @param args       the builder additional arguments
     * @param argClasses the builder additional argument classes
     */
    public static <T> T fromConfig(String file, Object[] args, Class<?>[] argClasses) {
        try {
            JsonNode config = Utils.fromFile(file);
            BASE_CONFIG.apply(Locator.root()).accept(config);
            String active = Locator.locate("active").getNode(config).asText();
            Locator baseLocator = Locator.locate("configurations").path(active);
            return Utils.createObject(config, baseLocator, args, argClasses);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new Wheelly().start(args);
    }

    protected final JFrame frame;
    protected final EnvironmentPanel envPanel;
    private final AverageValue avgRewards;
    private final AverageValue reactionRobotTime;
    private final AverageValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final JFrame comFrame;
    protected Namespace args;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private PolarPanel polarPanel;
    private JFrame radarFrame;
    private long start;
    private RobotEnvironment environment;
    private Agent agent;
    private long prevRobotStep;
    private long prevStep;

    /**
     *
     */
    public Wheelly() {
        this.envPanel = new EnvironmentPanel();
        this.frame = createFrame(Messages.getString("Wheelly.title"), envPanel);
        this.comMonitor = new ComMonitor();
        this.comFrame = createFrame(Messages.getString("ComMonitor.title"), new JScrollPane(comMonitor));
        this.robotStartTimestamp = -1;
        this.avgRewards = AverageValue.create();
        this.reactionRobotTime = AverageValue.create();
        this.reactionRealTime = AverageValue.create();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .toFlowable(BackpressureStrategy.DROP)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .toFlowable(BackpressureStrategy.DROP)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();
        SwingObservable.window(comFrame, SwingObservable.WINDOW_ACTIVE)
                .toFlowable(BackpressureStrategy.DROP)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();
    }

    /**
     * Returns the agent
     *
     * @param env the environment
     */
    protected Agent createAgent(WithSignalsSpec env) {
        return fromConfig(args.getString("agent"), new Object[]{env}, new Class[]{WithSignalsSpec.class});
    }

    /**
     * Returns the environment
     */
    protected RobotEnvironment createEnvironment() {
        RobotApi robot = fromConfig(args.getString("robot"), new Object[0], new Class[0]);
        RobotControllerApi controller = fromConfig(args.getString("controller"), new Object[]{robot}, new Class[]{RobotApi.class});
        return fromConfig(args.getString("env"), new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    private void handleInference(RobotStatus status) {
        long robotClock = status.getTime();
        long time = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(time - prevStep) * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep) * 1e-3);
        }
        prevRobotStep = robotClock;
        prevStep = time;
    }

    private void handleResult(Environment.ExecutionResult result) {
        double reward = result.getReward();
        envPanel.setReward(avgRewards.add(reward));
        agent.observe(result);
    }

    private void handleShutdown() {
        try {
            agent.close();
        } catch (IOException e) {
            logger.atError().setCause(e).log();
        }
        frame.dispose();
        if (radarFrame != null) {
            radarFrame.dispose();
        }
        comFrame.dispose();
        if (!args.getBoolean("silent")) {
            JOptionPane.showMessageDialog(null,
                    "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleStatusReady(RobotStatus status) {
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.getTime();
        }
        envPanel.setRobotStatus(status);
        if (environment instanceof WithRadarMap) {
            envPanel.setRadarMap(((WithRadarMap) environment).getRadarMap());
        }
        if (environment instanceof WithRadarMap) {
            envPanel.setRadarMap(((WithRadarMap) environment).getRadarMap());
        }
        if (environment instanceof WithPolarMap) {
            polarPanel.setPolarMap(((WithPolarMap) environment).getPolarMap());
        }
        long robotElapsed = status.getTime() - robotStartTimestamp;
        envPanel.setTimeRatio((double) robotElapsed / (System.currentTimeMillis() - start));
        if (robotElapsed > sessionDuration) {
            environment.shutdown();
        }
    }

    private void handleWindowClosing(WindowEvent windowEvent) {
        environment.shutdown();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void handleWindowOpened(WindowEvent e) {
        try (INDArray ignored = Nd4j.zeros(1)) {
        }
        RobotApi robot = environment.getController().getRobot();
        if (robot instanceof SimRobot) {
            Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).getObstaclesMap();
            obstaclesMap.map(ObstacleMap::getPoints)
                    .ifPresent(envPanel::setObstacleMap);
            obstaclesMap.map(ObstacleMap::getTopology)
                    .map(GridTopology::getGridSize)
                    .ifPresent(envPanel::setObstacleSize);
        }
        environment.start();
    }

    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            logger.atInfo().log("Creating environment");
            this.environment = createEnvironment();

            logger.atInfo().log("Creating agent");
            this.agent = createAgent(environment);
            if (environment instanceof PolarRobotEnv) {
                this.polarPanel = new PolarPanel();
                double radarMaxDistance = ((PolarRobotEnv) environment).getMaxRadarDistance();
                polarPanel.setRadarMaxDistance(radarMaxDistance);
                radarFrame = createFixFrame(Messages.getString("Radar.title"), DEFAULT_RADAR_DIMENSION, polarPanel);
                SwingObservable.window(radarFrame, SwingObservable.WINDOW_ACTIVE)
                        .toFlowable(BackpressureStrategy.DROP)
                        .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                        .doOnNext(this::handleWindowClosing)
                        .subscribe();
                layHorizontaly(frame, radarFrame, comFrame);
            } else {
                layHorizontaly(frame, comFrame);
            }
            sessionDuration = this.args.getLong("time");
            logger.atInfo().log("Starting session ...");
            logger.atInfo().setMessage("Session are running for {} sec...").addArgument(sessionDuration).log();
            sessionDuration *= 1000;

            String kpis = this.args.getString("kpis");
            if (kpis.length() != 0) {
                createKpis(agent, environment.getActions(), new File(kpis), this.args.getString("labels"));
            }
            this.start = System.currentTimeMillis();
            environment.setOnInference(this::handleInference);
            environment.setOnAct(agent::act);
            environment.setOnStatusReady(this::handleStatusReady);
            environment.setOnResult(this::handleResult);
            environment.setOnReadLine(comMonitor::onReadLine);
            environment.setOnWriteLine(comMonitor::onWriteLine);
            environment.setOnError(err -> {
                comMonitor.onError(err);
                logger.atError().setCause(err).log();
            });
            environment.readShutdown()
                    .doOnComplete(this::handleShutdown)
                    .subscribe();

            frame.setVisible(true);
            if (radarFrame != null) {
                radarFrame.setVisible(true);
            }
            comFrame.setVisible(true);
            environment.start();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
