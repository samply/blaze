(ns blaze.elm.aggregates
  "21. Aggregate Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.protocols :as p])
  (:import
   [javax.measure Quantity Unit]
   [tech.units.indriya.quantity Quantities]))

(set! *warn-on-reflection* true)

(defn- numeric-value
  "Returns the numeric value of `x`.

  Quantities are converted into the unit of the first quantity seen, which is
  stored at index `unit-idx` of `acc`."
  [^objects acc unit-idx x]
  (if (instance? Quantity x)
    (let [unit (or (aget acc unit-idx) (aset acc unit-idx (.getUnit ^Quantity x)))]
      (p/to-decimal (.getValue (.to ^Quantity x unit))))
    x))

(defn- with-unit
  "Returns `x` as quantity with the unit stored at index `unit-idx` of `acc`, if
  there is one."
  [^objects acc unit-idx x]
  (if-let [unit (aget acc unit-idx)]
    (when x (Quantities/getQuantity ^Number x ^Unit unit))
    x))

(defn avg-reducer
  "Reducing fn to compute the arithmetic mean of all non-nil elements of a list."
  ([]
   (object-array [0M 0 nil]))
  ([^objects acc]
   (with-unit acc 2 (p/divide (aget acc 0) (aget acc 1))))
  ([^objects acc x]
   (if x
     (doto acc
       (aset 0 (p/add (aget acc 0) (numeric-value acc 2 x)))
       (aset 1 (inc (aget acc 1))))
     acc)))

(defn count-reducer
  "Reducing fn to compute the number of non-nil elements of a list."
  ([] 0)
  ([acc] acc)
  ([acc x]
   (if x
     (inc acc)
     acc)))

(defn geometric-mean-reducer
  "Reducing fn to compute the geometric mean of all non-nil elements of a list."
  ([]
   (object-array [0M 0]))
  ([^objects acc]
   (p/exp (p/divide (aget acc 0) (aget acc 1))))
  ([^objects acc x]
   (if x
     (doto acc
       (aset 0 (p/add (aget acc 0) (p/ln x)))
       (aset 1 (inc (aget acc 1))))
     acc)))

(defn product-reducer
  "Reducing fn to compute the product of all non-nil elements of a list."
  ([] nil)
  ([acc] acc)
  ([acc x]
   (if x
     (if acc (p/multiply acc x) x)
     acc)))

(defn max-reducer
  "Returns a reducing fn to compute the maximum of all non-nil elements of a
  list relative to the evaluation request at `now`."
  [now]
  (fn
    ([] nil)
    ([acc] acc)
    ([acc x]
     (if acc
       (if (p/greater x acc now) x acc)
       x))))

(defn min-reducer
  "Returns a reducing fn to compute the minimum of all non-nil elements of a
  list relative to the evaluation request at `now`."
  [now]
  (fn
    ([] nil)
    ([acc] acc)
    ([acc x]
     (if acc
       (if (p/less x acc now) x acc)
       x))))

(defn max-freq-reducer
  "Reducing fn to compute the key with the maximum frequency of a map of keys
   to frequencies."
  ([] [nil nil])
  ([[max-x]] max-x)
  ([[max-x max-freq :as acc] [_ freq :as i]]
   (if (some? max-x)
     (if (> freq max-freq) i acc)
     i)))

(defn- moments-reducer
  "Returns a reducing fn that accumulates the sum of squares, the sum and the
  count of all non-nil elements of a list and calls `f` with them on completion.

  Quantities are converted into the unit of the first quantity and the result
  of `f` gets that unit."
  [f]
  (fn
    ([]
     (object-array [0M 0M 0 nil]))
    ([^objects acc]
     (with-unit acc 3 (f (aget acc 0) (aget acc 1) (aget acc 2))))
    ([^objects acc x]
     (if x
       (let [x (numeric-value acc 3 x)]
         (doto acc
           (aset 0 (p/add (aget acc 0) (p/multiply x x)))
           (aset 1 (p/add (aget acc 1) x))
           (aset 2 (inc (aget acc 2)))))
       acc))))

(defn- population-variance [sum-q sum n]
  (let [mean (p/divide sum n)]
    (p/subtract (p/divide sum-q n) (p/multiply mean mean))))

(defn- variance [sum-q sum n]
  (let [mean (p/divide sum n)]
    (p/divide
     (p/subtract
      (p/add sum-q (p/multiply n (p/multiply mean mean)))
      (p/multiply 2 (p/multiply mean sum)))
     (p/subtract n 1M))))

(defn- sqrt [x]
  (p/power x 0.5M))

(def population-variance-reducer
  "Reducing fn to compute the population variance of all non-nil elements of a
  list."
  (moments-reducer population-variance))

(def population-std-dev-reducer
  "Reducing fn to compute the population standard deviation of all non-nil
  elements of a list."
  (moments-reducer (comp sqrt population-variance)))

(defn sum-reducer
  "Reducing fn to compute the sum of all non-nil elements of a list."
  ([] nil)
  ([acc] acc)
  ([acc x]
   (if x
     (if acc (p/add acc x) x)
     acc)))

(def variance-reducer
  "Reducing fn to compute the variance of all non-nil elements of a list."
  (moments-reducer variance))

(def std-dev-reducer
  "Reducing fn to compute the standard deviation of all non-nil elements of a
  list."
  (moments-reducer (comp sqrt variance)))
