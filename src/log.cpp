#include <string>
#include <cstdarg>
#include <stdexcept>
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

int Logger::log(const char* format, va_list args) {
    if (!fout) {
        throw std::runtime_error("Trying to print with an invalid Logger.");
    }
    return std::vfprintf(fout, format, args);
}