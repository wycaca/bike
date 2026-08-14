package cn.bike.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BikeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BikeBackendApplication.class, args);
    }
}
