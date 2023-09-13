package com._7aske.grain.plugin.util;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GrainClassFinder {
    private final String basePath;
    private final Log log;

    public GrainClassFinder(MavenProject project, Log log) {
        this.log = log;
        this.basePath = project.getBuild().getOutputDirectory();
    }

    public Set<String> findClasses(Predicate<String> predicate) {
        try {
            return doLoadClasses(basePath)
                    .stream()
                    .map(c -> {
                        String className = c.replace(basePath, "")
                                .replace("/", ".")
                                .replace(".class", "");
                        if (className.startsWith("."))
                            className = className.substring(1);
                        return className;
                    })
                    .filter(predicate)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> doLoadClasses(String path) throws IOException {
        Set<String> loaded = new HashSet<>();
        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            paths.forEach(p -> {
                        if (p.toString().endsWith(".class")) {
                            log.debug("Loading class: " + p);
                            loaded.add(p.toString());
                        }
                    });
        }
        log.info("Loaded: " + loaded);
        return loaded;
    }
}
