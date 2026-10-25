
> For the following defense, assume all relative directories are from `PROJECT_ROOT/src/main/java/net/eabbott/riscvm`, unless specified otherwise.
> 
## Category A — Build and Structure (0.5 pts)
Does the Part IV code compile, and is the decode stage factored into its own source file(s) as required?

| Step | Criterion | Deduction |
| - | - | - |
| A1 | Compiles cleanly, decode stage in its own file, no external dependencies. | -0.0 |
| A2 | Compiles but emits warnings that indicate real problems (e.g., truncation, signedness on immediate assembly, unaligned field reads). | -0.25 |
| A3 | Compiles only after trivial fixes (missing include/import, one typo), **or** decode is not separated into its own file. | -0.5 |
| A4 | Does not compile; requires nontrivial edits to build. | -0.5 (and verify remaining categories by code reading only) |

I believe I have earned full marks for this category. The program runs smoothly, with additional logging to identify the opcodes being processed by each hart. JUnit was used to perform unit tests, but the library has no effect on the virtual machine itself.

## Category B — Opcode Decode and Type Classification (1.5 pts)
Reads the 7-bit opcode and classifies each instruction into exactly one of the six types (R, I, S, B, U, J).

| Step | Criterion | Deduction |
| - | - | - |
| B1 | Extracts the 7-bit opcode (`inst[6:0]`); classifies every supported instruction into the correct single type; no opcode resolves to more than one type; unknown opcodes flagged as illegal. | -0.0 |
| B2 | Classification correct, but a minor issue: illegal/unknown opcodes not detected (silently mis-typed), **or** one category label wrong while the field layout still matches. | -0.5 |
| B3 | One opcode systematically mapped to the wrong type (e.g., JALR treated as J), so that instruction class decodes incorrectly. | -0.75 |
| B4 | Type classification driven by something other than the opcode (e.g., guessing from funct3), **or** two or more opcodes mis-typed. | -1.0 |
| B5 | No usable opcode-based type classification. | -1.5 |

I believe I have earned full marks for this category.

Most of the code for part IV is in `machine/stage/DecodeStage.java`. Lines 74-80 fetch the first seven bits of the instruction word and check for an opcode. If the returned parsed opcode is `null`, then the opcode was invalid and the hart's status is set to `HartStatus.ILLEGAL_INSTRUCTION`.

Lines 90-227 are a switch statement which map each opcode to their corresponding type. The switch statement does not allow having the same opcode map to multiple types.

## Category C — Field Extraction and Immediate Reconstruction (2.5 pts)
Extracts `rs1`, `rs2`, `rd`, `funct3`, `funct7`, and reassembles each type’s immediate from its scattered bit fields, sign-extended to 32 bits via the Part I `sign_extend`.

| Step | Criterion | Deduction |
| - | - | - |
| C1 | All register/funct fields extracted at correct positions; I/S/B/U/J immediates reassembled correctly (including the scrambled `imm[12\|10:5]`/`imm[4:1\|11]` for B and `imm[20\|10:1\|11\|19:12]` for J); each immediate sign-extended to 32 bits with `sign_extend`. | -0.0 |
| C2 | Immediates correct, but a minor issue: `shamt` for SLLI/SRLI/SRAI not masked to 5 bits, **or** one register/funct field read at a slightly wrong position that does not break the test set. | -0.5 |
| C3 | One immediate type reconstructed incorrectly (most commonly B or J bit-scrambling), **or** U-type immediate not placed in the upper 20 bits. | -1.0 |
| C4 | Immediate sign extension omitted or done by the host type (works only for positive immediates), **or** two or more immediate types reconstructed incorrectly. | -1.75 |
| C5 | Immediates and/or register fields not meaningfully extracted. | -2.5 |

I believe I have earned full marks for this category.

Most of the code for part IV is in `machine/stage/DecodeStage.java`. Lines 83-87 parse the rd ,funct3 ,rs1 ,rs2 ,and funct7 fields regardless of the type of instruction. Sign extensions for immediates can also be found several times throughout the file. The first of these is at line 93, where the I-type immediate is sign extended.

Lines 165-169 show how scrambled immediates are assembled by providing the J-type immediate as an example. Multiple ORs and shifts are used to ensure each bit segment is added at the right place. A sign extension completes the process.

## Category D — Operand Selection and Register-File Reads (2.5 pts)
Populates the `left`/`right` ALU inputs and `strval` per instruction type, reading the register file on the rising edge.

| Step | Criterion | Deduction |
| - | - | - |
| D1 | Correct operands per type: R uses rs1/rs2; I uses rs1 + immediate; S uses rs1 (base) + immediate with the store value from rs2 in `strval`; B uses rs1/rs2; register file read on the **rising** edge; `rd` captured for writing types and set to 0 for B/S. | -0.0 |
| D2 | Operands correct, but read timing is wrong (register file read on the falling edge or outside the two-phase discipline), **or** `rd` not zeroed for B/S types. | -0.5 |
| D3 | One operand-selection error: store value taken from the wrong register, **or** I-type right operand taken from a register instead of the immediate, **or** base/offset swapped for S. | -1.0 |
| D4 | Operands not routed through the `left`/`right`/`strval` structure fields (e.g., decoded into loose variables later parts cannot consume), **or** register file not read via the read/write API. | -1.75 |
| D5 | Operands not selected/populated in a usable way. | -2.5 |

I believe I have earned full marks for this category.

Most of the code for part IV is in `machine/stage/DecodeStage.java`. Lines 181-182 specify the left and right instruction register fields as rs1 and rs2 for R-type instructions. Lines 94-95 specify the left and right instruction register fields as rs1 and imm for I-type instructions. Lines 137-139 do the same thing but for S-types, as well as setting strval as rs2. Lines 158-159 also use the left/right and rs1/rs2 format mentioned for I-types.

Pretty much all the code for this part was done within the `DecodeStage.rising()` function declared at line 59. This includes the various register file readings, meaning all reads to registers happen within the rising edge of the virtual machine.

The recently refactored `machine/register/PipelineRegister.java` class implements all the instruction register fields, as well as the program counter. More interestingly, lines 47-60 of this file state that the register's write-only half of the skid buffer is zeroed out. Because of this, any instruction type which does not specify an rd value will have an rd value of 0 passed to the execute stage. Because neither B nor S-type instructions set this value, they have an rd value of 0.

The other instruction types do set this rd value, as can be seen in `machine/stage/DecodeStage.java:96` for I-type instructions.

## Category E — ALU Operation Decode (1.5 pts)
Maps opcode + funct3 (+ funct7 where required) to the correct `AluOp`.

| Step | Criterion | Deduction |
| - | - | - |
| E1 | Correct `AluOp` for all supported instructions; funct7 disambiguates the collisions (ADD/SUB, SRL/SRA, and the M-extension ADD/MUL, SRL/DIVU, etc.); LOAD/STORE forced to ADD; branches use SUB. | -0.0 |
| E2 | Mostly correct, but a minor issue: one funct7 collision resolved wrong (e.g., SRA decoded as SRL), **or** the `-m` disable path not honored (M ops decode even when RV32M is off). | -0.5 |
| E3 | funct7 not consulted where required, so a group of same-funct3 instructions all map to one op, **or** LOAD/STORE not forced to ADD. | -1.0 |
| E4 | ALU operation not meaningfully derived from the encoding. | -1.5 |

I believe I have earned full marks for this category.

Most of the code for this part is in `machine/stage/DecodeStage.java`, in the `DecodeStage.rising()` function. However, the `ALUOp` parsing function is a small exception to this. The `DecodeStage.decodeRType()` can be found from lines 26-57, and carefully maps each funct3/funct7 combination to the correct ALU operation for R-types.

For I-types, lines 100-130 in the original `rising` function parse for the correct ALU operation.

Lines 101 and 140 set the ALU operation for LOAD/STORE to ADD. Line 161 sets B-type instructions to SUB.

## Category F — Special-Case Instructions and PC-Relative Setup (1.5 pts)
Handles the encoding-specific semantics for LUI, AUIPC, JAL, and JALR.

| Step | Criterion | Deduction |
| - | - | - |
| F1 | LUI adds the upper immediate to 0; AUIPC adds the upper immediate to the **PC of the AUIPC instruction itself**; JAL is J-type and PC-relative; JALR is correctly treated as **I-type** (rs1 + immediate), not J-type. | -0.0 |
| F2 | Three of the four correct, but one minor slip: AUIPC uses the wrong PC (e.g., PC+4 instead of the instruction’s own PC), **or** LUI adds to something other than 0. | -0.5 |
| F3 | JALR mis-decoded as J-type (or its target computed PC-relative instead of rs1-relative). | -1.0 |
| F4 | Two or more of LUI/AUIPC/JAL/JALR handled incorrectly, **or** their PC-relative setup is absent. | -1.5 |

I believe I have earned full marks for this category.

Most of the code for this part is in `machine/stage/DecodeStage.java`. Line 190 sets the right operand for the ALU as 0 for LUI and the sum of the immediate and PC for AUIPC using the ternary operator.

Meanwhile, line 163 identifies JAL as the only J-type instruction. Line 170 establishes the opcode as being PC-relative.

Line 91 identifies JALR as an I-type.

## Score Summary
| Category | Max | Earned |
| - | - | - |
| A — Build and Structure | 0.5 | .5 |
| B — Opcode Decode & Type Classification | 1.5 | 1.5 |
| C — Field Extraction & Immediates | 2.5 | 2.5 |
| D — Operand Selection & Reg Reads | 2.5 | 2.5 |
| E — ALU Operation Decode | 1.5 | 1.5 |
| F — Special-Case & PC-Relative Setup | 1.5 | 1.5 |
| **Total** | **10.0** | 10.0 |
