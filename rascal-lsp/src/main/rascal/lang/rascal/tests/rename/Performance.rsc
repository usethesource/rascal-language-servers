module lang::rascal::tests::rename::Performance

import lang::rascal::tests::rename::TestUtils;

int LARGE_TEST_SIZE = 200;
test bool largeTest() = ({0} | it + {foos + 3, foos + 4, foos + 5} | i <- [0..LARGE_TEST_SIZE], foos := 5 * i) == testRenameOccurrences((
    "int foo = 8;"
    | "<it>
      'int f<i>(int foo) = foo;
      'foo = foo + foo;"
    | i <- [0..LARGE_TEST_SIZE])
);
