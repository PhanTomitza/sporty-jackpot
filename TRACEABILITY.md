# TRACEABILITY — implementation vs. the assignment specification

Source of requirements: `jackpot.pdf` ("Jackpot - BE Home Assignment", Sporty Group). **This
document is traced against the PDF only.** Where the implementation does something the PDF does not
ask for, the row says so.

Status values:

- **IMPLEMENTED AND VERIFIED LIVE** — exercised against a running application and the result
  observed directly (HTTP response, broker payload, or a SQL read of the persisted row).
- **IMPLEMENTED AND UNIT/INTEGRATION TESTED ONLY** — covered by the automated suite, not observed
  against a running application.
- **IMPLEMENTED BUT UNVERIFIED** — code exists, no evidence gathered.
- **INTERPRETED** — the PDF is silent or ambiguous; the interpretation is stated.
- **NOT IMPLEMENTED** — absent.

Each "VERIFIED LIVE" row states its evidence inline: the HTTP response, the broker payload, or the
SQL read-back that was observed.

---

## Overview section

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| O-1 | "Please include a `README` in any format on how to run and use the program." | `README.md` | Read the file. It documents both run modes with the exact commands, the seeded jackpots, and a worked `curl` example per endpoint with its response. | **IMPLEMENTED** |

## Requirements section

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| R-1 | "An API endpoint to publish a bet to Kafka." | `BetController.publishBet` (`POST /api/bets`) | Live under both profiles: `POST /api/bets` → `202 ACCEPTED`; under `kafka` the payload was then read off the broker by an independent console consumer. | **IMPLEMENTED AND VERIFIED LIVE** |
| R-2 | "A Kafka consumer that listens to `jackpot-bets` Kafka named-topic." | `KafkaBetConsumer.onBet` | Live under `kafka`: log shows `partitions assigned: [jackpot-bets-0, jackpot-bets-1, jackpot-bets-2]`, then `Received bet kafka-bet-A ... from topic`. | **IMPLEMENTED AND VERIFIED LIVE** |
| R-3 | "Each bet must contribute to a matching jackpot pool." | `JackpotContributionService.processBet` | Live under both profiles: pool moved `1000.00 → 1010.00` (`fast-start`) and `1000.00 → 5000.00` (`must-drop`), with a matching ledger row read back by SQL. | **IMPLEMENTED AND VERIFIED LIVE** |
| R-4 | "Each bet must be evaluated for jackpot reward." | `RewardEvaluationService.evaluate` | Live under both profiles. **Interpretation applies** — see U4-a: evaluation is caller-triggered, not automatic on consumption. | **INTERPRETED** (see U4-a) |
| R-5 | "An API endpoint to evaluate if a bet wins a jackpot reward." | `BetController.evaluateBet` (`POST /api/bets/{betId}/evaluate`) | Live under both profiles: forced win returned `{"won":true,"rewardAmount":5000.00}`; a loss returned `{"won":false,"rewardAmount":null}`; unknown id returned `404`. | **IMPLEMENTED AND VERIFIED LIVE** |
| R-6 | "This refers to the entire flow starting from the API, down to the persistence layer." | `api` → `messaging` → `service` → `strategy` → `domain` (JPA + H2) | Live: a single HTTP call was traced through to committed rows in `bet`, `jackpot_contribution`, `jackpot_reward` and an updated `jackpot` row, all read back by SQL. | **IMPLEMENTED AND VERIFIED LIVE** |
| R-7 | "It is expected for you to spend around 90 minutes to complete the exercise." | — | Not a property of the code. The delivered scope is larger than 90 minutes of work. Recorded here so the discrepancy is visible rather than implicit. | **INTERPRETED** — guidance about expected scope, which this solution exceeds. |

## Use case 1 — publish a bet to Kafka

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| U1-a | "This endpoint should publish a bet to Kafka." | `KafkaBetPublisher.publish` (profile `kafka`) | Live: `Published bet kafka-bet-A to jackpot-bets-1 (key=fast-start)`, corroborated by an independent console consumer in a separate consumer group. | **IMPLEMENTED AND VERIFIED LIVE** |
| U1-a-i | Bet is represented by: "Bet ID" | `BetMessage.betId`, `PublishBetRequest.betId` | Observed on the wire: `{"betId":"kafka-bet-A",...}`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U1-a-ii | "User ID" | `BetMessage.userId` | Observed on the wire: `"userId":"user-alice"`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U1-a-iii | "Jackpot ID" | `BetMessage.jackpotId` | Observed on the wire: `"jackpotId":"fast-start"`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U1-a-iv | "Bet Amount" | `BetMessage.betAmount` (`BigDecimal`) | Observed on the wire: `"betAmount":100.00`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U1-b | "The topic should be `jackpot-bets` as commented before." | `jackpot.kafka.topic: jackpot-bets` in `application.yml`; used by publisher and consumer | Live: `kafka-topics.sh --list` returned `jackpot-bets`; the record was published to partition 1 of that topic. | **IMPLEMENTED AND VERIFIED LIVE** |

## Use case 2 — Kafka consumer

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| U2-a | "A Kafka consumer that listens to `jackpot-bets` Kafka named-topic." | `KafkaBetConsumer` (`@KafkaListener(topics = "${jackpot.kafka.topic}")`) | Live, as R-2. Consumer group `jackpot-service` assigned all 3 partitions and consumed both test bets. | **IMPLEMENTED AND VERIFIED LIVE** |

## Use case 3 — contribution to a matching jackpot

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| U3-a-1 | "Each jackpot will start with a configurable initial pool value." | `jackpot.seed[*].initialPoolAmount` → `JackpotSeedProperties` → `DataSeeder` | Live: startup logged all four seeded jackpots with `initialPool=1000.00`; SQL confirmed `current_pool_amount == initial_pool_amount` at seed time. Value is set in `application.yml`, requiring no recompile. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-a-2 | "Each Jackpot can have a different configuration for contribution." | `Jackpot.contributionType` + per-jackpot rate fields | Live: `fast-start` (VARIABLE) and `must-drop` (FIXED) contributed under different rules in the same run — `10.00` on a `100.00` stake vs `4000.00` on an `80000.00` stake. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-a-3 | "In the beginning we want to support just two options, but have the option to add more configurations in future." | `ContributionStrategy` interface + `ContributionStrategyResolver` (collection injection, `EnumMap` lookup, no switch) | Code inspection + `ContributionStrategyResolverTest`. Adding a type requires a new `@Component` and a new enum constant, with no edit to the resolver. Extensibility itself is a design claim, not a runtime observation. | **IMPLEMENTED AND UNIT/INTEGRATION TESTED ONLY** |
| U3-a-i | "Fixed contribution as a percentage of the Bet Amount." | `FixedContributionStrategy.calculateContribution` | Live: `must-drop` at rate `0.05`, stake `80000.00` → contribution `4000.00` (exactly 5%). | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-a-ii | "Variable contribution as a percentage of the Bet Amount. In the beginning the contribution is bigger and over time it becomes lower at fixed rate as the jackpot pool increases." | `VariableContributionStrategy.calculateContribution` | Live on `fast-start`, four successive `10000.00` stakes; effective rate computed in SQL as `contribution/stake` fell monotonically: `0.100 → 0.0995 → 0.04975 → 0.024875 → 0.02`. Decay is linear in pool growth ("at fixed rate"). The final `0.02` is the configured `minContributionRate` floor — **the floor is not in the PDF**; see the note below the table. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-b | "The System checks for a matching jackpot based on Jackpot ID. If there is such a jackpot, the system should contribute to the jackpot pool according to its configuration." | `JackpotContributionService.processBet` (lookup by id; returns without contributing if absent) | Live: matching path verified as above. Non-matching path verified in `JackpotContributionIntegrationTest` — logs `references unknown jackpot ... no contribution recorded` and writes nothing. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-c-i | Jackpot Contribution composed by: "Bet ID" | `JackpotContribution.betId` | SQL read-back, both profiles: `mock-bet-A` / `kafka-bet-A`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-c-ii | "User ID" | `JackpotContribution.userId` | SQL read-back: `user-alice`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-c-iii | "Jackpot ID" | `JackpotContribution.jackpotId` | SQL read-back: `fast-start`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-c-iv | "Stake Amount" | `JackpotContribution.stakeAmount` | SQL read-back: `100.00` (equals the submitted bet amount). | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-c-v | "Contribution Amount" | `JackpotContribution.contributionAmount` | SQL read-back: `10.00`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U3-c-vi | "Current Jackpot Amount" | `JackpotContribution.currentJackpotAmount` | SQL read-back: `1010.00`. **INTERPRETED**: stored as the pool *after* this contribution. The PDF does not say whether it is the before or after value; "current" was read as the balance the contribution produced, so the row is self-contained. | **INTERPRETED** — after-value; stated above. |
| U3-c-vii | "Created At Date" | `JackpotContribution.createdAt` (`Instant`) | SQL read-back: `2026-07-19 17:09:05.030759+00`. **INTERPRETED**: an instant, not a date — the PDF says "Date"; a timestamp was stored as strictly more information. | **INTERPRETED** — timestamp rather than calendar date. |

> **Note on U3-a-ii (`minContributionRate`).** The PDF describes only a rate that decreases; it
> specifies no floor. The floor is an addition, made because the chosen linear decay is unbounded
> and would otherwise cross zero and turn contributions negative — draining the pool. It is
> configurable per jackpot. A reader of the PDF alone would not expect the rate to stop falling at
> `0.02`.

## Use case 4 — evaluate a bet for the jackpot reward

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| U4-a | "This endpoint should check if a contributing bet wins the jackpot reward and return the reward as a response." | `BetController.evaluateBet` → `RewardEvaluationService.evaluate` | Live, both profiles: forced win returned `{"betId":"mock-bet-B","won":true,"rewardAmount":5000.00}`. **INTERPRETED**: the PDF also states "Each bet **must be evaluated** for jackpot reward" (R-4), which could be read as evaluating automatically on consumption. Here evaluation happens **only when the endpoint is called** — a bet that is never evaluated stays unevaluated forever. This follows the endpoint-centric reading of use case 4. | **INTERPRETED** — caller-triggered, not automatic. |
| U4-b | "Each Jackpot can have a different configuration for rewards. In the beginning we want to support just two options, but have the option to add more configurations in future." | `RewardStrategy` + `RewardStrategyResolver`; `Jackpot.rewardType` | Live: `fast-start` (FIXED chance `0.10`) and `must-drop` (VARIABLE) were evaluated under different rules in one run. Extensibility mechanism is as U3-a-3 (design claim, unit tested). | **IMPLEMENTED AND VERIFIED LIVE** (extensibility: tested only) |
| U4-c | "Fixed chance for a reward as a percentage." | `FixedRewardStrategy.winChance` | Live on `fast-start` (chance `0.10`), DEBUG log at two deliberately different pool sizes: pool `6000.00` → `chance 0.100000`; pool `7000.00` → `chance 0.100000`. Constant under a changing pool, which is what distinguishes it from U4-d. | **IMPLEMENTED AND VERIFIED LIVE** |
| U4-d | "Variable chance as a percentage. In the beginning the chance for reward is smaller and over time it becomes bigger as the jackpot pool increases. If the jackpot pool hits a limit, then the chance becomes 100%." | `VariableRewardStrategy.winChance` | Live on `must-drop` (base `0.01`, limit `5000.00`), DEBUG log: pool `2000.00` → `chance 0.25750000`; pool `3000.00` → `chance 0.5050000`; pool `5000.00` (== limit) → `chance 1`. All three clauses observed: smaller at first, rising with the pool, exactly 100% at the limit. **INTERPRETED**: growth is measured from `initialPoolAmount`, not from zero, so a freshly reset jackpot restarts at the base chance. | **IMPLEMENTED AND VERIFIED LIVE** |
| U4-e | "When a jackpot is rewarded, it should be reset to the initial pool value." | `Jackpot.resetPool()`, called in `RewardEvaluationService.evaluate` | Live and **observed, not inferred**, three times: `must-drop` went `5000.00 → 1000.00` under `mock`, `5000.00 → 1000.00` under `kafka`, and `3000.00 → 1000.00` mid-ladder. The reset was confirmed by SQL read-back of `jackpot.current_pool_amount`, and independently by a later evaluation whose variable chance had fallen back to the base-relative value. | **IMPLEMENTED AND VERIFIED LIVE** |
| U4-f-i | Jackpot Reward composed by: "Bet ID" | `JackpotReward.betId` | SQL read-back: `mock-bet-B` / `kafka-bet-B`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U4-f-ii | "User ID" | `JackpotReward.userId` | SQL read-back: `user-bob`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U4-f-iii | "Jackpot ID" | `JackpotReward.jackpotId` | SQL read-back: `must-drop`. | **IMPLEMENTED AND VERIFIED LIVE** |
| U4-f-iv | "Jackpot Reward Amount" | `JackpotReward.jackpotRewardAmount` | SQL read-back: `5000.00`, equal to the pool immediately before reset. **INTERPRETED**: the PDF never states the reward *size*; it was read as the whole pool, since U4-e resets the pool to its initial value and any other amount would leave an unexplained remainder. | **INTERPRETED** — reward == entire current pool. |
| U4-f-v | "Created At Date" | `JackpotReward.createdAt` (`Instant`) | SQL read-back: `2026-07-19 17:09:22.764687+00`. Same date-vs-timestamp interpretation as U3-c-vii. | **INTERPRETED** — timestamp rather than calendar date. |

## Conditions

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| C-1 | "If the Kafka setup is too complex, use mocks for the Kafka producer. Just log the payload." | `LoggingBetPublisher` (profile `mock`, the default) | Live with the Docker stack fully torn down (`docker compose down -v`, no containers running): the app started on the default `mock` profile and logged `[MOCK] Publishing to topic 'jackpot-bets' (key=must-drop): betId=..., betAmount=80000.00`. **Both** paths are provided, so the PDF's fallback is available without giving up the real one. **INTERPRETED**: the mock publisher does more than log — it also calls the contribution service synchronously, so the mock profile exercises the full downstream flow rather than stopping at the log line. | **IMPLEMENTED AND VERIFIED LIVE** (with the stated extension) |
| C-2 | "Use an in-memory database for the bets and jackpots." | H2 in-memory, `spring.datasource.url=jdbc:h2:mem:jackpot`, `ddl-auto: create-drop` | Live: the app ran with no external database; schema was created at startup and all four tables (`bet`, `jackpot`, `jackpot_contribution`, `jackpot_reward`) were queried directly. | **IMPLEMENTED AND VERIFIED LIVE** |

## Delivery

| # | Requirement (quoted from the PDF) | Where implemented | How it was verified | Status |
|---|---|---|---|---|
| D-1 | "The solution needs to be 100% executable" | whole project | Verified against a **fresh clone of the committed branch**: `./mvnw clean verify` → `BUILD SUCCESS`, 74/74 tests. Both profiles were also run live end to end. | **IMPLEMENTED AND VERIFIED LIVE** |
| D-2 | "Provide a link to the GitHub repository where your solution is committed" | `origin` = `github.com/PhanTomitza/sporty-jackpot`, branch `implementation-branch` | The full solution is committed and pushed: `origin/implementation-branch` matches local `HEAD` and builds green from a clean clone. **Caveat:** the default branch `origin/main` is still at the initial commit, so a plain repository link lands a reviewer on an empty project. The link supplied must name the branch, or `implementation-branch` must be merged into `main` before submission. | **IMPLEMENTED** — with the branch caveat stated. |
| D-3 | "Please make sure it is not private or restricted" | GitHub repository settings | Live: unauthenticated `GET https://api.github.com/repos/PhanTomitza/sporty-jackpot` → `HTTP 200`, so the repository is publicly readable. | **IMPLEMENTED AND VERIFIED LIVE** |
| D-4 | "Provide a `README` file (documentation) how to run and use the solution" | `README.md` | Same file as O-1. It covers the `mock` default and the `kafka` profile including the `docker compose` step, both endpoints with request and response bodies, the H2 console credentials, and the interpretations made where the PDF is silent. | **IMPLEMENTED** |
