module demo::lang::picoish::Syntax

import ParseTree;

lexical Id = [a-z][a-z0-9]* !>> [a-z0-9];

// Pico-ish: Weird highlighting of numbers
lexical Natural = Digit+;
lexical Digit   = [02468] | @category="string" Odd;
lexical Odd     = [1357]  | @category="comment" Nine;
lexical Nine    = [9];

lexical String = "\"" ![\"]*  "\"";

layout Layout = WhitespaceAndComment* !>> [\ \t\n\r%];

lexical WhitespaceAndComment
   = [\ \t\n\r]
   | @category="Comment" ws2: "%" ![%]+ "%"
   | @category="Comment" ws3: "%%" ![\n]* $
   ;

start syntax Program
   = program: "begin" Declarations decls {Statement  ";"}* body "end" ;

syntax Declarations
   = "declare" {Declaration ","}* decls ";" ;

syntax Declaration = decl: Id id ":" Type tp;

// Pico-ish: Types are highlighted
syntax Type
    = @category="storage.type" natural:"natural"
    | @category="storage.type" string :"string"
    ;

syntax Statement
    = asgStat: Id var ":="  Expression val
    | ifElseStat: "if" Condition cond "then" {Statement ";"}*  thenPart "else" {Statement ";"}* elsePart "fi"
    | whileStat: "while" Condition cond "do" {Statement ";"}* body "od"
    ;

// Pico-ish: Conditions are highlighted
syntax Condition
    = @category="string.regexp" Expression;

// Pico-ish: Expressions are intentionally ambiguous (for testing purposes)
syntax Expression
    = Constant
    | bracket "(" Expression e ")"
    | conc: Expression lhs "||" Expression rhs
    | add: Expression lhs "+" Expression rhs
    | sub: Expression lhs "-" Expression rhs
    ;

// Pico-ish: Constants are highlighted
syntax Constant
    = @category="variable.other" id: Id name
    | @category="string.quoted.double" strcon: String string
    | @category="constant.numeric" natcon: Natural natcon
    | RegExp
    ;

// Pico-ish: Regular expressions with special highlighting
lexical RegExp
    = @category="string.regexp" "/" RegExpBody "/";

lexical RegExpBody
    = RegExpBody "?"
    | RegExpBody "+"
    | RegExpBody "*"
    | "(" RegExpBody ")"
    | @category="string.unquoted" Char
    > left RegExpBody RegExpBody
    ;

lexical Char = [a-z];

start[Program] program(str s) {
  return parse(#start[Program], s);
}

start[Program] program(str s, loc l) {
  return parse(#start[Program], s, l);
}
