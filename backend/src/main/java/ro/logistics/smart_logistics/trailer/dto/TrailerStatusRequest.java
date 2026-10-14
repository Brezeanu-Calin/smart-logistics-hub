package ro.logistics.smart_logistics.trailer.dto;

import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;

import jakarta.validation.constraints.NotNull;

public record TrailerStatusRequest(@NotNull TrailerStatus status) {
}
