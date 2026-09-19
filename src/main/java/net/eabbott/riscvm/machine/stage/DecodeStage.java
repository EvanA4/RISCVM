package net.eabbott.riscvm.machine.stage;

import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.util.VMLogger;

/*
* The pipeline decode stage.
* */
public class DecodeStage extends AbstractStage {
    private final VMLogger logger = VMLogger.getInstance();
    private final Hart hart;

    public DecodeStage(Hart hart) {
        this.hart = hart;
    }

    public void rising() {
        logger.log("Running rising edge for decode stage for hart #%d", this.hart.id);
    }

    public void falling() {
        logger.log("Running falling edge for decode stage for hart #%d", this.hart.id);
    }
}