package org.zexnocs.teanekoapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.zexnocs.teanekocore"})
public class TeaNekoAppApplication {

	public static void main(String[] args) {
		var ctx = SpringApplication.run(TeaNekoAppApplication.class, args);
		Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
	}
}
