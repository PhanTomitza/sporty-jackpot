package com.sporty.jackpot.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sporty.jackpot.messaging.BetMessage;
import com.sporty.jackpot.messaging.BetPublisher;

import jakarta.validation.Valid;

/**
 * Accepts bets and hands them to the {@link BetPublisher}.
 *
 * <p>Returns <b>202 Accepted</b>, not 200 or 201: the bet has been accepted for asynchronous
 * processing but has not yet been processed or persisted, and no jackpot pool has moved. 201 would
 * assert a resource now exists at some location, which is not true until the consumer handles it;
 * 200 would imply the work is done.
 */
@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BetPublisher betPublisher;

    public BetController(BetPublisher betPublisher) {
        this.betPublisher = betPublisher;
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
}
