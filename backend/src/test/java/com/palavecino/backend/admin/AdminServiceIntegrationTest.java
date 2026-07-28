package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.appointment.Appointment;
import com.palavecino.backend.appointment.AppointmentRepository;
import com.palavecino.backend.appointment.AppointmentStatus;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.service.dto.ServiceCreateRequest;
import com.palavecino.backend.service.dto.ServiceUpdateRequest;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AdminServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private User patientUser;
    private User professionalUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    // ---- Public endpoint still works without auth ----

    @Test
    void publicListServicesStillWorksWithoutAuth() throws Exception {
        serviceRepository.save(new Service("Public Service", 60, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/services"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Public Service')]").exists());
    }

    // ---- Authorization tests ----

    @Test
    void adminServicesRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void patientCannotAccessAdminServices() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotAccessAdminServices() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void adminCanListServices() throws Exception {
        serviceRepository.save(new Service("Test Service", 60, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Test Service')].active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Test Service')].durationMinutes").value(60));
    }

    // ---- Create ----

    @Test
    void createServiceReturns201() throws Exception {
        ServiceCreateRequest request = new ServiceCreateRequest("Kinesiología General", 60);

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Kinesiología General"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.durationMinutes").value(60))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        assertThat(serviceRepository.findById(id)).isPresent();
    }

    @Test
    void createServiceWithDuplicateActiveNameReturns409() throws Exception {
        serviceRepository.save(new Service("Duplicate Name", 60, true));

        ServiceCreateRequest request = new ServiceCreateRequest("Duplicate Name", 45);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void createServiceWithInactiveDuplicateNameSucceeds() throws Exception {
        serviceRepository.save(new Service("Reactivable Name", 60, false));

        ServiceCreateRequest request = new ServiceCreateRequest("Reactivable Name", 45);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));
    }

    @Test
    void createServiceWithInvalidNameReturns400() throws Exception {
        ServiceCreateRequest request = new ServiceCreateRequest("", 60);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void createServiceWithInvalidDurationReturns400() throws Exception {
        ServiceCreateRequest request = new ServiceCreateRequest("Valid Name", 2);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- Update ----

    @Test
    void updateServiceReturns200() throws Exception {
        Service service = serviceRepository.save(new Service("Original Name", 60, true));

        ServiceUpdateRequest request = new ServiceUpdateRequest("Updated Name", 45);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/services/" + service.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Updated Name"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.durationMinutes").value(45))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));
    }

    @Test
    void updateServiceWithNonExistentIdReturns404() throws Exception {
        ServiceUpdateRequest request = new ServiceUpdateRequest("New Name", 60);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/services/999999")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void updateServiceDurationDoesNotAffectExistingAppointment() throws Exception {
        Service service = serviceRepository.save(new Service("Snapshot Test", 60, true));

        User proUser = userRepository.save(new User(unique("proSnap") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = new Professional("Pro", "Test", proUser);
        professional.setServices(new HashSet<>(java.util.List.of(service)));
        professional = professionalRepository.save(professional);

        User patUser = userRepository.save(new User(unique("patSnap") + "@example.com", "hash", Role.PATIENT, true));
        Patient patient = patientRepository.save(new Patient("Pat", "Test", "111111", patUser));

        Appointment appointment = appointmentRepository.save(
                new Appointment(patient, professional, service,
                        LocalDateTime.of(2026, 8, 1, 9, 0), AppointmentStatus.BOOKED, 60));

        ServiceUpdateRequest request = new ServiceUpdateRequest("Snapshot Test", 90);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/services/" + service.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.durationMinutes").value(90));

        Appointment loaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(loaded.getDurationMinutes()).isEqualTo(60);
    }

    // ---- Deactivate ----

    @Test
    void deactivateServiceMarksActiveFalse() throws Exception {
        Service service = serviceRepository.save(new Service("To Deactivate", 60, true));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services/" + service.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(false));

        Service loaded = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(loaded.isActive()).isFalse();
    }

    @Test
    void deactivateDoesNotDeleteService() throws Exception {
        Service service = serviceRepository.save(new Service("To Keep", 60, true));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services/" + service.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(serviceRepository.findById(service.getId())).isPresent();
    }

    // ---- Reactivate ----

    @Test
    void reactivateServiceMarksActiveTrue() throws Exception {
        Service service = serviceRepository.save(new Service("To Reactivate", 60, false));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services/" + service.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));

        Service loaded = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void reactivateAlreadyActiveServiceReturns200NoOp() throws Exception {
        Service service = serviceRepository.save(new Service("Already Active", 60, true));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/services/" + service.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));
    }

    // ---- List with includeInactive ----

    @Test
    void listWithIncludeInactiveTrueReturnsAll() throws Exception {
        serviceRepository.save(new Service("Active Svc", 60, true));
        serviceRepository.save(new Service("Inactive Svc", 30, false));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Active Svc')].active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Inactive Svc')].active").value(false));
    }

    @Test
    void listDefaultReturnsOnlyActive() throws Exception {
        serviceRepository.save(new Service("Active Svc", 60, true));
        serviceRepository.save(new Service("Inactive Svc", 30, false));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Active Svc')]").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.name == 'Inactive Svc')]").isEmpty());
    }

    // ---- Get by ID ----

    @Test
    void getServiceByIdReturnsAdminResponse() throws Exception {
        Service service = serviceRepository.save(new Service("Find Me", 45, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services/" + service.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Find Me"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.durationMinutes").value(45))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));
    }

    @Test
    void getServiceByIdWithNonExistentIdReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/services/999999")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
