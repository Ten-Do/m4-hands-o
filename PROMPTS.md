# Session 4A — AI-assisted debugging

## Part A — Explain the stack trace

### Prompt

> Explain this Java stack trace in plain English. What is the most likely root cause? Do NOT propose a patch yet.

### Stack-trace excerpt supplied to the AI

```text
org.opentest4j.AssertionFailedError: 5-year loyalty customer should get 10% off ==> expected: <90.0> but was: <95.0>
    at PriceEngineTest.quotesDiscountForLoyalCustomer(PriceEngineTest.java:92)
```

### AI response (verbatim)

The test `quotesDiscountForLoyalCustomer` failed because it expected a final quote of 90.00 for a customer with five loyalty years, but `PriceEngine.quote` returned 95.00. The stack trace locates the failed assertion in the test, not the calculation itself. The most likely root cause is that the loyalty-tier logic treats exactly five years as the 5% tier instead of the documented 10% tier; 95.00 is exactly what a 5% discount on 100.00 would produce.

### My assessment

I agree: the difference between the expected and actual values precisely matches the tier-2 discount, so an off-by-one boundary check is the most plausible cause.
The first two lines were sufficient to form and verify the hypothesis after reading the tier rules; the remaining frames would not identify the source line and would mainly add noise.

## Part B — Propose and verify the patch

### Prompt

> Given root cause X (the loyalty-tier condition classifies exactly five loyalty years in the 5% tier rather than the 10% tier), write the smallest patch that fixes the bug without changing behaviour on any other test. Show only the diff.

### AI response (verbatim)

```diff
--- a/src/PriceEngine.java
+++ b/src/PriceEngine.java
@@
-        if (years > 5) {
+        if (years >= 5) {
             loyaltyRate = 0.10;
```

### Review and verification

The diff changes only the previously uncovered boundary case, `years == 5`; values below five and above five retain their existing rates. After applying it, `make test` completed successfully: all 10 tests passed with no regressions.

Commit created: `Fix five-year loyalty discount boundary`.

## Part C — Reflection

The explanation reply was the most useful because it connected the numerical mismatch (95 instead of 90) to the 5% loyalty tier and narrowed the investigation to one condition. The patch reply was useful but less valuable, because once the inclusive boundary was identified, the one-character change was straightforward. I would normally provide the shortest stack-trace excerpt that includes the exception, expected-versus-actual values, and the first project-code frame; supplying the full trace can add irrelevant framework frames, make diagnosis slower, and disclose more project details than necessary. If the short excerpt does not distinguish competing causes, I would add frames incrementally rather than pasting everything at once.
