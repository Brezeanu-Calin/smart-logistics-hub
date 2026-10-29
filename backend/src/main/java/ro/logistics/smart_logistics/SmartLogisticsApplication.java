package ro.logistics.smart_logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SmartLogisticsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartLogisticsApplication.class, args);
	}

}
