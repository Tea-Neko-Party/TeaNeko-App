package org.zexnocs.teanekocore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TeaNekoCoreApplication 是 TeaNeko 核心模块的入口类。
 *
 * @author zExNocs
 * @date 2026/02/19
 */
@SpringBootApplication
public class TeaNekoCoreApplication {

    public static void main(String[] args) {
        var ctx = SpringApplication.run(TeaNekoCoreApplication.class, args);
        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
    }
}
