> For the following defense, assume all relative directories are from `PROJECT_ROOT/src/main/java/net/eabbott/riscvm`, unless specified otherwise.

## Category A — Build and Structure (0.5 pts)

Does the Part V code compile and is the execute stage factored into its own source file(s) as required?

| Step | Criterion | Deduction |
| --- | --- | --- |
| A1 | Compiles cleanly, execute stage in its own file, no external dependencies. | -0.0 |
| A2 | Compiles but emits warnings that indicate real problems (e.g., truncation, signedness on the ALU operands, undefined shift amounts). | -0.25 |
| A3 | Compiles only after trivial fixes (missing include/import, one typo), OR execute is not separated into its own file. | -0.5 |
| A4 | Does not compile; requires nontrivial edits to build. | -0.5 (and verify remaining categories by code reading only) |

I believe I have earned full marks for this category. 

## Category B — Core ALU Operations (5.0 pts)

Takes the `aluop` field and computes `result` for every supported operation, with correct signed/unsigned behavior.

| Step | Criterion | Deduction |
| --- | --- | --- |
| B1 | Every `AluOp` computes the correct 32-bit `result`: Add/Sub/Mul; the signed/unsigned splits (Div/DivU, Rem/RemU) with defined behavior on divide-by-zero and signed overflow; the shift splits (LeftShift, RightShiftA arithmetic, RightShiftL logical) with the shift amount masked to 5 bits; Or/Xor/And; Slt/SltU. Reads the `left`/`right` inputs populated by decode. | -0.0 |
| B2 | Mostly correct, but a minor issue: one signed/unsigned pair swapped (e.g., SRA computed as SRL, or DIVU as DIV), OR shift amount not masked to 5 bits (undefined behavior for shifts ≥ 32). | -1.5 |
| B3 | Divide-by-zero or signed-overflow (`INT_MIN / -1`) not handled to the RISC-V defined result, OR one arithmetic group (e.g., all shifts, or Mul) systematically wrong. | -2.5 |
| B4 | Two or more operation groups wrong, OR the ALU inspects the instruction/opcode instead of dispatching purely on `aluop`. | -3.5 |
| B5 | ALU does not meaningfully compute results from `aluop`. | -4.5 |

## Category C — NZCV Flags and Comparison/Branch Conditions (4.0 pts)

Derives the NZCV status flags from the ALU result and produces the correct comparison bits for SLT/SLTU and the six branch conditions (via the SUB op).

| Step | Criterion | Deduction |
| --- | --- | --- |
| C1 | N/Z/C/V computed from a single wide (≥33-bit) add, with Sub as `a + ~b + 1`; carry convention correct (`C == 1` means no borrow, i.e., `a ≥ b` unsigned); SLT/SLTU produce the correct set result; all six branch conditions (EQ/NE via Z, LT/GE via N≠V, LTU/GEU via C) resolve correctly, including at the `0xFFFFFFFF` unsigned boundary. | -0.0 |
| C2 | Flags and conditions correct, but a minor issue: SLT/SLTU result placed in the wrong bit, OR one flag computed correctly but by an ad-hoc path rather than the shared wide-add (fragile but works on the test set). | -1.5 |
| C3 | The carry/borrow convention is inverted, so **all** unsigned comparisons (BLTU/BGEU/SLTU) are backwards, OR the signed overflow flag (V) is wrong so signed branches (BLT/BGE) fail on overflow cases. | -2.5 |
| C4 | Branch/comparison conditions not derived from the SUB result/flags (e.g., separate hand-written comparisons that disagree with the flags), OR two or more conditions systematically wrong. | -3.0 |
| C5 | NZCV flags and comparison conditions not meaningfully produced. | -3.5 |

## Category D — Load/Store Address Calculation (3.0 pts)

For LOAD and STORE, the ALU computes base + offset via ADD, independent of the instruction, and hands the address forward in `result`.

| Step | Criterion | Deduction |
| --- | --- | --- |
| D1 | LOAD/STORE force the ALU to ADD (`left` = base register, `right` = sign-extended offset); the ALU does **not** inspect the instruction to decide this; the computed address lands in `result` for the memory stage; `strval` is left intact for stores. | -0.0 |
| D2 | Address computed correctly, but the execute stage inspects the opcode/instruction to special-case load/store instead of relying on the `aluop` set by decode. | -1.0 |
| D3 | One error: offset not sign-extended (works only for positive offsets), OR base/offset swapped in a way the test set tolerates. | -1.5 |
| D4 | Address not computed by an ADD (wrong operation), OR not placed in `result` where the memory stage can consume it. | -2.5 |

## Category E — Operand Forwarding (3.5 pts)

Resolves read-after-write data hazards by forwarding the most recent in-flight result into the ALU inputs before executing.

| Step | Criterion | Deduction |
| --- | --- | --- |
| E1 |  | -0.0 |
| E2 | Forwarding works, but a minor issue: priority between the two forward sources not enforced (stale value can win when both match), OR x0 not excluded so a spurious forward from `rd == 0` can occur. | -0.75 |
| E3 | Only one forwarding path implemented (EX/MEM **or** MEM/WB but not both), so a class of two-instruction-distance hazards reads stale register values. | -1.5 |
| E4 |  | -2.25 |
| E5 | No operand forwarding; dependent instructions consume stale register-file values. | -3.0 |

## Category F — Stalling and the Load-Use Hazard (2.0 pts)

Detects the load-use hazard (which forwarding cannot cover) and stalls exactly one cycle, injecting a bubble and freezing the front end.

| Step | Criterion | Deduction |
| --- | --- | --- |
| F1 | Detects when the instruction in execute is a LOAD whose `rd` matches an `rs1`/`rs2` of the instruction in decode; stalls exactly one cycle (freeze PC and the fetch/decode latch, inject one bubble); then forwards from the MEM/WB latch on the following cycle so the load value is delivered. | -0.0 |
| F2 | Load-use detected and stalled, but a minor issue: stalls more than one cycle (over-stall, correct but slow), OR the bubble is injected into the wrong latch while the result stays correct. | -0.5 |
| F3 | Stall mechanism present but the front end is not frozen correctly (PC advances or the decode latch is overwritten), so an instruction is dropped or duplicated on the stall. | -1.0 |
| F4 | Load-use hazard not detected (relies on forwarding alone, which cannot deliver the not-yet-loaded value), so the consumer reads a stale/undefined value. | -1.5 |
| F5 | No stalling mechanism of any kind. | -2.0 |

## Category G — Branch Resolution in Execute (2.0 pts)

Resolves branches/jumps in the execute stage, compares against the fetch-stage prediction, and squashes + redirects on a misprediction.

| Step | Criterion | Deduction |
| --- | --- | --- |
| G1 | Computes taken/not-taken (from the NZCV flags for branches, forced taken for JAL/JALR) and the actual target; compares both against the fetch prediction; on a misprediction squashes the instructions in IF and ID (bubbles), redirects the PC to the correct target, and updates the branch predictor with the real outcome; correct prediction incurs no bubble. | -0.0 |
| G2 | Resolution correct, but a minor issue: predictor not updated with the real outcome, OR JAL/JALR handled by a separate path that computes the right target but bypasses the misprediction check. | -0.5 |
| G3 | Misprediction detected but recovery is incomplete: only one of IF/ID squashed (a speculative instruction leaks through), OR the PC redirect is off (e.g., redirects to target vs. fall-through incorrectly in one direction). | -1.0 |
| G4 | Branch outcome computed but not compared against the prediction (no squash/redirect), so mispredicted paths execute wrong instructions, OR resolution uses stale (non-forwarded) operands for the condition. | -1.5 |
| G5 | Branches not resolved in execute in any usable way. | -2.0 |

## Score Summary

| Category | Max | Earned |
| --- | --- | --- |
| A — Build and Structure | 0.5 | 0.5 |
| B — Core ALU Operations | 5.0 | 5.0 |
| C — NZCV Flags & Comparison/Branch Conds | 4.0 | 4.0 |
| D — Load/Store Address Calculation | 3.0 | 3.0 |
| E — Operand Forwarding | 3.5 | 3.5 |
| F — Stalling & the Load-Use Hazard | 2.0 | 2.0 |
| G — Branch Resolution in Execute | 2.0 | 2.0 |
| **Total** | **20.0** | **20.0** |

---

*Last updated: 2026-10-27 14:37:36 -0400*
