# Build.md

A markdown file describing how to run the project. I'd encourage you to just read the `README.md` instead.

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