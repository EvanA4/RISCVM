#include <cstring>
#include <optional>
#include <stdexcept>
#include <string>
#include <stdio.h>
#include <fstream>
#include "args.hpp"

std::optional<std::string> get_config_file(int argc, char **argv) {
    std::optional<std::string> output = std::nullopt;
    for (int i = 0; i < argc; ++i) {
        if (!std::strcmp("-f", argv[i])) {
            if (i != argc - 1) {
                output = std::string(argv[i+1]);   
            } else {
                throw std::invalid_argument("Missing file name for configuration file.");
            }
        }
    }
    return output;
}

void read_config_file(Args *args, std::string file_path) {
    std::ifstream file("input.txt");
    if (!file) {
        // throw error
        return;
    }
    
    // process file into fake command-line arguments
    std::string sum, line;
    while (std::getline(file, line)) {
        const size_t ignore_idx = line.find("#");
        if (ignore_idx != std::string::npos) {
            line = line.substr(0, ignore_idx);
        }

        // strip
        // split by space, filter out empty strings
        // add hyphen to first word in line?
    }

    // amend args object w/ command-line arg parser
}

void read_command_line(Args *args, int argc, char **argv) {
    printf("read_command_line: %d args\n", argc);
    // pain and suffering
}

Args* process_args(int argc, char **argv) {
    Args *args = new Args();
    std::optional<std::string> cf = get_config_file(argc, argv);
    if (cf.has_value()) read_config_file(args, cf.value());
    read_command_line(args, argc, argv);
    return args;
}