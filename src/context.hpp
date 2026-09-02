#include <cstdint>
#include <optional>
#include <string>
#include <vector>
#include "log.hpp"

enum CacheAssociativity {
  DIRECT_MAPPED, SET_ASSOC, FULL_ASSOC  
};

enum EvictionPolicy {
    FIFO, LRU, LFU
};

enum CacheCoherency {
    NONE, SNOOP, DIR
};

struct CacheType {
    CacheAssociativity ca;
    int ways;
    uint64_t size;
    int block_size;
    bool is_write_back;
    EvictionPolicy eviction;
};

class Context {
    public:
        std::optional<std::string> config_file = std::nullopt;
        std::optional<std::string> elf_file = std::nullopt;
        std::optional<std::string> output_file = std::nullopt;
        bool allow_rv32m = true;
        bool allow_rv32a = true;
        uint64_t ram_size = 1'048'576;
        int num_harts = 4;
        int cycle_frequency = 100;
        int cache_depth = 3;
        CacheType l1_cache = {
            DIRECT_MAPPED,
            -1,
            1024,
            4,
            false,
            FIFO
        };
        CacheType l2_cache = {
            SET_ASSOC,
            3,
            2048,
            5,
            false,
            LRU
        };
        CacheType l3_cache = {
            FULL_ASSOC,
            -1,
            4096,
            6,
            true,
            LFU
        };
        CacheCoherency cache_coherency = SNOOP;
        int branch_prediction_rows = 64;
        int default_prediction = 0;
        bool allow_mmu = true;
        int num_tlb_slots = 8;
        EvictionPolicy tlb_eviction = LRU;

        Context(int argc, char **argv);
        int log(const char *format, ...);
        void dump();

        Logger logger;
        
    private:
        bool is_digit(char src);
        bool is_letter(char src);
        char to_uppercase(char src);
        CacheCoherency parse_cache_coherency(std::string value, std::string name);
        EvictionPolicy parse_eviction_policy(std::string value, std::string name);
        std::pair<CacheAssociativity, int> parse_cache_type(std::string value, std::string name);
        int parse_int(std::string value, std::string name);
        uint64_t parse_metric(std::string value, std::string name);
        bool parse_bool(std::string value, std::string name, const char *truthy, const char *falsey);
        void read_command_line(std::vector<std::string> &vargs);
        std::optional<std::string> get_config_file(std::vector<std::string> &vargs);
        void read_config_file(std::string file_path);
};
