#include <iostream>
#include <thread>
#include <mutex>
using namespace std;

mutex mtx;
int ctr = 0;

void increment() {
    mtx.lock();
    for (int i = 0; i < 1'000'000; ++i) ++ctr;
    mtx.unlock();
}

int main() {
    thread t1(increment);
    thread t2(increment);

    t1.join();
    t2.join();

    printf("Counter: %d\n", ctr);
}