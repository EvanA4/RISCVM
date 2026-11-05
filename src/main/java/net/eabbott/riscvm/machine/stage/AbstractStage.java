package net.eabbott.riscvm.machine.stage;

/*
* The generic definition of a pipeline stage.
* */
public abstract class AbstractStage {
    public boolean needToNOP = false;
    public boolean needToFreeze = false;
    public abstract void rising();
    public abstract void falling();
}
