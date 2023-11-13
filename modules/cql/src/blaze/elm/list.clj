(ns blaze.elm.list
  "Implementation of the list type."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.concept :as concept]
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
      (if (= (.count x) (count y))
        (if (zero? (.count x))
          true
          (loop [[t & ts] (map p/equal x y)]
            (if (and (true? t) ts)
              (recur ts)
              t)))
        false)))

  IReduceInit
  (equal [x y]
    (p/equal (PersistentVector/create x) y)))


;; 12.2. Equivalent
(extend-protocol p/Equivalent
  PersistentVector
  (equivalent [x y]
    (if y
      (if (= (.count x) (count y))
        (if (zero? (.count x))
          true
          (loop [[t & ts] (map p/equivalent x y)]
            (if (and (true? t) ts)
              (recur ts)
              t)))
        false)
      (nil? x)))

  IReduceInit
  (equivalent [x y]
    (p/equivalent (PersistentVector/create x) y)))


;; 17.6. Indexer
(extend-protocol p/Indexer
  PersistentVector
  (indexer [list index]
    (when index
      (.nth list index nil)))

  IReduceInit
  (indexer [list index]
    (p/indexer (PersistentVector/create list) index)))


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
  IReduceInit
  (except [x y]
    (when y
      (.reduce
        x
        (fn [result x]
          (if (or (p/contains result x nil) (p/contains y x nil))
            result
            (conj result x)))
        []))))


;; 19.13. Includes
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Includes
  IReduceInit
  (includes [x y _]
    (when y
      (every? #(p/contains x % nil) y))))


;; 19.15. Intersect
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Intersect
  PersistentVector
  (intersect [x y]
    (when y
      (if (<= (.count x) (count y))
        (.reduce
          x
          (fn [result x]
            (if (p/contains y x nil)
              (conj result x)
              result))
          [])
        (p/intersect y x))))

  IReduceInit
  (intersect [x y]
    (p/intersect (PersistentVector/create x) y)))


;; 19.24. ProperContains
(extend-protocol p/ProperContains
  PersistentVector
  (proper-contains [list x precision]
    (and (p/contains list x precision) (> (.count list) 1)))

  IReduceInit
  (proper-contains [list x precision]
    (p/proper-contains (PersistentVector/create list) x precision)))


;; 19.26. ProperIncludes
(extend-protocol p/ProperIncludes
  PersistentVector
  (proper-includes [x y precision]
    (and (p/includes x y precision) (> (.count x) (count y))))

  IReduceInit
  (proper-includes [x y precision]
    (p/proper-includes (PersistentVector/create x) y precision)))


;; 19.31. Union
(extend-protocol p/Union
  IReduceInit
  (union [x y]
    (when y
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
          x)
        y))))


(defn- more-than-one-element-anom [list]
  (ba/conflict "More than one element in `SingletonFrom` expression." :list list))


;; 20.25. SingletonFrom
(extend-protocol p/SingletonFrom
  PersistentVector
  (singleton-from [list]
    (if (<= (.count list) 1)
      (.nth list 0 nil)
      (throw-anom (more-than-one-element-anom list))))

  IReduceInit
  (singleton-from [list]
    (p/singleton-from (.reduce list ((take 2) conj) []))))


;; 22.21. ToConcept
(extend-protocol p/ToConcept
  PersistentVector
  (to-concept [x]
    (concept/to-concept x)))
