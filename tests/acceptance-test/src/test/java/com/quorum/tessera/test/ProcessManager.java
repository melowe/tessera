package com.quorum.tessera.test;

import com.quorum.tessera.config.CommunicationType;
import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.util.JaxbUtil;
import com.quorum.tessera.io.FilesDelegate;
import com.quorum.tessera.test.util.ElUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.ws.rs.core.UriBuilder;

public class ProcessManager {

    private final Map<String, Path> pids = new HashMap<>();

    private final Map<String, URL> configFiles;

    private final CommunicationType communicationType;

    private final URL logbackConfigFile = ProcessManager.class.getResource("/logback-node.xml");

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ProcessManager(CommunicationType communicationType) {
        this.communicationType = Objects.requireNonNull(communicationType);

        String pathTemplate = "/" + communicationType.name().toLowerCase() + "/config%s.json";
        final Map<String, URL> configs = new HashMap<>();
        configs.put("A", getClass().getResource(String.format(pathTemplate, "1")));
        configs.put("B", getClass().getResource(String.format(pathTemplate, "2")));
        configs.put("C", getClass().getResource(String.format(pathTemplate, "3")));
        configs.put("D", getClass().getResource(String.format(pathTemplate, "4")));
        configs.put("E", getClass().getResource(String.format(pathTemplate, "-whitelist")));
        this.configFiles = Collections.unmodifiableMap(configs);
    }

    public String findJarFilePath() {
        return Objects.requireNonNull(System.getProperty("application.jar", null),
                "System property application.jar is undefined.");
    }

    public void startNodes() throws Exception {
        List<String> nodeAliases = Arrays.asList(configFiles.keySet().toArray(new String[0]));
        Collections.shuffle(nodeAliases);

        for (String nodeAlias : nodeAliases) {
            start(nodeAlias);
        }
    }

    public void stopNodes() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("kill");

        pids.values().stream()
                .flatMap(p -> {
                    try {
                        return Files.readAllLines(p).stream();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .filter(Objects::nonNull)
                .filter(s -> !Objects.equals("", s))
                .forEach(args::add);

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();

    }

    public void start(String nodeAlias) throws Exception {
        final String jarfile = findJarFilePath();

        URL configFile = configFiles.get(nodeAlias);
        Path pid = Paths.get(System.getProperty("java.io.tmpdir"), "pid" + nodeAlias + ".pid");

        pids.put(nodeAlias, pid);

        List<String> args = Arrays.asList(
                "java",
                "-Dspring.profiles.active=disable-unixsocket,disable-sync-poller",
                "-Dnode.number=" + nodeAlias,
                "-Dlogback.configurationFile=" + logbackConfigFile.getFile(),
                "-Ddebug=true",
                "-jar",
                jarfile,
                "-configfile",
                ElUtil.createAndPopulatePaths(configFile).toAbsolutePath().toString(),
                "-pidfile",
                pid.toAbsolutePath().toString(),
                "-server.communicationType",
                communicationType.name()
        );
        System.out.println(String.join(" ", args));

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        executorService.submit(() -> {

            try (BufferedReader reader = Stream.of(process.getInputStream())
                    .map(InputStreamReader::new)
                    .map(BufferedReader::new)
                    .findAny().get()) {

                String line = null;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });

        final String nodeId = nodeAlias;

        final Config config = JaxbUtil.unmarshal(configFile.openStream(), Config.class);

        final URL bindingUrl = config.getServerConfig().getBindingUri().toURL();

        CountDownLatch startUpLatch = new CountDownLatch(communicationType == CommunicationType.GRPC ? 2 : 1);

        if (communicationType == CommunicationType.GRPC) {
            URL grpcUrl = UriBuilder.fromUri(config.getServerConfig().getBindingUri())
                    .port(config.getServerConfig().getGrpcPort())
                    .build().toURL();

            executorService.submit(() -> {

                while (true) {
                    try {
                        URLConnection conn = grpcUrl.openConnection();
                        conn.connect();
                        System.out.println(grpcUrl + " started. ");
                        startUpLatch.countDown();
                        return;
                    } catch (IOException ex) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(200L);
                        } catch (InterruptedException ex1) {
                        }
                    }
                }

            });
        }

        executorService.submit(() -> {

            while (true) {
                try {
                    URLConnection conn = bindingUrl.openConnection();
                    conn.connect();
                    System.out.println(bindingUrl + " started. ");
                    startUpLatch.countDown();
                    return;
                } catch (IOException ex) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(200L);
                    } catch (InterruptedException ex1) {
                    }
                }
            }

        });

        boolean started = startUpLatch.await(2, TimeUnit.MINUTES);

        if (!started) {
            System.err.println(bindingUrl + " Not started. ");
        }

        executorService.submit(() -> {
            try {
                int exitCode = process.waitFor();
                if (0 != exitCode) {
                    System.err.println("Node " + nodeId + " exited with code " + exitCode);
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });

    }

    public void kill(String nodeAlias) throws Exception {

        FilesDelegate fileDelegate = FilesDelegate.create();
        Path pidFile = pids.remove(nodeAlias);
        fileDelegate.lines(pidFile);

        String pid = Files.lines(pidFile).findFirst().get();

        List<String> args = Arrays.asList("kill", pid);
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("application.jar", "/Users/mark/Projects/tessera/tessera-app/target/tessera-app-0.8-SNAPSHOT-app.jar");

        ProcessManager pm = new ProcessManager(CommunicationType.REST);
        pm.startNodes();

        System.in.read();

        pm.stopNodes();

    }

}
