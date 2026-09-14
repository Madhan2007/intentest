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
    @DisplayName("generates formatted lead code with prefix and 5-digit zero padding")
    void generatesFormattedLeadCode() {
        String companyId = UUID.randomUUID().toString();
        when(repository.reserveNext(companyId, "LED")).thenReturn(42L);

        String code = service.reserveNextCode(companyId, null);

        assertThat(code).isEqualTo("LED-00042");
        verify(repository, times(1)).reserveNext(companyId, "LED");
    }

    @Test
    @DisplayName("formats high sequence numbers correctly with custom prefix")
    void formatsLargeSequenceNumber() {
        String companyId = UUID.randomUUID().toString();
        when(repository.reserveNext(companyId, "MKT")).thenReturn(12345L);

        String code = service.reserveNextCode(companyId, "MKT");

        assertThat(code).isEqualTo("MKT-12345");
    }
}
