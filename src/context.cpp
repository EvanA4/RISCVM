#include <cstring>
#include <cinttypes>
#include <optional>
#include <stdexcept>
#include <string>
#include <stdio.h>
#include <fstream>
#include <stdarg.h>
#include "context.hpp"
#include "util.hpp"

const char *VALID_ARGS[] = {
    "-f", "-i", "-o", "-m", "-a", "-mem", "-harts", "-hz", "-cL", "-c1",
    "-c2", "-c3", "-cS1", "-cS2", "-cS3", "-cB1", "-cB2", "-cB3", "-cW1", "-cW2",
    "-cW3", "-cE1", "-cE2", "-cE3", "-cC", "-bp", "-bpD", "-mmu", "-tlb", "-tlbE"
};
const int NUM_VALID_ARGS = sizeof(VALID_ARGS) / sizeof(char *);

bool Context::is_digit(char src) {
    return src >= '0' && src <= '9';
}

bool Context::is_letter(char src) {
    return (src >= 'a' && src <= 'z') || (src >= 'A' && src <= 'Z');
}

char Context::to_uppercase(char src) {
    if (src >= 'a' && src <= 'z') {
        return src - 'a' + 'A';
    }
    return src;
}

CacheCoherency Context::parse_cache_coherency(std::string value, std::string name) {
    if (!strcmp(value.c_str(), "none")) {
        return NONE;
    }
    else if (!strcmp(value.c_str(), "snoop")) {
        return SNOOP;
    }
    else if (!strcmp(value.c_str(), "dir")) {
        return DIR;
    }

    char msg[100];
    sprintf(msg, "Invalid argument for \"%s\": \"%s\"", name.c_str(), value.c_str());
    throw std::invalid_argument(msg);
}

EvictionPolicy Context::parse_eviction_policy(std::string value, std::string name) {
    if (!strcmp(value.c_str(), "fifo")) {
        return FIFO;
    }
    else if (!strcmp(value.c_str(), "lru")) {
        return LRU;
    }
    else if (!strcmp(value.c_str(), "lfu")) {
        return LFU;
    }

    char msg[100];
    sprintf(msg, "Invalid argument for \"%s\": \"%s\"", name.c_str(), value.c_str());
    throw std::invalid_argument(msg);
}

std::pair<CacheAssociativity, int> Context::parse_cache_type(std::string value, std::string name) {
    if (value.size() < 2LU) {
        char msg[100];
        sprintf(msg, "Invalid argument for \"%s\": \"%s\"", name.c_str(), value.c_str());
        throw std::invalid_argument(msg);
    }

    const char start[] = {value[0], value[1], '\0'};
    if (!strcmp(start, "dm")) {
        return std::pair<CacheAssociativity, int>(DIRECT_MAPPED, -1);
    }
    else if (!strcmp(start, "fa")) {
        return std::pair<CacheAssociativity, int>(FULL_ASSOC, -1);
    }
    else if (!strcmp(start, "sa")) {
        int ways;
        if (!sscanf(value.c_str(), "sa,%d", &ways)) {
            char msg[100];
            sprintf(msg, "Failed to parse numeric for \"%s\": \"%s\"", name.c_str(), value.c_str());
            throw std::invalid_argument(msg);
        }
        return std::pair<CacheAssociativity, int>(SET_ASSOC, ways);
    }

    char msg[100];
    sprintf(msg, "Invalid argument for \"%s\": \"%s\"", name.c_str(), value.c_str());
    throw std::invalid_argument(msg);
}

int Context::parse_int(std::string value, std::string name) {
    int output;
    if (!sscanf(value.c_str(), "%d", &output)) {
        char msg[100];
        sprintf(msg, "Failed to parse argument for \"%s\": \"%s\"", name.c_str(), value.c_str());
        throw std::invalid_argument(msg);
    }
    return output;
}

uint64_t Context::parse_metric(std::string value, std::string name) {
    // confirm every non-terminal char is digit
    for (uint64_t i = 0; i < value.size() - 1; ++i) {
        if (!is_digit(value[i])) {
            char msg[100];
            sprintf(msg, "Invalid numeric for argument \"%s\": \"%s\".", name.c_str(), value.c_str());
            throw std::invalid_argument(msg);
        }
    }

    // confirm terminal char is digit or letter
    char end = value[value.size() - 1];
    if (!is_digit(end) && !is_letter(end)) {
        char msg[100];
        sprintf(msg, "Invalid value for argument \"%s\": \"%s\".", name.c_str(), value.c_str());
        throw std::invalid_argument(msg);
    }

    // handle only digits
    uint64_t output;
    if (is_digit(end)) {
        sscanf(value.c_str(), "%lu", &output);
        return output;
    }

    // handle letter at end
    // - validate letter
    if (end != 'K' && end != 'M' && end != 'G') {
        char msg[100];
        sprintf(msg, "Invalid metric prefix for argument \"%s\": \"%c\".", name.c_str(), end);
        throw std::invalid_argument(msg);
    }

    // - parse numeric
    uint64_t numeric;
    sscanf(value.substr(0, value.size() - 1).c_str(), "%lu", &numeric);

    // - multiply by corresponding power of two
    switch (end) {
        case 'K':
            return numeric * 1'024;
        case 'M':
            return numeric * 1'048'576;
        case 'G':
            return numeric * 1'073'741'824;
    }

    char msg[100];
    sprintf(msg, "Error parsing metric value for argument \"%s\": \"%s\".", name.c_str(), value.c_str());
    throw std::invalid_argument(msg);
}

bool Context::parse_bool(std::string value, std::string name, const char *truthy, const char *falsey) {
    if (!strcmp(value.c_str(), truthy)) return true;
    else if (!strcmp(value.c_str(), falsey)) return false;
    char msg[100];
    sprintf(msg, "Argument for \"%s\" must be %s\\%s, not \"%s\".", name.c_str(), truthy, falsey, value.c_str());
    throw std::invalid_argument(msg);
}

void Context::read_command_line(std::vector<std::string> &vargs) {
    for (uint64_t i = 1; i < vargs.size(); i += 2) {
        const char *varg = vargs.at(i).c_str();

        // confirm argument is valid
        bool is_valid_arg = false;
        for (int j = 0; j < NUM_VALID_ARGS; ++j) {
            if (!strcmp(varg, VALID_ARGS[j])) {
                is_valid_arg = true;
                break;
            }
        }
        if (!is_valid_arg) {
            char msg[100];
            sprintf(msg, "Invalid argument: \"%s\".", varg);
            throw std::invalid_argument(msg);
        }

        // confirm space for additional argument
        if (i == vargs.size() - 1) {
            char msg[100];
            sprintf(msg, "Missing value for argument: \"%s\".", varg);
            throw std::invalid_argument(msg);
        }
        std::string next = vargs.at(i + 1);

        // unholy arg handling of dispair :(
        if (strcmp(varg, "-f") == 0) {
            config_file = next;
        }
        else if (strcmp(varg, "-i") == 0) {
            elf_file = next;
        }
        else if (strcmp(varg, "-o") == 0) {
            output_file = next;
        }
        else if (strcmp(varg, "-m") == 0) {
            allow_rv32m = parse_bool(next, varg, "on", "off");
        }
        else if (strcmp(varg, "-a") == 0) {
            allow_rv32a = parse_bool(next, varg, "on", "off");
        }
        else if (strcmp(varg, "-mem") == 0) {
            ram_size = parse_metric(next, "mem");
        }
        else if (strcmp(varg, "-harts") == 0) {
            num_harts = parse_int(next, "harts");
        }
        else if (strcmp(varg, "-hz") == 0) {
            cycle_frequency = parse_metric(next, "hz");
        }
        else if (strcmp(varg, "-cL") == 0) {
            cache_depth = parse_int(next, "cL");
        }
        else if (strcmp(varg, "-c1") == 0) {
            std::pair<CacheAssociativity, int> parsed = parse_cache_type(next, "c1");
            l1_cache.ca = parsed.first;
            l1_cache.ways = parsed.second;
        }
        else if (strcmp(varg, "-c2") == 0) {
            std::pair<CacheAssociativity, int> parsed = parse_cache_type(next, "c2");
            l2_cache.ca = parsed.first;
            l2_cache.ways = parsed.second;
        }
        else if (strcmp(varg, "-c3") == 0) {
            std::pair<CacheAssociativity, int> parsed = parse_cache_type(next, "c3");
            l3_cache.ca = parsed.first;
            l3_cache.ways = parsed.second;
        }
        else if (strcmp(varg, "-cS1") == 0) {
            l1_cache.size = parse_metric(next, "cS1");
        }
        else if (strcmp(varg, "-cS2") == 0) {
            l2_cache.size = parse_metric(next, "cS2");
        }
        else if (strcmp(varg, "-cS3") == 0) {
            l3_cache.size = parse_metric(next, "cS3");
        }
        else if (strcmp(varg, "-cB1") == 0) {
            l1_cache.block_size = parse_int(next, "cB1");
        }
        else if (strcmp(varg, "-cB2") == 0) {
            l2_cache.block_size = parse_int(next, "cB2");
        }
        else if (strcmp(varg, "-cB3") == 0) {
            l3_cache.block_size = parse_int(next, "cB3");
        }
        else if (strcmp(varg, "-cW1") == 0) {
            l1_cache.is_write_back = parse_bool(next, "cW1", "wb", "wt");
        }
        else if (strcmp(varg, "-cW2") == 0) {
            l2_cache.is_write_back = parse_bool(next, "cW2", "wb", "wt");
        }
        else if (strcmp(varg, "-cW3") == 0) {
            l3_cache.is_write_back = parse_bool(next, "cW3", "wb", "wt");
        }
        else if (strcmp(varg, "-cE1") == 0) {
            l1_cache.eviction = parse_eviction_policy(next, "cE1");
        }
        else if (strcmp(varg, "-cE2") == 0) {
            l2_cache.eviction = parse_eviction_policy(next, "cE2");
        }
        else if (strcmp(varg, "-cE3") == 0) {
            l3_cache.eviction = parse_eviction_policy(next, "cE3");
        }
        else if (strcmp(varg, "-cC") == 0) {
            cache_coherency = parse_cache_coherency(next, "cC");
        }
        else if (strcmp(varg, "-bp") == 0) {
            branch_prediction_rows = parse_int(next, "bp");
        }
        else if (strcmp(varg, "-bpD") == 0) {
            default_prediction = parse_int(next, "bpD");
        }
        else if (strcmp(varg, "-mmu") == 0) {
            allow_mmu = parse_bool(vargs.at(i + 1), varg, "on", "off");
        }
        else if (strcmp(varg, "-tlb") == 0) {
            num_tlb_slots = parse_int(next, "tlb");
        }
        else if (strcmp(varg, "-tlbE") == 0) {
            tlb_eviction = parse_eviction_policy(next, "tlbE");
        }
        else {
            // should never be reached
            char msg[100];
            sprintf(msg, "Invalid argument: \"%s\".", varg);
            throw std::invalid_argument(msg);
        }
    }
}

std::optional<std::string> Context::get_config_file(std::vector<std::string> &vargs) {
    std::optional<std::string> output = std::nullopt;
    for (uint64_t i = 0; i < vargs.size(); ++i) {
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

void Context::read_config_file(std::string file_path) {
    std::ifstream file(file_path);
    if (!file) {
        char msg[100];
        sprintf(msg, "Failed to access file: \"%s\".", file_path.c_str());
        throw std::runtime_error(msg);
    }
    
    // process file into fake command-line arguments
    std::vector<std::string> vargs;
    vargs.push_back("fake argv[0]");
    std::string line;
    while (std::getline(file, line)) {
        // ignore comments and strip
        const uint64_t ignore_idx = line.find("#");
        if (ignore_idx != std::string::npos) {
            line = line.substr(0, ignore_idx);
        }
        line = strstrip(line);
        if (line.empty()) continue;
        
        // guaranteed nonempty line
        std::vector<std::string> words = strsplit(line, " ");
        if (words.size() != 2) {
            char msg[100];
            sprintf(msg, "Invalid config file line: \"%s\".", line.c_str());
            throw std::runtime_error(msg);
        }

        words[0] = "-" + words[0];
        vargs.push_back(words[0]);
        vargs.push_back(words[1]);
    }

    // amend args object w/ command-line arg parser
    read_command_line(vargs);
}

Context::Context(int argc, char **argv) {
    // convert args to vector format
    std::vector<std::string> vargs;
    for (int i = 0; i < argc; ++i) {
        vargs.push_back(argv[i]);
    }

    // update defaults with provided values
    std::optional<std::string> cf = get_config_file(vargs);
    if (cf.has_value()) read_config_file(cf.value());
    read_command_line(vargs);

    // check whether ELF file was set
    if (!elf_file.has_value()) {
        throw std::invalid_argument("Missing ELF file argument.");
    }

    // open output file in logger
    if (output_file.has_value()) {
        logger_.open(output_file.value());
    }
}

int Context::log(const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = logger_.log(format, args);
    va_end(args);
    return result;
}

void Context::dump() {
    CacheType caches[] = {l1_cache, l2_cache, l3_cache};
    const char *assocs[] = {"DIRECT_MAPPED", "SET_ASSOC", "FULL_ASSOC"};
    const char *evicts[] = {"FIFO", "LRU", "LFU"};
    const char *coherencies[] = {"NONE", "SNOOP", "DIR"};

    log("##### CONTEXT DUMP #####\n");
    log("Config file: %s\n", config_file.has_value() ? config_file.value().c_str() : "null");
    log("ELF file: %s\n", elf_file.has_value() ? elf_file.value().c_str() : "null");
    log("Output file: %s\n", output_file.has_value() ? output_file.value().c_str() : "null");
    log("Allow RV32M: %s\n", allow_rv32m ? "true" : "false");
    log("Allow RV32A: %s\n", allow_rv32a ? "true" : "false");
    log("RAM size: %" PRIu64 "\n", ram_size);
    log("Number of harts: %d\n", num_harts);
    log("Cycle frequency: %d\n", cycle_frequency);
    log("Cache depth: %d\n", cache_depth);
    for (int i = 0; i < 3; ++i) {
        log("L%d Cache:\n", i + 1);
        log("\tAssociativity: %s\n", assocs[caches[i].ca]);
        log("\tWays: %d\n", caches[i].ways);
        log("\tSize: %" PRIu64 "\n", caches[i].size);
        log("\tBlock size: %d\n", caches[i].block_size);
        log("\tWrite policy: %s\n", caches[i].is_write_back ? "write-back" : "write-through");
        log("\tEviction policy: %s\n", evicts[caches[i].eviction]);
    }
    log("Cache coherency: %s\n", coherencies[cache_coherency]);
    log("Branch prediction rows: %d\n", branch_prediction_rows);
    log("Default preditction: %d\n", default_prediction);
    log("Allow MMU: %s\n", allow_mmu ? "true" : "false");
    log("Number of TLB slots: %d\n", num_tlb_slots);
    log("TLB eviction Policy: %s\n", evicts[tlb_eviction]);
    log("##### CONTEXT DUMP #####\n");
}