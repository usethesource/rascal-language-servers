module SingleModuleRenameTest

void t() {
  int foo = 8;
  bar := 9;
}

void main() {
    int foo(int y) {
        return y;
    }

    int foo(str s) {
        return s > "foo" ? 1 : 0;
    }

    foo("foo");

    int foo() { return 8; }
    int foo = 8;

    foo = foo();

    {
      int x = 8;
      x = 5;
      x = 6;

      int s = foo(x);
    }
}
