package net.eabbott.riscvm.machine.memory;

import net.eabbott.riscvm.machine.Hart;

public class MemoryManager {
    public RAM ram;
    public Hart hart;
}

/*
* Notes:
* ELF file (the user) is responsible for setting up page tables and setting satp to non-zero.
*
* Types of memory requests:
* - Permissions (store = W, load = R, execute = PC)
* - Read/write to memory
* - Virtual memory or not (determined by satp)
* - On scale of byte, halfword, or word
*
* When memory request is made:
* - ...If virtual memory
*   - Translate, verifying permissions
* - Look up in local L1 cache
*   - Caches map from memory block address to the block data
*   - Use fancy MESI protocol
*   - SNOOP vs. DIR = whether they track who has shared values or if you just broadcast
*   - MESI is a write-invalidate protocol
*   - Hardcode satp to be 0 if MMU is disabled by command line
* */