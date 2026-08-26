CXX := clang++
CXXFLAGS := -std=c++17 -Wall -Wextra

TARGET := bin/vm
SOURCES := src/log.cpp src/util.cpp src/context.cpp src/main.cpp
OBJECTS := bin/log.o bin/util.o bin/context.o bin/main.o

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(OBJECTS)
	@mkdir -p bin
	$(CXX) $(OBJECTS) -o $@
	$(CXX) $(OBJECTS) -o $@.exe

bin/log.o: src/log.cpp src/log.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/util.o: src/util.cpp src/util.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/context.o: src/context.cpp src/context.hpp src/log.hpp src/util.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/main.o: src/main.cpp src/context.hpp
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
	rm -f $(TARGET) $(TARGET).exe $(OBJECTS)
