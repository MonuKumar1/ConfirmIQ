package com.meridian.confirmiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class ConfirmIqApplication {

    public static void main(String[] args) {
        // Operations dates (trade date, ageing, SLA) are computed in UTC regardless of host locale.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ConfirmIqApplication.class, args);
    }
}
