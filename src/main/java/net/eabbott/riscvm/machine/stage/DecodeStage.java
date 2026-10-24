package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.ALUOp;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.instruction.InstructionRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

/*
* The pipeline decode stage.
* */
public class DecodeStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;

    public DecodeStage(Hart hart) {
        this.hart = hart;
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
                default -> null;
            };
        }

        // Base instructions
        return switch (funct3) {
            case 0b000 -> switch (funct7) {
                case 0x00 -> ALUOp.Add;
                case 0x20 -> ALUOp.Sub;
                default -> null;
            };
            case 0b001 -> funct7 == 0 ? ALUOp.LeftShift : null;
            case 0b100 -> funct7 == 0 ? ALUOp.Xor : null;
            case 0b101 -> switch (funct7) {
                case 0x00 -> ALUOp.RightShiftL;
                case 0x20 -> ALUOp.RightShiftA;
                default -> null;
            };
            case 0b110 -> funct7 == 0 ? ALUOp.Or : null;
            case 0b111 -> funct7 == 0 ? ALUOp.And : null;
            default -> null;
        };
    }

    public void rising() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running rising edge for decode stage for hart #%d", this.hart.id);

        InstructionRegister instruction = hart.pipelineRegisters[0].getInstructionRegister();
        if (instruction == null) {
            logger.log("No instruction register yet for hart #%d.", this.hart.id);
            return;
        }

        int opcodeNum = BinaryUtil.intFromBits(instruction.inst, 2, 7, false);
        Opcode opcode = Opcode.fromInt(opcodeNum & 0b11_111);
        if (opcode == null) {
            // Could be ECALL
            opcode = Opcode.fromInt(opcodeNum);

            // If not, illegal instruction
            if (opcode == null) {
                logger.log("Found illegal instruction opcode for hart #%d.", this.hart.id);
                this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                return;
            }
        }

        switch (opcode) {
            case LOAD, JALR, OP_IMM, SYSTEM -> {
                logger.log("Processing type I: %s", opcode);
                int rd = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1 = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 20, 12, true);
                instruction.left = rs1;
                instruction.right = imm;
                instruction.disp = imm;
            }
            case STORE -> {
                logger.log("Processing type S: %s", opcode);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1 = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int rs2 = BinaryUtil.intFromBits(instruction.inst, 20, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                imm |= BinaryUtil.intFromBits(instruction.inst, 25, 7, false) << 5;
                imm = BinaryUtil.sext(imm, 11);
                instruction.disp = imm;
                instruction.strval = this.hart.registers.read(rs2);
            }
            case BRANCH -> {
                logger.log("Processing type B: %s", opcode);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1 = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int rs2 = BinaryUtil.intFromBits(instruction.inst, 20, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 8, 4, false) << 1;
                imm |= BinaryUtil.intFromBits(instruction.inst, 25, 6, false) << 5;
                imm |= BinaryUtil.intFromBits(instruction.inst, 7, 1, false) << 11;
                imm |= BinaryUtil.intFromBits(instruction.inst, 31, 1, false) << 12;
                imm = BinaryUtil.sext(imm, 12);
                instruction.disp = imm;
            }
            case JAL -> {
                logger.log("Processing type J: %s", opcode);
                int rd = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 21, 10, false) << 1;
                imm |= BinaryUtil.intFromBits(instruction.inst, 20, 1, false) << 11;
                imm |= BinaryUtil.intFromBits(instruction.inst, 12, 8, false) << 12;
                imm |= BinaryUtil.intFromBits(instruction.inst, 31, 1, false) << 20;
                imm = BinaryUtil.sext(imm, 20);
                instruction.disp = imm;
            }
            case OP -> {
                logger.log("Processing type R: %s", opcode);
                int rd = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1 = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int rs2 = BinaryUtil.intFromBits(instruction.inst, 20, 5, false);
                int funct7 = BinaryUtil.intFromBits(instruction.inst, 25, 7, false);
                ALUOp op = decodeRType(funct3, funct7);
                if (op == null) {
                    this.hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    return;
                }
                instruction.left = this.hart.registers.read(rs1);
                instruction.right = this.hart.registers.read(rs2);
                instruction.aluop = op.toByte();
            }
            case AUIPC, LUI -> {
                logger.log("Processing type U: %s", opcode);
                int rd = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 12, 20, false) << 12;
                instruction.disp = imm;
            }
            case AMO -> {
                logger.log("Processing type A: %s", opcode);
                int rd    = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1   = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int rs2   = BinaryUtil.intFromBits(instruction.inst, 20, 5, false);
                int rl    = BinaryUtil.intFromBits(instruction.inst, 25, 1, false);
                int aq    = BinaryUtil.intFromBits(instruction.inst, 26, 1, false);
                int funct5 = BinaryUtil.intFromBits(instruction.inst, 27, 5, false);


            }
            case ECALL -> {
                logger.log("Processing ECALL");

            }
        }
    }

    public void falling() {
        if (this.hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for decode stage for hart #%d", this.hart.id);
    }
}