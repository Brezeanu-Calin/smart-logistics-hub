package ro.logistics.smart_logistics.driver.dto;

import ro.logistics.smart_logistics.driver.domain.DriverStatus;

import jakarta.validation.constraints.NotNull;

public record DriverStatusRequest(@NotNull DriverStatus status) {
}
