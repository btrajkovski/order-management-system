package com.btrajkovski;

import akka.actor.typed.ActorSystem;
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CreateTableTestUtils {

    /**
     * Test utility to create journal and projection tables for tests environment. JPA/Hibernate
     * tables are auto created (drop-and-create) using settings flag, see persistence-test.conf
     */
    public static void createTables(ActorSystem<?> system)
            throws Exception {

        // create schemas
        // ok to block here, main test thread
        SchemaUtils.dropIfExists(system).toCompletableFuture().get(30, SECONDS);
        SchemaUtils.createIfNotExists(system).toCompletableFuture().get(30, SECONDS);

        Path path = Paths.get("ddl-scripts/create_tables.sql");
        String script = Files.lines(path, StandardCharsets.UTF_8).collect(Collectors.joining("\n"));
        SchemaUtils.applyScript(script, system).toCompletableFuture().get(30, SECONDS);


        LoggerFactory.getLogger(CreateTableTestUtils.class).info("Tables created");
    }
}
