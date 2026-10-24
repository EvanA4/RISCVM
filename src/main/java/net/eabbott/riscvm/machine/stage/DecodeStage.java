package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
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
            }
            case STORE -> {
                logger.log("Processing type S: %s", opcode);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1 = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int rs2 = BinaryUtil.intFromBits(instruction.inst, 20, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                imm |= BinaryUtil.intFromBits(instruction.inst, 25, 7, false) << 5;
            }
            case BRANCH -> {
                logger.log("Processing type B: %s", opcode);
            }
            case JAL -> {
                logger.log("Processing type J: %s", opcode);
            }
            case OP -> {
                logger.log("Processing type R: %s", opcode);
                int rd = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int funct3 = BinaryUtil.intFromBits(instruction.inst, 12, 3, false);
                int rs1 = BinaryUtil.intFromBits(instruction.inst, 15, 5, false);
                int rs2 = BinaryUtil.intFromBits(instruction.inst, 20, 5, false);
                int funct7 = BinaryUtil.intFromBits(instruction.inst, 25, 7, false);
            }
            case AUIPC, LUI -> {
                logger.log("Processing type U: %s", opcode);
                int rd = BinaryUtil.intFromBits(instruction.inst, 7, 5, false);
                int imm = BinaryUtil.intFromBits(instruction.inst, 12, 20, false) << 12;
            }
            case AMO -> {
                logger.log("Processing type A: %s", opcode);
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