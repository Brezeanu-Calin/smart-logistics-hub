package ro.logistics.smart_logistics;

import ro.logistics.smart_logistics.driver.application.DriverService;
import ro.logistics.smart_logistics.driver.dto.DriverRequest;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SmartLogisticsApplicationTests {
	@Autowired
	private DriverService driverService;

	@Test
	void contextLoads() {
	}

	@Test
	void serviceRejectsInvalidCrudRequestBeforePersistence() {
		assertThrows(
				ConstraintViolationException.class,
				() -> driverService.create(new DriverRequest("", "not-an-email", Set.of()))
		);
	}

	@Test
	void serviceRejectsNonPositiveCrudIds() {
		assertThrows(ConstraintViolationException.class, () -> driverService.findById(0L));
	}

}
