module RenameTest

int main() {
    int foo(int i) {
        return 8;
    }

    int foo(str s) {
        return 8;
    }

    int x = 8;
    x = 5;
    x = 6;

    int s = foo(x);
    foo("foo");

    return s;
}
