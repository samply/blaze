(ns blaze.elm.aggregates
  (:require
    [blaze.elm.protocols :as p]))


(set! *warn-on-reflection* true)


(defn avg-reducer
  "Reducing fn to compute the arithmetic mean of all non-nil elements of a list."
  ([]
   (object-array [0M 0]))
  ([^objects acc]
   (p/divide (aget acc 0) (aget acc 1)))
  ([^objects acc x]
   (if x
     (doto acc
       (aset 0 (p/add (aget acc 0) x))
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
  "Reducing fn to compute the maximum of all non-nil elements of a list."
  ([] nil)
  ([acc] acc)
  ([acc x]
   (if acc
     (if (p/greater x acc) x acc)
     x)))


(defn min-reducer
  "Reducing fn to compute the minimum of all non-nil elements of a list."
  ([] nil)
  ([acc] acc)
  ([acc x]
   (if acc
     (if (p/less x acc) x acc)
     x)))


(defn max-freq-reducer
  "Reducing fn to compute the key with the maximum frequency of a map of keys
   to frequencies."
  ([] [nil nil])
  ([[max-x]] max-x)
  ([[max-x max-freq :as acc] [_ freq :as i]]
   (if (some? max-x)
     (if (> freq max-freq) i acc)
     i)))


(defn population-variance-reducer
  "Reducing fn to compute the population variance of all non-nil elements of a
  list."
  ([]
   (object-array [0M 0M 0]))
  ([^objects acc]
   (let [sum-q (aget acc 0)
         sum (aget acc 1)
         n (aget acc 2)
         mean (p/divide sum n)]
     (p/subtract (p/divide sum-q n) (p/multiply mean mean))))
  ([^objects acc x]
   (if x
     (doto acc
       (aset 0 (p/add (aget acc 0) (p/multiply x x)))
       (aset 1 (p/add (aget acc 1) x))
       (aset 2 (inc (aget acc 2))))
     acc)))


(defn sum-reducer
  "Reducing fn to compute the sum of all non-nil elements of a list."
  ([] nil)
  ([acc] acc)
  ([acc x]
   (if x
     (if acc (p/add acc x) x)
     acc)))


(defn variance-reducer
  "Reducing fn to compute the variance of all non-nil elements of a
  list."
  ([]
   (object-array [0M 0M 0]))
  ([^objects acc]
   (let [sum-q (aget acc 0)
         sum (aget acc 1)
         n (aget acc 2)
         mean (p/divide sum n)]
     (p/divide
       (p/subtract
         (p/add sum-q (p/multiply n (p/multiply mean mean)))
         (p/multiply 2 (p/multiply mean sum)))
       (p/subtract n 1M))))
  ([^objects acc x]
   (if x
     (doto acc
       (aset 0 (p/add (aget acc 0) (p/multiply x x)))
       (aset 1 (p/add (aget acc 1) x))
       (aset 2 (inc (aget acc 2))))
     acc)))
