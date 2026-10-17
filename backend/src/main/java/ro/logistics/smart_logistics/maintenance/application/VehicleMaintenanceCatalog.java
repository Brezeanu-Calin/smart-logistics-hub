package ro.logistics.smart_logistics.maintenance.application;

import ro.logistics.smart_logistics.vehicle.domain.TransmissionType;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

import java.util.List;

public final class VehicleMaintenanceCatalog {
    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(VehicleType.VAN, "engine-oil-filter", "Engine oil & filter change", 15_000, 12, null, null),
            new Definition(VehicleType.VAN, "coolant-flush", "Coolant (antifreeze) flush", 100_000, 36, null, null),
            new Definition(VehicleType.VAN, "brake-pads-rotors", "Brake pads & rotors", 30_000, null, null, "Inspect at every service"),
            new Definition(VehicleType.VAN, "tire-replacement", "Tire replacement", 40_000, 60, null, null),
            new Definition(VehicleType.VAN, "transmission-manual", "Transmission service (manual)", 80_000, 48, TransmissionType.MANUAL, null),
            new Definition(VehicleType.VAN, "transmission-automatic", "Transmission service (automatic)", 60_000, 36, TransmissionType.AUTOMATIC, null),
            new Definition(VehicleType.VAN, "timing-belt-water-pump", "Timing belt & water pump", 100_000, 60, null, null),
            new Definition(VehicleType.VAN, "timing-chain-tensioners", "Timing chain & tensioners", null, null, null, "Lifetime component; inspection only"),
            new Definition(VehicleType.VAN, "clutch-assembly", "Clutch assembly", null, null, null, "Replace as needed"),
            new Definition(VehicleType.VAN, "fuel-pump-module", "Fuel pump / module", null, null, null, "Replace as needed"),
            new Definition(VehicleType.VAN, "water-pump", "Water pump", 100_000, null, null, "Service with timing belt"),
            new Definition(VehicleType.VAN, "engine-oil-pump", "Engine oil pump", null, null, null, "Service during engine overhaul"),

            new Definition(VehicleType.RIGID_TRUCK, "engine-oil-filter", "Engine oil & filter change", 60_000, 12, null, "Also inspect at 1,000 engine hours (hours are not tracked)"),
            new Definition(VehicleType.ARTICULATED_TRUCK, "engine-oil-filter", "Engine oil & filter change", 60_000, 12, null, "Also inspect at 1,000 engine hours (hours are not tracked)"),
            new Definition(VehicleType.RIGID_TRUCK, "coolant-flush", "Coolant (antifreeze) flush", 300_000, 36, null, null),
            new Definition(VehicleType.ARTICULATED_TRUCK, "coolant-flush", "Coolant (antifreeze) flush", 300_000, 36, null, null),
            new Definition(VehicleType.RIGID_TRUCK, "brake-pads-shoes-discs", "Brake pads / shoes & drums / discs", 150_000, null, null, "Inspect every 30,000 km"),
            new Definition(VehicleType.ARTICULATED_TRUCK, "brake-pads-shoes-discs", "Brake pads / shoes & drums / discs", 150_000, null, null, "Inspect every 30,000 km"),
            new Definition(VehicleType.RIGID_TRUCK, "steer-axle-tires", "Tire replacement (steer axle)", 120_000, 60, null, null),
            new Definition(VehicleType.ARTICULATED_TRUCK, "steer-axle-tires", "Tire replacement (steer axle)", 120_000, 60, null, null),
            new Definition(VehicleType.RIGID_TRUCK, "drive-axle-tires", "Tire replacement (drive axle)", 200_000, 60, null, null),
            new Definition(VehicleType.ARTICULATED_TRUCK, "drive-axle-tires", "Tire replacement (drive axle)", 200_000, 60, null, null),
            new Definition(VehicleType.RIGID_TRUCK, "transmission-amt", "Automated manual transmission (AMT)", 300_000, 36, TransmissionType.AMT, null),
            new Definition(VehicleType.ARTICULATED_TRUCK, "transmission-amt", "Automated manual transmission (AMT)", 300_000, 36, TransmissionType.AMT, null),
            new Definition(VehicleType.RIGID_TRUCK, "differential-axle-oil", "Differential / axle oil", 300_000, 36, null, null),
            new Definition(VehicleType.ARTICULATED_TRUCK, "differential-axle-oil", "Differential / axle oil", 300_000, 36, null, null),
            new Definition(VehicleType.RIGID_TRUCK, "timing-gears-chain", "Timing gears / chain", null, null, null, "Service during engine overhaul"),
            new Definition(VehicleType.ARTICULATED_TRUCK, "timing-gears-chain", "Timing gears / chain", null, null, null, "Service during engine overhaul"),
            new Definition(VehicleType.RIGID_TRUCK, "clutch-assembly-actuator", "Clutch assembly & actuator", null, null, null, "Replace as needed"),
            new Definition(VehicleType.ARTICULATED_TRUCK, "clutch-assembly-actuator", "Clutch assembly & actuator", null, null, null, "Replace as needed"),
            new Definition(VehicleType.RIGID_TRUCK, "fuel-delivery-high-pressure-pump", "Fuel delivery / high-pressure pump", null, null, null, "Replace as needed"),
            new Definition(VehicleType.ARTICULATED_TRUCK, "fuel-delivery-high-pressure-pump", "Fuel delivery / high-pressure pump", null, null, null, "Replace as needed"),
            new Definition(VehicleType.RIGID_TRUCK, "water-pump", "Water pump", 400_000, 48, null, null),
            new Definition(VehicleType.ARTICULATED_TRUCK, "water-pump", "Water pump", 400_000, 48, null, null),
            new Definition(VehicleType.RIGID_TRUCK, "engine-oil-pump", "Engine oil pump", null, null, null, "Service during engine overhaul"),
            new Definition(VehicleType.ARTICULATED_TRUCK, "engine-oil-pump", "Engine oil pump", null, null, null, "Service during engine overhaul")
    );

    private VehicleMaintenanceCatalog() {
    }

    public static List<Definition> forVehicle(Vehicle vehicle) {
        return DEFINITIONS.stream()
                .filter(definition -> definition.vehicleType() == vehicle.getVehicleType())
                .filter(definition -> definition.transmissionType() == null
                        || definition.transmissionType() == vehicle.getTransmissionType())
                .toList();
    }

    public record Definition(
            VehicleType vehicleType,
            String key,
            String label,
            Integer serviceIntervalKm,
            Integer serviceIntervalMonths,
            TransmissionType transmissionType,
            String inspectionNote
    ) {
        public boolean hasHealthInterval() {
            return serviceIntervalKm != null || serviceIntervalMonths != null;
        }
    }
}
