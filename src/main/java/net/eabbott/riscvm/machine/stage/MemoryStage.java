package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.util.VMLogger;

public class MemoryStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;

    public MemoryStage(Hart hart) {
        this.hart = hart;
    }

    public void rising() {
        logger.log("Running rising edge for memory stage for hart #%d", this.hart.id);
    }

    public void falling() {
        logger.log("Running falling edge for memory stage for hart #%d", this.hart.id);
    }
}