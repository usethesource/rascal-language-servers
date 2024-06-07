module SingleModuleRenameTest

int main() {
    int foo(int i) {
        return i;
    }

    int foo(str s) {
        return s > "foo" ? 1 : 0;
    }

    int x = 8;
    x = 5;
    x = 6;

    int s = foo(x);
    foo("foo");

    return s;
}
