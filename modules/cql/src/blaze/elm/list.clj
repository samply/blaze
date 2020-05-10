(ns blaze.elm.list
  "Implementation of the list type."
  (:require
    [blaze.elm.protocols :as p])
  (:import
    [clojure.lang PersistentVector IReduceInit]))


(set! *warn-on-reflection* true)


;; 12.1. Equal
;;
;; We can't use the Clojure equality semantics of lists because `(p/equal nil nil)` is `nil`.
(extend-protocol p/Equal
  PersistentVector
  (equal [x y]
    (when y
      (if (= (count x) (count y))
        (if (empty? x)
          true
          (loop [[t & ts] (map p/equal x y)]
            (if (and (true? t) ts)
              (recur ts)
              t)))
        false))))


;; 12.2. Equivalent
(extend-protocol p/Equivalent
  PersistentVector
  (equivalent [x y]
    (if y
      (if (= (count x) (count y))
        (if (empty? x)
          true
          (loop [[t & ts] (map p/equivalent x y)]
            (if (and (true? t) ts)
              (recur ts)
              t)))
        false)
      (nil? x))))


;; 17.6. Indexer
(extend-protocol p/Indexer
  PersistentVector
  (indexer [list index]
    (when (and index (<= 0 index) (< index (count list)))
      (nth list index))))


;; 19.5. Contains
;;
;; TODO: implementation is O(n)
(extend-protocol p/Contains
  IReduceInit
  (contains [list x _]
    (when x
      (true?
        (.reduce
          list
          (fn [_ item] (when (p/equal item x) (reduced true)))
          nil)))))


;; 19.10. Except
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Except
  PersistentVector
  (except [x y]
    (when y
      (reduce
        (fn [result x]
          (if (or (p/contains result x nil) (p/contains y x nil))
            result
            (conj result x)))
        []
        x))))


;; 19.13. Includes
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Includes
  PersistentVector
  (includes [x y _]
    (when y
      (every? #(p/contains x % nil) y))))


;; 19.15. Intersect
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Intersect
  PersistentVector
  (intersect [a b]
    (when b
      (if (<= (count a) (count b))
        (reduce
          (fn [result x]
            (if (p/contains b x nil)
              (conj result x)
              result))
          []
          a)
        (p/intersect b a)))))


;; 19.24. ProperContains
(extend-protocol p/ProperContains
  PersistentVector
  (proper-contains [list x _]
    (and (p/contains list x _) (> (count list) 1))))


;; 19.26. ProperIncludes
(extend-protocol p/ProperIncludes
  PersistentVector
  (proper-includes [x y _]
    (and (p/includes x y _) (> (count x) (count y)))))


;; 19.31. Union
(extend-protocol p/Union
  PersistentVector
  (union [a b]
    (when b
      (reduce
        (fn [result x]
          (if (p/contains result x nil)
            result
            (conj result x)))
        (reduce
          (fn [result x]
            (if (p/contains result x nil)
              result
              (conj result x)))
          []
          a)
        b))))


;; 20.25. SingletonFrom
(extend-protocol p/SingletonFrom
  PersistentVector
  (singleton-from [list]
    (let [[fst snd] list]
      (if (nil? snd)
        fst
        (throw (Exception.)))))

  IReduceInit
  (singleton-from [list]
    (p/singleton-from (.reduce list ((take 2) conj) []))))
