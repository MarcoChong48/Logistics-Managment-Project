package com.lms.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "lms.client.orders.expiry.enabled=false")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
