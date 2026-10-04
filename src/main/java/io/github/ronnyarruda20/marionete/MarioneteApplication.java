package io.github.ronnyarruda20.marionete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarioneteApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarioneteApplication.class, args);
    }
}
