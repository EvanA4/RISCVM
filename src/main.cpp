#include <stdexcept>
#include "args.hpp"

int main(int argc, char **argv) {
    Args *args;
    try {
        args = process_args(argc, argv);
    } catch (std::invalid_argument &e) {
        printf("Error: %s\n", e.what());
    } catch (std::runtime_error &e) {
        printf("Error: %s\n", e.what());
    }

    args->print();
    free(args);
}