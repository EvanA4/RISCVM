package net.eabbott.riscvm.context;

/**
 * An enum representing the different levels of logging for messages.
 */
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