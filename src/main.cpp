#include "args.hpp"

int main(int argc, char **argv) {
    Args *args = process_args(argc, argv);
    args->print();
    free(args);
}