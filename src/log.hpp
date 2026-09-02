#include <cstdint>
#include <string>
#include <stdarg.h>
class Logger {
    private:
        std::string file_name_;
        FILE *fout = stdout;
    
    public:
        void open(std::string file_name);
        int log(const char *format, ...);
};

void log_binary(Logger logger, void *src, int32_t size, int32_t bytes_per_line = 4, bool reverse = true);