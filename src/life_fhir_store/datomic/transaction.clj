(ns life-fhir-store.datomic.transaction
  (:require
    [clojure.data :refer [diff]]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [life-fhir-store.datomic.coding :as coding]
    [life-fhir-store.datomic.quantity :refer [quantity]]
    [life-fhir-store.datomic.value :as value]
    [life-fhir-store.spec]
    [life-fhir-store.structure-definition :as sd]
    [life-fhir-store.util :as util]
    [clojure.string :as str])
  (:import
    [java.time Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year
               YearMonth]
    [java.time.format DateTimeFormatter]
    [java.util Date]))


(defn- type-code [element]
  (-> element :life.element-definition/type :life.element-definition.type/code))


(defn- coerce-date-time [^String value]
  (value/write
    (case (.length value)
      4
      (Year/parse value)
      7
      (YearMonth/parse value)
      10
      (LocalDate/parse value)
      (try
        (OffsetDateTime/parse value)
        (catch Exception _
          (LocalDateTime/parse value))))))


(defn- coerce-date [^String value]
  (value/write
    (case (.length value)
      4
      (Year/parse value)
      7
      (YearMonth/parse value)
      10
      (LocalDate/parse value))))


(s/fdef coerce-value
  :args (s/cat :type-code string? :value some?))

(defn coerce-value
  [type-code value]
  (case type-code
    ("string" "code" "id" "markdown" "uri" "xhtml") value
    "instant" (Date/from (Instant/from (.parse DateTimeFormatter/ISO_DATE_TIME value)))
    "decimal" (double value)
    "dateTime" (coerce-date-time value)
    "date" (coerce-date value)
    "time" (value/write (LocalTime/parse value))
    nil))


(defn strip-type-namespaces-from-keys [m]
  (let [f (fn [[k v]] (if-not (= "db" (namespace k)) [(keyword (name k)) v] [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn- partition-indent [element]
  (keyword "life.part" (type-code element)))

(defn- tempid [element]
  (d/tempid (partition-indent element)))

(s/fdef update-tx-data
  :args (s/cat :structure-definitions map?
               :old (s/nilable map?) :new (s/nilable map?))
  :ret ::ds/tx-data)


(defn- to-decimal [value]
  (if (int? value)
    (BigDecimal/valueOf ^long value)
    value))


(defn update-tx-data
  "Takes a function returning Life structure definitions, the old version of a
  resource and the new version and return Datomic transaction data for the
  update."
  {:arglists '([structure-definitions old new])}
  [structure-definitions old {type :resourceType :as new}]
  (if-let [{:life.structure-definition/keys [elements]} (structure-definitions type)]
    (let [db-id (:db/id old)
          old (strip-type-namespaces-from-keys old)
          [retract add] (diff old new)]
      (into
        []
        (mapcat
          (fn [[key val]]
            (let [typed-key (keyword type (name key))]
              (when-let [element (elements typed-key)]
                (cond
                  (sd/primitive-type? element)
                  (if (sd/cardinality-many? element)
                    (into
                      []
                      (map
                        (fn [val]
                          (when-let [val (coerce-value (type-code element) val)]
                            [:db/add db-id typed-key val])))
                      val)
                    (when-let [val (coerce-value (type-code element) val)]
                      [[:db/add db-id typed-key val]]))

                  (= "Reference" (type-code element))
                  (when-let [reference (:reference val)]
                    ;; TODO: check for valid internal references
                    (let [[type id] (str/split reference #"/")]
                      (if (structure-definitions type)
                        (let [tid (d/tempid (keyword "life.part" type))]
                          [{:db/id tid
                            (keyword type "id") id}
                           [:db/add db-id typed-key tid]])
                        (println "Skip external reference:" reference))))

                  (= "Coding" (type-code element))
                  (let [add-coding
                        (fn [val]
                          (let [tid (d/tempid :life.part/Coding)]
                            (conj (coding/upsert tid val)
                                  [:db/add db-id typed-key tid])))]
                    (if (sd/cardinality-many? element)
                      (into [] (mapcat add-coding) val)
                      (add-coding val)))

                  (= "Quantity" (type-code element))
                  (let [add-quantity
                        (fn [{:keys [value unit]}]
                          [[:db/add db-id typed-key (value/write (quantity (to-decimal value) unit))]])]
                    (if (sd/cardinality-many? element)
                      (into [] (mapcat add-quantity) val)
                      (add-quantity val)))

                  (sd/cardinality-many? element)
                  (into
                    []
                    (comp
                      (map-indexed
                        (fn [idx val]
                          (when val
                            (if-let [old (get-in retract [key idx])]
                              (update-tx-data
                                structure-definitions
                                old
                                (assoc val :resourceType (type-code element)))
                              (let [new {:db/id (tempid element)}
                                    result
                                    (update-tx-data
                                      structure-definitions
                                      new
                                      (assoc val :resourceType (type-code element)))]
                                (when-not (empty? result)
                                  (conj
                                    result
                                    [:db/add db-id typed-key (:db/id new)])))))))
                      (mapcat identity))
                    val)

                  :else
                  (if-let [old (get retract key)]
                    (update-tx-data
                      structure-definitions
                      old
                      (assoc val :resourceType (type-code element)))
                    (let [new {:db/id (tempid element)}
                          result (update-tx-data
                                   structure-definitions
                                   new
                                   (assoc val :resourceType (type-code element)))]
                      (when-not (empty? result)
                        (conj
                          result
                          [:db/add db-id typed-key (:db/id new)])))))))))
        add))
    []))


(comment
  (-> (util/structure-definitions "Observation")
      :life.structure-definition/elements
      :Observation/identifier)
  (util/structure-definitions "Period")

  (diff {:db/id 1 :code {:db/id 2 :coding [{:db/id 3 :system 1 :code 1} {:db/id 4 :system 1 :code 2}]}}
        {:code {:coding [{:system 1 :code 1} {:system 1 :code 3} {:system 1 :code 4}]}})

  (update-tx-data
    util/structure-definitions
    {:db/id 1}
    life-fhir-store.core/o1
    )

  (update-tx-data
    util/structure-definitions
    {:db/id 1}
    {:resourceType "Observation" :effectivePeriod {:start "foo"}})


  (strip-type-namespaces-from-keys {:a/b 1})
  )
