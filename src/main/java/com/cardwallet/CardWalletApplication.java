package com.cardwallet;

import com.cardwallet.db.Database;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CardWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardWalletApplication.class, args);
    }

    @Bean
    ApplicationRunner initDatabase() {
        return args -> Database.initDb(false);
    }
}
