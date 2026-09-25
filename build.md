# Build.md

A markdown file describing how to run the project. I'd encourage you to just read the `README.md` instead.

## Prerequisites

This project is based in both Java and Gradle. That said, you will need
Java 26 installed on your computer.

## Locations of Files

For all files uploaded through Canvas:

- if the file was modified below, replace it.
- if the file was also moved, move the file to the target location
- if a file was added, add the file to the target location

Note that, for some of these files, you may need to create new directories.

### File Updates from Part 2
- `.gitignore` was modified
- `defense/RUBRIC.2.md` was added
- `src/main/java/net/eabbott/riscvm/Main.java` was modified
- `src/main/java/net/eabbott/riscvm/context/AbstractContext.java` was modified
- `src/main/java/net/eabbott/riscvm/context/Context.java` was modified
- `src/main/java/net/eabbott/riscvm/context/CacheAssociativity.java` was modified and moved to `src/main/java/net/eabbott/riscvm/context/cache/CacheAssociativity.java`
- `src/main/java/net/eabbott/riscvm/context/CacheAssociativityType.java` was modified and moved to `src/main/java/net/eabbott/riscvm/context/cache/CacheAssociativityType.java`
- `src/main/java/net/eabbott/riscvm/context/CacheCoherency.java` was modified and moved to `src/main/java/net/eabbott/riscvm/context/cache/CacheCoherency.java`
- `src/main/java/net/eabbott/riscvm/context/CacheContext.java` was modified and moved to `src/main/java/net/eabbott/riscvm/context/cache/CacheContext.java`
- `src/main/java/net/eabbott/riscvm/context/EvictionPolicy.java` was modified, renamed, and moved to `src/main/java/net/eabbott/riscvm/context/cache/CacheEvictionPolicy.java`
- `src/main/java/net/eabbott/riscvm/context/elf/ELFContext.java` was added
- `src/main/java/net/eabbott/riscvm/context/elf/ELFHeader.java` was added
- `src/main/java/net/eabbott/riscvm/context/elf/ELFProgramHeader.java` was added
- `src/main/java/net/eabbott/riscvm/machine/Hart.java` was modified
- `src/main/java/net/eabbott/riscvm/machine/RandomAccessMemory.java` was modified
- `src/main/java/net/eabbott/riscvm/machine/VirtualMachine.java` was modified
- `src/main/java/net/eabbott/riscvm/util/BinaryUtil.java` was modified

> [!TIP]
> Alternatively, you can just clone the repository from my project's ["part-2" branch](https://github.com/EvanA4/RISCVM/tree/part-2).

## How to Run

To run through custom script (recommended):
```bash
# In Linux/MacOS
./run.sh arg1 arg2 ... argN

# In Windows
run.bat arg1 arg2 ... argN
```

To run directly through Gradle:
```bash
# In Linux/MacOS
./gradlew run --args="arg1 arg2 ... argN"

# In Windows
gradlew run --args="arg1 arg2 ... argN"
```

To run via JAR, build the project with Gradle and run the JAR with Java:
```bash
# In Linux/MacOS
./gradlew build

# In Windows
gradlew build

# ...And finally
java -jar build/libs/RISCVM-1.0-SNAPSHOT.jar arg1 arg2 ... argN
```

> [!WARNING]
> When running this program with the scripts,`run.sh` and `run.bat`
> expect arguments. If no arguments are provided, the Gradle process will
> error before reaching the virtual machine code.