package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.AMOOp;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

import java.util.Arrays;

public class MemoryStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister prevReg;
    private final PipelineRegister nextReg;

    public MemoryStage(Hart hart) {
        this.hart = hart;
        this.prevReg = hart.pipelineRegisters[2];
        this.nextReg = hart.pipelineRegisters[3];
    }

    @Override
    public void rising() {
        if (hart.status != HartStatus.RUNNING) return;

        logger.log("Running rising edge for memory stage for hart #%d", hart.id);

        byte[] inst = prevReg.getInst();
        if (inst.length == 0) return;

        nextReg.setPC(prevReg.getPC());
        nextReg.setInst(inst);
        nextReg.setPredictedNextPc(prevReg.getPredictedNextPc());
        nextReg.setResult(prevReg.getResult());
        nextReg.setRd(prevReg.getRd());
        nextReg.setMemop(prevReg.getMemop());
        nextReg.setStrval(prevReg.getStrval());
        nextReg.setAMOOp(prevReg.getAMOOp());
        nextReg.setAMORl(prevReg.getAMORl());
        nextReg.setAMOAq(prevReg.getAMOAq());

        Opcode opcode = Opcode.fromInt(BinaryUtil.intFromBits(inst, 0, 7, false));
        int memop = prevReg.getMemop() & 0xff;
        long address = Integer.toUnsignedLong(prevReg.getResult());

        if (opcode == Opcode.LOAD) {
            int size = switch (memop) {
                case 0b001 -> 1; // LB
                case 0b010 -> 2; // LH
                case 0b011 -> 4; // LW
                default -> {
                    hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    yield 0;
                }
            };
            if (hart.status != HartStatus.RUNNING) return;

            byte[] data = hart.memman.read(address, size, AccessType.LOAD);
            int value = BinaryUtil.intFromBytesLE(data, 0, size);
            if (size == 1) value = (byte) value;
            else if (size == 2) value = (short) value;
            nextReg.setResult(value);

        } else if (opcode == Opcode.STORE) {
            int size = switch (memop) {
                case 0b101 -> 1; // SB
                case 0b110 -> 2; // SH
                case 0b111 -> 4; // SW
                default -> {
                    hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    yield 0;
                }
            };
            if (hart.status != HartStatus.RUNNING) return;

            hart.memman.write(
                    address,
                    Arrays.copyOfRange(BinaryUtil.bytesFromIntLE(prevReg.getStrval()), 0, size),
                    AccessType.STORE
            );

        } else if (opcode == Opcode.AMO) {
            AMOOp operation = AMOOp.fromByte(prevReg.getAMOOp());
            int operand = prevReg.getStrval();

            if (operation == AMOOp.LR) {
                nextReg.setResult(hart.memman.loadReserved(address));
                return;
            }

            if (operation == AMOOp.SC) {
                nextReg.setResult(hart.memman.storeConditional(address, operand));
                return;
            }

            int oldValue = hart.memman.rmw(address, prev -> switch (operation) {
                case SWAP -> operand;
                case ADD -> prev + operand;
                case XOR -> prev ^ operand;
                case AND -> prev & operand;
                case OR  -> prev | operand;
                case MIN -> Math.min(prev, operand);
                case MAX -> Math.max(prev, operand);
                case MINU -> Integer.compareUnsigned(prev, operand) <= 0
                        ? prev : operand;
                case MAXU -> Integer.compareUnsigned(prev, operand) >= 0
                        ? prev : operand;
                default -> throw new IllegalArgumentException(
                        "Unsupported AMO operation: " + operation
                );
            });

            // RMW's write to result
            nextReg.setResult(oldValue);
        }
    }

    @Override
    public void falling() {
        if (hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for memory stage for hart #%d", hart.id);
        hart.pipelineRegisters[3].tick();
    }
}
