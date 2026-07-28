package com.palavecino.backend.admin;

import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.professional.dto.ProfessionalAdminMapper;
import com.palavecino.backend.professional.dto.ProfessionalAdminResponse;
import com.palavecino.backend.professional.dto.ProfessionalCreateRequest;
import com.palavecino.backend.professional.dto.ProfessionalCreatedResponse;
import com.palavecino.backend.professional.dto.ProfessionalUpdateRequest;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AdminProfessionalService {

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminProfessionalService(ProfessionalRepository professionalRepository,
                                     UserRepository userRepository,
                                     ServiceRepository serviceRepository,
                                     PasswordEncoder passwordEncoder) {
        this.professionalRepository = professionalRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ProfessionalAdminResponse> list(boolean includeInactive) {
        List<Professional> professionals = professionalRepository.findAll(Sort.by("firstName", "lastName"));
        return professionals.stream()
                .filter(p -> includeInactive || p.getUser().isActive())
                .map(ProfessionalAdminMapper::toAdminResponse)
                .toList();
    }

    public ProfessionalAdminResponse findById(Long id) {
        Professional professional = professionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + id));
        return ProfessionalAdminMapper.toAdminResponse(professional);
    }

    @Transactional
    public ProfessionalCreatedResponse create(ProfessionalCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("El email " + request.email() + " ya está registrado");
        }

        List<Long> serviceIds = request.serviceIds() != null ? request.serviceIds() : List.of();
        List<Service> services = validateServices(serviceIds);

        String temporaryPassword = generateTemporaryPassword();
        String hashedPassword = passwordEncoder.encode(temporaryPassword);

        User user = new User(request.email(), hashedPassword, Role.PROFESSIONAL, true, true);
        user = userRepository.save(user);

        Professional professional = new Professional(request.firstName(), request.lastName(), user);
        professional.setServices(new HashSet<>(services));
        professional = professionalRepository.save(professional);

        return ProfessionalAdminMapper.toCreatedResponse(professional, temporaryPassword);
    }

    @Transactional
    public ProfessionalAdminResponse update(Long id, ProfessionalUpdateRequest request) {
        Professional professional = professionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + id));

        professional.setFirstName(request.firstName());
        professional.setLastName(request.lastName());

        List<Long> serviceIds = request.serviceIds() != null ? request.serviceIds() : List.of();
        List<Service> services = validateServices(serviceIds);

        professional.getServices().clear();
        professional.getServices().addAll(services);

        professional = professionalRepository.save(professional);
        return ProfessionalAdminMapper.toAdminResponse(professional);
    }

    @Transactional
    public ProfessionalAdminResponse deactivate(Long id) {
        Professional professional = professionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + id));

        User user = professional.getUser();
        user.setActive(false);
        userRepository.save(user);

        return ProfessionalAdminMapper.toAdminResponse(professional);
    }

    @Transactional
    public ProfessionalAdminResponse reactivate(Long id) {
        Professional professional = professionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + id));

        User user = professional.getUser();
        user.setActive(true);
        userRepository.save(user);

        return ProfessionalAdminMapper.toAdminResponse(professional);
    }

    private List<Service> validateServices(List<Long> serviceIds) {
        if (serviceIds.isEmpty()) {
            return List.of();
        }

        List<Service> services = serviceRepository.findAllById(serviceIds);
        Map<Long, Service> serviceMap = services.stream()
                .collect(Collectors.toMap(Service::getId, s -> s));

        List<Long> invalidIds = serviceIds.stream()
                .filter(id -> {
                    Service s = serviceMap.get(id);
                    return s == null || !s.isActive();
                })
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Los siguientes servicios no existen o están inactivos: " + invalidIds);
        }

        return services;
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
