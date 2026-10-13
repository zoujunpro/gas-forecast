package com.gas.forecast;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.gas.forecast", "bio.drqi"})
@MapperScan("com.gas.forecast.dao.mapper")
@EnableScheduling
public class GasForecastStartupApplication {

    public static void main(String[] args) {
        SpringApplication.run(GasForecastStartupApplication.class, args);
    }
}
