#include <cstdint>
#include <cstdlib>
#include <stdexcept>
#include "context.hpp"
#include "util.hpp"

int main(int argc, char **argv) {
    try {
        Context context = Context(argc, argv);
        context.dump();
        int32_t *num = (int32_t *) malloc(sizeof(int32_t));
        for (int i = 0; i < 30; ++i) {
            *num = 1 << i;
            log_binary(context.logger, num, sizeof(int32_t));
        }
        free(num);
    } catch (std::invalid_argument &e) {
        printf("Error: %s\n", e.what());
        return -1;
    } catch (std::runtime_error &e) {
        printf("Error: %s\n", e.what());
        return -1;
    }
}