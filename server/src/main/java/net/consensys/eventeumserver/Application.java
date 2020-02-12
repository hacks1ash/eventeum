package net.consensys.eventeumserver;

import net.consensys.eventeum.annotation.EnableEventeum;
import net.consensys.eventeum.config.DatabaseConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableEventeum
//@EnableMongoRepositories(basePackages = "net.consensys")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
