(ns blaze.elm.literal
  (:refer-clojure
   :exclude [abs and boolean count distinct first flatten last list long max
             min not or time])
  (:require
   [blaze.elm.spec]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

;; 1. Simple Values

;; 1.1. Literal
(defn boolean [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Boolean"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Boolean"
   :value s})

(defn decimal [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Decimal"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Decimal"
   :value s})

(defn long [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Long"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Long"
   :value s})

(defn integer [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}Integer"
   :resultTypeName "{urn:hl7-org:elm-types:r1}Integer"
   :value s})

(defn string [s]
  {:type "Literal"
   :valueType "{urn:hl7-org:elm-types:r1}String"
   :resultTypeName "{urn:hl7-org:elm-types:r1}String"
   :value s})

;; 2. Structured Values

;; 2.1. Tuple
(defn tuple [elements]
  {:type "Tuple"
   :element (reduce #(conj %1 {:name (key %2) :value (val %2)}) [] elements)})

;; 2.2. Instance
(defn instance [[type elements]]
  {:type "Instance"
   :classType type
   :element
   (mapv
    (fn [[name value]]
      {:type "InstanceElement" :name name :value value})
    elements)})

;; 2.3. Property
(defn source-property [[source path]]
  {:type "Property"
   :source source
   :path path})

(defn scope-property [[scope path]]
  {:type "Property"
   :scope scope
   :path path})

;; 3. Clinical Values

;; 3.1 Code
(defn code [[system-name code display]]
  (cond->
   {:type "Code"
    :system {:type "CodeSystemRef" :name system-name}
    :code code}
    display
    (assoc :display display)))

;; 3.3. CodeRef
(defn code-ref [name]
  {:type "CodeRef" :name name})

;; 3.5. CodeSystemRef
(defn code-system-ref [name]
  {:type "CodeSystemRef" :name name})

;; 3.6. Concept
(defn concept [[codes display]]
  (cond->
   {:type "Concept"
    :codes codes}
    display
    (assoc :display display)))

;; 3.8. ConceptRef
(defn concept-ref [name]
  {:type "ConceptRef" :name name})

;; 3.9. Quantity
(defn quantity [[value unit]]
  (cond->
   {:type "Quantity"
    :value value}
    unit
    (assoc :unit unit)))

;; 3.10. Ratio
(defn ratio
  [[[numerator-value numerator-unit] [denominator-value denominator-unit]]]
  {:type "Ratio"
   :numerator (cond->
               {:type "Quantity"
                :value numerator-value}
                numerator-unit
                (assoc :unit numerator-unit))
   :denominator (cond->
                 {:type "Quantity"
                  :value denominator-value}
                  denominator-unit
                  (assoc :unit denominator-unit))})

;; 3.12. ValueSetRef
(defn value-set-ref [name]
  {:type "ValueSetRef"
   :name name
   :resultTypeName "{urn:hl7-org:elm-types:r1}ValueSet"})

;; 7. Parameters

;; 7.2. ParameterRef
(defn parameter-ref [name]
  {:type "ParameterRef" :name name})

;; 9. Reusing Logic

;; 9.2. ExpressionRef
(defn expression-ref [name]
  {:type "ExpressionRef"
   :name name})

;; 9.4. FunctionRef
(defn function-ref [[name & ops]]
  {:type "FunctionRef"
   :name name
   :operand (vec ops)})

;; 9.5. OperandRef
(defn operand-ref [name]
  {:type "OperandRef"
   :name name})

;; 10. Queries

;; 10.2. AliasedQuerySource
(defn aliased-query-source [[expression alias]]
  {:type "AliasedQuerySource"
   :expression expression
   :alias alias})

;; 11. External Data

;; 11.1. Retrieve
(defn retrieve [{:keys [type codes code-property context]}]
  (cond->
   {:type "Retrieve"
    :dataType (str "{http://hl7.org/fhir}" type)}
    codes
    (assoc :codes codes)
    code-property
    (assoc :codeProperty code-property)
    context
    (assoc :context context)))

;; 12. Comparison Operators

;; 12.1. Equal
(defn equal [ops]
  {:type "Equal"
   :operand ops})

;; 12.2. Equivalent
(defn equivalent [ops]
  {:type "Equivalent"
   :operand ops})

;; 12.3. Greater
(defn greater [ops]
  {:type "Greater"
   :operand ops})

;; 12.4. GreaterOrEqual
(defn greater-or-equal [ops]
  {:type "GreaterOrEqual"
   :operand ops})

;; 12.5. Less
(defn less [ops]
  {:type "Less"
   :operand ops})

;; 12.6. LessOrEqual
(defn less-or-equal [ops]
  {:type "LessOrEqual"
   :operand ops})

;; 13. Logical Operators

;; 13.1. And
(defn and [ops]
  {:type "And"
   :operand ops})

;; 13.3 Not
(defn not [op]
  {:type "Not"
   :operand op})

;; 13.4. Or
(defn or [ops]
  {:type "Or"
   :operand ops})

;; 13.5. Xor
(defn xor [ops]
  {:type "Xor"
   :operand ops})

;; 14.2. Coalesce
(defn coalesce [ops]
  {:type "Coalesce"
   :operand ops})

;; 14.3. IsFalse
(defn is-false [op]
  {:type "IsFalse"
   :operand op})

;; 14.4. IsNull
(defn is-null [op]
  {:type "IsNull"
   :operand op})

;; 14.5. IsTrue
(defn is-true [op]
  {:type "IsTrue"
   :operand op})

(defn list [elements]
  {:type "List"
   :element elements})

;; 15.2. If
(defn if-expr [[condition then else]]
  {:type "If" :condition condition :then then :else else})

;; 16.1. Abs
(defn abs [op]
  {:type "Abs"
   :operand op})

;; 16.2. Add
(defn add [ops]
  {:type "Add"
   :operand ops})

;; 16.3. Ceiling
(defn ceiling [op]
  {:type "Ceiling"
   :operand op})

;; 16.4. Divide
(defn divide [ops]
  {:type "Divide"
   :operand ops})

;; 16.5. Exp
(defn exp [op]
  {:type "Exp"
   :operand op})

;; 16.6. Floor
(defn floor [op]
  {:type "Floor"
   :operand op})

;; 16.8. Log
(defn log [ops]
  {:type "Log"
   :operand ops})

;; 16.10. Ln
(defn ln [op]
  {:type "Ln"
   :operand op})

;; 16.11. MaxValue
(defn max-value [type]
  {:type "MaxValue" :valueType type})

;; 16.12. MinValue
(defn min-value [type]
  {:type "MinValue" :valueType type})

;; 16.13. Modulo
(defn modulo [ops]
  {:type "Modulo"
   :operand ops})

;; 16.14. Multiply
(defn multiply [ops]
  {:type "Multiply"
   :operand ops})

;; 16.15. Negate
(defn negate [op]
  {:type "Negate"
   :operand op})

;; 16.16. Power
(defn power [ops]
  {:type "Power"
   :operand ops})

;; 16.18. Predecessor
(defn predecessor [op]
  {:type "Predecessor"
   :operand op})

;; 16.19. Round
(defn round [arg]
  (let [[operand precision] (if (sequential? arg) arg [arg])]
    (cond->
     {:type "Round"
      :operand operand}
      precision
      (assoc :precision precision))))

;; 16.20. Subtract
(defn subtract [ops]
  {:type "Subtract"
   :operand ops})

;; 16.21. Successor
(defn successor [op]
  {:type "Successor"
   :operand op})

;; 16.22. Truncate
(defn truncate [op]
  {:type "Truncate"
   :operand op})

;; 16.23. TruncatedDivide
(defn truncated-divide [ops]
  {:type "TruncatedDivide"
   :operand ops})

;; 17. String Operators

;; 17.1. Combine
(defn combine [arg]
  (if (vector? arg)
    {:type "Combine"
     :source (clojure.core/first arg)
     :separator (second arg)}
    {:type "Combine"
     :source arg}))

;; 17.2. Concatenate
(defn concatenate [ops]
  {:type "Concatenate"
   :operand ops})

;; 17.3. EndsWith
(defn ends-with [ops]
  {:type "EndsWith"
   :operand ops})

;; 17.6. Indexer
(defn indexer [ops]
  {:type "Indexer"
   :operand ops})

;; 17.7. LastPositionOf
(defn last-position-of [[pattern s]]
  {:type "LastPositionOf"
   :pattern pattern
   :string s})

;; 17.8. Length
(defn length [x]
  {:type "Length"
   :operand x})

;; 17.9. Lower
(defn lower [x]
  {:type "Lower"
   :operand x})

;; 17.10. Matches
(defn matches [ops]
  {:type "Matches"
   :operand ops})

;; 17.12. PositionOf
(defn position-of [[pattern s]]
  {:type "PositionOf"
   :pattern pattern
   :string s})

;; 17.13. ReplaceMatches
(defn replace-matches [ops]
  {:type "ReplaceMatches"
   :operand ops})

;; 17.14. Split
(defn split [[s separator]]
  {:type "Split"
   :stringToSplit s
   :separator separator})

;; 17.16. StartsWith
(defn starts-with [ops]
  {:type "StartsWith"
   :operand ops})

;; 17.17. Substring
(defn substring [[s start-index length]]
  (cond->
   {:type "Substring"
    :stringToSub s
    :startIndex start-index}
    length
    (assoc :length length)))

;; 17.18. Upper
(defn upper [x]
  {:type "Upper"
   :operand x})

;; 18. Date and Time Operators

;; 18.14. SameAs
(defn same-as [[x y precision]]
  (cond->
   {:type "SameAs"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 18.15. SameOrBefore
(defn same-or-before [[x y precision]]
  (cond->
   {:type "SameOrBefore"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 18.15. SameOrAfter
(defn same-or-after [[x y precision]]
  (cond->
   {:type "SameOrAfter"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 18.6. Date
(defn date [arg]
  (cond
    (string? arg)
    (date (map integer (str/split arg #"-")))
    (sequential? arg)
    (let [[year month day] arg]
      (cond->
       {:type "Date"
        :year year
        :resultTypeName "{urn:hl7-org:elm-types:r1}Date"}
        month (assoc :month month)
        day (assoc :day day)))
    :else
    {:type "Date"
     :year arg
     :resultTypeName "{urn:hl7-org:elm-types:r1}Date"}))

;; 18.7. DateFrom
(defn date-from [op]
  {:type "DateFrom"
   :operand op})

;; 18.8. DateTime
(defn date-time [arg]
  (cond
    (string? arg)
    (date-time (map integer (str/split arg #"[-T:.]")))
    (sequential? arg)
    (let [[year month day hour minute second millisecond timezone-offset] arg]
      (cond->
       {:type "DateTime"
        :year year
        :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime"}
        month (assoc :month month)
        day (assoc :day day)
        hour (assoc :hour hour)
        minute (assoc :minute minute)
        second (assoc :second second)
        millisecond (assoc :millisecond millisecond)
        timezone-offset (assoc :timezoneOffset timezone-offset)))
    :else
    {:type "DateTime"
     :year arg
     :resultTypeName "{urn:hl7-org:elm-types:r1}DateTime"}))

;; 18.9. DateTimeComponentFrom
(defn date-time-component-from [[x precision]]
  {:type "DateTimeComponentFrom"
   :operand x
   :precision precision})

;; 18.10. DifferenceBetween
(defn difference-between [[x y precision]]
  {:type "DifferenceBetween"
   :operand [x y]
   :precision precision})

;; 18.11. DurationBetween
(defn duration-between [[x y precision]]
  {:type "DurationBetween"
   :operand [x y]
   :precision precision})

;; 18.18. Time
(defn time [arg]
  (cond
    (string? arg)
    (time (map integer (str/split (if (.contains ^String arg ".")
                                    (subs (str arg "000") 0 12)
                                    arg) #"[:.]")))
    (sequential? arg)
    (let [[hour minute second millisecond] arg]
      (cond->
       {:type "Time"
        :hour hour}
        minute (assoc :minute minute)
        second (assoc :second second)
        millisecond (assoc :millisecond millisecond)))
    :else
    {:type "Time"
     :hour arg}))

;; 18.21. TimeOfDay
(def time-of-day
  {:type "TimeOfDay"})

;; 18.22. Today
(def today
  {:type "Today"})

;; 19.1. Interval
(defn interval [args]
  (let [low-open (= :< (clojure.core/first args))
        [low high high-open] (cond-> args low-open rest)]
    {:type "Interval"
     :low low
     :high high
     :lowClosed (false? low-open)
     :highClosed (nil? high-open)
     :resultTypeSpecifier
     {:type "IntervalTypeSpecifier",
      :pointType
      {:type "NamedTypeSpecifier"
       :name (clojure.core/or (:resultTypeName low) (:resultTypeName high))}}}))

;; 19.2. After
(defn after [[x y precision]]
  (cond->
   {:type "After"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.3. Before
(defn before [[x y precision]]
  (cond->
   {:type "Before"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.4. Collapse
(defn collapse [ops]
  {:type "Collapse" :operand ops})

;; 19.5. Contains
(defn contains [[list x precision]]
  (cond->
   {:type "Contains"
    :operand [list x]}
    precision
    (assoc :precision precision)))

;; 19.6. End
(defn end [interval]
  {:type "End" :operand interval})

;; 19.7. Ends
(defn ends [[x y precision]]
  (cond->
   {:type "Ends"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.10. Except
(defn except [ops]
  {:type "Except" :operand ops})

;; 19.13. Includes
(defn includes [[x y precision]]
  (cond->
   {:type "Includes"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.15. Intersect
(defn intersect [ops]
  {:type "Intersect" :operand ops})

;; 19.17. MeetsBefore
(defn meets-before [[x y precision]]
  (cond->
   {:type "MeetsBefore"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.18. MeetsAfter
(defn meets-after [[x y precision]]
  (cond->
   {:type "MeetsAfter"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.20. Overlaps
(defn overlaps [[x y precision]]
  (cond->
   {:type "Overlaps"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.23. PointFrom
(defn point-from [interval]
  {:type "PointFrom" :operand interval})

;; 19.24. ProperContains
(defn proper-contains [[x y precision]]
  (cond->
   {:type "ProperContains"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.26. ProperIncludes
(defn proper-includes [[x y precision]]
  (cond->
   {:type "ProperIncludes"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.29. Start
(defn start [interval]
  {:type "Start" :operand interval})

;; 19.30. Starts
(defn starts [[x y precision]]
  (cond->
   {:type "Starts"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 19.31. Union
(defn union [ops]
  {:type "Union" :operand ops})

;; 19.32. Width
(defn width [interval]
  {:type "Width" :operand interval})

;; 20.3. Current
(defn current [scope]
  (cond-> {:type "Current"} scope (assoc :scope scope)))

;; 20.4. Distinct
(defn distinct [list]
  {:type "Distinct" :operand list})

;; 20.8. Exists
(defn exists [list]
  {:type "Exists" :operand list})

;; 20.10. First
(defn first [source]
  {:type "First" :source source})

;; 20.11. Flatten
(defn flatten [list]
  {:type "Flatten" :operand list})

;; 20.16. IndexOf
(defn index-of [[source element]]
  {:type "IndexOf" :source source :element element})

;; 20.18. Last
(defn last [source]
  {:type "Last" :source source})

;; 20.25. SingletonFrom
(defn singleton-from [list]
  {:type "SingletonFrom" :operand list})

;; 20.26. Slice
(defn slice [[source start-index end-index]]
  {:type "Slice" :source source :startIndex start-index :endIndex end-index})

;; 20.28. Times
(defn times [lists]
  {:type "Times" :operand lists})

;; 21.1. AllTrue
(defn all-true [source]
  {:type "AllTrue" :source source})

;; 21.2. AnyTrue
(defn any-true [source]
  {:type "AnyTrue" :source source})

;; 21.3. Avg
(defn avg [source]
  {:type "Avg" :source source})

;; 21.4. Count
(defn count [source]
  {:type "Count" :source source})

;; 21.5. GeometricMean
(defn geometric-mean [source]
  {:type "GeometricMean" :source source})

;; 21.6. Product
(defn product [source]
  {:type "Product" :source source})

;; 21.7. Max
(defn max [source]
  {:type "Max" :source source})

;; 21.8. Median
(defn median [source]
  {:type "Median" :source source})

;; 21.9. Min
(defn min [source]
  {:type "Min" :source source})

;; 21.10. Mode
(defn mode [source]
  {:type "Mode" :source source})

;; 21.11. PopulationVariance
(defn population-variance [source]
  {:type "PopulationVariance" :source source})

;; 21.12. PopulationStdDev
(defn population-std-dev [source]
  {:type "PopulationStdDev" :source source})

;; 21.13. Sum
(defn sum [source]
  {:type "Sum" :source source})

;; 21.14. StdDev
(defn std-dev [source]
  {:type "StdDev" :source source})

;; 21.15. Variance
(defn variance [source]
  {:type "Variance" :source source})

;; 22.1. As
(defn as [[type operand]]
  {:type "As" :asType type :operand operand})

;; 22.4. CanConvertQuantity
(defn can-convert-quantity [list]
  {:type "CanConvertQuantity" :operand list})

;; 22.4. Children
(defn children [source]
  {:type "Children" :source source})

;; 22.6. ConvertQuantity
(defn convert-quantity [ops]
  {:type "ConvertQuantity" :operand ops})

;; 22.7. ConvertsToBoolean
(defn converts-to-boolean [operand]
  {:type "ConvertsToBoolean" :operand operand})

;; 22.8. ConvertsToDate
(defn converts-to-date [operand]
  {:type "ConvertsToDate" :operand operand})

;; 22.9. ConvertsToDateTime
(defn converts-to-date-time [operand]
  {:type "ConvertsToDateTime" :operand operand})

;; 22.10. ConvertsToDecimal
(defn converts-to-decimal [operand]
  {:type "ConvertsToDecimal" :operand operand})

;; 22.11. ConvertsToLong
(defn converts-to-long [operand]
  {:type "ConvertsToLong" :operand operand})

;; 22.12. ConvertsToInteger
(defn converts-to-integer [operand]
  {:type "ConvertsToInteger" :operand operand})

;; 22.13. ConvertsToQuantity
(defn converts-to-quantity [operand]
  {:type "ConvertsToQuantity" :operand operand})

;; 22.14. ConvertsToRatio
(defn converts-to-ratio [operand]
  {:type "ConvertsToRatio" :operand operand})

;; 22.15. ConvertsToString
(defn converts-to-string [operand]
  {:type "ConvertsToString" :operand operand})

;; 22.16. ConvertsToTime
(defn converts-to-time [operand]
  {:type "ConvertsToTime" :operand operand})

;; 22.17. Descendents
(defn descendents [source]
  {:type "Descendents" :source source})

;; 22.18. Is
(defn is [[type operand]]
  {:type "Is" :isType type :operand operand})

;; 22.19. ToBoolean
(defn to-boolean [operand]
  {:type "ToBoolean" :operand operand})

;; 22.20. ToChars
(defn to-chars [operand]
  {:type "ToChars" :operand operand})

;; 22.21. ToConcept
(defn to-concept [operand]
  {:type "ToConcept" :operand operand})

;; 22.22. ToDate
(defn to-date [operand]
  {:type "ToDate" :operand operand})

;; 22.23. ToDateTime
(defn to-date-time [operand]
  {:type "ToDateTime" :operand operand})

;; 22.24. ToDecimal
(defn to-decimal [operand]
  {:type "ToDecimal" :operand operand})

;; 22.25. ToInteger
(defn to-integer [operand]
  {:type "ToInteger" :operand operand})

;; 22.26. ToList
(defn to-list [operand]
  {:type "ToList" :operand operand})

;; 22.27. ToLong
(defn to-long [operand]
  {:type "ToLong" :operand operand})

;; 22.28. ToQuantity
(defn to-quantity [operand]
  {:type "ToQuantity" :operand operand})

;; 22.29. ToRatio
(defn to-ratio [operand]
  {:type "ToRatio" :operand operand})

;; 22.30. ToString
(defn to-string [operand]
  {:type "ToString" :operand operand})

;; 22.31. ToTime
(defn to-time [operand]
  {:type "ToTime" :operand operand})

;; 23. Clinical Operators

;; 23.4. CalculateAgeAt
(defn calculate-age-at [[x y precision]]
  (cond->
   {:type "CalculateAgeAt"
    :operand [x y]}
    precision
    (assoc :precision precision)))

;; 23.7. InCodeSystem
(defn in-code-system [[code code-system]]
  {:type "InCodeSystem"
   :code code
   :codesystem code-system})

(defn in-code-system-expression [[code code-system]]
  {:type "InCodeSystem"
   :code code
   :codesystemExpression code-system})

;; 23.8. InValueSet
(defn in-value-set [[code value-set]]
  {:type "InValueSet"
   :code code
   :valueset value-set})

(defn in-value-set-expression [[code value-set]]
  {:type "InValueSet"
   :code code
   :valuesetExpression value-set})
