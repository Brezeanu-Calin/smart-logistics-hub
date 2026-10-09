package ro.logistics.smart_logistics.vehicle.dto;

import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;

import jakarta.validation.constraints.NotNull;

public record VehicleStatusRequest(@NotNull VehicleStatus status) {
}
