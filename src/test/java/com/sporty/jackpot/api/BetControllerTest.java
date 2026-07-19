package com.sporty.jackpot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sporty.jackpot.messaging.BetMessage;
import com.sporty.jackpot.messaging.BetPublisher;
import com.sporty.jackpot.exception.BetNotFoundException;
import com.sporty.jackpot.service.RewardEvaluationService;
import com.sporty.jackpot.service.RewardResult;

/**
 * Web-layer test for {@link BetController}. {@code @WebMvcTest} loads only the MVC slice, so no
 * database, no seeding and no broker are involved — the publisher is mocked, which is the whole
 * boundary this phase owns.
 */
@WebMvcTest(BetController.class)
class BetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BetPublisher betPublisher;

    // Required by the controller's constructor since Phase 5. The evaluation logic itself is
    // covered by RewardEvaluationServiceTest; here the mock only proves the HTTP mapping.
    @MockitoBean
    private RewardEvaluationService rewardEvaluationService;

    @Test
    void validBetReturns202AndAcceptedBody() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "betId": "bet-1",
                                  "userId": "user-1",
                                  "jackpotId": "classic-progressive",
                                  "betAmount": 100.00
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.betId").value("bet-1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void validBetIsPublishedAsMappedBetMessage() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "betId": "bet-1",
                                  "userId": "user-1",
                                  "jackpotId": "classic-progressive",
                                  "betAmount": 100.00
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<BetMessage> captor = ArgumentCaptor.forClass(BetMessage.class);
        verify(betPublisher).publish(captor.capture());

        BetMessage published = captor.getValue();
        assertThat(published.betId()).isEqualTo("bet-1");
        assertThat(published.userId()).isEqualTo("user-1");
        assertThat(published.jackpotId()).isEqualTo("classic-progressive");
        // compareTo, not equals: 100.00 and 100 are equal in value but not as BigDecimal objects
        assertThat(published.betAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void blankAndMissingFieldsReturn400WithPerFieldMessages() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "betId": "",
                                  "userId": "user-1",
                                  "jackpotId": "classic-progressive",
                                  "betAmount": -5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.betId").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.betAmount").value("must be greater than zero"));

        verify(betPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullBetAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "betId": "bet-1",
                                  "userId": "user-1",
                                  "jackpotId": "classic-progressive"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.betAmount").value("must not be null"));

        verify(betPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void evaluateReturns200WithTheRewardResult() throws Exception {
        when(rewardEvaluationService.evaluate("bet-1"))
                .thenReturn(RewardResult.won("bet-1", new BigDecimal("1500.00")));

        mockMvc.perform(post("/api/bets/bet-1/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.betId").value("bet-1"))
                .andExpect(jsonPath("$.won").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(1500.00));
    }

    @Test
    void evaluateOfALosingBetReturnsWonFalseAndNullAmount() throws Exception {
        when(rewardEvaluationService.evaluate("bet-2")).thenReturn(RewardResult.lost("bet-2"));

        mockMvc.perform(post("/api/bets/bet-2/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(false))
                // Present-but-null, not absent: the caller can tell "no reward" from "field missing".
                .andExpect(jsonPath("$.rewardAmount").doesNotExist());
    }

    @Test
    void evaluateOfAnUnknownBetReturns404() throws Exception {
        when(rewardEvaluationService.evaluate("nope")).thenThrow(new BetNotFoundException("nope"));

        mockMvc.perform(post("/api/bets/nope/evaluate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No processed bet found with id 'nope'"));
    }
}
