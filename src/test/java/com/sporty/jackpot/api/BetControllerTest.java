package com.sporty.jackpot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}
