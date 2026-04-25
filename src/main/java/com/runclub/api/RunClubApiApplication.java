package com.runclub.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RunClubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunClubApiApplication.class, args);
    }

}
