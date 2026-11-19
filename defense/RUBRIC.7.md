> For the following defense, assume all relative directories are from `PROJECT_ROOT/src/main/java/net/eabbott/riscvm`, unless specified otherwise.

## Category A — Build and Structure (0.5 pts)

Does the Part VII code compile, and is the writeback stage factored into its own source file(s) as required?

| Step | Criterion | Deduction |
|---|---|---:|
| A1 | Compiles cleanly, writeback stage in its own file, no external dependencies. | -0.0 |
| A2 | Compiles but emits warnings that indicate real problems (e.g., truncation, signedness on the committed value, unchecked syscall-number narrowing). | -0.25 |
| A3 | Compiles only after trivial fixes (missing include/import, one typo), **or** writeback is not separated into its own file. | -0.5 |
| A4 | Does not compile; requires nontrivial edits to build. | -0.5 (and verify remaining categories by code reading only) |

I believe I have earned full marks for this category. The project can currently run `nothing.elf` to completion in about 2.3 million cycles.

## Category B — Register Writeback and x0 Semantics (2.0 pts)

Commits `result` to the destination register `rd`, on the falling edge, with `x0` hardwired and non-writing instruction types committing nothing.

| Step | Criterion | Deduction |
|---|---|---:|
| B1 | Writes `result` to `rd` in the register file on the **falling** edge (through the Part I read/write API); writes to `x0` are discarded and reads of `x0` return 0; B/S types (`rd == 0`) commit nothing; LOAD commits the memory-stage value that was folded into `result` in Part VI. | -0.0 |
| B2 | Correct, but a minor issue: writeback issued on the wrong phase (rising edge instead of falling) while the value is still correct on the test set, **or** relies on `rd == 0` alone without going through the x0-guarded API. | -0.5 |
| B3 | One error: x0 not discarded on write (a spurious write to x0 sticks), **or** a non-writing type (B/S) writes a register because `rd` was not forced to 0. | -1.0 |
| B4 | Register commit not performed through the read/write API (raw array write), **or** the committed value is taken from the wrong field so writing instructions store the wrong result. | -1.5 |
| B5 | No usable register writeback; results are not committed to the register file. | -2.0 |

I believe I have earned full marks for this category. The `WriteStage` can be found almost entirely within `machine/stage/WriteStage.java`. Lines 38-42 take a snapshot of the pipeline register values passed to it by the memory stage. In the falling edge, lines 97-100 default the write destination and write value to whatever `rd` and `result` are. Line 103 finally commits this value.

The x0 hardwiring can be found in `machine/register/RegisterFile.java`. Line 25 is an if-statement which disregards any writes to x0.

## Category C — Syscall Interface and Dispatch (ABI) (2.0 pts)

ECALL is dispatched in the writeback stage using the RISC-V calling convention: number in `a7`, arguments in `a0`–`a6`.

| Step | Criterion | Deduction |
|---|---|---:|
| C1 | ECALL recognized in writeback; the syscall number is read from `a7` (`x17`) and arguments from `a0`–`a6` (`x10`–`x16`); the call is dispatched to the correct handler; unknown/unsupported numbers are handled gracefully (defined error, no crash); return values placed in `a0` as specified. | -0.0 |
| C2 | Dispatch correct, but a minor issue: unknown syscall numbers not handled (silently ignored or fall through) while all supported numbers work, **or** return value written somewhere other than `a0` in a way the test set tolerates. | -0.5 |
| C3 | One error: number or arguments read from the wrong register(s) (e.g., number from `a0`), **or** ECALL handled outside the writeback stage. | -1.0 |
| C4 | Dispatch keys on something other than the `a7` number, **or** two or more supported calls route to the wrong handler. | -1.5 |
| C5 | No usable syscall dispatch; ECALL does not reach a handler via the ABI. | -2.0 |

I believe I have earned full marks for this category. The write stage at `machine/stage/WriteStage.java` makes a system call (or the lack of one, even) in lines 72-81. Note that the call sometimes is `NONE`. The stage unconditionally makes a system call request so the new system call barrier is always saturated. Lines 63-67 capture how the system call type is parsed from register `a7`. Lines 76-79 pass any additional registers (`a0` to `a3`) if the system call is not `NONE`.

## Category D — Core Syscalls (exit/putchar/getchar) (2.0 pts)

Implements the four base system calls with correct behavior and I/O serialized on the shared lock.

| Step | Criterion | Deduction |
|---|---|---:|
| D1 | `exit` (0) halts using `a0` as the exit code; `putchar` writes the `a0` character to output; `getchar` reads one character from input into `a0`; `putchar`/`getchar` serialize on the shared I/O lock so characters do not interleave. | -0.0 |
| D2 | All four behave correctly, but a minor issue: I/O not serialized on the shared lock (correct single-HART output, races only under concurrency), **or** `debug` emits noise on the graded path. | -0.5 |
| D3 | One core call wrong: `putchar`/`getchar` moves the wrong width/register, **or** `exit` ignores the `a0` exit code. | -1.0 |
| D4 | `exit` does not actually terminate the machine, **or** two or more core calls are wrong/absent. | -1.5 |
| D5 | Core syscalls not meaningfully implemented. | -2.0 |

I believe I have earned full marks for this category. System calls are handled during the falling edge on the main clock thread in `machine/VirtualMachine.java` from lines 84-121. The system call request logic can be found from lines 67-79, with each request being pushed to a sorted `ConcurrentSkipListMap`. Line 86 iterates through these requests by order of ascending hart ID for processing. Line 96 prints the value stored in `a0` as a character for `putchar`.

## Category E — Multi-HART Bootstrapping (start_hart/stop_hart) (2.5 pts)

Implements `start_hart` and `stop_hart` with phase-aligned wake, the specified ABI, and the required edge-case handling.

| Step | Criterion | Deduction |
|---|---|---:|
| E1 | `start_hart` wakes a dormant HART: `a0` = target id, `a1` = entry PC, `a2` = argument; target architectural state (PC, argument in its `a0`) applied on the **falling** edge and the target begins fetching on the **next rising** edge; `stop_hart` re-parks a HART; both return 0 on success or a non-zero error in `a0`; all edge cases handled (start on running HART, id out of range, stop on dormant HART, self-stop, restart after stop). | -0.0 |
| E2 | Start/stop work, but a minor issue: the target’s state applied on the rising edge (potential race that the test set does not expose), **or** one edge case returns success instead of an error without corrupting state. | -0.5 |
| E3 | One class wrong: the wake is not phase-aligned so the started HART races its own decode (intermittent), **or** two or more edge cases mishandled (e.g., double-start not rejected, out-of-range id not caught). | -1.25 |
| E4 | `start_hart` brings a HART up but at the wrong PC/argument (wrong ABI registers), **or** `stop_hart` does not actually re-park the HART. | -1.75 |
| E5 | No usable bootstrapping; non-zero HARTs cannot be started (or cannot be stopped) via the syscalls. | -2.5 |

I believe I have earned full marks for this category. To convert what would normally be a collection of parallelized system calls into a single thread of sequential calls, another barrier is used. `machine/VMClock.java` forces the clock thread to wait for all requests to be made on line 70. After all stages have waited on the barrier using the `systemCall` function in `machine/VirtualMachine.java` from lines 67-79, the `VMClock` then attempts to `processCalls in `machine/VMClock.java:73`. Afterward, the clock awaits the barrier a second time to signal to the write stage threads that the processing has finished.

This time window allows for the clock thread to process the calls safely. Line 107 in `machine/VirtualMachine.java` invokes the `unparkHart` function for the `START_HART` system call using all the required registers. Line 110 calls `parkHart` and sets the status accordingly.

## Category F — Termination and Determinism (1.0 pts)

Machine termination and reproducible ordering of same-tick side effects.

| Step | Criterion | Deduction |
|---|---|---:|
| F1 | `exit` terminates the **whole** machine; the machine also halts when **every** HART is dormant/stopped; shutdown cleanly drains and releases all stage threads (no thread left blocked on the barrier); same-tick side effects (`putchar`/`getchar`/`start_hart`/`stop_hart`) serialized in **ascending HART id** order for reproducible output. | -0.0 |
| F2 | Termination correct, but a minor issue: same-tick side effects not serialized in a fixed HART-id order (output ordering non-deterministic across runs) while termination itself is clean. | -0.5 |
| F3 | Termination or shutdown broken: all-dormant does not halt (hang) **or** shutdown leaves a thread blocked on the barrier, **or** `exit` stops only the calling HART instead of the machine. | -1.0 |

I believe I have earned full marks for this category. Lines 92-94 in `machine/VirtualMachine.java` define how an `EXIT` system call should be handled: the machine is marked as `isExiting`, and none of the other calls are processed. Line 82 in `machine/VMClock.java` checks if the virtual machine `isExiting` and quits if so. All threads are then interrupted from lines 94-98. Lines 123-132 check to see if all harts have parked after all of the system calls. If so, `isExiting` is set to `true`.

As stated earlier, the system calls are handled in order of ascending hart ID.

## Score Summary

| Category | Max | Earned |
|---|---:|---:|
| A — Build and Structure | 0.5 |0.5 |
| B — Register Writeback & x0 Semantics | 2.0 |2.0 |
| C — Syscall Interface & Dispatch (ABI) | 2.0 |2.0 |
| D — Core Syscalls | 2.0 |2.0 |
| E — Multi-HART Bootstrapping | 2.5 |2.5 |
| F — Termination & Determinism | 1.0 |1.0 |
| **Total** | **10.0** | **10.0** |
