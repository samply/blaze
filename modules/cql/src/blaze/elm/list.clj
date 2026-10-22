(ns blaze.elm.list
  "Implementation of the list type."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.concept :refer [concept]]
   [blaze.elm.protocols :as p])
  (:import
   [clojure.lang IReduceInit PersistentVector]))

(set! *warn-on-reflection* true)

;; 12.1. Equal
;;
;; We can't use the Clojure equality semantics of lists because `(p/equal nil nil)` is `nil`.
(extend-protocol p/Equal
  PersistentVector
  (equal [x y now]
    (when y
      (if (= (.count x) (count y))
        (if (zero? (.count x))
          true
          (loop [[t & ts] (map #(p/equal %1 %2 now) x y)]
            (if (and (true? t) ts)
              (recur ts)
              t)))
        false)))

  IReduceInit
  (equal [x y now]
    (p/equal (PersistentVector/create x) y now)))

;; 12.2. Equivalent
(extend-protocol p/Equivalent
  PersistentVector
  (equivalent [x y now]
    (if y
      (if (= (.count x) (count y))
        (if (zero? (.count x))
          true
          (loop [[t & ts] (map #(p/equivalent %1 %2 now) x y)]
            (if (and (true? t) ts)
              (recur ts)
              t)))
        false)
      (nil? x)))

  IReduceInit
  (equivalent [x y now]
    (p/equivalent (PersistentVector/create x) y now)))

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
  (contains [list x _ now]
    (when x
      (true?
       (.reduce
        list
        (fn [_ item] (when (p/equal item x now) (reduced true)))
        nil)))))

;; 19.10. Except
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Except
  IReduceInit
  (except [x y now]
    (when y
      (.reduce
       x
       (fn [result x]
         (if (or (p/contains result x nil now) (p/contains y x nil now))
           result
           (conj result x)))
       []))))

;; 19.13. Includes
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Includes
  IReduceInit
  (includes [x y _ now]
    (when y
      (every? #(p/contains x % nil now) y))))

;; 19.15. Intersect
;;
;; TODO: implementation is O(n^2)
(extend-protocol p/Intersect
  PersistentVector
  (intersect [x y now]
    (when y
      (if (<= (.count x) (count y))
        (.reduce
         x
         (fn [result x]
           (if (p/contains y x nil now)
             (conj result x)
             result))
         [])
        (p/intersect y x now))))

  IReduceInit
  (intersect [x y now]
    (p/intersect (PersistentVector/create x) y now)))

;; 19.24. ProperContains
(extend-protocol p/ProperContains
  PersistentVector
  (proper-contains [list x precision now]
    (and (p/contains list x precision now) (> (.count list) 1)))

  IReduceInit
  (proper-contains [list x precision now]
    (p/proper-contains (PersistentVector/create list) x precision now)))

;; 19.26. ProperIncludes
(extend-protocol p/ProperIncludes
  PersistentVector
  (proper-includes [x y precision now]
    (and (p/includes x y precision now) (> (.count x) (count y))))

  IReduceInit
  (proper-includes [x y precision now]
    (p/proper-includes (PersistentVector/create x) y precision now)))

;; 19.31. Union
(extend-protocol p/Union
  IReduceInit
  (union [x y now]
    (when y
      (reduce
       (fn [result x]
         (if (p/contains result x nil now)
           result
           (conj result x)))
       (reduce
        (fn [result x]
          (if (p/contains result x nil now)
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
    (concept x)))
