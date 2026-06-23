package com.kingdom;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableAsync
public class KingdomApplication {

    public static void main(String[] args) {
        SpringApplication.run(KingdomApplication.class, args);
    }
    @PostConstruct
    public void checkEnvVars() {
        System.out.println("TWILIO_ACCOUNT_SID = " + System.getenv("TWILIO_ACCOUNT_SID"));
        System.out.println("TWILIO_AUTH_TOKEN = " + System.getenv("TWILIO_AUTH_TOKEN"));
        System.out.println("MAIL_USERNAME = " + System.getenv("MAIL_USERNAME"));
        System.out.println("MAIL_PASSWORD = " + (System.getenv("MAIL_PASSWORD") != null ? "[SET]" : "NULL"));
    }
}
