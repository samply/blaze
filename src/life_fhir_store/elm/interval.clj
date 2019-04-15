(ns life-fhir-store.elm.interval
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.elm.date-time :refer [temporal?]]
    [life-fhir-store.elm.protocols :as p]
    [life-fhir-store.elm.quantity :refer [quantity?]]))


(defrecord Interval [start end]
  p/Equal
  (equal [_ {other-start :start other-end :end}]
    (and (p/equal start other-start)
         (p/equal end other-end)))

  p/SameOrBefore
  (same-or-before [_ y precision]
    (when-let [{y-start :start} y]
      (if (temporal? start)
        (p/same-or-before end y-start precision)
        (p/less-or-equal end y-start))))

  p/SameOrAfter
  (same-or-after [_ y precision]
    (when-let [{y-end :end} y]
      (if (temporal? start)
        (p/same-or-after start y-end precision)
        (p/greater-or-equal start y-end))))

  p/After
  (after [_ y precision]
    (when-let [{y-end :end} y]
      (if (temporal? start)
        (p/after start y-end precision)
        (p/greater start y-end))))

  p/Before
  (before [_ y precision]
    (when-let [{y-start :start} y]
      (if (temporal? start)
        (p/before end y-start precision)
        (p/less end y-start))))

  p/Contains
  (contains [_ x precision]
    (if (temporal? start)
      (and (p/same-or-before start x precision)
           (p/same-or-before x end precision))
      (and (p/less-or-equal start x)
           (p/less-or-equal x end))))

  p/Except
  (except [x y]
    (when-let [{y-start :start y-end :end} y]
      (cond
        ;; cut end
        (and (p/contains x y-start nil) (p/less-or-equal end y-end))
        (->Interval start (p/predecessor y-start))

        ;; cut start
        (and (p/contains x y-end nil) (p/less-or-equal y-start start))
        (->Interval (p/successor y-end) end))))

  p/Includes
  (includes [_ y precision]
    (when-let [{y-start :start y-end :end} y]
      (if (temporal? start)
        (and (p/same-or-before start y-start precision)
             (p/same-or-after end y-end precision))
        (and (p/less-or-equal start y-start)
             (p/greater-or-equal end y-end)))))

  p/ProperContains
  (proper-contains [_ x precision]
    (if (temporal? start)
      (and (p/before start x precision) (p/before x end precision))
      (and (p/less start x) (p/less x end))))

  p/ProperIncludes
  (proper-includes [x y precision]
    (and (p/includes x y precision) (not (p/equal x y)))))


(defn point?
  "Returns true iff `x` is of a valid point type for an interval."
  [x]
  (or (int? x) (decimal? x) (temporal? x) (quantity? x)))


(defn interval? [x]
  (instance? Interval x))


(s/fdef interval
  :args (s/cat :start (s/nilable point?) :end (s/nilable point?)))

(defn interval [start end]
  (if-not (false? (p/less-or-equal start end))
    (->Interval start end)
    (throw (ex-info "Invalid interval bounds." {:start start :end end}))))
