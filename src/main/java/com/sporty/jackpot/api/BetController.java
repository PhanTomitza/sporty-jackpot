package com.sporty.jackpot.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sporty.jackpot.messaging.BetMessage;
import com.sporty.jackpot.messaging.BetPublisher;
import com.sporty.jackpot.service.RewardEvaluationService;
import com.sporty.jackpot.service.RewardResult;

import jakarta.validation.Valid;

/**
 * Accepts bets and hands them to the {@link BetPublisher}, and evaluates processed bets for a win.
 *
 * <p>Publishing returns <b>202 Accepted</b>, not 200 or 201: the bet has been accepted for
 * asynchronous processing but has not yet been processed or persisted, and no jackpot pool has
 * moved. 201 would assert a resource now exists at some location, which is not true until the
 * consumer handles it; 200 would imply the work is done.
 */
@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BetPublisher betPublisher;
    private final RewardEvaluationService rewardEvaluationService;

    public BetController(BetPublisher betPublisher, RewardEvaluationService rewardEvaluationService) {
        this.betPublisher = betPublisher;
        this.rewardEvaluationService = rewardEvaluationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishBetResponse publishBet(@Valid @RequestBody PublishBetRequest request) {
        betPublisher.publish(new BetMessage(
                request.betId(),
                request.userId(),
                request.jackpotId(),
                request.betAmount()));

        return PublishBetResponse.accepted(request.betId());
    }

    /**
     * Evaluates a processed bet for a jackpot win, returning <b>200</b> with the outcome.
     *
     * <p><b>POST, not GET.</b> On a win this call pays out the pool and resets it — it mutates
     * server state, so it is neither safe nor idempotent in the HTTP sense, and GET must be both.
     * A GET here would also be eligible for caching by any intermediary, which for a
     * state-changing call is a correctness bug, not a performance detail. That the stored reward
     * makes <em>repeat</em> calls return the same answer does not make the method idempotent:
     * the first call is what moves the pool.
     */
    @PostMapping("/{betId}/evaluate")
    public RewardResult evaluateBet(@PathVariable String betId) {
        return rewardEvaluationService.evaluate(betId);
    }
}
