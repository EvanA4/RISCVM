# RISCVM

A Java-based RISC-V virtual machine.

## Setup

This project is based in both Java and Gradle. To run this project,

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

> [!WARNING]
> When running this program, both `gradlew` and `run.sh` and `run.bat`
> expect arguments. If no arguments are provided, the gradle process will error before reaching the virtual machine code.