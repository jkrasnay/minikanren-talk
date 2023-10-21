## miniKanren and The Reasoned Schemer

![The Reasoned Schemer](the-reasoned-schemer-cover.jpg)

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

(TODO short discussion on The Reasoned Schemer)




---
## Primer: Lisp Syntax

miniKanren was originally implemented in Scheme and uses Lisp syntax.

* Strings use `"double quotes"`.  Booleans are `#t` and `#f` in Scheme, but `true` and `false` in Clojure.
* Symbols like `foo` are typically dereferenced to yield an underlying value or function.
* Symbols can have some characters that would be illegal in most languages.  For example, `foo-bar`, `run*`, and `nil?` are all valid symbols.
* Use single quote to avoid dereferencing.  `'tomato` refers to the symbol `tomato` by itself, rather than the value to which it refers.
* Function calls are represented by lists, e.g. `(print "foo")` vs. `print(foo)`.
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
* `==` is a _relation_.  Compare this with functions: `+` is a function, while `(+ 1 1)` is a function call.
* `==` is called _unify_.  It succeeds if its arguments can be made equal.
  In this case it binds the _logic variable_ `q` to the symbol `olive`.
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

* `defrel` is used to create a new relation†.
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

---
## Implementation

---
## Applications


