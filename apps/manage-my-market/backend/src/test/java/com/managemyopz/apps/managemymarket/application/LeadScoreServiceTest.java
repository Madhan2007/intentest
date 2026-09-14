/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadScoreRepository;
import com.managemyopz.apps.managemymarket.domain.LeadScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LeadScoreServiceTest {

    private final LeadScoreRepository repository = mock(LeadScoreRepository.class);
    private final LeadScoreService service = new LeadScoreService(repository);

    @Test
    @DisplayName("records score and clamps between 0 and 100")
    void recordsScoreClamped() {
        UUID leadId = UUID.randomUUID();
        service.updateScore(leadId, 120, 150, "HIGH", "VIP prospect");

        ArgumentCaptor<LeadScore> captor = ArgumentCaptor.forClass(LeadScore.class);
        verify(repository).upsert(captor.capture());

        LeadScore saved = captor.getValue();
        assertThat(saved.leadId()).isEqualTo(leadId);
        assertThat(saved.score()).isEqualTo(100);
        assertThat(saved.engagementScore()).isEqualTo(100);
        assertThat(saved.fitGrade()).isEqualTo("HIGH");
        assertThat(saved.factorsSummary()).isEqualTo("VIP prospect");
    }

    @Test
    @DisplayName("adjusts engagement score correctly on activity events")
    void adjustsEngagementScore() {
        UUID leadId = UUID.randomUUID();
        service.recordActivityEngagement(leadId, 25, "Email opened and clicked link");

        ArgumentCaptor<LeadScore> captor = ArgumentCaptor.forClass(LeadScore.class);
        verify(repository).upsert(captor.capture());

        LeadScore saved = captor.getValue();
        assertThat(saved.leadId()).isEqualTo(leadId);
        assertThat(saved.engagementScore()).isEqualTo(25);
    }
}
