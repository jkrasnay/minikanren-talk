## miniKanren and The Reasoned Schemer

![The Reasoned Schemer](the-reasoned-schemer-cover.jpg)

---
## Agenda

* Programming Models
* miniKanren Summary
* The Reasoned Schemer
* Primer: Lisp Syntax
* Basics
* Lists
* Defining Relations
* Numbers
* Implementation
* Applications


---
## Programming Models

### Imperative

"Normal" programming languages

    list = [1 2 3]
    result = []
    for (int i = 0; i < list.length; i++) {
        result.append(list[i] * 2)
    }

### Functional

Languages based on the notion of a mathematical function.

    (def list [1 2 3])
    (map (fn [x] (* 2 x)) list)

### Declarative

Declare what you want, let the system figure out how to get it.

    select emp.name, dept.name
    from emp
    join dept on emp.dept_id = dept.id

> **NOTE**
>
> These categories are blurry!  Few languages fit cleanly into one of the categories.
>
> For example, when you write `1 + 2 * 3` in an imperative language, you don't tell
> it to do the `2 * 3` first.  The system figures it out based on order-of-operations
> rules.  This is a declarative model.
>
> Even in C, when we say `int x`, we're declaring that we want a place to store an integer.
> The compiler may choose to allocate that on the stack, or may reserve a register to store
> `x`.  It could even ignore us completely if the optimizer determines that `x` is not
> needed.  Even C has declarative aspects!

---
## miniKanren Summary

miniKanren is a _logic programming system_, a form of declarative language based on logic.

It finds values for one or more _logic variables_ that satisfy a given set of _goals_.


---
## The Reasoned Schemer

Like other books in the series, The Reasoned Schemer uses the Socratic method
of asking questions to lead the student to learn by their own answers.

Of course, they're not _our_ answers.  The book provides them too.

As a group, we read this with an editor covering the answer, to give us a
chance to guess at the answer first.

![Book with Editor](with-editor.png)

This format is very well suited for group reading. It takes some time to get used
to it, but I found it quite effective.


---
## Primer: Lisp Syntax

miniKanren was originally implemented in Scheme and uses Lisp syntax.

* Function calls are represented by lists, e.g. `(print "foo")` vs. `print(foo)`.
* Symbols like `foo` are typically dereferenced to yield an underlying value or function.
* Symbols can have some characters that would be illegal in most languages.  For example, `foo-bar`, `run*`, and `nil?` are all valid symbols.
* Use single quote to avoid dereferencing.  `'tomato` refers to the symbol `tomato` by itself, rather than the value to which it refers.
* Lists can be quoted too, to avoid the function call _and_ to avoid dereferencing symbols in the list: `'(print "foo")` is just a list.
* In some lists (e.g. Racket), square brackets `[]` can be used instead of parens.  In Clojure, they represent a different data structure (vectors) but the distinction doesn't matter for us.



---
## Basics

We will be using Clojure's `core.logic` as our miniKanren engine.

```
(require '[clojure.core.logic :refer :all])
```

```
(run* [q]
  (== 'olive q))
```

* `(== 'olive q)` is a _goal_.  It can either succeed or fail.
* `==` succeeds if its arguments can be _unified_, or "matched up".
  Here it binds the _logic variable_ `q` to the symbol `olive`.
* The `run*` macro takes a list of logic variables an one or more rules and
	returns zero or more sets of bindings that satisfy all of the goals.
* `run*` joins its goals via conjunction, or with AND logic.

```
(run* [q]
  (== 'olive 'oil))
```

Here `(== 'olive 'oil)` fails, since its arguments are not equal.

```
(run* [q]
  (== 'olive 'olive))
```

In this case the goal succeeds regardless of the value of `q`. The `_0`
value indicates that `q` can take any value.

```
(run* [q]
  (== 'olive q)
  (== 'oil q))
```

`run*` can take many goals The above returns an empty list, since no value of `q` can satisfy both goals.


---
## Lists

`append` (or `concat` in Clojure) is a _function_ that appends two lists.

```
(do (def append concat)
    (append '(a b) '(c d e)))
```

`appendo` is a _relation_ that succeeds if the first two arguments, when appended, yield the third argument.

```
(run* [q]
  (appendo '(a b) '(c d e) q))
```

Relations have one more argument than their equivalent function.  By convention, the value
that would be the result of the function is the last argument of the relation.

The cool thing about relations is that you can run them backwards!

```
(run* [q]
  (appendo q '(c d e) '(a b c d e)))
```

Read: "to what do I need to append '(c d e) to get '(a b c d e)?"

Even better: miniKanren can find us _all_ the lists that could possibly make up '(a b c d e)

```
(run* [q r]
  (appendo q r '(a b c d e)))
```


---
## Defining Relations

`appendo` is built into `clojure.core.logic`, but we can build our own relations.

Lisps have the function `cons`, which attaches a new value to the head of a list:

```
(cons 'a '(b c d))
```

`clojure.core.logic` has an equivalent relation, `conso`, that takes three
arguments: the first item, the existing list, and the result list. This is a
common pattern, a relation that is similar to a corresponding function but has
an additional function representing what would be the return value of the
function.

```
(run* [q]
  (conso 'a '(b c d) q))
```

Lisps also have the functions `car` and `cdr` that return the head and the tail of a list, respectively:

```
{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def car first)
(def cdr rest)
(defmacro defrel
  [rel arglist & goals]
  `(defn ~rel ~arglist
     (fn
       [s#]
       (fn
         []
         ((conde [~@goals]) s#)))))
{:nextjournal.clerk/visibility {:code :show :result :show}}
```

```
(car '(a b c d))
(cdr '(a b c d))
```

`clojure.core.logic` doesn't have `caro` and `cdro`, but we can build them using `conso`.

```
(defrel caro [l a]
  (fresh [d]
    (conso a d l)))
```

* `defrel`† is used to create a new relation.
* `car` takes a list and returns the first element. `caro` takes the list _and_ the first element.
* `fresh` introduces new logic variables. Here we don't care about the tail of the list, but we need
  a spare logic variable to hold the tail when we call `conso`.

It looks like we are using `conso` "in reverse" to figure out the `car` from
the list. But "in reverse" is the wrong way to think of it.  Better to think:
`caro` succeeds if `a` is the `car` of `l`.

```
(run* [q]
  (caro '(a b c d) q))
```

The definition of `cdro` is almost identical.  We just swap the `a` and `d` variables.

```
(defrel cdro [l d]
  (fresh [a]
    (conso a d l)))

(run* [q]
  (cdro '(a b c d) q))
```

†`defrel` is in the book but not in `clojure.core.logic`. We can define it with a macro as follows:

    (defmacro defrel
      [rel arglist & goals]
      `(defn ~rel ~arglist
         (fn
           [s#]
           (fn
             []
             ((conde [~@goals]) s#)))))


---
## Numbers

You might think miniKanren could do math like this:

    (run* [x]
      (+o 2 2 x))

You would be wrong. miniKanren is based on unification based on lists, or
matching up lists with one another.

But wait! The Reasoned Schemer shows how this can be done by representing
numbers in binary using strings of `0` and `1`.

They start by implementing bitwise AND and XOR relations.  `x` and `y` are the
arguments and `r` is the result.

```
(defrel bit-xoro [x y r]
  (conde
    [(== 0 x) (== 0 y) (== 0 r)]
    [(== 0 x) (== 1 y) (== 1 r)]
    [(== 1 x) (== 0 y) (== 1 r)]
    [(== 1 x) (== 1 y) (== 0 r)]))

(run* [x y r]
  (bit-xoro x y r))
```

```
(defrel bit-ando [x y r]
  (conde
    [(== 0 x) (== 0 y) (== 0 r)]
    [(== 0 x) (== 1 y) (== 0 r)]
    [(== 1 x) (== 0 y) (== 0 r)]
    [(== 1 x) (== 1 y) (== 1 r)]))
```

(Note `conde` works like an OR of ANDs: it succeeds if any of its children
succeed, and each successful child contributes to the result.)

They then use those to build `half-addero` which implements a _half adder_, a
component that adds two bits and returns a result bit and a carry bit.

```
(defrel half-addero [x y r c]
  (bit-xoro x y r)
  (bit-ando x y c))

(run* [x y r c]
  (half-addero x y r c))
```

The book goes on to implement `full-addero`, then applies that to lists of bits
to eventually define `+o`, which adds two numbers expressed as lists of bits.
It works like this.

    (run* [q]
      (+o '(1 0 1) '(0 1 1) q)) ; => ((1 1 0 1))

Read this as `5 + 6 = 11`, or `101b + 110b = 1011b`.  Note that the bits are in
reverse order in the lists, with the least-significant bit first.

Note that `+o` can also do subtraction.

    (run* [q]
      (+o q '(0 1 1) '(1 1 0 1))) ; => ((1 0 1))

Asking, "what is 11 minus 6" is the same as asking, "what, when added to 6, equals 11".

But there's even more!

The book builds on `+o` to define relations for multiplication, division,
exponentiation, and even logarithms.

This, however, is the longest and most complex part of the book. Not for the faint of heart!


---
## Implementation

The miniKanren implementation in the book is surprisingly simple.

* A _logic variable_ (lvar) is represented as a symbol in a vector (to distinguish it from a symbol as a value).
* A _substitution_ is a mapping from logic variables to values, other logic variables, or lists of both to which they've been unified.
* A _stream_ is a list of substitutions, a function that returns a stream (called a _suspension_), or a list of substitutions with a suspension as its tail.
* A _goal_ is a function of a substition that returns a stream of substitutions (or nil/empty list if it fails).

At the core is a function called `unify` that attempts to unify two values given an existing substitution. If
successful, `unify` returns an updated substitution, else it returns `nil`.

(Note: I use Scheme here instead of Clojure to match the book.)

    (define (unify u v s)
      (let ((u (walk u s)) (v (walk v s)))       ; `walk` fully resolves its arg given a substitution
        (cond
          ((eqv? u v) s)                         ; `u` and `v` are the same value, return `s` unmolested
          ((var? u) (ext-s u v s))               ; `u` is an lvar, bind it to `v` in the substitution
          ((var? v) (ext-s v u s))               ; `v` is an lvar, bind it to `u` in the substitution
          ((and (pair? u) (pair? v))             ; `u` and `v` are lists
            (let ((s (unify (car u) (car v) s))) ; Try to unify their heads
              (and s                             ; ...and if that works
                (unify (cdr u) (cdr v) s))))     ; Try to unify the rest of the lists
          (else #f))))


Here is the definition of `==`. `==` returns a goal.

    (define (== u v)
      (lambda (s)
        (let ((s (unify u v s)))
          (if s (list s) '()))))

`disj2` returns a goal that is the _disjunction_ of two goals. The goal
succeeds if either of both given goals succeed.

    (define (disj2 g1 g2)
      (lambda (s)
        (appendoo (g1 s) (g2 s))))

(`appendoo` appends two streams. I've left out its definition here for clarity.)

`conj2` returns a goal that is the _conjunction_ of two goals. The goal succeed
only if both given goals succeed.

    (define (conj2 g1 g2)
      (lambda (s)
        (append-mapoo g2 (g1 s))))

(`append-mapoo` takes a goal and a stream, applies the goal to each
substitution in the stream, and concatenates the results into a single stream.
Again, elided for clarity.)

`(run* [q] goal1 goal2 goal3...)` works as follows:

* Combines its child goals together using `conj2` (multiple times if there are more than two).
* Calls the combined goal with an empty substitution.
* Prints the value of `q` from each substitution in the resulting stream.


---
## Applications

Sudoku solver, from the `clojure.core.logic` examples.

```
(ns sudoku
  (:refer-clojure :exclude [==])
  (:use clojure.core.logic)
  (:require [clojure.core.logic.fd :as fd]))

(defn get-square [rows x y]
  (for [x (range x (+ x 3))
        y (range y (+ y 3))]
    (get-in rows [x y])))

(defn bind [var hint]
  (if-not (zero? hint)
    (== var hint)
    succeed))

(defn bind-all [vars hints]
  (and* (map bind vars hints)))

(defn sudokufd [hints]
  (let [vars (repeatedly 81 lvar)
        rows (->> vars (partition 9) (map vec) (into []))
        cols (apply map vector rows)
        sqs  (for [x (range 0 9 3)
                   y (range 0 9 3)]
               (get-square rows x y))]
    (run 1 [q]
      (== q vars)
      (everyg #(fd/in % (fd/domain 1 2 3 4 5 6 7 8 9)) vars)
      (bind-all vars hints)
      (everyg fd/distinct rows)
      (everyg fd/distinct cols)
      (everyg fd/distinct sqs))))

(def hints
  [2 0 7 0 1 0 5 0 8
   0 0 0 6 7 8 0 0 0
   8 0 0 0 0 0 0 0 6
   0 7 0 9 0 6 0 5 0
   4 9 0 0 0 0 0 1 3
   0 3 0 4 0 1 0 2 0
   5 0 0 0 0 0 0 0 1
   0 0 0 2 9 4 0 0 0
   3 0 6 0 8 0 4 0 9])

(->> (sudokufd hints)
     first
     (partition 9))
```
