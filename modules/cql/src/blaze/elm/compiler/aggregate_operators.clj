(ns blaze.elm.compiler.aggregate-operators
  "21. Aggregate Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.aggregates :as aggregates]
    [blaze.elm.compiler.macros :refer [defaggop]]
    [blaze.elm.compiler.queries :as queries]
    [blaze.elm.protocols :as p]))


;; 21.1. AllTrue
(defaggop all-true [source]
  (reduce #(if (false? %2) (reduced %2) %1) true source))


;; 21.2. AnyTrue
(defaggop any-true [source]
  (reduce #(if (true? %2) (reduced %2) %1) false source))


;; 21.3. Avg
(defaggop avg [source]
  (transduce identity aggregates/avg-reducer source))


;; 21.4. Count
(defaggop count [source]
  (transduce identity aggregates/count-reducer source))


;; 21.5. GeometricMean
(defaggop geometric-mean [source]
  (transduce identity aggregates/geometric-mean-reducer source))


;; 21.6. Product
(defaggop product [source]
  (transduce identity aggregates/product-reducer source))


;; 21.7. Max
(defaggop max [source]
  (transduce identity aggregates/max-reducer source))


;; 21.8. Median
(defaggop median [source]
  (let [sorted (vec (sort-by identity queries/asc-comparator (remove nil? source)))]
    (when (seq sorted)
      (if (zero? (rem (count sorted) 2))
        (let [upper-idx (quot (count sorted) 2)]
          (p/divide (p/add (nth sorted (dec upper-idx))
                           (nth sorted upper-idx))
                    2))
        (nth sorted (quot (count sorted) 2))))))


;; 21.9. Min
(defaggop min [source]
  (transduce identity aggregates/min-reducer source))


;; 21.10. Mode
(defaggop mode [source]
  (transduce identity aggregates/max-freq-reducer (frequencies (remove nil? source))))


;; 21.11. PopulationVariance
(defaggop population-variance [source]
  (transduce identity aggregates/population-variance-reducer source))


;; 21.12. PopulationStdDev
(defaggop population-std-dev [source]
  (p/power (transduce identity aggregates/population-variance-reducer source) 0.5M))


;; 21.13. Sum
(defaggop sum [source]
  (transduce identity aggregates/sum-reducer source))


;; 21.14. StdDev
(defaggop std-dev [source]
  (p/power (transduce identity aggregates/variance-reducer source) 0.5M))


;; 21.15. Variance
(defaggop variance [source]
  (transduce identity aggregates/variance-reducer source))
