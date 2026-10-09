package ro.logistics.smart_logistics.vehicle.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String licensePlate;

    @Column(nullable = false)
    private String vehicleType;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    private Integer year;
    private String engineSpec;
    private Double maxWeightCapacityKg;
    private Double maxVolumeM3;

    @Column(nullable = false)
    private String status;

    private Integer currentMileageKm;
}