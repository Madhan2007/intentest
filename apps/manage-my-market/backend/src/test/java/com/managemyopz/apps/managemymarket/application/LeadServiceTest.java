/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadRepository;
import com.managemyopz.apps.managemymarket.domain.Lead;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LeadServiceTest {

    private final LeadRepository repository = mock(LeadRepository.class);
    private final LeadCodeSequenceService sequenceService = mock(LeadCodeSequenceService.class);
    private final LeadActivityService activityService = mock(LeadActivityService.class);

    private final LeadService service = new LeadService(repository, sequenceService, activityService);

    @Test
    @DisplayName("creates new lead with generated sequence code and logs initial activity")
    void createsLeadSuccessfully() {
        String companyId = UUID.randomUUID().toString();
        when(sequenceService.reserveNextCode(companyId, "LED")).thenReturn("LED-00001");

        Lead created = service.createLead(
                companyId,
                "Acme Corp Lead",
                "MANUAL",
                "John",
                "Doe",
                "Acme Corp",
                "john@acme.com",
                "+1234567890",
                "Pricing page form",
                25000,
                "HOT",
                null,
                "agent-1",
                "creator-1",
                "Initial prospect contact"
        );

        assertThat(created).isNotNull();
        assertThat(created.leadCode()).isEqualTo("LED-00001");
        assertThat(created.displayName()).isEqualTo("Acme Corp Lead");
        assertThat(created.status()).isEqualTo("NEW");
        assertThat(created.estimatedValue()).isEqualTo(25000);

        verify(repository).insert(any(Lead.class));
        verify(activityService).logActivity(
                eq(created.id()),
                eq(ManageMyMarketConstants.ACTIVITY_TYPE_NOTE),
                eq("Lead Created"),
                anyString(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("creator-1")
        );
    }

    @Test
    @DisplayName("disqualifies lead with reason and logs disqualification activity")
    void disqualifiesLead() {
        String leadId = UUID.randomUUID().toString();
        String companyId = UUID.randomUUID().toString();
        Lead existing = new Lead(
                leadId, companyId, "LED-00005", "MANUAL",
                "Jane", "Smith", "Test Lead", "Smith LLC", "jane@smith.com", "+1000",
                "NEW", "web", 5000, "WARM", 10,
                null, "owner-1", "creator-1", "notes", Instant.now(), Instant.now()
        );

        when(repository.findById(leadId, companyId)).thenReturn(Optional.of(existing));

        service.disqualify(leadId, companyId, "Budget constraint", "admin-user");

        verify(repository).disqualify(leadId, companyId, "Budget constraint");
        verify(activityService).logActivity(
                eq(leadId),
                eq(ManageMyMarketConstants.ACTIVITY_TYPE_STATUS_CHANGE),
                eq("Lead Disqualified"),
                eq("Reason: Budget constraint"),
                eq("NEW"),
                eq("DISQUALIFIED"),
                isNull(),
                isNull(),
                eq("admin-user")
        );
    }
}
