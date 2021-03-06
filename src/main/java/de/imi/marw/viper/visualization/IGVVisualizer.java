/* Copyright (c) 2017 Marius Wöste
 *
 * This file is part of VIPER.
 *
 * VIPER is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VIPER is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VIPER.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package de.imi.marw.viper.visualization;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.DigestUtils;

/**
 *
 * @author marius
 */
public class IGVVisualizer extends Thread {

    private static final long IGV_START_TIMEOUT_MS = 120000;

    private static final int DEFAULT_VIEW_RANGE = 25;
    private static final int DEFAULT_PANEL_HEIGHT = 1000;

    private static final String IGV_PROPERTY_FILE = "igv.properties";

    private static final String CONFIG_VIEW_RANGE_KEY = "VIPER.VIEW_RANGE";
    private static final String CONFIG_PANEL_HEIGHT_KEY = "VIPER.PANEL_HEIGHT";

    private final int xvfbDisplay;
    private final int xvfbWidth;
    private final int xvfbHeight;
    private final int jvmMBSpace;

    private final Map<String, Object> configurationMap;
    private final Map<String, Boolean> visualizationProgressMap;
    private final PriorityBlockingQueue<IGVCommand> commandQueue;
    private final int port;
    private final String fastaRef;
    private final String igvJar;
    private final String workDir;
    private final String bamDir;
    private final String logFile;
    private Process igvProcess;
    private Process xvfbServer;
    private Socket client;

    public IGVVisualizer(String igvJar, String fastaRef, int port, String workDir, String bamDir, String logFile, int xvfbDisplay, int xvfbWidth, int xvfbHeight, int jvmMBSpace) {
        this.port = port;
        this.fastaRef = fastaRef;
        this.igvJar = igvJar;
        this.commandQueue = new PriorityBlockingQueue<>(100, Comparator.reverseOrder());
        this.workDir = workDir;
        this.visualizationProgressMap = new ConcurrentHashMap<>();
        this.bamDir = bamDir;
        this.logFile = logFile;
        this.xvfbDisplay = xvfbDisplay;
        this.xvfbWidth = xvfbWidth;
        this.xvfbHeight = xvfbHeight;
        this.jvmMBSpace = jvmMBSpace;
        this.configurationMap = new HashMap<>();
    }

    @Override
    public void run() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            startIGVProcess();
            loadIGVConfiguration();
            this.client = connectToIGV();
            this.setupViewer();

            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            while (this.igvProcess != null && this.igvProcess.isAlive()) {

                IGVCommand nextCommand = this.commandQueue.poll(1, TimeUnit.SECONDS);

                if (nextCommand == null) {
                    continue;
                }

                Arrays.stream(nextCommand.getSubCommands()).forEach((String subCommand) -> {
                    out.println(subCommand);
                    try {
                        String response = in.readLine();
                    } catch (IOException ex) {
                        Logger.getLogger(IGVVisualizer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });

                nextCommand.getFinishedCallback().run();
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(IGVVisualizer.class.getName()).log(Level.SEVERE, null, ex);
        } finally {

            shutdown();
        }
    }

    private void startIGVProcess() throws IOException {

        File logFile = new File(this.logFile);

        ProcessBuilder builder = new ProcessBuilder("java",
                "-Xmx" + this.jvmMBSpace + "m",
                "-Dproduction=true",
                "-Dsun.java2d.noddraw=true",
                "-Dapple.laf.useScreenMenuBar=true",
                "-Djava.net.preferIPv4Stack=true",
                "-jar", this.igvJar,
                "-p", "" + port,
                "-g", this.fastaRef,
                "-o", IGV_PROPERTY_FILE
        )
                .redirectOutput(logFile)
                .redirectError(logFile);

        if (isXvfbInstalled()) {
            ProcessBuilder xvfbBuilder = new ProcessBuilder("Xvfb",
                    ":" + xvfbDisplay,
                    "-screen", "0,", xvfbWidth + "x" + xvfbHeight + "x24")
                    .inheritIO();

            this.xvfbServer = xvfbBuilder.start();

            Map<String, String> igvEnv = builder.environment();
            igvEnv.put("DISPLAY", ":" + xvfbDisplay);
        }

        this.igvProcess = builder.start();

    }

    private Socket connectToIGV() {

        while (this.igvProcess != null) {
            try {
                Socket client = new Socket("127.0.0.1", port);
                return client;
            } catch (IOException ex) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex1) {
                    Logger.getLogger(IGVVisualizer.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }

        return null;
    }

    public void awaitStartup() {

        long startedAt = System.currentTimeMillis();

        while (this.client == null || !this.client.isConnected()) {
            try {
                Thread.sleep(100);

                if (System.currentTimeMillis() - startedAt > IGV_START_TIMEOUT_MS) {
                    throw new IllegalStateException("Connection to IGV failed, check the IGV log for possible exceptions");
                }

            } catch (InterruptedException ex) {
                Logger.getLogger(IGVVisualizer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean isSnapshotDone(String key) {
        return this.visualizationProgressMap.getOrDefault(key, false);
    }

    public synchronized void scheduleSnapshot(String sample, String chr, int pos, boolean isUrgent) {

        String key = sample + "-" + chr + "-" + pos + "-" + this.getConfigurationHash();
        boolean snapshotAlreadyScheduled = this.visualizationProgressMap.containsKey(key);

        if (snapshotAlreadyScheduled && (!isUrgent || this.visualizationProgressMap.get(key))) {
            return;
        }

        Path workdir = Paths.get(this.workDir);
        String imageFileName = workdir.resolve(key + ".png").toString();

        Path bamDir = Paths.get(this.bamDir);
        String bamName = bamDir.resolve(sample + ".bam").toString();

        int configViewRange = (int) this.configurationMap.get(CONFIG_VIEW_RANGE_KEY);

        String[] subCommands = new String[]{
            "new",
            "load " + bamName,
            "collapse",
            "goto " + chr + ":" + (pos - configViewRange) + "-" + (pos + configViewRange),
            "snapshot " + imageFileName
        };

        IGVCommand command = new IGVCommand(key, subCommands, isUrgent, () -> this.visualizationProgressMap.put(key, true));

        if (isUrgent) {

            boolean commandWasInQueue = this.commandQueue.remove(command);

            // prevents race condition when command was already removed by
            // visualization thread and added again
            if (!snapshotAlreadyScheduled || commandWasInQueue) {

                this.visualizationProgressMap.put(key, false);
                this.enqueueCommand(command);

            }

        } else {

            this.visualizationProgressMap.put(key, false);
            this.enqueueCommand(command);

        }

    }

    public void enqueueCommand(IGVCommand command) {
        this.commandQueue.offer(command);
    }

    private void setupViewer() {
        this.enqueueCommand(new IGVCommand("pref-change", new String[]{"setSleepInterval 0"}, true, () -> {
        }));
    }

    public synchronized void shutdown() {
        if (this.client != null) {
            try {
                this.client.close();
                this.client = null;
            } catch (IOException ex) {
                Logger.getLogger(IGVVisualizer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (this.igvProcess != null) {
            this.igvProcess.destroy();
            this.igvProcess = null;
        }

        if (this.xvfbServer != null) {
            this.xvfbServer.destroy();
            this.xvfbServer = null;
        }

        File workDir = new File(this.workDir);

        for (File file : workDir.listFiles()) {
            if (file.getName().endsWith(".png")) {
                file.deleteOnExit();
            }
        }
    }

    private boolean isXvfbInstalled() {
        Runtime rt = Runtime.getRuntime();
        Process proc;
        try {
            proc = rt.exec("Xvfb -help");
            proc.waitFor();
            int exitVal = proc.exitValue();

            return exitVal == 0;
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    public synchronized String setConfigurationValue(String key, Object value) {

        List<IGVCommand> commandsInProgress = new ArrayList<>();
        this.commandQueue.drainTo(commandsInProgress);
        this.commandQueue.clear();

        for (IGVCommand commandInProgress : commandsInProgress) {

            if ("pref-change".equals(commandInProgress.getKey())) {
                continue;
            }

            this.visualizationProgressMap.remove(commandInProgress.getKey());
        }

        this.configurationMap.put(key, value);

        switch (key) {
            // nothing to be done, only key in config is needed
            case CONFIG_VIEW_RANGE_KEY: {
                break;
            }
            case CONFIG_PANEL_HEIGHT_KEY: {

                int panelHeight = (int) configurationMap.get(CONFIG_PANEL_HEIGHT_KEY);

                IGVCommand command = new IGVCommand("pref-change", new String[]{"maxPanelHeight " + panelHeight}, true, () -> {
                });

                this.enqueueCommand(command);
                break;
            }
            default: {
                IGVCommand command = new IGVCommand("pref-change", new String[]{"preference " + key + " " + value}, true, () -> {
                });

                this.enqueueCommand(command);
            }
        }

        return this.getConfigurationHash();
    }

    public Map<String, Object> getConfiguration() {
        return this.configurationMap;
    }

    public String getConfigurationHash() {

        String stringToBeHashed = this.configurationMap.entrySet()
                .stream()
                .map((entry) -> entry.getKey() + entry.getValue())
                .sorted()
                .reduce((a, b) -> a + b)
                .orElse("");

        return DigestUtils.md5Hex(stringToBeHashed);
    }

    private void loadIGVConfiguration() throws IOException {

        try (Stream<String> stream = Files.lines(Paths.get(IGV_PROPERTY_FILE))) {

            Map<String, Object> configurationValues = stream.sorted()
                    .map(line -> line.split("="))
                    .collect(Collectors.toMap(splitPair -> splitPair[0], splitPair -> {

                        String rawString = splitPair[1];

                        if ("true".equals(rawString)) {
                            return true;
                        }
                        if ("false".equals(rawString)) {
                            return false;
                        }

                        try {
                            return Integer.parseInt(rawString, 10);
                        } catch (NumberFormatException ex) {
                            return rawString;
                        }

                    }));

            this.configurationMap.putAll(configurationValues);

            this.configurationMap.put(CONFIG_PANEL_HEIGHT_KEY, DEFAULT_PANEL_HEIGHT);
            this.configurationMap.put(CONFIG_VIEW_RANGE_KEY, DEFAULT_VIEW_RANGE);

        } catch (IOException ex) {
            Logger.getLogger(IGVVisualizer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
