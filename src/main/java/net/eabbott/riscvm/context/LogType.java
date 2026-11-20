package net.eabbott.riscvm.context;

public enum LogType {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL,
    SILENT;

    public boolean isGTE(LogType second) {
        return this.ordinal() >= second.ordinal();
    }
}