
> For the following defense, assume all relative directories are from `PROJECT_ROOT/src/main/java/net/eabbott/riscvm`, unless specified otherwise.

## Category A — Build and Structure (0.5 pts)

Does the Part II code compile and is the ELF loader factored into its own source file(s) as required?

| Step | Criterion | Deduction |
| - | - | - |
| A1 | Compiles cleanly, ELF loader in its own file, no external dependencies. | -0.0 |
| A2 | Compiles but emits warnings that indicate real problems (e.g., truncation, signedness, unaligned reads on header fields).| -0.25 |
| A3 | Compiles only after trivial fixes (missing include/import, one typo), OR loader is not separated into its own file.| -0.5 |
| A4 | Does not compile; requires nontrivial edits to build.| -0.5 (and verify remaining categories by code reading only) |

For this category, I believe I deserve the full score at step A1. The project builds successfully, even when supplying an invalid ELF file as input. The ELF loader is specified across three files:
- `context/elf/ELFContext.java`
  - In lines 15-41, the `read` function in `ELFContext` calls the respective `ELFHeader` and `ELFProgramHeader` classes to parse the ELF file.
- `context/elf/ELFHeader.java`
  - In lines 31-60, the `read` function in `ELFHeader` reads the raw bytes from the supposed ELF header and calls `BinaryUtil` many times to parse each header value.
- `context/elf/ELFProgramHeader.java`
  - In lines 18-34, the `read` function in `ELFProgramHeader` reads the raw bytes from the supposed ELF program header and calls `BinaryUtil` many times to parse each program header value.

A `RandomAccessFile` at `context/elf/ELFContext.java:17` is used to actually open the file for reading with seeking capabilities. The `ELFContext` follows this lifetime:
- At `context/Context.java:40`, the argument parsing function attempts to create a `ELFContext` using the ELF file path it parsed earlier. The result is later stored in a new `Context` object.
- At `Main.java:18`, the `Context` object is passed to the `VirtualMachine` initialization function.
- At `machine/VirtualMachine.java:23`, the `ELFContext` object is passed to the `RandomAccessMemory` object to populate its `MemorySegment` with the ELF file's binary data.

## Category B — ELF Header Parsing and Validation (2.5 pts)

Correctly reads the 52-byte ELF header at the required field offsets and performs the four required verification checks before loading.

| Step | Criterion | Deduction |
| - | - | - |
| B1 | All header fields read at correct offsets/sizes; validates e_ident (\x7fELF), e_type (executable), e_machine (RISC-V = 243), and e_bitsize (32-bit); rejects invalid files gracefully.| -0.0 |
| B2 | Header parsed correctly, but one of the four validation checks is missing or too weak (e.g., checks magic but not e_machine).| -0.5 |
| B3 | Two or more validation checks missing, OR one header field read at a wrong offset/size that happens not to break the test files.| -1.0 |
| B4 | Header field offsets are systematically wrong (e.g., 64-bit layout assumed), OR validation is essentially absent (loads anything).| -1.75 |
| B5 | Header not meaningfully parsed; e_entry/e_phoff/e_phnum not obtained correctly.| -2.5 |

In this category, I believe I have earned full points at step B1.

As stated before, `context/elf/ELFHeader.java:31-60` reads the raw bytes from the supposed ELF header and calls `BinaryUtil` many times to parse each header value. After this, the values are checked for validity as specified below.

In lines `context/elf/ELFContext.java:20-30`, the `read` function in `ELFContext` populates a `ELFHeader` object with the ELF file data and validates the four critical values with this code block:
```
if (
    context.header.ident != 0x464c457f ||
    context.header.type != 2 ||
    context.header.machine != 243 ||
    context.header.bitsize != 1
) {
    throw new IllegalArgumentException(
        "Invalid ELF header."
    );
}
```

Any invalid types are recognized and an `IllegalArgumentException` is thrown and later caught at `context/Context.java:49` to exit the program gracefully.

## Category C — Program Header Table Parsing (2.0 pts)

Seeks to e_phoff, iterates e_phnum entries of 32 bytes each, and reads the program header fields at the correct offsets.

| Step | Criterion | Deduction |
| - | - | - |
| C1 | Seeks to e_phoff; iterates exactly e_phnum entries of e_phentsize/32 bytes; all PH fields read at correct offsets (note p_flags at offset 24, unlike 64-bit ELF); filters on p_type == PT_LOAD.| -0.0 |
| C2 | Iterates correctly but one PH field read at a wrong offset, OR uses a hardcoded entry size instead of e_phentsize/32 where it matters.| -0.5 |
| C3 | Does not filter on p_type (attempts to load non-loadable segments), OR miscounts entries (off-by-one on e_phnum).| -1.0 |
| C4 | Program header table located or iterated incorrectly (wrong seek base, wrong stride) so segments are read from the wrong offsets.| -1.5 |
| C5 | Program header table not parsed.| -2.0 |

I believe I have earned full points for this category at step C1.

At `context/elf/ELFProgramHeader.java:18-34`, the `read` function in `ELFProgramHeader` reads the raw bytes from the supposed ELF program header and calls `BinaryUtil` many times to parse each program header value. More specifically, line 21 calls the seek function, whereas the line after reads the 32 bytes. The remainder of the code block parses each field of the program header.

In lines `context/elf/ELFContext.java:33-38`, the `read` function in `ELFContext` creates an empty list of program headers and populates them with each consecutive block of 32 bytes.

## Category D — Segment Loading and Zero-Fill (3.0 pts)

Copies each loadable segment’s p_filesz bytes from file offset p_offset to memory address p_vaddr, and zero-fills the p_memsz - p_filesz remainder.

| Step | Criterion | Deduction |
| - | - | - |
| D1 | Copies exactly p_filesz bytes from p_offset to p_vaddr; zero-fills p_memsz - p_filesz trailing bytes; handles multiple loadable segments in order.| -0.0 |
| D2 | Correct copy and zero-fill, but a minor issue: does not handle multiple segments robustly, or assumes zeroed memory instead of explicitly zero-filling (works only if RAM starts zeroed).| -1.0 |
| D3 | Loads a valid ELF but silently mishandles p_memsz > p_filesz (no zero-fill, or .bss left as garbage).| -2.0 |
| D4 | Copies p_memsz bytes from the file (reads past on-disk segment data, corrupting memory), OR copies to p_offset/p_paddr instead of p_vaddr.| -2.5 |
| D5 | Segment data not copied into memory correctly (wrong source, wrong destination, or not at all).| -3.0 |

I believe I have earned full points for this category at step D1.

At `machine/VirtualMachine.java:23`, the `ELFContext` object is passed to the `RandomAccessMemory` class to populate its `MemorySegment` with the ELF file's binary data.

At `machine/RandomAccessMemory.java:31-38`, a for loop is ran such that each loadable program header has it's corresponding data copied to the `MemorySegment`. Line 32 seeks the binary file reader to `p_offset`. The line after creates a buffer of `p_filesz` size. Lines 34-35 read and write the now loaded buffer to the `MemorySegment`. However, zeroing occurs when the `zero` function is called at line 36. Lines 51-64 are where the `zero` function sets the extra bytes from `p_memsz` to `0` in the `MemorySegment`.

## Category E — Entry Point (1.0 pts)

Sets the program counter to e_entry after loading.

| Step | Criterion | Deduction |
| - | - | - |
|E1| PC initialized to e_entry from the ELF header after all segments are loaded.| -0.0 |
| E2 | Entry point obtained but applied at the wrong time (e.g., before loading) or to the wrong register/field.| -0.5 |
| E3 | Entry point not set, OR hardcoded to a fixed address instead of e_entry.| -1.0 |

I believed I have earned full points for this category at step E1.

At`context/elf/ELFHeader.java:48`, the `e_entry` value is parsed in the overall `Context` pipeline. At `machine/VirtualMachine.java:34`, harts are initialized with the `e_entry` value parsed earlier. At `machine/Hart.java:22`, the `programCounter` property in the `Hart` class is set to the `e_entry` value passed into the constructor by the `VirtualMachine` initialization function.

## Category F — Robustness and Endianness (1.0 pts)

Multi-byte fields treated as little-endian; loader fails safely on malformed input rather than crashing.

| Step | Criterion | Deduction |
| - | - | - |
| F1 | All multi-byte header/PH fields interpreted little-endian; malformed or non-conforming files are rejected with a clear error, no crash or out-of-bounds access.| -0.0 |
| F2 | Little-endian handling correct, but bounds/error handling is weak (e.g., a segment whose p_vaddr + p_memsz exceeds RAM is not caught).| -0.5 |
| F3 | Endianness assumed from the host without translation (works only on little-endian hosts), OR malformed input causes a crash/undefined behavior.| -1.0 |

I believe I have earned a full score for this category at step F1.

At `util/BinaryUtil.java:42-49`, an `intFromBytes` function is defined to parse bytes in little-endian format and return an `int`. At `util/BinaryUtil.java:54-61`, a `longFromBytes` function is defined to parse bytes in little-endian format and return a `long`. This latter function is only called at`context/elf/ELFHeader.java:44` for the `e_padding` field, though.

At any point in parsing or validating the ELF header and program headers, an exception may be thrown. To handle these, exceptions are caught at `context/Context.java:49` to exit the program gracefully; a `null` value is returned by the `Context` initialization function. `Main.java:14` checks for this situation and returns safely.

It's also possible that an exception could be thrown when trying to actually populate the `RandomAccessMemory` object with ELF file data. At `machine/VirtualMachine.java:23-26`, the `VirtualMachine` initialization function checks for whether the `RandomAccessMemory` population function had an exception. If so, the the `VirtualMachine` initialization function returns `null`. `Main.java:19` then checks for if the machine is `null` and returns safely if so.

## Score Summary

In short, I believe I have earned a full score, as reflected in the table below:

| Category | Max | Earned |
| - | - | - |
| A — Build and Structure| 0.5 | 0.5 |
|B — ELF Header Parsing| 2.5 | 2.5 |
| C — Program Header Parsing | 2.0 | 2.0 |
| D — Segment Loading & Zero-Fill | 3.0 | 3.0 |
| E — Entry Point| 1.0 | 1.0 |
| F — Robustness & Endianness| 1.0 | 1.0 |
| Total | 10.0 | 10.0 |
	