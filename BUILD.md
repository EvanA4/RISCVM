# RISCVM

A Java-based virtual machine for the RISCV32IMA instruction set architecture.

## Prerequisites

This project is based in both Java and Gradle. That said, you will need
Java 26 installed on your computer.

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

## Logging

Logging can be turned on using the `log <level>` flag (e.g., `log v`). Here are the different valid arguments:

| Logging Argument | What it Enables |
| - | - |
| v | VERBOSE |
| d | DEBUG |
| i | INFO |
| w | WARNING |
| e | ERROR |
| f | FATAL |
| s| SILENT |

Note that each logging type also includes all of the levels of logging below it. For example, providing "e" as `-log e` on the command line would enable both ERROR and FATAL levels of logging. SILENT disables all logging and is the default.

## Mandatory Project Details

To avoid deadlocks, this project employs the following rules:
- The memory seam acquires locks starting from the outside: `L1 -> L2 -> L3 -> RAM`.
- When locking by address, locks are acquired in ascending address/index order.