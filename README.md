# sporty-jackpot

A bet is posted to the API and published to the `jackpot-bets` topic. A consumer picks it up and
contributes part of the stake to the matching jackpot pool, recording a ledger row. A second
endpoint evaluates a bet for the reward: on a win it pays out the whole current pool and resets
that pool to its initial value.

## Running it

Java 21. The Maven wrapper is checked in, so no local Maven install is needed.

The default profile is `mock` — it needs no broker and no database server:

```
./mvnw spring-boot:run
```

Bets posted under `mock` are logged instead of being sent to Kafka, and are handed straight to the
contribution logic, so the whole flow still runs.

For a real broker, start the stack first:

```
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=kafka
```

The `jackpot-kafka-init` container creates the topic and then exits with code 0. That is expected —
it is a one-shot job, not a service that died.

Under `kafka` the bet goes through a real broker, so evaluating straight after publishing can return
`404` until the consumer has processed it — wait a moment, or watch for the consumer's log line.
Under `mock` processing is synchronous and evaluate works immediately.

Tests:

```
./mvnw test
```

## Seeded jackpots

Four jackpots are seeded at startup from `application.yml`, one per contribution/reward combination:

| id | contribution | reward | what it represents |
|---|---|---|---|
| `classic-progressive` | FIXED 5% | FIXED 10% | flat rate in, flat chance out |
| `must-drop` | FIXED 5% | VARIABLE, base 1%, limit 5000.00 | guaranteed to pay once the pool reaches the limit |
| `fast-start` | VARIABLE 10% decaying to 2% | FIXED 10% | pays in fast early, then tapers |
| `dynamic` | VARIABLE 10% decaying to 2% | VARIABLE, base 1%, limit 5000.00 | both sides move with the pool |

All four start at a pool of `1000.00`. The reward chances are deliberately high so that a win is
easy to observe in a short manual run; they are not realistic values for a real jackpot.

## Usage

Publish a bet:

```
curl -i -X POST localhost:8080/api/bets \
  -H 'Content-Type: application/json' \
  -d '{"betId":"bet-1","userId":"user-alice","jackpotId":"fast-start","betAmount":100.00}'
```

```
HTTP/1.1 202
{"betId":"bet-1","status":"ACCEPTED"}
```

Evaluate that bet for the reward:

```
curl -i -X POST localhost:8080/api/bets/bet-1/evaluate
```

```
HTTP/1.1 200
{"betId":"bet-1","won":false,"rewardAmount":null}
```

On a win:

```
{"betId":"bet-1","won":true,"rewardAmount":1010.00}
```

An unknown bet id returns `404`.

For a win you can count on, use `must-drop`. It contributes a flat 5% and its reward chance reaches
100% once the pool hits 5000.00, so a single stake of 80000.00 contributes 4000.00 and takes the
pool from 1000.00 to exactly the limit:

```
curl -X POST localhost:8080/api/bets \
  -H 'Content-Type: application/json' \
  -d '{"betId":"bet-2","userId":"user-bob","jackpotId":"must-drop","betAmount":80000.00}'

curl -X POST localhost:8080/api/bets/bet-2/evaluate
```

```
{"betId":"bet-2","won":true,"rewardAmount":5000.00}
```

That evaluation always wins, pays out the whole pool, and resets `must-drop` to 1000.00.

H2 console: <http://localhost:8080/h2-console>, JDBC URL `jdbc:h2:mem:jackpot`, user `sa`, empty
password.

## Interpretations

Where the assignment was silent, this is what was chosen:

- `currentJackpotAmount` on a contribution row is the pool *after* that contribution.
- The reward is the entire current pool, not a fraction of it.
- `createdAt` is an `Instant`, not a calendar date.
- Evaluation happens when the endpoint is called, not automatically on consumption.
- Both variable strategies measure pool growth from the initial pool, not from zero, so a
  freshly reset jackpot starts over at its base rate.
- The contribution rate has a configurable floor. The assignment does not mention one; without it
  the linear decay eventually goes negative and starts draining the pool.
- A bet is evaluated once. A losing bet cannot be re-rolled, or a client could loop the endpoint
  until it wins.

`TRACEABILITY.md` maps each requirement to where it is implemented and how it was verified.

## Design decisions

### Strategy pattern for contribution and reward configurations

The assignment asks for two configuration types now and more later, so there is one interface per
family and one implementation per type. Each declares the enum constant it supports, and the
resolvers build an `EnumMap` from every implementation Spring injects as a collection, so a new
type is a new class and nothing existing changes. The alternative was a switch on the enum inside
the service, which works but puts every new type back in the middle of the contribution logic.

### BigDecimal for money

Floating point cannot represent most decimal fractions exactly, and on money that shows up as lost
cents. Monetary columns are scale 2 and final amounts round HALF_UP. Rates and probabilities are
scale 6 instead, because a computed variable rate carries more than two decimals and truncating it
would quietly change how much every later bet contributes.

### Dual publisher behind profiles

The assignment permits mocking the producer and just logging the payload, so `BetPublisher` has two
implementations selected by profile. The controller is identical in both modes, and the mock
publisher calls the same contribution entry point the real consumer does, so running without a
broker still exercises the real contribution path rather than stubbing it. The alternative was a
conditional inside the controller, which would have put the run mode into the request handler.

### Transaction boundary and locking

The bet, the ledger row and the updated pool commit in one transaction, so the ledger always
reconciles to the pool; split apart, a crash between them leaves a ledger row for money the pool
never received. The jackpot row is read under a pessimistic write lock because the pool has writers
outside Kafka — the evaluate endpoint mutates it from an HTTP thread. This is a trade-off:
optimistic locking would likely suit low contention better, and the pessimistic lock has not been
load-tested under contention.

### Idempotency

Kafka delivery is at-least-once, so the same bet legitimately arrives again after a restart or a
rebalance; a `betId` already in the bet table is skipped rather than contributed twice. On the
evaluate side, a bet that already has a reward returns it without rolling again or touching the
pool. Both checks sit after the lock is taken, not before it: checked first, two concurrent callers
would both read "not yet processed" and both proceed, which is the case the guard exists to stop.

### Kafka partition key

The message key is the `jackpotId`, not the `betId`, so every bet for one jackpot lands on one
partition and is consumed in order. Keying by `betId` would scatter bets for the same jackpot
across all partitions and let concurrent consumers apply contributions to one pool at once. This
buys ordering among Kafka consumers only, not mutual exclusion, and is not a substitute for the
lock.

### Configuration-driven seeding

The assignment calls the initial pool value configurable, so jackpots are defined under
`jackpot.seed` in `application.yml` and created at startup, and changing one needs no recompile.
Each seed is validated before it is persisted, so a jackpot missing a field its types require fails
startup instead of throwing from deep inside a strategy on the first bet that uses it.

## What I'd do with more time

- A dead-letter topic for poison messages.
- A load test that actually exercises the pessimistic lock under contention — never verified.
- Optimistic locking as an alternative under low contention.
- Polymorphic per-type jackpot configuration instead of nullable columns.
- Currency handling.
- An endpoint to create jackpots, which would move seed validation out of the seeder.
- Metrics.
