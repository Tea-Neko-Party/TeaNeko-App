package org.zexnocs.teanekocore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TeaNekoCoreApplication {

    public static void main(String[] args) {
        var ctx = SpringApplication.run(TeaNekoCoreApplication.class, args);
        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
    }

}
