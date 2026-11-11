package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.ALU;
import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.ALUOp;
import net.eabbott.riscvm.machine.instruction.Instruction;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

public class ExecuteStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister prevReg;
    private final PipelineRegister nextReg;
    private final ALU alu = new ALU();

    public ExecuteStage(Hart hart) {
        this.hart = hart;
        this.prevReg = hart.pipelineRegisters[1];
        this.nextReg = hart.pipelineRegisters[2];
    }

    private int executeCSR(Instruction instruction, int source) {
        int funct3 = instruction.funct3();
        int csr = instruction.imm();
        int rs1 = instruction.rs1();

        int oldValue;
        try {
            oldValue = hart.csrFile.read(csr);

            int newValue = switch (funct3) {
                case 0b001, 0b101 -> source;                 // CSRRW, CSRRWI
                case 0b010, 0b110 -> oldValue | source;      // CSRRS, CSRRSI
                case 0b011, 0b111 -> oldValue & ~source;     // CSRRC, CSRRCI
                default -> throw new IllegalArgumentException("Invalid CSR operation");
            };

            boolean write = switch (funct3) {
                case 0b001, 0b101 -> true;                   // CSRRW[I] writes even if rs1/zimm is zero
                case 0b010, 0b011 -> rs1 != 0;               // CSRRS/CSRRC: suppress write if rs1=x0
                case 0b110, 0b111 -> rs1 != 0;               // CSRRSI/CSRRCI: suppress write if zimm=0
                default -> false;
            };

            if (write) {
                hart.csrFile.write(csr, newValue);
            }

            return oldValue;
        } catch (IllegalArgumentException e) {
            hart.status = HartStatus.ILLEGAL_INSTRUCTION;
            return 0;
        }
    }

    @Override
    public void rising() {
        if (this.hart.status != HartStatus.RUNNING) {
            this.needToNOP = true;
        }

        logger.log("Running rising edge for execute stage for hart #%d", hart.id);

        byte[] instBytes = prevReg.getInst();
        Instruction instruction = new Instruction(instBytes);
        if (instruction.isEmpty()) return;
        int funct3 = instruction.funct3();

        int pc = prevReg.getPC();
        int left = prevReg.getLeft();
        int right = prevReg.getRight();
        int strval = prevReg.getStrval();

        Opcode opcode = instruction.opcode();
        if (opcode == null) {
            hart.status = HartStatus.ILLEGAL_INSTRUCTION;
            return;
        }

        // Operand forwarding time :(
        // 1. Try checking memory/write
        Instruction writeInst = new Instruction(hart.pipelineRegisters[3].getInst());
        if (!writeInst.isEmpty()) {
            Opcode writeOpcode = writeInst.opcode();
            if (writeOpcode == null) throw new RuntimeException("writeOpcode is somehow null?");
            switch (writeOpcode) {
                case OP, OP_IMM, LUI, AUIPC, JAL, JALR, AMO, LOAD -> {
                    int writeLatchReg = hart.pipelineRegisters[3].getRd();
                    int writeLatchValue = hart.pipelineRegisters[3].getResult();
                    if (writeLatchReg != 0) {
                        if (writeLatchReg == instruction.leftR()) left = writeLatchValue;
                        if (writeLatchReg == instruction.rightR()) right = writeLatchValue;
                        if (writeLatchReg == instruction.strvalR()) strval = writeLatchValue;
                    }
                }
            }
        }
        // 2. Try checking execute/memory latch
        Instruction memoryInst = new Instruction(hart.pipelineRegisters[2].getInst());
        if (!memoryInst.isEmpty()) {
            Opcode memoryOpcode = memoryInst.opcode();
            if (memoryOpcode == null) throw new RuntimeException("memoryOpcode is somehow null?");
            switch (memoryOpcode) {
                case OP, OP_IMM, LUI, AUIPC, JAL, JALR -> {
                    int memoryLatchReg = Byte.toUnsignedInt(hart.pipelineRegisters[2].getRd());
                    int memoryLatchValue = hart.pipelineRegisters[2].getResult();
                    if (memoryLatchReg != 0) {
                        if (memoryLatchReg == instruction.leftR()) left = memoryLatchValue;
                        if (memoryLatchReg == instruction.rightR()) right = memoryLatchValue;
                        if (memoryLatchReg == instruction.strvalR()) strval = memoryLatchValue;
                    }
                }
            }
        }

        ALUOp op = ALUOp.fromByte(prevReg.getAluop());
        boolean isCSR =  opcode == Opcode.SYSTEM && funct3 != 0;
        int result = isCSR ? executeCSR(instruction, left) : alu.execute(op, left, right);
        int actualNextPc = pc + 4;

        if (opcode == Opcode.BRANCH) {
            // Branch comparisons are defined by the subtraction flags.
            alu.execute(ALUOp.Sub, left, right);

            boolean taken = switch (funct3) {
                case 0b000 -> alu.isZero();                         // BEQ
                case 0b001 -> !alu.isZero();                        // BNE
                case 0b100 -> alu.isNegative() != alu.isOverflow(); // BLT
                case 0b101 -> alu.isNegative() == alu.isOverflow(); // BGE
                case 0b110 -> !alu.isCarry();                       // BLTU
                case 0b111 -> alu.isCarry();                        // BGEU
                default -> {
                    hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    yield false;
                }
            };

            if (hart.status != HartStatus.RUNNING) return;

            if (taken) actualNextPc = pc + prevReg.getDisp();
            hart.branchPredictor.update(pc, actualNextPc, taken);

        } else if (opcode == Opcode.JAL) {
            result = pc + 4; // Return address written to rd.
            actualNextPc = prevReg.getDisp();

        } else if (opcode == Opcode.JALR) {
            result = pc + 4; // Return address written to rd.
            actualNextPc = (left + right) & ~1;
        }

        boolean isControlFlow = opcode == Opcode.BRANCH
                || opcode == Opcode.JAL
                || opcode == Opcode.JALR;

        // If actual branch location does not match the predicted, NOP front-end
        if (isControlFlow
                && actualNextPc != prevReg.getPredictedNextPc()) {
            hart.programCounter = actualNextPc;
            this.hart.stages[0].needToNOP = true;
            this.hart.stages[1].needToNOP = true;
        }

        nextReg.setPC(pc);
        nextReg.setInst(instBytes);
        nextReg.setResult(result);
        nextReg.setRd(prevReg.getRd());
        nextReg.setMemop(prevReg.getMemop());
        nextReg.setStrval(strval);
        nextReg.setDisp(prevReg.getDisp());
        nextReg.setALUOp(prevReg.getAluop());
        nextReg.setAMOOp(prevReg.getAMOOp());
        nextReg.setAMORl(prevReg.getAMORl());
        nextReg.setAMOAq(prevReg.getAMOAq());
        nextReg.setPredictedNextPc(prevReg.getPredictedNextPc());
    }

    @Override
    public void falling() {
        logger.log("Running falling edge for execute stage for hart #%d", hart.id);

        if (this.needToNOP) {
            this.hart.pipelineRegisters[2].setNextToNop();
            this.needToNOP = false;
        }

        if (this.needToFreeze) {
            this.needToFreeze = false;
        } else {
            this.hart.pipelineRegisters[2].tick();
        }
    }
}
