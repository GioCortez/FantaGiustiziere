# Parallel calendar permutation processing

Below is a step-by-step breakdown of how calendar permutation generation now runs in parallel.

1. **Thread-safe state setup** – The permutator owns atomic counters and concurrent maps to track permutations, statistics, and the calendars selected for output without shared-state races. Each run starts by clearing these structures and resetting the limit flag.
2. **Parallel entry point** – `permuteCalendars` builds a `ForkJoinPool` sized to available processors (minimum two threads) and invokes a root `PermutationTask`, passing the team list and user-provided limit. This replaces single-threaded iteration with work-stealing parallelism, while allowing a `parallelExecution` flag to switch back to single-threaded processing for cross-checking results.
3. **Permutation processing guardrails** – Before evaluating a permutation, the code checks the limit flag and stops early if the configured permutation cap has been reached. Each computed calendar increments atomic position counters and, for winners, stores up to `calendarsToPrint` examples in a synchronized list for later file output.
4. **Limit-aware counting** – After each permutation, the atomic counter increments. If the optional limit is met, the code marks the run as complete and throws `LimitReachedException`, which the `ForkJoinPool` propagation catches to halt further work.
5. **Fork/join splitting** – `PermutationTask` recurses over remaining teams. When the branching factor exceeds the `parallelThreshold`, it forks a subtask per choice; otherwise it processes sequentially in the same thread to avoid overhead. When `parallelExecution` is false, recursion always stays on the same thread to ensure the requested single-threaded run.
6. **Statistics and results emission** – Once the pool finishes, percentage statistics are computed defensively (only when permutations were processed), logged, and stored calendars are written to per-player text files under `results/`.
