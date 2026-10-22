package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
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
                return;
            }
        }

        switch (opcode) {
            case LOAD, JALR, OP_IMM, SYSTEM -> {
                logger.log("Processing type I: %s", opcode);
            }
            case STORE -> {
                logger.log("Processing type S: %s", opcode);
            }
            case BRANCH -> {
                logger.log("Processing type B: %s", opcode);
            }
            case JAL -> {
                logger.log("Processing type J: %s", opcode);
            }
            case OP -> {
                logger.log("Processing type R: %s", opcode);
            }
            case AUIPC, LUI -> {
                logger.log("Processing type U: %s", opcode);
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
        logger.log("Running falling edge for decode stage for hart #%d", this.hart.id);
    }
}