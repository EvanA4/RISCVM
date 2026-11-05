package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.ALU;
import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.ALUOp;
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

    @Override
    public void rising() {
        if (hart.status != HartStatus.RUNNING) return;

        logger.log("Running rising edge for execute stage for hart #%d", hart.id);

        byte[] inst = prevReg.getInst();
        if (inst.length == 0) return;

        int pc = prevReg.getPC();
        int left = prevReg.getLeft();
        int right = prevReg.getRight();

        Opcode opcode = Opcode.fromInt(BinaryUtil.intFromBits(inst, 0, 7, false));
        if (opcode == null) {
            hart.status = HartStatus.ILLEGAL_INSTRUCTION;
            return;
        }

        ALUOp op = ALUOp.fromByte(prevReg.getAluop());
        int result = alu.execute(op, left, right);
        int actualNextPc = pc + 4;

        if (opcode == Opcode.BRANCH) {
            // Branch comparisons are defined by the subtraction flags.
            alu.execute(ALUOp.Sub, left, right);

            int funct3 = BinaryUtil.intFromBits(inst, 12, 3, false);
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

        if (isControlFlow
                && actualNextPc != prevReg.getPredictedNextPc()) {
            hart.programCounter = actualNextPc;
            this.hart.stages[0].needToNOP = true;
            this.hart.stages[1].needToNOP = true;
        }

        nextReg.setPC(pc);
        nextReg.setInst(inst);
        nextReg.setResult(result);
        nextReg.setRd(prevReg.getRd());
        nextReg.setMemop(prevReg.getMemop());
        nextReg.setStrval(prevReg.getStrval());
        nextReg.setDisp(prevReg.getDisp());
        nextReg.setALUOp(prevReg.getAluop());
        nextReg.setAMOOp(prevReg.getAMOOp());
        nextReg.setAMORl(prevReg.getAMORl());
        nextReg.setAMOAq(prevReg.getAMOAq());
        nextReg.setPredictedNextPc(prevReg.getPredictedNextPc());
    }

    @Override
    public void falling() {
        if (hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for execute stage for hart #%d", hart.id);
        this.hart.pipelineRegisters[2].tick();
    }
}
