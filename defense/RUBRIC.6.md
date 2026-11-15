> For the following defense, assume all relative directories are from `PROJECT_ROOT/src/main/java/net/eabbott/riscvm`, unless specified otherwise.

## Category A — Build and Structure (0.5 pts)

Does the Part VI code compile and is the memory stage factored into its
own source file(s) as required?


| Step | Criterion                                                                                                                                      | Deduction                                                   |
|------|------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| A1   | Compiles cleanly, memory stage in its own file, no external dependencies.                                                                      | -0.0                                                        |
| A2   | Compiles but emits warnings that indicate real problems (e.g., truncation, signedness on sub-word loads, unaligned access on the memory word). | -0.25                                                       |
| A3   | Compiles only after trivial fixes (missing include/import, one typo), OR memory is not separated into its own file.                            | -0.5                                                        |
| A4   | Does not compile; requires nontrivial edits to build.                                                                                          | -0.5 (and verify remaining categories by code reading only) |

I believe I have earned full credit for this category.

## Category B — Load/Store Execution and Sub-Word Extension (3.0 pts)

For LOAD and STORE only, uses the ALU-computed address in `result`,
stores `strval`, loads and overwrites `result`, with correct signed/zero
extension for sub-word accesses.

| Step | Criterion                                                                                                                                                                                                                                                                                   | Deduction |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| B1   | STORE writes `strval` to the address in `result` (SB/SH/SW at 1/2/4 bytes, little-endian); LOAD reads from `result` and **overwrites** `result` with the loaded value; LB/LH sign-extend via the Part I `sign_extend`, LBU/LHU zero-extend; non-memory instructions pass through untouched. | -0.0      |
| B2   | Correct, but a minor issue: LOAD result written to a field other than `result` (later parts tolerate it), OR SW/LW correct but a sub-word width read/writes one extra/fewer byte in a way the test set tolerates.                                                                           | -0.5      |
| B3   | One extension error: LB/LH zero-extend instead of sign-extend (or LBU/LHU sign-extend), OR sub-word access not little-endian.                                                                                                                                                               | -1.0      |
| B4   | Load/store not sized by the memop width (always 32-bit), OR STORE uses the wrong value/address, OR LOAD does not overwrite `result` for the writeback stage.                                                                                                                                | -2.0      |
| B5   | Load/store data not moved to/from memory in a usable way.                                                                                                                                                                                                                                   | -3.0      |

I believe I have earned full credit for this category.

## Category C — Cache Hierarchy and Lookup (3.0 pts)

Performs the L1 → L2 → L3 lookup path against the configured levels
(`-cL`), indexing by the per-level block size, and determines hit/miss
correctly.

| Step | Criterion                                                                                                                                                                                                                                                                                    | Deduction |
|------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| C1   | Access probes L1, then L2, then L3 on a miss, then main memory; each level indexed/tagged by its own block size (`-cbs`, a power of two); `-cL` honored (`0` = no cache, `1`/`2`/`3` = levels present); a lower-level hit fills the higher levels; hit/miss determined by a valid tag match. | -0.0      |
| C2   | Lookup works, but a minor issue: a lower-level hit does not refill the higher level(s) (correct data, wrong locality), OR `-cL 0` path still routes through a cache structure that always misses rather than bypassing.                                                                      | -0.5      |
| C3   | One level mis-indexed (wrong block-offset/index split for its `-cbs`), OR the hierarchy is probed in the wrong order so a stale higher-level copy can shadow a fresher lower-level one.                                                                                                      | -1.5      |
| C4   | Only a single flat cache implemented regardless of `-cL`, OR hit/miss determined by something other than a valid tag match.                                                                                                                                                                  | -2.25     |
| C5   | No usable cache lookup; every access goes straight to memory (with caches configured on).                                                                                                                                                                                                    | -3.0      |

I believe I have earned full credit for this category.

## Category D — Associativity and Eviction Policies (2.5 pts)

Implements the per-level cache type (`dm`/`sa`/`fa`) and the eviction
policy (`fifo`/`lru`/`lfu`) for choosing a victim within a set.

| Step | Criterion                                                                                                                                                                                                                                                                                     | Deduction |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| D1   | Direct-mapped, set-associative, and fully-associative mappings all correct per level; on a miss into a full set the victim is chosen by the level’s configured policy (FIFO/LRU/LFU); the chosen policy’s bookkeeping (insertion order / recency / frequency) is updated on the right events. | -0.0      |
| D2   | Associativity correct, but a minor issue: one policy’s bookkeeping updated on the wrong event (e.g., LRU updated only on miss, not on hit), producing occasional wrong victims.                                                                                                               | -0.5      |
| D3   | One eviction policy systematically wrong (e.g., LFU behaves as FIFO), OR set-associative/fully-associative collapses to a single way.                                                                                                                                                         | -1.25     |
| D4   | Associativity not honored (all levels treated as direct-mapped regardless of `dm`/`sa`/`fa`), OR victim chosen arbitrarily rather than by the configured policy.                                                                                                                              | -1.75     |
| D5   | No meaningful associativity or eviction; entries overwritten without a set/policy model.                                                                                                                                                                                                      | -2.5      |

I believe I have earned full credit for this category.

## Category E — Write Policies (2.0 pts)

Implements write-through vs. write-back per level, with dirty-bit
tracking and writeback-on-eviction.

| Step | Criterion                                                                                                                                                                                                                                                      | Deduction |
|------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| E1   | `wt` writes propagate to the next level (and ultimately memory) on every store; `wb` marks the line dirty and defers the write, flushing to the next level only on eviction of a dirty line; dirty bits tracked per line; the `-cw` setting honored per level. | -0.0      |
| E2   | Policy correct, but a minor issue: a dirty line is flushed on eviction even when clean (redundant writes, correct data), OR write-allocate/no-write-allocate choice inconsistent but not data-corrupting.                                                      | -0.5      |
| E3   | Write-back does not flush dirty lines on eviction (silently loses stores), OR write-through does not propagate past the first level.                                                                                                                           | -1.0      |
| E4   | Write policy not distinguished (`wt` and `wb` behave identically), OR dirty state not tracked at all.                                                                                                                                                          | -2.0      |

I believe I have earned full credit for this category.

## Category F — Cache Coherency (MESI) (3.5 pts)

Maintains coherence across HARTs via the configured protocol (`snoop` =
MESI), with a serialized bus/snoop point.

| Step | Criterion                                                                                                                                                                                                                                                                                                                                                                                                                                          | Deduction |
|------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| F1   | Lines carry Modified/Exclusive/Shared/Invalid state; a read miss that finds the line in another cache lands in Shared (with the holder demoted from M/E as required, writing back if Modified); a write/RFO invalidates other copies and moves the writer to Modified; snoops serialize on a single bus point so at most one transaction is in flight; `-cc none` disables coherency and `-cc dir` uses a directory instead of a global snoop bus. | -0.0      |
| F2   | Transitions correct, but a minor issue: Exclusive not distinguished from Shared (a clean exclusive line always treated as Shared, causing extra bus traffic but no incorrect data), OR write-back on an M→S downgrade issued redundantly.                                                                                                                                                                                                          | -0.75     |
| F3   | One transition class wrong: a write does not invalidate other sharers (stale reads possible on another HART), OR an M line is not written back when another HART reads it.                                                                                                                                                                                                                                                                         | -1.75     |
| F4   | Snoops not serialized (no single bus/serialization point), so concurrent transactions can race, OR two or more state transitions systematically wrong.                                                                                                                                                                                                                                                                                             | -2.5      |
| F5   | No coherency protocol; per-HART caches can hold conflicting values with no reconciliation.                                                                                                                                                                                                                                                                                                                                                         | -3.5      |

I believe I have earned full credit for this category.

## Category G — MMU and TLB (SV32) (3.5 pts)

Translates virtual to physical addresses via an SV32 two-level
page-table walk, gated by `satp`, with a TLB cache of translations.

| Step | Criterion                                                                                                                                                                                                                                                                                                                                                          | Deduction |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| G1   | When `satp.MODE = 1` (SV32) performs a two-level page-table walk (10-bit VPN\[1\]/VPN\[0\], 12-bit page offset), assembling the physical address from the leaf PTE’s PPN plus offset; TLB probed first and filled on a miss; `satp.MODE = 0` (BARE) and `-mmu off` bypass translation entirely (physical == virtual); `-tlb 0` disables the TLB (walk every time). | -0.0      |
| G2   | Translation correct, but a minor issue: TLB not invalidated when `satp` changes (stale mappings survive a page-table switch), OR a superpage/megapage leaf at level 1 not handled while 4 KiB pages work.                                                                                                                                                          | -0.75     |
| G3   | One walk error: VPN split or PTE field offsets slightly wrong so a class of addresses mistranslates, OR the TLB returns a hit without checking validity.                                                                                                                                                                                                           | -1.75     |
| G4   | Page-table walk not two-level (single-level or wrong stride), OR `-mmu off`/BARE mode does not bypass translation (translates when it must not).                                                                                                                                                                                                                   | -2.5      |
| G5   | No usable address translation; virtual addresses used directly even in SV32 mode.                                                                                                                                                                                                                                                                                  | -3.5      |

I believe I have earned full credit for this category.

## Category H — Structural Stalls and Multi-Cycle Memory (2.0 pts)

Stalls the pipeline when memory cannot complete in one tick (cache miss,
lower-level fetch, lock contention) instead of advancing with stale
data.

| Step | Criterion                                                                                                                                                                                                                                                                                                                           | Deduction |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| H1   | When an access cannot complete in the current tick (miss servicing, page walk, contended lock), the memory stage stalls: the front end is frozen and a bubble is injected downstream so the rest of the pipeline drains, and the stage resumes with the real data once it arrives; no stage advances with data it has not received. | -0.0      |
| H2   | Stalls correctly, but a minor issue: over-stalls (holds more cycles than needed, correct but slow), OR the bubble is injected into the wrong latch while data stays correct.                                                                                                                                                        | -0.5      |
| H3   | Multi-cycle memory modeled but the front end is not frozen correctly, so an instruction is dropped or duplicated during the stall.                                                                                                                                                                                                  | -1.0      |
| H4   | Memory treated as always single-cycle (no stall on miss/contention), so the pipeline advances with data that has not actually arrived.                                                                                                                                                                                              | -2.0      |

I believe I have earned full credit for this category.

## Score Summary

| Category                                | Max      | Earned |
|-----------------------------------------|----------|----------|
| A — Build and Structure                 | 0.5      | 0.5      |
| B — Load/Store & Sub-Word Extension     | 3.0      | 3.0      |
| C — Cache Hierarchy & Lookup            | 3.0      | 3.0      |
| D — Associativity & Eviction            | 2.5      | 2.5      |
| E — Write Policies                      | 2.0      | 2.0      |
| F — Cache Coherency (MESI)              | 3.5      | 3.5      |
| G — MMU & TLB (SV32)                    | 3.5      | 3.5      |
| H — Structural Stalls & Multi-Cycle Mem | 2.0      | 2.0      |
| **Total**                               | **20.0** | **20.0** |
