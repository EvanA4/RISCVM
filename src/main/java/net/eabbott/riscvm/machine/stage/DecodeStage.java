package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.instruction.ALUOp;
import net.eabbott.riscvm.machine.instruction.AMOOp;
import net.eabbott.riscvm.machine.instruction.Instruction;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.VMLogger;

/*
* The pipeline decode stage.
* */
public class DecodeStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister prevReg, nextReg;
    private final boolean allowM;
    private final boolean allowA;

    public DecodeStage(Hart hart) {
        this.hart = hart;
        this.prevReg = this.hart.pipelineRegisters[0];
        this.nextReg = this.hart.pipelineRegisters[1];

        VirtualMachine machine = VirtualMachine.getInstance();
        this.allowM = machine.context.allowRV32M;
        this.allowA = machine.context.allowRV32A;
    }

    public void rising() {
        if (this.hart.status != HartStatus.RUNNING) {
            this.needToNOP = true;
        }

        logger.log("Running rising edge for decode stage for hart #%d", this.hart.id);

        // Fetch raw instruction bytes from pipeline register
        byte[] instBytes = this.prevReg.getInst();
        if (instBytes.length == 0) {
            logger.log("No instruction register yet for hart #%d.", this.hart.id);
            return;
        }
        this.nextReg.setPC(this.prevReg.getPC());
        this.nextReg.setInst(this.prevReg.getInst());
        this.nextReg.setPredictedNextPc(this.prevReg.getPredictedNextPc());

        Instruction instruction = new Instruction(instBytes);

        // Fetch opcode from raw instruction bytes
        Opcode opcode = instruction.opcode();
        if (opcode == null) {
            logger.log("Found illegal instruction opcode for hart #%d.", this.hart.id);
            onIllegalInstruction();
            return;
        }

        // Generic processing
        int rd = instruction.rd();
        int funct3 = instruction.funct3();
        int rs1 = instruction.rs1();
        int rs2 = instruction.rs2();

        // Case-by-case processing
        switch (opcode) {
            case LOAD -> {
                logger.log("Processing type I: %s", opcode);

                int imm = instruction.imm();
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(imm);
                this.nextReg.setRd((byte) rd);
                this.nextReg.setDisp(imm);
                this.nextReg.setALUOp(ALUOp.Add.toByte());

                // Parse memop
                switch (funct3) {
                    case 0b000, 0b100 -> this.nextReg.setMemop((byte) 0b001); // Byte/LBU
                    case 0b001, 0b101 -> this.nextReg.setMemop((byte) 0b010); // Half-word/LHU
                    case 0b010 -> this.nextReg.setMemop((byte) 0b011); // Word
                    default -> {
                        onIllegalInstruction();
                        return;
                    }
                }
            }
            case SYSTEM -> {
                logger.log("Processing type I: %s", opcode);

                int imm = instruction.imm();

                if (funct3 == 0b000) {
                    // ECALL
                    if (instruction.isECALL()) {
                        this.nextReg.setRd((byte) 0);
                        this.nextReg.setMemop((byte) 0);
                        this.nextReg.setALUOp(ALUOp.None.toByte());
                        break;
                    }

                    // SFENCE.VMA
                    if (instruction.isSFENCE()) {
                        this.nextReg.setLeft(this.hart.registers.read(rs1));
                        this.nextReg.setRight(this.hart.registers.read(rs2));
                        this.nextReg.setRd((byte) 0);
                        this.nextReg.setMemop((byte) 0);
                        this.nextReg.setALUOp(ALUOp.None.toByte());
                        break;
                    }

                    onIllegalInstruction();
                    return;
                }

                // CSR instructions
                if (instruction.csrOp() != null) {
                    this.nextReg.setLeft(this.hart.registers.read(rs1));
                    this.nextReg.setRight(imm);
                    this.nextReg.setRd((byte) rd);
                    this.nextReg.setDisp(imm);
                    this.nextReg.setMemop((byte) 0);
                    this.nextReg.setALUOp(ALUOp.None.toByte());
                } else {
                    onIllegalInstruction();
                    return;
                }
            }
            case JALR -> {
                logger.log("Processing type I: %s", opcode);
                if (funct3 != 0) {
                    onIllegalInstruction();
                    return;
                }

                int imm = instruction.imm();
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(imm);
                this.nextReg.setDisp(imm);
                this.nextReg.setRd((byte) rd);
                this.nextReg.setALUOp(ALUOp.Add.toByte());
            }
            case OP_IMM -> {
                logger.log("Processing type I: %s", opcode);
                int imm = instruction.imm();
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(imm);
                this.nextReg.setRd((byte) rd);

                ALUOp parsed = instruction.aluOp();
                if (parsed == ALUOp.None) {
                    onIllegalInstruction();
                    return;
                }

                this.nextReg.setALUOp(parsed.toByte());
            }
            case STORE -> {
                logger.log("Processing type S: %s", opcode);
                int imm = instruction.imm();
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(imm);
                this.nextReg.setStrval(this.hart.registers.read(rs2));
                this.nextReg.setALUOp(ALUOp.Add.toByte());
                switch (funct3) {
                    case (0b000) -> this.nextReg.setMemop((byte) 0b101);
                    case (0b001) -> this.nextReg.setMemop((byte) 0b110);
                    case (0b010) -> this.nextReg.setMemop((byte) 0b111);
                    default -> {
                        onIllegalInstruction();
                        return;
                    }
                }
            }
            case BRANCH -> {
                logger.log("Processing type B: %s", opcode);
                int imm = instruction.imm();
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(this.hart.registers.read(rs2));
                this.nextReg.setDisp(imm);
                this.nextReg.setALUOp(ALUOp.Sub.toByte());
            }
            case JAL -> {
                logger.log("Processing type J: %s", opcode);
                int imm = instruction.imm();
                this.nextReg.setDisp(imm);
                this.nextReg.setALUOp(ALUOp.None.toByte());
                this.nextReg.setRd((byte) rd);
            }
            case OP -> {
                logger.log("Processing type R: %s", opcode);
                ALUOp op = instruction.aluOp();
                if (op == ALUOp.None || (ALUOp.isM(op) && !this.allowM)) {
                    onIllegalInstruction();
                    return;
                }
                this.nextReg.setLeft(this.hart.registers.read(rs1));
                this.nextReg.setRight(this.hart.registers.read(rs2));
                this.nextReg.setALUOp(op.toByte());
                this.nextReg.setRd((byte) rd);
            }
            case AUIPC, LUI -> {
                logger.log("Processing type U: %s", opcode);
                int imm = instruction.imm();
                this.nextReg.setLeft(opcode == Opcode.LUI ? prevReg.getPC() : 0);
                this.nextReg.setRight(imm);
                this.nextReg.setALUOp(ALUOp.Add.toByte());
                this.nextReg.setRd((byte) rd);
            }
            case AMO -> {
                logger.log("Processing type A: %s", opcode);
                if (!this.allowA) {
                    onIllegalInstruction();
                    return;
                }

                int rl = instruction.rl();
                int aq = instruction.aq();
                AMOOp amoOp = instruction.amoOp();
                if (amoOp == AMOOp.INVALID) {
                    onIllegalInstruction();
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

        // Check for hazards
        int left = instruction.leftR();
        int right = instruction.rightR();
        int strval = instruction.strvalR();

        // Check for load-use hazard
        boolean hasLoadUseHazard = false;
        Instruction execInst = new Instruction(this.hart.pipelineRegisters[1].getInst());
        int execRd = execInst.rd();
        if (execInst.opcode() == Opcode.LOAD && execRd != 0) {
            hasLoadUseHazard = execRd == left
                || execRd == right
                || execRd == strval;
        }

        // Stall if load-use hazard
        if (hasLoadUseHazard) {
            this.hart.stages[0].needToFreeze = true;
            this.needToNOP = true;
        }

        // If one of current instruction's registers is number 10, stall if ECALL in pipeline
        if (
            instruction.leftR() == 10
                || instruction.rightR() == 10
                || instruction.strvalR() == 10
        ) {
            for (int i = 1; i <= 3; ++i) {
                Instruction current = new Instruction(this.hart.pipelineRegisters[i].getInst());
                if (current.isECALL()) {
                    this.hart.stages[0].needToFreeze = true;
                    this.needToNOP = true;
                }
            }
        }
    }

    public void falling() {
        logger.log("Running falling edge for decode stage for hart #%d", this.hart.id);
        
        if (this.needToNOP) {
            this.hart.pipelineRegisters[1].setNextToNop();
            this.needToNOP = false;
        }
        
        if (this.needToFreeze) {
            this.needToFreeze = false;
        } else {
            this.hart.pipelineRegisters[1].tick();
        }
    }
    
    private void onIllegalInstruction() {
        VirtualMachine.getInstance().parkHart(this.hart.id, HartStatus.ILLEGAL_INSTRUCTION);
    }
}