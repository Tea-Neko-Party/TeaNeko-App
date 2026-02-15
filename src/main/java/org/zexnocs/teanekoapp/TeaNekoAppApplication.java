package org.zexnocs.teanekoapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TeaNekoAppApplication 是 TeaNeko 应用程序的入口类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@SpringBootApplication(scanBasePackages = {"org.zexnocs.teanekocore"})
public class TeaNekoAppApplication {

	public static void main(String[] args) {
		var ctx = SpringApplication.run(TeaNekoAppApplication.class, args);
		Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
	}
}
