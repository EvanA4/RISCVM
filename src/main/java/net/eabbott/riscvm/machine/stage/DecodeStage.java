package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.ALUOp;
import net.eabbott.riscvm.machine.instruction.AMOOp;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

/*
* The pipeline decode stage.
* */
public class DecodeStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister prevReg, nextReg;

    public DecodeStage(Hart hart) {
        this.hart = hart;
        this.prevReg = this.hart.pipelineRegisters[0];
        this.nextReg = this.hart.pipelineRegisters[1];
    }

    ALUOp decodeRType(int funct3, int funct7) {
        // M extension
        if (funct7 == 0x01) {
            return switch (funct3) {
                case 0b000 -> ALUOp.Mul;
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

    public void rising() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running rising edge for decode stage for hart #%d", this.hart.id);

        // Fetch raw instruction bytes from pipeline register
        byte[] instruction = this.prevReg.getInst();
        if (instruction.length == 0) {
            logger.log("No instruction register yet for hart #%d.", this.hart.id);
            return;
        }
        this.nextReg.setPC(this.prevReg.getPC());
        this.nextReg.setInst(this.prevReg.getInst());

        // Fetch opcode from raw instruction bytes
        int opcodeNum = BinaryUtil.intFromBits(instruction, 0, 7, false);
        Opcode opcode = Opcode.fromInt(opcodeNum);
        if (opcode == null) {
            logger.log("Found illegal instruction opcode for hart #%d.", this.hart.id);
            this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
            return;
        }

        // Generic processing
        int rd = BinaryUtil.intFromBits(instruction, 7, 5, false);
        int funct3 = BinaryUtil.intFromBits(instruction, 12, 3, false);
        int rs1 = BinaryUtil.intFromBits(instruction, 15, 5, false);
        int rs2 = BinaryUtil.intFromBits(instruction, 20, 5, false);
        int funct7 = BinaryUtil.intFromBits(instruction, 25, 7, false);

        // Case-by-case processing
        switch (opcode) {
            case LOAD, JALR, OP_IMM, SYSTEM -> {
                logger.log("Processing type I: %s", opcode);
                int imm = BinaryUtil.intFromBits(instruction, 20, 12, true);
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(imm);
                this.nextReg.setRd((byte) rd);
                this.nextReg.setDisp(imm);

                // Parse I-type ALUOp
                if (opcode == Opcode.LOAD) { // Force LOAD to ALUOp ADD
                    this.nextReg.setALUOp(ALUOp.Add.toByte());
                    switch (funct3) {
                        case (0b000) -> this.nextReg.setMemop((byte) 0b001);
                        case (0b001) -> this.nextReg.setMemop((byte) 0b010);
                        case (0b010) -> this.nextReg.setMemop((byte) 0b011);
                        default -> {
                            this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                            return;
                        }
                    }

                } else { // Parse others normally
                    ALUOp parsed = switch (funct3) {
                        case 0b000 -> ALUOp.Add;
                        case 0b001 -> ALUOp.LeftShift;
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
                    if (parsed == ALUOp.None) {
                        this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                        return;
                    }
                }
            }
            case STORE -> {
                logger.log("Processing type S: %s", opcode);
                int imm = BinaryUtil.intFromBits(instruction, 7, 5, false);
                imm |= BinaryUtil.intFromBits(instruction, 25, 7, false) << 5;
                imm = BinaryUtil.sext(imm, 11);
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(imm);
                this.nextReg.setStrval(this.hart.registers.read(rs2));
                this.nextReg.setALUOp(ALUOp.Add.toByte());
                switch (funct3) {
                    case (0b000) -> this.nextReg.setMemop((byte) 0b101);
                    case (0b001) -> this.nextReg.setMemop((byte) 0b110);
                    case (0b010) -> this.nextReg.setMemop((byte) 0b111);
                    default -> {
                        this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                        return;
                    }
                }
            }
            case BRANCH -> {
                logger.log("Processing type B: %s", opcode);
                int imm = BinaryUtil.intFromBits(instruction, 8, 4, false) << 1;
                imm |= BinaryUtil.intFromBits(instruction, 25, 6, false) << 5;
                imm |= BinaryUtil.intFromBits(instruction, 7, 1, false) << 11;
                imm |= BinaryUtil.intFromBits(instruction, 31, 1, false) << 12;
                imm = BinaryUtil.sext(imm, 12);
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(this.hart.registers.read(rs2));
                this.nextReg.setDisp(imm);
                this.nextReg.setALUOp(ALUOp.Sub.toByte());
            }
            case JAL -> {
                logger.log("Processing type J: %s", opcode);
                int imm = BinaryUtil.intFromBits(instruction, 21, 10, false) << 1;
                imm |= BinaryUtil.intFromBits(instruction, 20, 1, false) << 11;
                imm |= BinaryUtil.intFromBits(instruction, 12, 8, false) << 12;
                imm |= BinaryUtil.intFromBits(instruction, 31, 1, false) << 20;
                imm = BinaryUtil.sext(imm, 20);
                this.nextReg.setDisp(imm + this.prevReg.getPC());
                this.nextReg.setALUOp(ALUOp.None.toByte());
                this.nextReg.setRd((byte) rd);
            }
            case OP -> {
                logger.log("Processing type R: %s", opcode);
                ALUOp op = decodeRType(funct3, funct7);
                if (op == ALUOp.None) {
                    this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    return;
                }
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(this.hart.registers.read(rs2));
                this.nextReg.setALUOp(op.toByte());
                this.nextReg.setRd((byte) rd);
            }
            case AUIPC, LUI -> {
                logger.log("Processing type U: %s", opcode);
                int imm = BinaryUtil.intFromBits(instruction, 12, 20, false) << 12;
                this.nextReg.setLeft(imm);
                this.nextReg.setRight(opcode == Opcode.LUI ? prevReg.getPC() : 0);
                this.nextReg.setALUOp(ALUOp.None.toByte());
                this.nextReg.setRd((byte) rd);
            }
            case AMO -> {
                logger.log("Processing type A: %s", opcode);
                int rl = BinaryUtil.intFromBits(instruction, 25, 1, false);
                int aq = BinaryUtil.intFromBits(instruction, 26, 1, false);
                int funct5 = BinaryUtil.intFromBits(instruction, 27, 5, false);

                AMOOp amoOp = switch (funct5) {
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

                if (amoOp == AMOOp.INVALID) {
                    this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    return;
                }

                this.nextReg.setAMOOp(amoOp.toByte());
                this.nextReg.setAMORl((byte) rl);
                this.nextReg.setAMOAq((byte) aq);
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setStrval(this.hart.registers.read(rs2));
                this.nextReg.setRd((byte) rd);
            }
        }
    }

    public void falling() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for decode stage for hart #%d", this.hart.id);
    }
}