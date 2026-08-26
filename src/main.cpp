#include <stdexcept>
#include "context.hpp"

int main(int argc, char **argv) {
    try {
        Context context = Context(argc, argv);
        context.dump();
    } catch (std::invalid_argument &e) {
        printf("Error: %s\n", e.what());
        return -1;
    } catch (std::runtime_error &e) {
        printf("Error: %s\n", e.what());
        return -1;
    }
}