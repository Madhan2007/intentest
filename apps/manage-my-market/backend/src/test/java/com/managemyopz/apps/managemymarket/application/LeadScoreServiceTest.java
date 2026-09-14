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
    @DisplayName("upserts lead score with breakdown JSON")
    void recordsScore() {
        String leadId = UUID.randomUUID().toString();
        service.upsertScore(leadId, 85, "{\"profile\": 40, \"engagement\": 45}");

        ArgumentCaptor<LeadScore> captor = ArgumentCaptor.forClass(LeadScore.class);
        verify(repository).upsert(captor.capture());

        LeadScore saved = captor.getValue();
        assertThat(saved.leadId()).isEqualTo(leadId);
        assertThat(saved.score()).isEqualTo(85);
        assertThat(saved.breakdownJson()).isEqualTo("{\"profile\": 40, \"engagement\": 45}");
        assertThat(saved.updatedAt()).isNotNull();
    }
}
