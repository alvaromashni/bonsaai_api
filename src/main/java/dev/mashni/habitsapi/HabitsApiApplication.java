package dev.mashni.habitsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HabitsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitsApiApplication.class, args);
    }

}
