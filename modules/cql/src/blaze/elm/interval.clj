(ns blaze.elm.interval
  "Implementation of the interval type."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.date-time :refer [temporal?]]
   [blaze.elm.protocols :as p]))

(defrecord Interval [low high]
  p/Equal
  (equal [_ {other-low :low other-high :high} now]
    (and (p/equal low other-low now)
         (p/equal high other-high now)))

  p/SameAs
  (same-as [_ y precision now]
    (when y
      (if (temporal? low)
        (and (p/same-as low (:low y) precision now)
             (p/same-as high (:high y) precision now))
        (and (p/equal low (:low y) now)
             (p/equal high (:high y) now)))))

  p/SameOrBefore
  (same-or-before [_ y precision now]
    (when-let [{y-low :low} y]
      (if (temporal? low)
        (p/same-or-before high y-low precision now)
        (p/less-or-equal high y-low now))))

  p/SameOrAfter
  (same-or-after [_ y precision now]
    (when-let [{y-high :high} y]
      (if (temporal? low)
        (p/same-or-after low y-high precision now)
        (p/greater-or-equal low y-high now))))

  p/After
  (after [_ y precision now]
    (when-let [{y-high :high} y]
      (if (temporal? low)
        (p/after low y-high precision now)
        (p/greater low y-high now))))

  p/Before
  (before [_ y precision now]
    (when-let [{y-low :low} y]
      (if (temporal? low)
        (p/before high y-low precision now)
        (p/less high y-low now))))

  p/Contains
  (contains [_ x precision now]
    (if (temporal? low)
      (and (p/same-or-before low x precision now)
           (p/same-or-before x high precision now))
      (and (p/less-or-equal low x now)
           (p/less-or-equal x high now))))

  p/Except
  (except [x y now]
    (when-let [{y-low :low y-high :high} y]
      (cond
        ;; cut high
        (and (p/contains x y-low nil now) (p/less-or-equal high y-high now))
        (->Interval low (p/predecessor y-low))

        ;; cut low
        (and (p/contains x y-high nil now) (p/less-or-equal y-low low now))
        (->Interval (p/successor y-high) high))))

  p/Intersect
  (intersect [a b now]
    (let [[left right] (if (p/less (:low a) (:low b) now) [a b] [b a])]
      (when (p/greater-or-equal (:high left) (:low right) now)
        (some->> (if (p/less (:high left) (:high right) now)
                   (:high left)
                   (:high right))
                 (->Interval (:low right))))))

  p/Includes
  (includes [_ y precision now]
    (when-let [{y-low :low y-high :high} y]
      (if (temporal? low)
        (and (p/same-or-before low y-low precision now)
             (p/same-or-after high y-high precision now))
        (and (p/less-or-equal low y-low now)
             (p/greater-or-equal high y-high now)))))

  p/ProperContains
  (proper-contains [_ x precision now]
    (if (temporal? low)
      (and (p/before low x precision now) (p/before x high precision now))
      (and (p/less low x now) (p/less x high now))))

  p/ProperIncludes
  (proper-includes [x y precision now]
    (and (p/includes x y precision now) (not (p/equal x y now))))

  p/Union
  (union [a b now]
    (let [[left right] (if (p/less (:low a) (:low b) now) [a b] [b a])]
      (when (p/greater-or-equal (:high left) (p/predecessor (:low right)) now)
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
  (-optimize [expr _]
    expr)
  (-eval [this _ _ _]
    this)
  (-form [_]
    (list 'interval (core/-form low) (core/-form high))))

(defn interval
  "Returns an interval with the given `low` and `high` bounds.

  The bounds are compared relative to the evaluation request at `now`, which
  can be omitted if the bounds are no DateTime values with offset, like static
  bounds at compile time."
  ([low high]
   (interval low high nil))
  ([low high now]
   (if-not (false? (p/less-or-equal low high now))
     (->Interval low high)
     (throw (ex-info "Invalid interval bounds." {:low low :high high})))))
