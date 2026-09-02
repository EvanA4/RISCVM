#include <cstdint>
#include <string>
#include <cstdarg>
#include <stdexcept>
#include <stdarg.h>
#include "log.hpp"


void Logger::open(std::string file_name) {
    file_name_ = file_name;

    // close existing file
    fclose(fout);

    // open new file
    const char *fn = file_name.c_str();
    fout = fopen(fn, "w");
    if (!fout) {
        char msg[100];
        sprintf(msg, "Failed to open file: \"%s\"", fn);
        throw std::runtime_error(msg);
    }
}

int Logger::log(const char* format, ...) {
    if (!fout) {
        throw std::runtime_error("Trying to print with an invalid Logger.");
    }
    va_list args;
    va_start(args, format);
    int32_t result = std::vfprintf(fout, format, args);
    va_end(args);
    return result;
}

void log_binary(Logger logger, void *src, int32_t size, int32_t bytes_per_line, bool reverse) {
    char *csrc = (char *) src;

    if (reverse) {
        for (int32_t i = size - 1; i >= 0; --i) {
            char byte = csrc[i];
            for (int32_t j = 7; j >= 0; --j) {
                logger.log("%d", (int) (byte >> j));
            }
            logger.log(" ");
    
            if ((((size - i) % bytes_per_line) == 0)) {
                logger.log("\n");
            }
        }

    } else {
        for (int32_t i = 0; i < size; ++i) {
            char byte = csrc[i];
            for (int32_t j = 0; j < 8; ++j) {
                logger.log("%d", (int32_t) (byte >> j));
                byte >>= 1;
            }
    
            if (i > 0 && i % bytes_per_line == bytes_per_line - 1) {
                logger.log("\n");
            }
        }
    }

    if (size % bytes_per_line != 0) {
        logger.log("\n");
    }
}