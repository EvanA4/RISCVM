package net.eabbott.riscvm.machine.memory.virtual;

public class MemoryFault extends RuntimeException {
    public final FaultType type;
    public final long virtualAddress;
    public final long physicalAddress;

    public MemoryFault(
            FaultType type,
            long virtualAddress,
            long physicalAddress,
            String message) {
        super(message);
        this.type = type;
        this.virtualAddress = virtualAddress;
        this.physicalAddress = physicalAddress;
    }

    public MemoryFault(
            AccessType access,
            long va,
            String message) {
        FaultType type = switch (access) {
            case FETCH -> FaultType.INSTRUCTION_PAGE_FAULT;
            case LOAD -> FaultType.LOAD_PAGE_FAULT;
            case STORE, AMO -> FaultType.STORE_AMO_PAGE_FAULT;
        };

        super(message);
        this.type = type;
        this.virtualAddress = va;
        this.physicalAddress = -1;
    }
}