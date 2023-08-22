(ns blaze.elm.compiler.interval-operators
  "19. Interval Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.arithmetic-operators :as ao]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defbinop defbinopp defunop]]
    [blaze.elm.interval :refer [interval]]
    [blaze.elm.protocols :as p]))


;; 19.1. Interval
(defn- determine-type [{:keys [resultTypeName asType type]}]
  (or resultTypeName
      asType
      (when (= "ToDateTime" type) "{urn:hl7-org:elm-types:r1}DateTime")))


(defrecord IntervalExpression
  [type low high low-closed-expression high-closed-expression low-closed
   high-closed]
  core/Expression
  (-static [_]
    false)
  (-eval [_ context resource scope]
    (let [low (core/-eval low context resource scope)
          high (core/-eval high context resource scope)
          low-closed (or (core/-eval low-closed-expression context resource
                                     scope)
                         low-closed)
          high-closed (or (core/-eval high-closed-expression context resource
                                      scope)
                          high-closed)]
      (interval
        (if low-closed
          (if (nil? low)
            (ao/min-value type)
            low)
          (p/successor low))
        (if high-closed
          (if (nil? high)
            (ao/max-value type)
            high)
          (p/predecessor high))))))


(defmethod core/compile* :elm.compiler.type/interval
  [context {:keys [low high]
            low-closed-expression :lowClosedExpression
            high-closed-expression :highClosedExpression
            low-closed :lowClosed
            high-closed :highClosed
            :or {low-closed true high-closed true}}]
  (let [type (determine-type low)
        low (some->> low (core/compile* context))
        high (some->> high (core/compile* context))
        low-closed-expression (some->> low-closed-expression
                                       (core/compile* context))
        high-closed-expression (some->> high-closed-expression
                                        (core/compile* context))]
    (assert (string? type) (prn-str low))
    (if (and (core/static? low)
             (core/static? high)
             (core/static? low-closed-expression)
             (core/static? high-closed-expression))
      (let [low-closed (or low-closed-expression low-closed)
            high-closed (or high-closed-expression high-closed)]
        (interval
          (if low-closed
            (if (nil? low)
              (ao/min-value type)
              low)
            (p/successor low))
          (if high-closed
            (if (nil? high)
              (ao/max-value type)
              high)
            (p/predecessor high))))
      (->IntervalExpression type low high low-closed-expression
                            high-closed-expression low-closed high-closed))))


;; 19.2. After
(defbinopp after [operand-1 operand-2 precision]
  (p/after operand-1 operand-2 precision))


;; 19.3. Before
(defbinopp before [operand-1 operand-2 precision]
  (p/before operand-1 operand-2 precision))


;; 19.4. Collapse
(defbinop collapse [source _]
  (when source
    (let [source (sort-by :start (remove nil? source))]
      (reverse
        (reduce
          (fn [r right]
            (let [[left & others] r]
              (if (p/greater-or-equal (:end left) (p/predecessor (:start right)))
                (cons (interval (:start left) (:end right)) others)
                (cons right r))))
          (cond-> (list) (first source) (conj (first source)))
          (rest source))))))


;; 19.5. Contains
(defbinopp contains
  {:optimizations #{:first :non-distinct}}
  [list-or-interval x precision]
  (p/contains list-or-interval x precision))


;; 19.6. End
(defunop end [{:keys [end]}]
  end)


;; 19.7. Ends
(defbinopp ends [x y _]
  (and (p/greater-or-equal (:start x) (:start y))
       (p/equal (:end x) (:end y))))


;; 19.10. Except
(defbinop except [x y]
  (p/except x y))


;; TODO 19.11. Expand


;; 19.12. In
(defmethod core/compile* :elm.compiler.type/in
  [_ _]
  (throw (Exception. "Unsupported In expression. Please normalize the ELM tree before compiling.")))


;; 19.13. Includes
(defbinopp includes [x y precision]
  (p/includes x y precision))


;; 19.14. IncludedIn
(defmethod core/compile* :elm.compiler.type/included-in
  [_ _]
  (throw (Exception. "Unsupported IncludedIn expression. Please normalize the ELM tree before compiling.")))


;; 19.15. Intersect
(defbinop intersect [a b]
  (p/intersect a b))


;; 19.16. Meets
(defmethod core/compile* :elm.compiler.type/meets
  [_ _]
  (throw (Exception. "Unsupported Meets expression. Please normalize the ELM tree before compiling.")))


;; 19.17. MeetsBefore
(defbinopp meets-before [x y _]
  (p/equal (:end x) (p/predecessor (:start y))))


;; 19.18. MeetsAfter
(defbinopp meets-after [x y _]
  (p/equal (:start x) (p/successor (:end y))))


;; 19.20. Overlaps
(defbinopp overlaps [x y _]
  (and (p/greater-or-equal (:end x) (:start y))
       (p/less-or-equal (:start x) (:end y))))


;; 19.21. OverlapsBefore
(defmethod core/compile* :elm.compiler.type/overlaps-before
  [_ _]
  (throw (Exception. "Unsupported OverlapsBefore expression. Please normalize the ELM tree before compiling.")))


;; 19.22. OverlapsAfter
(defmethod core/compile* :elm.compiler.type/overlaps-after
  [_ _]
  (throw (Exception. "Unsupported OverlapsAfter expression. Please normalize the ELM tree before compiling.")))


;; 19.23. PointFrom
(defunop point-from [interval {{:keys [locator]} :operand :as expression}]
  (when interval
    (if (p/equal (:start interval) (:end interval))
      (:start interval)
      (throw (ex-info (core/append-locator "Invalid non-unit interval in `PointFrom` expression at" locator)
                      {:expression expression})))))


;; 19.24. ProperContains
(defbinopp proper-contains [list-or-interval x precision]
  (p/proper-contains list-or-interval x precision))


;; 19.25. ProperIn
(defmethod core/compile* :elm.compiler.type/proper-in
  [_ _]
  (throw (Exception. "Unsupported ProperIn expression. Please normalize the ELM tree before compiling.")))


;; 19.26. ProperIncludes
(defbinopp proper-includes [x y precision]
  (p/proper-includes x y precision))


;; 19.27. ProperIncludedIn
(defmethod core/compile* :elm.compiler.type/proper-included-in
  [_ _]
  (throw (Exception. "Unsupported ProperIncludedIn expression. Please normalize the ELM tree before compiling.")))


;; 19.29. Start
(defunop start [{:keys [start]}]
  start)


;; 19.30. Starts
(defbinopp starts [x y _]
  (and (p/equal (:start x) (:start y))
       (p/less-or-equal (:end x) (:end y))))


;; 19.31. Union
(defbinop union [a b]
  (p/union a b))


;; 19.32. Width
(defunop width [{:keys [start end]}]
  (p/subtract end start))
