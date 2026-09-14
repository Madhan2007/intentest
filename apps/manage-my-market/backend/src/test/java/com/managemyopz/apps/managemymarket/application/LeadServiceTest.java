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
        UUID companyId = UUID.randomUUID();
        when(sequenceService.generateNextCode(companyId)).thenReturn("LEAD-000001");

        Lead created = service.createLead(
                companyId,
                "Acme Corp Lead",
                "INBOUND_WEB",
                "John",
                "Doe",
                "Acme Corp",
                "john@acme.com",
                "+1234567890",
                "Pricing page form",
                BigDecimal.valueOf(25000),
                "HIGH",
                null,
                "agent-1",
                "creator-1",
                "Initial prospect contact"
        );

        assertThat(created).isNotNull();
        assertThat(created.leadCode()).isEqualTo("LEAD-000001");
        assertThat(created.displayName()).isEqualTo("Acme Corp Lead");
        assertThat(created.status()).isEqualTo("NEW");
        assertThat(created.estimatedValue()).isEqualTo(BigDecimal.valueOf(25000));

        verify(repository).insert(any(Lead.class));
        verify(activityService).logActivity(
                eq(created.id().toString()),
                eq("CREATED"),
                eq("Lead Created"),
                contains("pricing page form"),
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
        UUID leadId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Lead existing = new Lead(
                leadId, companyId, "LEAD-000005", "Test Lead", "INBOUND_WEB",
                "Jane", "Smith", "Smith LLC", "jane@smith.com", "+1000",
                "web", "NEW", BigDecimal.valueOf(5000), "MEDIUM",
                null, "owner-1", "creator-1", "notes", null, null
        );

        when(repository.findById(leadId)).thenReturn(Optional.of(existing));

        service.disqualifyLead(leadId.toString(), "Budget constraint", "admin-user");

        verify(repository).disqualify(leadId, "Budget constraint");
        verify(activityService).logActivity(
                eq(leadId.toString()),
                eq("DISQUALIFIED"),
                eq("Lead Disqualified"),
                contains("Budget constraint"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("admin-user")
        );
    }
}
