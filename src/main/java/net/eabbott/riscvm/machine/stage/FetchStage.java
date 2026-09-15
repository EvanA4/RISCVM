package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.util.VMLogger;

public class FetchStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;

    public FetchStage(Hart hart) {
        this.hart = hart;
    }

    public void rising() {
        logger.log("Running rising edge for fetch stage for hart #%d", this.hart.id);
    }

    public void falling() {
        logger.log("Running falling edge for fetch stage for hart #%d", this.hart.id);
    }
}
