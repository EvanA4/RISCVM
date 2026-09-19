# Rubric 1 Defense

Each part of my defense will include a snippet from the first rubric, as 
well as an explanation as to why my project meets those specifications.
Assume each relative path given is from the path
`src/main/java/net/eabbott/riscvm`, which is a relative path from the
project root.

For reference, this is the current structure of the project as of September 18th, 2026:
```
src/main/java/net/eabbott/riscvm/
├── context
│         ├── AbstractContext.java
│         ├── CacheAssociativity.java
│         ├── CacheAssociativityType.java
│         ├── CacheCoherency.java
│         ├── CacheContext.java
│         ├── Context.java
│         └── EvictionPolicy.java
├── machine
│         ├── CSRFile.java
│         ├── Hart.java
│         ├── RandomAccessMemory.java
│         ├── RegisterFile.java
│         ├── stage
│         │         ├── AbstractStage.java
│         │         ├── DecodeStage.java
│         │         ├── ExecuteStage.java
│         │         ├── FetchStage.java
│         │         ├── MemoryStage.java
│         │         └── WriteStage.java
│         ├── thread
│         │         └── StageThread.java
│         ├── VirtualMachine.java
│         └── VMClock.java
├── Main.java
└── util
    ├── BinaryUtil.java
    ├── Nullable.java
    └── VMLogger.java
```

### Category A -- Build and Structure
| Step | Criterion |
| - | - |
| A1 | Compiles cleanly, machine core in its own file, no external dependencies. |

You'll have to confirm the project builds for yourself, but I've tested the
project on both Arch Linux and Windows 11. There are no external
dependencies in this project. You can find the core virtual machine class 
in `machine/VirtualMachine.java`.

### Category B -- Configuration Parsing and Precedence
| Step | Criterion |
| - | - |
| B1 | All switches parse; config file parses (# comments, whitespace-separated key/value, last-duplicate-wins); precedence is correct; -mem accepts no-suffix/K/M/G. |

The `context` package is responsible for parsing arguments. The `AbstractContext` and `Context` classes work together to parse the command line arguments. All the other classes in the package are simple data structures for holding values in a type safe manner.

As for the more specific requirements:
- The config file parser always splits lines by "#" to determine what to actually parse;
- When splitting by a space for computing words, the parser filters out any empty strings to account for any amount of whitespace;
- The parser always prioritizes the config file, then the command line arguments;
- The parser also overwrites any previously set configuration, even if there was a duplicate key;
- The custom `parseMetric` function ensures all values are parsed correctly--with or without a suffix.

### Category C -- Machine Memory
| Step | Criterion |
| - | - |
| C1 | Allocates exactly mem bytes; address N indexes byte N; not pre-populated. |

Java does not like allocating memory without initializing every section to some default value. For example an integer array `int[]` initializes with all zeros. For byte-by-byte storage, however, Java does offer `Arena` and `MemorySegment` classes as an exception. None of these bytes are initialized to 0, in exchange for some instability.

You can find an implementation of the RAM in `machine/RandomAccessMemory.java`. Here, basic read and write functions have been defined to interact with the private `MemorySegment` object. Although both functions operate in terms of byte arrays, reading 1 byte at offset N will behave as is expected for this project.

### Category D -- Register File and x0 Semantics
| Step | Criterion |
| - | - |
| D1 | 32 registers behind a read/write API; reads of x0 return 0; writes to x0 are discarded; enforced inside the API. |

You can find an implementation of the register files in `machine/RegisterFile.java`. Here, basic read and write functions have been defined to interact with the private `int[]` object. While the stack pointer isn't explicitly mentioned here, it is treated just like any other register in the register file implementation. Conditionals were placed to check when the program is trying to read or write to x0, always ignoring the writes and returning a zero for the reads.

### Category E -- PC and CSRs
| Step | Criterion |
| - | - |
| E1 | PC present and non-addressable; all CSRs present with correct numbers and read/write behavior (mhartid read-only, satp hardwired to 0 when -mmu off). |

You can find an implementation of the CSR files in `machine/CSRFile.java`. Here, basic read and write functions have been defined to interact with the private `int` properties for the different CSRs. The program counter is a `Hart` property separate from the CSR and Register files. It's also private, and can only be accessed by a `getAndIncrementPC` function. A special if-statement checks for whether to set the satp CSR to zero, as well. Only the satp CSR can be written to, all other CSRs throw an exception if written to.

### Category F -- Sign Extension
| Step | Criterion |
| - | - |
| F1 | Correct for all sign-bit positions; contains no loops and no if/else/ternary/switch. |

You can find an implementation of the sign extension function in `util/BinaryUtil.java`. There is only one function, focusing on sign extension.

### Summary

To my knowledge, I have fulfilled all requirements to the best of my 
ability.