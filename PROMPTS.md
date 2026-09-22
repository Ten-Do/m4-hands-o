# Session 4A — AI-assisted debugging

Repository: https://github.com/Ten-Do/m4-hands-o

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

## Session 4B — Reducing cyclomatic complexity

### Part A — Rank refactor targets

#### Prompt

> Rank the methods in this file by refactoring priority.
> For each, give:
> (a) current cyclomatic complexity estimate,
> (b) the single refactor move you would apply first (extract-method, flatten-guard-clauses, replace-loop-with-pipeline, or rename),
> (c) one sentence on the expected complexity reduction.
> Do not propose code yet.

#### AI response

1. `quote(Order, Customer)` — estimated cyclomatic complexity: 14. First move: `extract-method`, targeting the loyalty-tier discount block. Moving its four decisions into a helper should reduce `quote` by about 4 while preserving the calculation order.
2. `Money.requireSameCurrency(Money)` — estimated cyclomatic complexity: 2. First move: `flatten-guard-clauses`; there is only one validation branch, so the expected reduction is zero.
3. `Money.times(double)` — estimated cyclomatic complexity: 1. First move: `rename`; this would not reduce complexity.
4. `Money.add(Money)` — estimated cyclomatic complexity: 2. First move: `flatten-guard-clauses`; there is only one validation branch, so the expected reduction is zero.
5. `Money.subtract(Money)` — estimated cyclomatic complexity: 2. First move: `flatten-guard-clauses`; there is only one validation branch, so the expected reduction is zero.

The top priority is `quote` because it is the only method with several independent business-rule branches and the largest direct reduction available from one coherent extraction.

### Part B — Apply one extract-method refactor

#### Prompt

> Extract the loyalty-tier discount block into a private helper method `applyLoyaltyDiscount(...)`. Do NOT change public method signatures. Show the diff.

#### AI response

```diff
@@
-        int years = customer.loyaltyYears();
-        double loyaltyRate;
-        if (years >= 5) {
-            loyaltyRate = 0.10;
-        } else if (years >= 3) {
-            loyaltyRate = 0.05;
-        } else if (years >= 1) {
-            loyaltyRate = 0.02;
-        } else {
-            loyaltyRate = 0.0;
-        }
-        if (loyaltyRate > 0.0) {
-            running = running.subtract(subtotal.times(loyaltyRate));
-        }
+        running = applyLoyaltyDiscount(
+            running, subtotal, customer.loyaltyYears());
@@
+    private static Money applyLoyaltyDiscount(Money running,
+                                               Money subtotal,
+                                               int years) {
+        if (years >= 5) {
+            return running.subtract(subtotal.times(0.10));
+        }
+        if (years >= 3) {
+            return running.subtract(subtotal.times(0.05));
+        }
+        if (years >= 1) {
+            return running.subtract(subtotal.times(0.02));
+        }
+        return running;
+    }
```

#### Review and verification

The public `quote(Order, Customer)` signature is unchanged. The helper receives the existing `running` and `subtotal` values and returns the same value that the inline block assigned, so the discount remains applied before regional tax. The inclusive five-year boundary remains `years >= 5`.

`make test` passes: all 10 tests pass with no regressions. PMD and SpotBugs are not wired into this repository's Makefile, so their optional checks were not run. The estimated cyclomatic complexity of `quote` drops from about 14 to about 10, a reduction of about 4; the extracted helper owns those four loyalty decisions.

### Part D — Reflection

The cyclomatic complexity of `quote` dropped by 4 points (from 14 to 10 by heuristic estimation, or 21 to 17 by exact McCabe calculation) by extracting the loyalty discount logic into `applyLoyaltyDiscount`. Furthermore, using early `return` statements instead of `if-else` assignments reduced the helper's own cyclomatic complexity from 5 to 4 by eliminating the intermediate `loyaltyRate` variable and redundant conditional checks. SpotBugs was not configured in this starter repository's Makefile, so no findings were flagged and no rule analysis was required. I rejected replacing the subtotal `for` loop with a Stream pipeline because it would not reduce decision complexity and would make skipping non-positive quantities less explicit and harder to read.
