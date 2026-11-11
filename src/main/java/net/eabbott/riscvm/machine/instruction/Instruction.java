package net.eabbott.riscvm.machine.instruction;

import net.eabbott.riscvm.util.BinaryUtil;

public class Instruction {
    private byte[] inst;

    public Instruction(byte[] inst) {
        this.inst = inst;
    }

    public Opcode opcode() {
        int opcodeNum = BinaryUtil.intFromBits(this.inst, 0, 7, false);
        return Opcode.fromInt(opcodeNum);
    }

    /**
     * Fetches the immediate for the provided instruction. Should not be called on an OP/AMO.
     */
    public int imm() {
        return switch (this.opcode()) {
            case LOAD, SYSTEM, JALR, OP_IMM -> BinaryUtil.intFromBits(this.inst, 20, 12, true);
            case STORE -> {
                int imm = BinaryUtil.intFromBits(this.inst, 7, 5, false);
                imm |= BinaryUtil.intFromBits(this.inst, 25, 7, false) << 5;
                yield BinaryUtil.sext(imm, 11);
            }
            case BRANCH -> {
                int imm = BinaryUtil.intFromBits(this.inst, 8, 4, false) << 1;
                imm |= BinaryUtil.intFromBits(this.inst, 25, 6, false) << 5;
                imm |= BinaryUtil.intFromBits(this.inst, 7, 1, false) << 11;
                imm |= BinaryUtil.intFromBits(this.inst, 31, 1, false) << 12;
                yield BinaryUtil.sext(imm, 12);
            }
            case JAL -> {
                int imm = BinaryUtil.intFromBits(this.inst, 21, 10, false) << 1;
                imm |= BinaryUtil.intFromBits(this.inst, 20, 1, false) << 11;
                imm |= BinaryUtil.intFromBits(this.inst, 12, 8, false) << 12;
                imm |= BinaryUtil.intFromBits(this.inst, 31, 1, false) << 20;
                yield BinaryUtil.sext(imm, 20);
            }
            case OP, AMO -> throw new RuntimeException("Tried to extract immediate for OP/AMO.");
            case AUIPC, LUI -> BinaryUtil.intFromBits(this.inst, 12, 20, false) << 12;
        };
    }

    public int rd() {
        return BinaryUtil.intFromBits(this.inst, 7, 5, false);
    }

    public int funct3() {
        return BinaryUtil.intFromBits(this.inst, 12, 3, false);
    }

    public int rs1() {
        return BinaryUtil.intFromBits(this.inst, 15, 5, false);
    }

    public int rs2() {
        return BinaryUtil.intFromBits(this.inst, 20, 5, false);
    }

    public int funct7() {
        return BinaryUtil.intFromBits(this.inst, 25, 7, false);
    }

    private ALUOp aluOpR() {
        int funct3 = this.funct3();
        int funct7 = this.funct7();

        // M extension
        if (funct7 == 0x01) {
            return switch (funct3) {
                case 0b000 -> ALUOp.Mul;
                case 0b001 -> ALUOp.MulH;
                case 0b010 -> ALUOp.MulHSU;
                case 0b011 -> ALUOp.MulHU;
                case 0b100 -> ALUOp.Div;
                case 0b101 -> ALUOp.DivU;
                case 0b110 -> ALUOp.Rem;
                case 0b111 -> ALUOp.RemU;
                default -> ALUOp.None;
            };
        }

        // Base instructions
        return switch (funct3) {
            case 0b000 -> switch (funct7) {
                case 0x00 -> ALUOp.Add;
                case 0x20 -> ALUOp.Sub;
                default -> ALUOp.None;
            };
            case 0b001 -> funct7 == 0 ? ALUOp.LeftShift : ALUOp.None;
            case 0b010 -> funct7 == 0 ? ALUOp.SLT : ALUOp.None;
            case 0b011 -> funct7 == 0 ? ALUOp.SLTU : ALUOp.None;
            case 0b100 -> funct7 == 0 ? ALUOp.Xor : ALUOp.None;
            case 0b101 -> switch (funct7) {
                case 0x00 -> ALUOp.RightShiftL;
                case 0x20 -> ALUOp.RightShiftA;
                default -> ALUOp.None;
            };
            case 0b110 -> funct7 == 0 ? ALUOp.Or : ALUOp.None;
            case 0b111 -> funct7 == 0 ? ALUOp.And : ALUOp.None;
            default -> ALUOp.None;
        };
    }

    private ALUOp aluOpI() {
        int imm = this.imm();
        int funct3 = this.funct3();
        ALUOp parsed = switch (funct3) {
            case 0b000 -> ALUOp.Add;
            case 0b001 -> ALUOp.LeftShift;
            case 0b010 -> ALUOp.SLT;
            case 0b011 -> ALUOp.SLTU;
            case 0b100 -> ALUOp.Xor;
            case 0b101 -> ALUOp.RightShiftL;
            case 0b110 -> ALUOp.Or;
            case 0b111 -> ALUOp.And;
            default -> ALUOp.None;
        };
        if (parsed == ALUOp.RightShiftL) {
            if ((imm >> 5) == 0b0100000) parsed = ALUOp.RightShiftA;
            else if ((imm >> 5) != 0) parsed = ALUOp.None;
        }
        return parsed;
    }

    public ALUOp aluOp() {
        Opcode opcode = this.opcode();
        if (opcode == Opcode.OP_IMM) return this.aluOpI();
        else if (opcode == Opcode.OP) return this.aluOpR();
        else throw new RuntimeException("Tried to get ALUOp from non-OP_IMM/OP.");
    }

    public boolean isECALL() {
        return this.inst[0] == 0x73
            && this.inst[1] == 0x00
            && this.inst[2] == 0x00
            && this.inst[3] == 0x00;
    }

    public boolean isSFENCE() {
        Opcode opcode = this.opcode();
        int funct3 = this.funct3();
        int funct7 = this.funct7();
        int rd = this.rd();
        return opcode == Opcode.SYSTEM
            && funct3 == 0
            && funct7 == 0b0001001
            && rd == 0;
    }

    public CSROp csrOp() {
        Opcode opcode = this.opcode();
        int funct3 = this.funct3();
        if (opcode != Opcode.SYSTEM)
            throw new RuntimeException("Tried to access CSROp from non-SYSTEM.");
        return CSROp.fromValue(funct3);
    }

    public int rl() {
        Opcode opcode = this.opcode();
        if (opcode != Opcode.AMO)
            throw new RuntimeException("Tried to access rl from non-AMO.");
        return BinaryUtil.intFromBits(this.inst, 25, 1, false);
    }

    public int aq() {
        Opcode opcode = this.opcode();
        if (opcode != Opcode.AMO)
            throw new RuntimeException("Tried to access aq from non-AMO.");
        return BinaryUtil.intFromBits(this.inst, 26, 1, false);
    }

    public int funct5() {
        Opcode opcode = this.opcode();
        if (opcode != Opcode.AMO)
            throw new RuntimeException("Tried to access funct5 from non-AMO.");
        return BinaryUtil.intFromBits(this.inst, 27, 5, false);
    }

    public AMOOp amoOp() {
        Opcode opcode = this.opcode();
        if (opcode != Opcode.AMO)
            throw new RuntimeException("Tried to access amoOp from non-AMO.");
        int rs2 = this.rs2();
        int funct5 = this.funct5();
        return switch (funct5) {
            case 0b00010 -> rs2 == 0 ? AMOOp.LR : AMOOp.INVALID;
            case 0b00011 -> AMOOp.SC;
            case 0b00001 -> AMOOp.SWAP;
            case 0b00000 -> AMOOp.ADD;
            case 0b00100 -> AMOOp.XOR;
            case 0b01100 -> AMOOp.AND;
            case 0b01000 -> AMOOp.OR;
            case 0b10000 -> AMOOp.MIN;
            case 0b10100 -> AMOOp.MAX;
            case 0b11000 -> AMOOp.MINU;
            case 0b11100 -> AMOOp.MAXU;
            default -> AMOOp.INVALID;
        };
    }

    public boolean isEmpty() {
        return this.inst.length == 0;
    }

    /**
     * Determines the register number for "left" or returns -1.
     */
    public int leftR() {
        int rs1 = this.rs1();
        Opcode opcode = this.opcode();
        int funct3 = this.funct3();
        return switch (opcode) {
            case LOAD -> switch (funct3) {
                case 0b000, 0b100, 0b001, 0b101, 0b010 -> (byte) rs1;
                default -> -1;
            };
            case SYSTEM -> {
                if (funct3 == 0b000) {
                    if (this.isECALL()) yield -1;
                    if (this.isSFENCE()) yield (byte) rs1;
                    yield -1;
                }
                // CSR instructions
                if (this.csrOp() != null) yield (byte) rs1;
                else yield -1;
            }
            case JALR -> {
                if (funct3 != 0) yield -1;
                yield (byte) rs1;
            }
            case OP_IMM, OP -> {
                ALUOp parsed = this.aluOp();
                if (parsed == ALUOp.None) yield -1;
                yield (byte) rs1;
            }
            case STORE -> switch (funct3) {
                case 0b000, 0b001, 0b010 -> (byte) rs1;
                default -> -1;
            };
            case BRANCH -> (byte) rs1;
            case AMO -> {
                AMOOp amoOp = this.amoOp();
                if (amoOp == AMOOp.INVALID) yield -1;
                yield (byte) rs1;
            }
            default -> -1;
        };
    }

    /**
     * Determines the register number for "right" or returns -1.
     */
    public int rightR() {
        Opcode opcode = this.opcode();
        int funct3 = this.funct3();
        int rs2 = this.rs2();
        return switch (opcode) {
            case SYSTEM -> {
                if (funct3 == 0b000) {
                    if (this.isECALL()) yield -1;
                    if (this.isSFENCE()) yield (byte) rs2;
                }
                yield -1;
            }
            case BRANCH -> (byte) rs2;
            case OP -> {
                ALUOp op = this.aluOp();
                if (op == ALUOp.None) yield -1;
                yield (byte) rs2;
            }
            default -> -1;
        };
    }

    /**
     * Determines the register number for "strval" or returns -1.
     */
    public int strvalR() {
        Opcode opcode = this.opcode();
        int funct3 = this.funct3();
        int rs2 = this.rs2();
        return switch (opcode) {
            case STORE -> switch (funct3) {
                case 0b000, 0b001, 0b010 -> (byte) rs2;
                default -> -1;
            };
            case AMO -> {
                AMOOp amoOp = this.amoOp();
                if (amoOp == AMOOp.INVALID) yield -1;
                yield (byte) rs2;
            }
            default -> -1;
        };
    }
}
