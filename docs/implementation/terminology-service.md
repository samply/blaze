# Terminology Service

## Concept Index

The concept index is a tree of module-id over concept-id to a sorted map of effective times to activity status.

### Example

```clojure
{900000000000207008
 {106004
  {20240301 false
   20020131 true}}
 11000274103 
 {20081001000107
  {20230501 true}}}
```

## Parent Index

The parent index is a tree of module-id over concept-id to a sorted map of effective times to lists of added and removed child concepts.

```clojure
{900000000000207008
 {100000000
  {20090731 {false [102272007]}
   20020131 {true [102272007]}}
  100001001
  {20090731 {false [102272007]}
   20020131 {true [102272007]}}}}
```

## Finding a Concept

A concept is defined in one module at each time. Modules have versioned dependencies on other modules. If a concept is not directly defined in a module, Blaze will search in all modules the initial module depends on.
