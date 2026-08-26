#include <iostream>
#include <optional>
#include <string>   

class Logger {
    private:
        std::string file_name_;
        FILE *fout = stdout;
    
    public:
        void open(std::string file_name);
        int log(_In_z_ _Printf_format_string_ char const* const format, ...);
};

/*
int proxy_printf(const char* format, ...) {
    va_list args;
    va_start(args, format);

    int result = std::vprintf(format, args);

    va_end(args);
    return result;
}
*/