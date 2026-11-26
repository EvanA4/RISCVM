package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.instruction.AMOOp;
import net.eabbott.riscvm.machine.instruction.Instruction;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.memory.AMOResult;
import net.eabbott.riscvm.machine.memory.cache.MemoryResult;
import net.eabbott.riscvm.machine.memory.virtual.AccessType;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
import net.eabbott.riscvm.util.VMLogger;

import java.util.Arrays;

/**
 * The pipeline memory stage.
 */
public class MemoryStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister prevReg;
    private final PipelineRegister nextReg;

    private int toWait = 0;
    private boolean startedWaiting = false;
    private int memoryResult = 0;

    public MemoryStage(Hart hart) {
        this.hart = hart;
        this.prevReg = hart.pipelineRegisters[2];
        this.nextReg = hart.pipelineRegisters[3];
    }

    // Executes the rising edge of the MEMORY stage
    @Override
    public void rising() {
        // TODO: REMOVE THIS
        // Instruction tmp = new Instruction(this.prevReg.getInst());
        // if (tmp.isNOP()) return;
        logger.debug("Execute/memory pipeline register:");
        this.hart.pipelineRegisters[2].dump("Execute/memory");

        if (this.hart.status != HartStatus.RUNNING) {
            this.needToNOP = true;
        }

        if (toWait > 0) {
            setStall();
            --toWait;
            return;
        }

        logger.debug("Running rising edge for memory stage for hart #%d", hart.id);

        Instruction inst = new Instruction(prevReg.getInst());
        int funct3 = inst.funct3();

        nextReg.setPC(prevReg.getPC());
        nextReg.setInst(prevReg.getInst());
        nextReg.setPredictedNextPc(prevReg.getPredictedNextPc());
        nextReg.setResult(prevReg.getResult());
        nextReg.setRd(prevReg.getRd());
        nextReg.setMemop(prevReg.getMemop());
        nextReg.setStrval(prevReg.getStrval());
        nextReg.setAMOOp(prevReg.getAMOOp());
        nextReg.setAMORl(prevReg.getAMORl());
        nextReg.setAMOAq(prevReg.getAMOAq());

        if (startedWaiting && toWait == 0) {
            nextReg.setResult(memoryResult);
            startedWaiting = false;
            memoryResult = 0;
            return;
        }

        Opcode opcode = inst.opcode();
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

            MemoryResult result = hart.memman.read(address, size, AccessType.LOAD);
            int value = BinaryUtil.intFromBytesLE(result.data(), 0, size);

            // if not LBU, sign extend
            if (size == 1 && funct3 != 0b100) value = BinaryUtil.sext(value, 7);
            // if not LHU, sign extend
            else if (size == 2 && funct3 != 0b101) value = BinaryUtil.sext(value, 15);

            // If stall was requested, pocket the result
            if (result.shouldWait() > 0) {
                logger.verbose("Memory manager requested %d stalls for LOAD at %d with result %d!", result.shouldWait(), address, value);
                setStall();
                toWait = result.shouldWait() - 1;
                memoryResult = value;
                startedWaiting = true;
            } else {
                // Otherwise, pass the result to the next pipeline register
                nextReg.setResult(value);
            }

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

            MemoryResult result = hart.memman.write(
                address,
                Arrays.copyOfRange(BinaryUtil.bytesFromIntLE(prevReg.getStrval()), 0, size),
                AccessType.STORE
            );
            if (result.shouldWait() > 0) {
                logger.verbose("Memory manager requested %d stalls for STORE of %d at %d!", result.shouldWait(), prevReg.getStrval(), address);
                setStall();
                toWait = result.shouldWait() - 1;
                memoryResult = 0;
                startedWaiting = true;
            } else {
                logger.verbose("STORE finished without requesting any stalls. Yay.");
            }

        } else if (opcode == Opcode.AMO) {
            AMOOp operation = AMOOp.fromByte(prevReg.getAMOOp());
            int operand = prevReg.getStrval();

            if (operation == AMOOp.LR) {
                AMOResult result = hart.memman.loadReserved(address);
                if (result.shouldWait() > 0) {
                    setStall();
                    toWait = result.shouldWait() - 1;
                    memoryResult = result.data();
                    startedWaiting = true;
                }
                else nextReg.setResult(result.data());
                return;
            }

            if (operation == AMOOp.SC) {
                AMOResult result = hart.memman.storeConditional(address, operand);
                if (result.shouldWait() > 0) {
                    setStall();
                    toWait = result.shouldWait() - 1;
                    memoryResult = result.data();
                    startedWaiting = true;
                }
                else nextReg.setResult(result.data());
                return;
            }

            AMOResult result = hart.memman.rmw(address, prev -> switch (operation) {
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
            if (result.shouldWait() > 0) {
                setStall();
                toWait = result.shouldWait() - 1;
                memoryResult = result.data();
                startedWaiting = true;
            }
            else nextReg.setResult(result.data());
        }
    }

    // Executes the rising edge of the MEMORY stage
    @Override
    public void falling() {
        logger.debug("Running falling edge for memory stage for hart #%d", hart.id);

        if (this.needToNOP) {
            this.hart.pipelineRegisters[3].setNextToNop();
            this.needToNOP = false;
        }

        if (this.needToFreeze) {
            this.needToFreeze = false;
        } else {
            this.hart.pipelineRegisters[3].tick();
        }
    }

    // If a memory operation requests a stall, stall :)
    private void setStall() {
        hart.stages[0].needToFreeze = true;
        hart.stages[1].needToFreeze = true;
        hart.stages[2].needToFreeze = true;
        hart.stages[3].needToFreeze = true;
    }
}
