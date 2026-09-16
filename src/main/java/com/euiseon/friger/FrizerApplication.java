package com.euiseon.friger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FrizerApplication {
    public static void main(String[] args) {
        var context=SpringApplication.run(FrizerApplication.class, args);
        if (context.getEnvironment().getProperty("frizer.account-provision.enabled",Boolean.class,false)
                && "none".equals(context.getEnvironment().getProperty("spring.main.web-application-type")))
            context.close();
    }
}
