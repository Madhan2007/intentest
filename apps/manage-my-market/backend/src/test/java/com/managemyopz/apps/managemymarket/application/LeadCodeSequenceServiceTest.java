/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadCodeSequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LeadCodeSequenceServiceTest {

    private final LeadCodeSequenceRepository repository = mock(LeadCodeSequenceRepository.class);
    private final LeadCodeSequenceService service = new LeadCodeSequenceService(repository);

    @Test
    @DisplayName("generates formatted lead code with company prefix and 6-digit zero padding")
    void generatesFormattedLeadCode() {
        UUID companyId = UUID.randomUUID();
        when(repository.reserveNext(companyId)).thenReturn(42L);

        String code = service.generateNextCode(companyId);

        assertThat(code).isEqualTo("LEAD-000042");
        verify(repository, times(1)).reserveNext(companyId);
    }

    @Test
    @DisplayName("formats high sequence numbers correctly")
    void formatsLargeSequenceNumber() {
        UUID companyId = UUID.randomUUID();
        when(repository.reserveNext(companyId)).thenReturn(1234567L);

        String code = service.generateNextCode(companyId);

        assertThat(code).isEqualTo("LEAD-1234567");
    }
}
