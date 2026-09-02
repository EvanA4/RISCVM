#include <cinttypes>
#include <string>
#include <vector>
#include "util.hpp"



// practical



bool is_whitespace(char src) {
    return src == ' ' || src == '\t' || src == '\n';
}

std::string strstrip(std::string src) {
    int start = 0;
    int end = (int) src.size() - 1;
    while (end >= 0 && is_whitespace(src[end])) --end;
    if (end < 0) return "";
    while (start != (int) src.size() && is_whitespace(src[start])) ++start;
    return src.substr(start, end + 1 - start);
}

std::vector<std::string> strsplit(std::string src, std::string delimiter) {
    std::vector<std::string> output;
    size_t idx;
    while ((idx = src.find(delimiter)) != std::string::npos) {
        if (idx != 0) {
            output.push_back(src.substr(0, idx));
        }
        src = src.substr(
            idx + delimiter.size(),
            src.size() - idx - delimiter.size()
        );
    }
    output.push_back(src);
    return output;
}

int32_t signext(int32_t src, int32_t idx) {
    // set bits after idx to 0
    // (accounts for when src is positive)
    src &= (1 << (idx + 1)) - 1;

    // create mask with all bits after idx as 1
    int32_t mask = -1;
    mask ^= (1 << idx) - 1;

    // set mask to 0 if src is positive
    mask *= (src >> idx) & 1;

    // put mask on src
    return src | mask;
}