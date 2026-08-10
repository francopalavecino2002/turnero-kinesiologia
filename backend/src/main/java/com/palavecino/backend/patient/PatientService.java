package com.palavecino.backend.patient;

import com.palavecino.backend.patient.dto.PatientMapper;
import com.palavecino.backend.patient.dto.PatientSearchResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientService {

    private static final int MAX_SEARCH_RESULTS = 10;

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public List<PatientSearchResponse> search(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }

        return patientRepository.search(term.trim(), PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(PatientMapper::toSearchResponse)
                .toList();
    }
}
