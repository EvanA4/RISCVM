
> For the following defense, assume all relative directories are from `PROJECT_ROOT/src/main/java/net/eabbott/riscvm`, unless specified otherwise.

## Category A — Build and Structure (0.5 pts)
Does the Part III code compile and is the fetch stage factored into its own source file(s) as required?

| Step | Criterion | Deduction |
| - | - | - |
| A1 | Compiles cleanly, fetch stage in its own file, no external dependencies. | -0.0 |
| A2 | Compiles but emits warnings that indicate real problems (e.g., truncation, signedness, unaligned reads on the instruction word). | -0.25 |
| A3 | Compiles only after trivial fixes (missing include/import, one typo), OR fetch is not separated into its own file. | -0.5 |
| A4 | Does not compile; requires nontrivial edits to build. | -0.5 (and verify remaining categories by code reading only) |

I believe I have earned a full score for this category at step A1. The fetch stage is in its own `FetchStage` class at `src/main/java/net/eabbott/riscvm/machine/stage/FetchStage.java`. However, it does rely on a few additional classes, the most notable of these being the `BranchPredictor` and `PipelineRegister` classes. It combines both the branch target buffer and the branch prediction table into a single class. The program runs smoothly.

## Category B — Instruction Fetch and Little-Endian Assembly (3.0 pts)

Reads four bytes starting at the program counter, assembles them into a 32-bit instruction word as little-endian, and advances the PC.

| Step | Criterion | Deduction |
| - | - | - |
| B1 | Reads exactly 4 bytes at the PC; assembles them little-endian into the 32-bit inst; advances PC by 4 (except where redirected by a branch/jump); starts from e_entry. | -0.0 |
| B2 | Fetch works, but a minor issue: PC advance handled awkwardly (e.g., advanced in the wrong phase) or fetch does not cleanly start from e_entry.	| -0.5 |
| B3 | Byte assembly is host-endian rather than explicitly little-endian (works only on little-endian hosts), OR the 4-byte read is not bounds-checked against RAM. | -0.75 |
| B4 | Instruction word assembled at wrong byte order/offsets so the fetched value is systematically wrong, OR PC not advanced. | -1.0 |
| B5 | Instruction not fetched from memory at the PC. | -1.5 |

I believe I have earned a full score for this category at step B1. At `src/main/java/net/eabbott/riscvm/machine/stage/FetchStage.java:32`, 4 bytes are directly read from RAM and stored in a `byte[]`. No virtual memory has been implemented yet. Line 33 of the same file reverses the bytes for little endian format and writes it to an `InstructionRegister`. Lines 28 and 29 of the `FetchStage` class use a `BranchPredictor` to check if the PC needs to jump to a new address. If so, the PC is set to the jump location stored in the branch target buffer. Otherwise, the program counter is simply incremented.

## Category C — Pipeline Instruction Structure (2.5 pts)

Defines the per-instruction structure handed stage-to-stage, with the correct field layout for later parts; only inst is populated at this stage.

| Step | Criterion | Deduction |
| - | - | - |
| C1 | Structure defined with all specified fields (inst, left, right, result, disp/strval, rd, memop, aluop) at correct sizes; inst populated by fetch; structure is what gets handed between stages. | -0.0 |
| C2 | Structure present and used, but one or two fields missing/mis-sized, OR fetch populates fields it should not yet touch. | -0.5 |
| C3 | Structure exists but is not the vehicle passed between stages (e.g., fetch writes loose variables instead), so later parts cannot build on it. | -1.0 |
| C4 | No usable pipeline instruction structure. | -2.0 |

I believe I have earned a full score for this category at step C1. Two classes span this category's requirements:
- `src/main/java/net/eabbott/riscvm/machine/register/InstructionRegister.java`
- `src/main/java/net/eabbott/riscvm/machine/register/PipelineRegister.java`

The `PipelineRegister` class represents a skid buffer between two pipeline stages. After a falling edge tick, all of these buffers are updated, so each stage has new values to read for the next rising edge. `PipelineRegister.java:19-22` define the `tick` function to update these skid buffers. The `PipelineRegister` class also contains an `InstructionRegister` property, encapsulating most of the information passed between stages. Lines `InstructionRegister.java:6-12` contain all the properties listed in the requirements. Lines 10-11 define `rd`, `aluop`, and `memop` as single-byte properties.

## Category D — Branch Predictor Table (3.5 pts)

A branch target buffer keyed on the branch instruction’s PC, storing 2-bit state per entry, with the -bdp default for a PC not present in the table.

| Step | Criterion | Deduction |
| - | - | - |
| D1 | Table keyed on branch PC with -bp rows; 2-bit state per entry; a PC not in the table predicts the -bdp default; -bp 0 disables prediction. | -0.0 |
| D2 | Predictor works, but a minor issue: -bdp default not honored (hardcoded to strongly-not-taken), OR -bp 0 not handled as "disabled." | -0.5 |
| D3 | Table present but keyed/indexed incorrectly (e.g., wrong PC bits, collisions not handled as spec requires), OR only 1 bit of state kept. | -1.5 |
| D4 | Predictor table located or updated incorrectly so predictions are not meaningfully tied to the branch PC. | -2.5 |
| D5 | No branch predictor table. | -3.5 |

I believe I have earned a full score for this category at step D1. All the requirements for this section can be found in one file:
- `src/main/java/net/eabbott/riscvm/machine/BranchPredictor.java`

Lines 17 and 19 set both the branch target buffer and the branch prediction table to arrays of the same `-bp` length. Furthermore, line 10 declares the branch prediction table as a map from a partial-PC to a byte (the smallest structure to contain the 2-bit prediction values). These 2-bit prediction values are only ever updated with bit operations--no if or switch statements--, as seen in line 56. Finally, line 20 fills the branch prediction table with the default prediction value `-bdp`.

## Category E — Shift-Register Implementation (0.5 pts)

The 2-bit state must be implemented as a shift register, not as a list of if/switch statements (an explicit graded restriction).

| Step | Criterion | Deduction |
| - | - | - |
| E1 | The 2-bit state is updated as a shift-left register (outcome shifted in); contains no if/switch ladder enumerating the four states. | -0.0 |
| E2 | Predictor produces correct state transitions, but is implemented with an if/switch state machine instead of a shift register (violates the restriction). | -0.5 |
| E3 | Neither a shift register nor a correct state machine (transitions wrong). | -0.5 |

I believe I have earned a full score for this category at step E1. All the requirements for this section can be found in one file:
- `src/main/java/net/eabbott/riscvm/machine/BranchPredictor.java`

Line 10 declares the branch prediction table as a map from a partial-PC to a byte (the smallest structure to contain the 2-bit prediction values). These 2-bit prediction values are only ever updated with bit operations--no if or switch statements--, as seen in line 56.

## Score Summary

In short, I believe I have earned a full score, as reflected in the table below:

| Category | Max | Earned |
| - | - | - |
| A — Build and Structure | 0.5 | 0.5 |
| B — Instruction Fetch & Endianness | 3.0 | 3.0 |
| C — Pipeline Instruction Structure | 2.5 | 2.5 |
| D — Branch Predictor Table | 3.5 | 3.5 |
| E — Shift-Register Implementation| 0.5 | 0.5 |
| Total | 5.0 | 5.0 |