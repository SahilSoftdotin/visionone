package com.visiondigitallab.visionone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VisionOne - Practice Growth Operating System.
 *
 * <p>A modular monolith. Modules are packages with narrow public surfaces; see
 * {@code docs/architecture.md} and the ArchUnit tests for the rules that keep them apart.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class VisionOneApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisionOneApplication.class, args);
    }
}
