# RISCVM
A RISCV, 32-bit, integer, multiply, and atomic virtual machine implemented in C++.

## Getting Started

Build the virtual machine:

```
make
```

Execute the binary:

```
.\bin\vm.exe
```

Command-line arguments:

| Switch | Default | Description |
| --- | --- | --- |
| -f | none | Specifies a configuration file. If this is omitted, configuration is default or set by command line arguments. |
| -i | error | Input ELF file. If this switch is not given, it is an error. |
| -o | - | Output file. If - is given, output is written to standard output (stdout). |
| -m | on | State of the RV32M extension. on = RV32M instructions are enabled, off = RV32M instructions cause an "Illegal instruction" if used. |
| -a | on | State of the RV32A extension. on = RV32A instructions are enabled, off = RV32A instructions cause an "Illegal instruction" if used. |
| -mem | 1M | RAM size (1234567 = 1,234,567 bytes, 1K = 1 kibibyte = 1024B, 1M = 1 Mebibyte = 1,048,576 bytes). Must accept no suffix or K, M, and G for kibibyte, mebibyte, and gibibyte, respectively. |
| -harts | 4 | Specifies the number of HARTs on your virtual machine. Must be 1..=16. |
| -hz | 100 | Cycle frequency in Hertz (100 Hz = 100 times per second). May have the suffix K, M, or G for kilohertz, megahertz, and gigahertz, respectively. This controls the cycle counters and how fast the pipeline moves for each HART. Every HART has the same cycle frequency. |
| -cL | 3 | Cache levels (0 = no cache, 1 = L1 only, 2 = L1 and L2, 3 = L1, L2, and L3). |
| -c1 | dm | Cache type for L1: dm = direct-mapped, sa,<ways exponent> = set-associative, or fa = fully-associative. Exponent is the number of ways as an exponent to 2. Example (3 = 23=8 ways) |
| -c2 | sa,3 | Cache type for L2: dm = direct-mapped, sa,<ways exponent> = set-associative, or fa = fully-associative. Exponent is the number of ways as an exponent to 2. Example (3 = 23=8 ways) |
| -c3 | fa | Cache type for L3: dm = direct-mapped, sa,<ways exponent> = set-associative, or fa = fully-associative. Exponent is the number of ways as an exponent to 2. Example (3 = 23=8 ways) |
| -cS1 | 1K | L1 cache size in bytes. Must accept no suffix or K, M, and G for kibibyte, mebibyte, and gibibyte, respectively. |
| -cS2 | 2K | L2 cache size in bytes. Must accept no suffix or K, M, and G for kibibyte, mebibyte, and gibibyte, respectively. |
| -cS3 | 4K | L3 cache size in bytes. Must accept no suffix or K, M, and G for kibibyte, mebibyte, and gibibyte, respectively. |
| -cB1 | 4 | L1 cache block size as a power of two (24=16B) |
| -cB2 | 5 | L2 cache block size as a power of two (25=32B) |
| -cB3 | 6 | L3 cache block size as a power of two (26=64B) |
| -cW1 | wt | L1 cache write policy (wb = write-back or wt = write-through) |
| -cW2 | wt | L2 cache write policy (wb = write-back or wt = write-through) |
| -cW3 | wb | L3 cache write policy (wb = write-back or wt = write-through) |
| -cE1 | fifo | L1 eviction policy (fifo = FIFO, lru = LRU, lfu = LFU) |
| -cE2 | lru | L2 eviction policy (fifo = FIFO, lru = LRU, lfu = LFU) |
| -cE3 | lfu | L3 eviction policy (fifo = FIFO, lru = LRU, lfu = LFU) |
| -cC | snoop | Specifies the cache coherency protocol. Can be none, snoop, or dir for no coherency, snooping coherency, and directory-based coherency, respectively. The MESI protocol will be used. |
| -bp | 64 | Number of rows in the branch predictor table. 0 disables branch prediction. |
| -bpD | 0 | Default prediction if branch address is not in the table. Can be 0 (strongly not taken), 1, 2, or 3 (strongly taken) |
| -mmu | on | MMU state (on = can be turned on via satp register, off = satp register hardwired to 0) |
| -tlb | 8 | Number of TLB slots. 0 disables the TLB. |
| -tlbE | lru | The eviction policy of the TLB (fifo = first-in, first-out, lfu = least-frequently used, or lru = least-recently used |