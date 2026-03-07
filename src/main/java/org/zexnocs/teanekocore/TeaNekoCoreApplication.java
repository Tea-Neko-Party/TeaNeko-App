package org.zexnocs.teanekocore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TeaNekoCoreApplication 是 TeaNeko 核心的入口。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@SpringBootApplication(scanBasePackages = {"org.zexnocs.teanekocore"})
public class TeaNekoCoreApplication {

	public static void main(String[] args) {
		var ctx = SpringApplication.run(TeaNekoCoreApplication.class, args);
		Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
	}
}
