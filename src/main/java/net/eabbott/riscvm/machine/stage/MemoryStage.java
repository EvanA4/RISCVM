package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.AMOOp;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

/*
 * The pipeline memory stage.
 * */
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

        // Pass through the fields needed by writeback, replacing result for loads.
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
            int value = littleEndianInt(data);
            if (size == 1) value = (byte) value;       // LB: sign-extend
            else if (size == 2) value = (short) value; // LH: sign-extend
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

            hart.memman.write(address, littleEndianBytes(prevReg.getStrval(), size),
                    AccessType.STORE);

        } else if (opcode == Opcode.AMO) {
            AMOOp operation = AMOOp.fromByte(prevReg.getAMOOp());
            int oldValue = littleEndianInt(
                    hart.memman.read(address, 4, AccessType.AMO));
            int operand = prevReg.getStrval();
            int newValue;

            switch (operation) {
                case LR -> {
                    // LR returns the loaded word. Reservation tracking is not
                    // represented by the supplied MemoryManager API.
                    nextReg.setResult(oldValue);
                    return;
                }
                case SC -> {
                    // Without reservation tracking, this treats SC as a store.
                    newValue = operand;
                }
                case SWAP -> newValue = operand;
                case ADD  -> newValue = oldValue + operand;
                case XOR  -> newValue = oldValue ^ operand;
                case AND  -> newValue = oldValue & operand;
                case OR   -> newValue = oldValue | operand;
                case MIN  -> newValue = Math.min(oldValue, operand);
                case MAX  -> newValue = Math.max(oldValue, operand);
                case MINU -> newValue =
                        Integer.compareUnsigned(oldValue, operand) <= 0
                                ? oldValue : operand;
                case MAXU -> newValue =
                        Integer.compareUnsigned(oldValue, operand) >= 0
                                ? oldValue : operand;
                default -> {
                    hart.status = HartStatus.ILLEGAL_INSTRUCTION;
                    return;
                }
            }

            hart.memman.write(address, littleEndianBytes(newValue, 4),
                    AccessType.AMO);

            if (operation == AMOOp.SC) {
                nextReg.setResult(0); // success; full reservation semantics need tracking
            } else {
                nextReg.setResult(oldValue);
            }
        }
    }

    @Override
    public void falling() {
        if (hart.status != HartStatus.RUNNING) return;

        logger.log("Running falling edge for memory stage for hart #%d", hart.id);
        hart.pipelineRegisters[3].tick();
    }

    private static int littleEndianInt(byte[] data) {
        int value = 0;
        for (int i = 0; i < data.length; i++) {
            value |= (data[i] & 0xff) << (8 * i);
        }
        return value;
    }

    private static byte[] littleEndianBytes(int value, int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (value >>> (8 * i));
        }
        return data;
    }
}
