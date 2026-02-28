package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoapp.response.ResponseTestTeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 */
@SpringBootTest
public class NormalTest {
    @Autowired
    private ILogger iLogger;
    @Autowired
    private ITeaUserService iTeaUserService;
    @Autowired
    private ResponseTestTeaNekoClient responseTestTeaNekoClient;

    @Test
    public void run() {
        iTeaUserService.getId(responseTestTeaNekoClient, "test");
    }
}
