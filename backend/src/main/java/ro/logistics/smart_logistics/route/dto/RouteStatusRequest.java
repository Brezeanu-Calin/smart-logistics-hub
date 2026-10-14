package ro.logistics.smart_logistics.route.dto;

import ro.logistics.smart_logistics.route.domain.RouteStatus;

import jakarta.validation.constraints.NotNull;

public record RouteStatusRequest(@NotNull RouteStatus status) {
}
