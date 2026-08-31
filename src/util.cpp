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



// debugging



void print_strs(std::vector<std::string> src) {
    for (uint64_t i = 0; i < src.size(); ++i) {
        printf("[%" PRIu64 "] \"%s\"\n", i, src[i].c_str());
    }
}