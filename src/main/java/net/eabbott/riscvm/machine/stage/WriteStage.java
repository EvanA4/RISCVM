package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.HartStatus;
import net.eabbott.riscvm.machine.SystemCall;
import net.eabbott.riscvm.machine.SystemOpcode;
import net.eabbott.riscvm.machine.VirtualMachine;
import net.eabbott.riscvm.machine.instruction.Opcode;
import net.eabbott.riscvm.machine.register.PipelineRegister;
import net.eabbott.riscvm.util.BinaryUtil;
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

    public WriteStage(Hart hart) {
        this.hart = hart;
        this.prevReg = hart.pipelineRegisters[3];
        this.forwardBarrier = new CyclicBarrier(2);
    }

    @Override
    public void rising() {
        logger.log("Running rising edge for write stage for hart #%d", hart.id);

        byte[] inst = prevReg.getInst();
        int funct7 = BinaryUtil.intFromBits(inst, 25, 7, false);
        int raw = BinaryUtil.intFromBytesLE(inst, 0, 4);
        Opcode opcode = Opcode.fromInt(raw & 0x7f);

        // Perform system call (or lack thereof)
        boolean isECALL = opcode == Opcode.SYSTEM && raw == 0x00000073;
        int callNumber = isECALL ? hart.registers.read(17) : -1;
        SystemOpcode callOp = SystemOpcode.fromValue(callNumber);

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

        // Use system call result if ECALL
        if (isECALL) {
            // System-call return value is placed in a0.
            writeRegister = 10;
            writeValue = result;
        }

        // SFENCE.VMA
        else if (opcode == Opcode.SYSTEM && funct7 == 0b0001001) {
            hart.memman.sfenceVma(prevReg.getLeft(), prevReg.getRight());
        }

        // TODO: CSR instructions here...

        // Normal operations
        else {
            writeRegister = Byte.toUnsignedInt(prevReg.getRd());
            writeValue = prevReg.getResult();
        }
    }

    @Override
    public void falling() {
        logger.log("Running falling edge for write stage for hart #%d", hart.id);

        hart.registers.write(writeRegister, writeValue);
    }
}
