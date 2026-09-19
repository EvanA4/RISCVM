package net.eabbott.riscvm.machine.stage;

/*
* The generic definition of a pipeline stage.
* */
public abstract class AbstractStage {
    public abstract void rising();
    public abstract void falling();
}
