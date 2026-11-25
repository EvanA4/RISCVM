package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.SystemCall;
import net.eabbott.riscvm.machine.SystemOpcode;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.instruction.Instruction;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.VMLogger;

import java.util.concurrent.CyclicBarrier;

public class WriteStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;
    private final PipelineRegister prevReg;

    public final CyclicBarrier forwardBarrier;

    // Private values computed on rising edge and committed on falling edge.
    private int writeRegister;
    private int writeValue;

    // Snapshot values from rising edge
    Instruction snapInst;
    int snapLeft, snapRight;
    int snapRd, snapResult;

    public WriteStage(Hart hart) {
        this.hart = hart;
        this.prevReg = hart.pipelineRegisters[3];
        this.forwardBarrier = new CyclicBarrier(2);
    }

    @Override
    public void rising() {
        // ALWAYS snapshot current prevReg
        snapInst = new Instruction(this.prevReg.getInst());
        snapLeft = prevReg.getLeft();
        snapRight = prevReg.getRight();
        snapRd = Byte.toUnsignedInt(prevReg.getRd());
        snapResult = prevReg.getResult();

        // TODO: REMOVE THIS
        // if (snapInst.isNOP()) return;

        logger.debug("Memory/write pipeline register:");
        this.hart.pipelineRegisters[3].dump("Memory/write");

        logger.debug("Running rising edge for write stage for hart #%d", hart.id);
    }

    @Override
    public void falling() {
        logger.debug("Running falling edge for write stage for hart #%d", hart.id);

        writeRegister = 0;
        writeValue = 0;

        // Perform system call (or lack thereof)
        boolean isECALL = snapInst.isECALL();
        int callNumber = isECALL ? hart.registers.read(17) : -1;
        SystemOpcode callOp = SystemOpcode.fromValue(callNumber);

        // Make a "system call" even if one isn't provided
        // Saturates the CyclicBarrier
        if (callOp != SystemOpcode.NONE) logger.info("Making non-trivial system call: %s", callOp.name());
        int result = VirtualMachine.getInstance().systemCall(
            new SystemCall(
                hart.id,
                callOp,
                isECALL ? hart.registers.read(10) : 0, // a0
                isECALL ? hart.registers.read(11) : 0, // a1
                isECALL ? hart.registers.read(12) : 0, // a2
                isECALL ? hart.registers.read(13) : 0  // a3
            )
        );
        logger.verbose("System call [%s] value: %d", callOp.name(), result);

        // Use system call result if ECALL
        if (isECALL) {
            // System-call return value is placed in a0.
            writeRegister = 10;
            writeValue = result;
        }

        // SFENCE.VMA
        else if (snapInst.isSFENCE()) {
            hart.memman.sfenceVma(snapLeft, snapRight);
        }

        // Normal operations
        else {
            writeRegister = snapRd;
            writeValue = snapResult;
        }
        logger.verbose("isECALL: %s    isSFENCE: %s", isECALL, Boolean.toString(snapInst.isSFENCE()));
        logger.verbose("writeRegister: %d    writeValue: %d", writeRegister, writeValue);
        hart.registers.write(writeRegister, writeValue);
    }
}
