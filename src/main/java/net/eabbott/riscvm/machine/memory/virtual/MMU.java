package net.eabbott.riscvm.machine.memory.virtual;

import net.eabbott.riscvm.machine.register.CSRFile;
import net.eabbott.riscvm.machine.Hart;
import net.eabbott.riscvm.machine.VirtualMachine;

public final class MMU {
    private static final long ADDRESS_MASK = 0xFFFF_FFFFL;
    private static final long PPN_MASK = 0x3FFFFFL;
    private final TLB tlb;
    private final VirtualMachine machine;
    private final Hart hart;

    public MMU(Hart hart) {
        this.hart = hart;
        this.machine = VirtualMachine.getInstance();
        this.tlb = this.machine.tlb;
    }

    /**
     * Translates a virtual address and checks that the requested byte range is within physical RAM.
     */
    public long translate(long address, int size, AccessType access) throws MemoryFault {
        long va = address & ADDRESS_MASK;

        // Find leaf PTE
        TLBEntry entry = tlb.lookup(va);
        if (entry == null) {
            entry = walkPageTable(va, access);
            tlb.insert(entry);
        } else {
            checkPermission(entry, va, access);
        }

        // Translate address based on PTE and confirm physical bounds
        long translated = entry.translate(va);
        checkPhysicalBounds(va, translated, size);

        return translated;
    }

    /**
     * SFENCE.VMA invalidation:
     *  rs1 == x0 and rs2 == x0 ==> invalidate all entries;
     *  rs1 != x0               ==> invalidate the entry matching that virtual address.
     */
    public void sfenceVma(long rs1, long rs2) {
        if (!tlb.isEnabled()) return;
        if (rs1 == 0 && rs2 == 0) {
            tlb.invalidateAll();
        } else if (rs1 != 0) {
            tlb.invalidateAddress(rs1 & ADDRESS_MASK);
        }
    }

    private TLBEntry walkPageTable(long va, AccessType access) throws MemoryFault {
        long currentSatp = this.hart.csrFile.read(CSRFile.SATP);

        long rootPpn = currentSatp & PPN_MASK;
        int vpn1 = (int) ((va >>> 22) & 0x3FF);
        int vpn0 = (int) ((va >>> 12) & 0x3FF);

        long tablePpn = rootPpn;

        for (int level = 1; level >= 0; level--) {
            int vpnIndex = (level == 1) ? vpn1 : vpn0;
            long pteAddress = (tablePpn << 12) + (vpnIndex * 4L);
            int pte = readPte(va, pteAddress);

            boolean valid = (pte & 0x001) != 0;
            boolean readable = (pte & 0x002) != 0;
            boolean writable = (pte & 0x004) != 0;
            boolean executable = (pte & 0x008) != 0;

            long ptePpn = ((pte >>> 10) & PPN_MASK);

            if (!valid || (writable && !readable)) {
                throw new MemoryFault(access, va, "Invalid SV32 page-table entry");
            }

            // R=1 or X=1 identifies a leaf PTE.
            if (readable || executable) {
                if (level == 1 && (ptePpn & 0x3FFL) != 0) {
                    throw new MemoryFault(access, va, "Misaligned SV32 superpage");
                }

                TLBEntry entry = new TLBEntry(
                    level,
                    level == 1 ? vpn1 : ((vpn1 << 10) | vpn0),
                    level == 1 ? (ptePpn >>> 10) : ptePpn,
                    readable,
                    writable,
                    executable,
                    (pte & 0x010) != 0, // U
                    (pte & 0x020) != 0, // G
                    (pte & 0x040) != 0, // A
                    (pte & 0x080) != 0, // D
                    0
                );

                checkPermission(entry, va, access);
                return entry;
            }

            // A non-leaf at level 0 cannot lead to another page-table level.
            if (level == 0) {
                throw new MemoryFault(access, va, "No leaf PTE at level 0");
            }

            tablePpn = ptePpn;
        }

        throw new MemoryFault(access, va, "No valid SV32 translation");
    }

    private int readPte(long va, long pteAddress) throws MemoryFault {
        checkPhysicalBounds(va, pteAddress, 4);
        try {
            byte[] word = this.machine.ram.fetch(pteAddress);
            return (word[0] & 0xFF)
                | ((word[1] & 0xFF) << 8)
                | ((word[2] & 0xFF) << 16)
                | ((word[3] & 0xFF) << 24);
        } catch (RuntimeException ex) {
            throw new MemoryFault(
                FaultType.ACCESS_FAULT,
                va,
                pteAddress,
                "Could not read page-table entry from physical RAM"
            );
        }
    }

    private void checkPermission(TLBEntry entry, long va, AccessType access) throws MemoryFault {
        boolean permitted = switch (access) {
            case FETCH -> entry.permitsExecute();
            case LOAD -> entry.permitsRead();
            case STORE, AMO -> entry.permitsWrite();
        };

        if (!permitted) {
            throw new MemoryFault(access, va, "SV32 permission check failed");
        }
    }

    private void checkPhysicalBounds(long va, long pa, int sizeBytes) throws MemoryFault {
        long ramSize = this.machine.ram.length();

        // The subtraction form avoids overflow in pa + sizeBytes.
        if (pa < 0 || pa >= ramSize || sizeBytes > ramSize - pa) {
            throw new MemoryFault(
                FaultType.ACCESS_FAULT,
                va,
                pa,
                "Physical address is outside RAM bounds"
            );
        }
    }
}
