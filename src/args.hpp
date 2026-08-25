#include <optional>
#include <string>

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
    int size;
    int block_size;
    bool is_write_back;
    EvictionPolicy eviction;
};

struct Args {
    std::optional<std::string> config_file = std::nullopt;
    std::optional<std::string> elf_file = std::nullopt;
    std::optional<std::string> output_file = std::nullopt;
    bool allow_rv32m = true;
    bool allow_rv32a = true;
    int ram_size = 1'048'576;
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

    void print() {

    }
};

Args* process_args(int argc, char **argv);