#include <string>   

class Logger {
    private:
        std::string file_name_;
        FILE *fout = stdout;
    
    public:
        void open(std::string file_name);
        int log(const char *format, va_list args);
};