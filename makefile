all:
	clang -Wall -Wextra src/main.cpp -o bin/vm.exe
clean:
	rm bin/vm.exe