package org.william.cex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CexApplication {

    public static void main(String[] args) {
        SpringApplication.run(CexApplication.class, args);
    }

}
