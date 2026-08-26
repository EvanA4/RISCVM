#include <cstddef>
#include <cstring>
#include <optional>
#include <stdexcept>
#include <string>
#include <stdio.h>
#include <fstream>
#include "args.hpp"
#include "util.hpp"

void read_command_line(Args *args, std::vector<std::string> vargs) {
    printf("read_command_line: %lu args\n", vargs.size());
    // pain and suffering
}

std::optional<std::string> get_config_file(std::vector<std::string> vargs) {
    std::optional<std::string> output = std::nullopt;
    for (size_t i = 0; i < vargs.size(); ++i) {
        if (!std::strcmp("-f", vargs.at(i).c_str())) {
            if (i != vargs.size() - 1) {
                output = std::string(vargs.at(i+1).c_str());   
            } else {
                throw std::invalid_argument("Missing file name for configuration file.");
            }
        }
    }
    return output;
}

void read_config_file(Args *args, std::string file_path) {
    std::ifstream file(file_path);
    if (!file) {
        // throw error
        return;
    }
    
    // process file into fake command-line arguments
    std::vector<std::string> vargs;
    std::string line;
    while (std::getline(file, line)) {
        // ignore comments and strip
        const size_t ignore_idx = line.find("#");
        if (ignore_idx != std::string::npos) {
            line = line.substr(0, ignore_idx);
        }
        line = strstrip(line);
        if (line.empty()) continue;
        
        // guaranteed nonempty line
        std::vector<std::string> words = strsplit(line, " ");
        if (words.size() != 2) {
            // throw error
        }

        words[0] = "-" + words[0];
        vargs.push_back(words[0]);
        vargs.push_back(words[1]);
    }

    // amend args object w/ command-line arg parser
    read_command_line(args, vargs);
}

Args* process_args(int argc, char **argv) {
    // convert args to vector format
    std::vector<std::string> vargs;
    for (int i = 0; i < argc; ++i) {
        vargs.push_back(argv[i]);
    }

    Args *args = new Args();
    std::optional<std::string> cf = get_config_file(vargs);
    if (cf.has_value()) read_config_file(args, cf.value());
    read_command_line(args, vargs);
    return args;
}