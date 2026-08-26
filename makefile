CXX := clang++
CXXFLAGS := -std=c++17 -Wall -Wextra

TARGET := bin/vm
SOURCES := src/util.cpp src/args.cpp src/main.cpp
OBJECTS := bin/util.o bin/args.o bin/main.o

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(OBJECTS)
	@mkdir -p bin
	$(CXX) $(OBJECTS) -o $@
	$(CXX) $(OBJECTS) -o $@.exe

bin/util.o: src/util.cpp src/util.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/args.o: src/args.cpp src/args.hpp src/util.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/main.o: src/main.cpp src/args.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
	rm -f $(TARGET) $(TARGET).exe $(OBJECTS)
