package ro.logistics.smart_logistics.trailer.application;

import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.dto.TrailerRequest;
import ro.logistics.smart_logistics.trailer.dto.TrailerResponse;
import ro.logistics.smart_logistics.trailer.persistence.TrailerRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class TrailerService {
    private static final List<RouteStatus> ACTIVE_OR_PLANNED = List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE);

    private final TrailerRepository trailerRepository;
    private final RouteRepository routeRepository;

    public TrailerService(TrailerRepository trailerRepository, RouteRepository routeRepository) {
        this.trailerRepository = trailerRepository;
        this.routeRepository = routeRepository;
    }

    @Transactional(readOnly = true)
    public List<TrailerResponse> findAll() {
        return trailerRepository.findAllByOrderByLicensePlateAsc().stream()
                .map(TrailerService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrailerResponse findById(Long id) {
        return toResponse(findTrailer(id));
    }

    public TrailerResponse create(TrailerRequest request) {
        String plate = normalizePlate(request.licensePlate());
        ensurePlateAvailable(plate, null);
        Trailer trailer = new Trailer(
                plate,
                request.trailerType(),
                request.make().trim(),
                request.model().trim(),
                request.year(),
                request.maxWeightCapacityKg(),
                request.maxVolumeM3()
        );
        return save(trailer, plate);
    }

    public TrailerResponse update(Long id, TrailerRequest request) {
        Trailer trailer = lockTrailer(id);
        ensureNotScheduled(trailer);
        String plate = normalizePlate(request.licensePlate());
        ensurePlateAvailable(plate, id);
        trailer.updateDetails(
                plate,
                request.trailerType(),
                request.make().trim(),
                request.model().trim(),
                request.year(),
                request.maxWeightCapacityKg(),
                request.maxVolumeM3()
        );
        return save(trailer, plate);
    }

    public TrailerResponse updateDispatcherManagedStatus(Long id, TrailerStatus status) {
        Trailer trailer = lockTrailer(id);
        if (trailer.getStatus() == TrailerStatus.IN_TRANSIT) {
            throw new InvalidTrailerStatusException("Trailer status cannot be changed manually while IN_TRANSIT.");
        }
        if (routeRepository.existsByTrailer_IdAndStatusIn(trailer.getId(), ACTIVE_OR_PLANNED)) {
            throw new InvalidTrailerStatusException(
                    "Trailer status cannot be changed while assigned to a PLANNED or ACTIVE route."
            );
        }
        try {
            trailer.updateDispatcherManagedStatus(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTrailerStatusException(exception.getMessage());
        }
        return toResponse(trailerRepository.save(trailer));
    }

    public void delete(Long id) {
        Trailer trailer = lockTrailer(id);
        ensureNotScheduled(trailer);
        trailerRepository.delete(trailer);
        try {
            trailerRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new TrailerInUseException(id);
        }
    }

    private void ensureNotScheduled(Trailer trailer) {
        if (trailer.getStatus() == TrailerStatus.IN_TRANSIT
                || routeRepository.existsByTrailer_IdAndStatusIn(trailer.getId(), ACTIVE_OR_PLANNED)) {
            throw new TrailerInUseException(trailer.getId());
        }
    }

    private TrailerResponse save(Trailer trailer, String plate) {
        try {
            return toResponse(trailerRepository.saveAndFlush(trailer));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateTrailerLicensePlateException(plate);
        }
    }

    private void ensurePlateAvailable(String plate, Long id) {
        boolean exists = id == null
                ? trailerRepository.existsByLicensePlateIgnoreCase(plate)
                : trailerRepository.existsByLicensePlateIgnoreCaseAndIdNot(plate, id);
        if (exists) {
            throw new DuplicateTrailerLicensePlateException(plate);
        }
    }

    private Trailer findTrailer(Long id) {
        return trailerRepository.findById(id).orElseThrow(() -> new TrailerNotFoundException(id));
    }

    private Trailer lockTrailer(Long id) {
        return trailerRepository.findByIdForUpdate(id).orElseThrow(() -> new TrailerNotFoundException(id));
    }

    private static String normalizePlate(String plate) {
        return plate.trim().toUpperCase(Locale.ROOT);
    }

    private static TrailerResponse toResponse(Trailer trailer) {
        return new TrailerResponse(
                trailer.getId(),
                trailer.getLicensePlate(),
                trailer.getTrailerType(),
                trailer.getMake(),
                trailer.getModel(),
                trailer.getYear(),
                trailer.getMaxWeightCapacityKg(),
                trailer.getMaxVolumeM3(),
                trailer.getStatus()
        );
    }
}
