# RISCVM

A Java-based RISC-V virtual machine.

## Setup

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

To run via JAR:
```bash
# In Linux/MacOS
./gradlew build

# In Windows
gradlew build

# ...And finally
java -jar build/libs/RISCVM-1.0-SNAPSHOT.jar arg1 arg2 ... argN
```

> [!WARNING]
> When running this program, both `gradlew` and `run.sh` and `run.bat`
> expect arguments. If no arguments are provided, the gradle process will 
> error before reaching the virtual machine code.