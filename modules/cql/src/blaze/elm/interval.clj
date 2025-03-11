(ns blaze.elm.interval
  "Implementation of the interval type."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.date-time :refer [temporal?]]
   [blaze.elm.protocols :as p]))

(defrecord Interval [low high]
  p/Equal
  (equal [_ {other-low :low other-high :high}]
    (and (p/equal low other-low)
         (p/equal high other-high)))

  p/SameAs
  (same-as [_ y precision]
    (when y
      (if (temporal? low)
        (and (p/same-as low (:low y) precision)
             (p/same-as high (:high y) precision))
        (and (p/equal low (:low y))
             (p/equal high (:high y))))))

  p/SameOrBefore
  (same-or-before [_ y precision]
    (when-let [{y-low :low} y]
      (if (temporal? low)
        (p/same-or-before high y-low precision)
        (p/less-or-equal high y-low))))

  p/SameOrAfter
  (same-or-after [_ y precision]
    (when-let [{y-high :high} y]
      (if (temporal? low)
        (p/same-or-after low y-high precision)
        (p/greater-or-equal low y-high))))

  p/After
  (after [_ y precision]
    (when-let [{y-high :high} y]
      (if (temporal? low)
        (p/after low y-high precision)
        (p/greater low y-high))))

  p/Before
  (before [_ y precision]
    (when-let [{y-low :low} y]
      (if (temporal? low)
        (p/before high y-low precision)
        (p/less high y-low))))

  p/Contains
  (contains [_ x precision]
    (if (temporal? low)
      (and (p/same-or-before low x precision)
           (p/same-or-before x high precision))
      (and (p/less-or-equal low x)
           (p/less-or-equal x high))))

  p/Except
  (except [x y]
    (when-let [{y-low :low y-high :high} y]
      (cond
        ;; cut high
        (and (p/contains x y-low nil) (p/less-or-equal high y-high))
        (->Interval low (p/predecessor y-low))

        ;; cut low
        (and (p/contains x y-high nil) (p/less-or-equal y-low low))
        (->Interval (p/successor y-high) high))))

  p/Intersect
  (intersect [a b]
    (let [[left right] (if (p/less (:low a) (:low b)) [a b] [b a])]
      (when (p/greater-or-equal (:high left) (:low right))
        (some->> (if (p/less (:high left) (:high right))
                   (:high left)
                   (:high right))
                 (->Interval (:low right))))))

  p/Includes
  (includes [_ y precision]
    (when-let [{y-low :low y-high :high} y]
      (if (temporal? low)
        (and (p/same-or-before low y-low precision)
             (p/same-or-after high y-high precision))
        (and (p/less-or-equal low y-low)
             (p/greater-or-equal high y-high)))))

  p/ProperContains
  (proper-contains [_ x precision]
    (if (temporal? low)
      (and (p/before low x precision) (p/before x high precision))
      (and (p/less low x) (p/less x high))))

  p/ProperIncludes
  (proper-includes [x y precision]
    (and (p/includes x y precision) (not (p/equal x y))))

  p/Union
  (union [a b]
    (let [[left right] (if (p/less (:low a) (:low b)) [a b] [b a])]
      (when (p/greater-or-equal (:high left) (p/predecessor (:low right)))
        (->Interval (:low left) (:high right)))))

  core/Expression
  (-static [_]
    true)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-eval [this _ _ _]
    this)
  (-form [_]
    (list 'interval (core/-form low) (core/-form high))))

(defn interval
  "Returns an interval with the given `low` and `high` bounds."
  [low high]
  (if-not (false? (p/less-or-equal low high))
    (->Interval low high)
    (throw (ex-info "Invalid interval bounds." {:low low :high high}))))
