(ns blaze.datomic.transaction
  (:require
    [blaze.datomic.quantity :as quantity]
    [blaze.datomic.pull :as pull]
    [blaze.datomic.value :as value]
    [blaze.datomic.util :as util]
    [blaze.fhir-client :as client]
    [blaze.spec]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate LocalDateTime LocalTime OffsetDateTime Year
               YearMonth]
    [java.time.format DateTimeFormatter]
    [java.util Base64]
    [java.util Date Map$Entry UUID]
    [java.util.concurrent ExecutionException]))


(defn- coerce-decimal [value]
  (cond
    (decimal? value) value
    (int? value) (BigDecimal/valueOf ^long value)
    :else (throw (ex-info (str "Invalid decimal value `" value "`.")
                          {::anom/category ::anom/incorrect
                           :value value}))))


(defn- coerce-date-time [^String value]
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
        (LocalDateTime/parse value)))))


(defn- coerce-date [^String value]
  (case (.length value)
    4
    (Year/parse value)
    7
    (YearMonth/parse value)
    10
    (LocalDate/parse value)))


(defn- quantity [{:strs [value code unit]}]
  (let [value (coerce-decimal value)]
    (try
      (if code
        (quantity/quantity value code)
        (quantity/quantity value unit))
      (catch Exception _
        ;; TODO: find a better solution as to skip the unit
        (quantity/quantity value "")))))


(s/fdef coerce-value
  :args (s/cat :type-code string? :value some?))

(defn coerce-value
  [type-code value]
  (case type-code
    ("boolean"
      "integer"
      "string"
      "uri"
      "url"
      "canonical"
      "code"
      "oid"
      "id"
      "markdown"
      "unsignedInt"
      "positiveInt"
      "xhtml") value
    "decimal" (coerce-decimal value)
    "instant" (Date/from (Instant/from (.parse DateTimeFormatter/ISO_DATE_TIME value)))
    "date" (coerce-date value)
    "dateTime" (coerce-date-time value)
    "time" (LocalTime/parse value)
    "base64Binary" (.decode (Base64/getDecoder) ^String value)
    "uuid" (UUID/fromString value)
    "Quantity" (quantity value)))


(defn- write
  "Calls write on values stored as byte array."
  [type-code valueType value]
  (assert (some? value) (str "Nil value in write of type `" type-code "`."))
  (let [value (coerce-value type-code value)]
    (if (= :db.type/bytes valueType)
      (value/write value)
      value)))


(defn find-json-value
  "Tries to find a value suitable for `element` in a JSON `entity`.

  Returns a tuple of the value and the element resulted from a possible choice
  typed element.

  Returns the first found value on choice typed elements.

  Returns nil of nothing was found."
  [db {:element/keys [choice-type? type-choices json-key] :as element} entity]
  (if choice-type?
    (transduce
      (map #(util/cached-entity db %))
      (completing
        (fn [_ {:element/keys [json-key] :as element}]
          (let [value (get entity json-key)]
            (when (some? value)
              (reduced
                [value element])))))
      nil
      type-choices)
    (when-some [value (get entity json-key)]
      [value element])))



;; ---- Add Element -----------------------------------------------------------

(defn- add-code
  "Returns tx-data for adding a code. All of `system`, `version` and `code` are
  optional."
  [ident id system version code]
  (let [tid (d/tempid :part/code)]
    [(cond->
       {:db/id tid
        :code/id (str system "|" version "|" code)}
       system
       (assoc :code/system system)
       version
       (assoc :code/version version)
       code
       (assoc :code/code code))
     [:db/add id ident tid]]))


(defn- add-code-according-value-set-binding
  [{:db/keys [ident]} id code]
  ;; TODO: resolve the code system and version
  (add-code ident id nil nil code))


(defn- add-primitive-element
  {:arglists '([context element id value])}
  [{{:strs [system version]} :code}
   {:element/keys [type-code part-of-choice-type? type-attr-ident
                   value-set-binding]
    :db/keys [ident valueType] :as element}
   id value]
  (cond->
    (case type-code
      "code"
      (if value-set-binding
        (add-code-according-value-set-binding element id value)
        (add-code ident id system version value))

      [[:db/add id ident (write type-code valueType value)]])
    part-of-choice-type?
    (conj [:db/add id type-attr-ident ident])))


(defn- index-ident [ident]
  (keyword (str (namespace ident) ".index") (name ident)))


(defn- find-code-tids [tx-data]
  (into
    #{}
    (comp
      (filter vector?)
      (filter
        (fn [[op _ a]]
          (and (= :db/add op) (= :Coding/code a))))
      (map
        (fn [[_ _ _ v]]
          v)))
    tx-data))


(declare upsert)


(defn- resolve-reference [{:keys [db tempids]} type id]
  (or (:db/id (util/resource db type id))
      (get-in tempids [type id])))


(defn- resolve-local-reference [{:keys [contained-resource-eids]} id]
  (get contained-resource-eids id))


(defn- resolve-referenced-entity
  [{:keys [db] :as context} {:strs [reference]}]
  (if (str/starts-with? reference "#")
    (d/entity db (resolve-local-reference context (subs reference 1)))
    (let [[type id] (str/split reference #"/")]
      (util/resource db type id))))


(defn- add-contained-resource
  {:arglists '([context element id value])}
  [context
   {:element/keys [part-of-choice-type? type-attr-ident] :db/keys [ident]}
   id value]
  (if-let [resource-id (get value "id")]
    (let [type (get value "resourceType")
          tid (d/tempid (keyword "part" type))
          tx-data (upsert context (keyword type) {:db/id tid}
                          (dissoc value "id"))]
      {:tx-data
       (cond->
         (conj
           tx-data
           [:db/add tid :local-id resource-id]
           [:db/add id ident tid])
         part-of-choice-type?
         (conj [:db/add id type-attr-ident ident]))
       :id resource-id
       :eid tid})
    (throw (ex-info "Invalid contained resource without `id` element."
                    {::anom/category ::anom/incorrect
                     :fhir/issue "value"
                     :contained-resource value}))))


(defn- add-non-primitive-element
  "Returns tx-data for adding the non-primitive `value` as child of the
  existing entity with `id`."
  {:arglists '([db element id value])}
  [{:keys [db] :as context}
   {:element/keys [type-code part-of-choice-type? type-attr-ident]
    :ElementDefinition/keys [path] :db/keys [ident] :as element}
   id value]
  (case type-code
    "Reference"
    (let [{:strs [reference identifier]} value]
      (cond
        reference
        (if (str/starts-with? reference "#")
          (if-let [eid (resolve-local-reference context (subs reference 1))]
            [[:db/add id ident eid]]
            (throw (ex-info (str "Local reference `" reference "` can't be resolved.")
                            {::anom/category ::anom/incorrect
                             :fhir/issue "value"
                             :reference value
                             :element/ident ident})))
          (let [[type ref-id] (str/split reference #"/")]
            (if (util/cached-entity db (keyword type))
              (if-let [eid (resolve-reference context type ref-id)]
                [[:db/add id ident eid]]
                (throw (ex-info (str "Reference `" reference "` can't be resolved.")
                                {::anom/category ::anom/incorrect
                                 :fhir/issue "value"
                                 :reference value
                                 :element/ident ident})))
              (throw (ex-info (str "Invalid reference `" reference `". The type `"
                                   type "` is unknown.")
                              {::anom/category ::anom/incorrect
                               :fhir/issue "value"
                               :reference value
                               :element/ident ident})))))
        identifier
        (throw (ex-info "Unsupported logical reference."
                        {::anom/category ::anom/unsupported
                         :fhir/issue "not-supported"
                         :reference value
                         :element/ident ident}))))

    "BackboneElement"
    (let [tid (d/tempid (keyword "part" path))
          tx-data (upsert context ident {:db/id tid} value)]
      (when-not (empty? tx-data)
        (conj tx-data [:db/add id ident tid])))

    "CodeableConcept"
    (let [tid (d/tempid (keyword "part" type-code))
          tx-data (upsert context (keyword type-code) {:db/id tid} value)
          code-tids (find-code-tids tx-data)]
      (when-not (empty? tx-data)
        (into
          (cond->
            (conj tx-data [:db/add id ident tid])
            part-of-choice-type?
            (conj [:db/add id type-attr-ident ident]))
          (map (fn [tid] [:db/add id (index-ident ident) tid]))
          (if part-of-choice-type? [] code-tids))))

    (let [tid (d/tempid (keyword "part" type-code))
          tx-data (upsert context (keyword type-code) {:db/id tid} value)]
      (when-not (empty? tx-data)
        (cond->
          (conj tx-data [:db/add id ident tid])
          part-of-choice-type?
          (conj [:db/add id type-attr-ident ident]))))))


(defn- add-element
  [context {:element/keys [primitive?] :as element} id value]
  (if primitive?
    (add-primitive-element context element id value)
    (add-non-primitive-element context element id value)))



;; ---- Upsert Element --------------------------------------------------------

(defn- upsert-primitive-card-one-element
  [{:keys [db] :as context} element old-entity new-value]
  (let [old-value (second (pull/pull-element db element old-entity))]
    (when-not (= old-value new-value)
      (add-primitive-element context element (:db/id old-entity) new-value))))


(defn- upsert-primitive-card-many-element
  {:arglists '([context element old-entity new-value])}
  [{:keys [db] :as context} {:db/keys [ident valueType] :element/keys [type-code] :as element}
   {:db/keys [id] :as old-entity} new-values]
  (let [old-values (set (second (pull/pull-element db element old-entity)))
        new-values (set new-values)
        retract (set/difference old-values new-values)
        add (set/difference new-values old-values)]
    (-> []
        (into
          (map (fn [value] [:db/retract id ident (write type-code valueType value)]))
          retract)
        (into
          (mapcat (fn [value] (add-primitive-element context element id value)))
          add))))


(defn- upsert-reference
  [context {:db/keys [ident] :as element} entity reference]
  (when-not (= (ident entity) (resolve-referenced-entity context reference))
    (add-non-primitive-element context element (:db/id entity) reference)))


(defn- upsert-non-primitive-card-one-element
  [context {:db/keys [ident] :element/keys [type-code] :as element} old-entity
   new-value]
  (if-let [old-value (ident old-entity)]
    (case type-code
      "Reference"
      (upsert-reference context element old-entity new-value)
      "BackboneElement"
      (upsert context ident old-value new-value)
      (upsert context (keyword type-code) old-value new-value))
    (add-non-primitive-element context element (:db/id old-entity) new-value)))


(defn- vector->set [x]
  (if (and (vector? x) (not (instance? Map$Entry x)))
    (set x)
    x))


(defn- gen-upsert-pairs [context type retract add]
  (for [old-entity retract
        new-entity add]
    {:old-entity old-entity
     :new-entity new-entity
     :retract-count
     (transduce
       (comp (filter #(= :db/retract (first %))) (map (constantly 1)))
       +
       (upsert context type (:entity (meta old-entity)) new-entity))}))


(defn- gen-contained-resource-upsert-pairs [context retract add]
  (for [old-entity retract
        :let [old-type (keyword (util/resource-type (:entity (meta old-entity))))]
        new-entity add
        :let [new-type (keyword (get new-entity "resourceType"))]
        :when (= old-type new-type)]
    {:old-entity old-entity
     :new-entity new-entity
     :retract-count
     (transduce
       (comp (filter #(= :db/retract (first %))) (map (constantly 1)))
       +
       (upsert context new-type (:entity (meta old-entity)) new-entity))}))


(defn- remove-duplicates
  [{:keys [seen-old seen-new] :as res} {:keys [old-entity new-entity] :as pair}]
  (if (or (contains? seen-old old-entity) (contains? seen-new new-entity))
    res
    (-> res
        (update :pairs conj pair)
        (update :seen-old conj old-entity)
        (update :seen-new conj new-entity))))


(defn- extract-upsert-pairs [num-reuse upsert-pairs]
  (->> (sort-by :retract-count upsert-pairs)
       (reduce remove-duplicates {:pairs [] :seen-old #{} :seen-new #{}})
       (:pairs)
       (take num-reuse)))


(defn- remove-non-stored-elements
  "Removes elements with are not stored by design like Reference.display from
  values."
  [type-code values]
  (case type-code
    "Reference"
    (mapv #(dissoc % "display") values)
    values))


(defn- upsert-non-primitive-card-many-element
  [{:keys [db] :as context}
   {:db/keys [ident] :element/keys [type-code] :as element}
   {:db/keys [id] :as old-entity} new-values]
  (let [type (case type-code "BackboneElement" ident (keyword type-code))
        old-entities (postwalk vector->set (second (pull/pull-element db element old-entity)))
        new-entities (postwalk vector->set (remove-non-stored-elements type-code new-values))
        retract (set/difference old-entities new-entities)
        add (set/difference new-entities old-entities)
        num-reuse (min (count retract) (count add))
        reuse (extract-upsert-pairs num-reuse (gen-upsert-pairs context type retract add))
        retract (set/difference retract (into #{} (map :old-entity) reuse))
        add (set/difference add (into #{} (map :new-entity) reuse))]
    (-> []
        (into
          (mapcat
            (fn [old-entity]
              (assert (:entity (meta old-entity)))
              (conj
                (upsert context type (:entity (meta old-entity)) nil)
                [:db/retract id ident (:db/id (:entity (meta old-entity)))])))
          retract)
        (into
          (mapcat
            (fn [{:keys [old-entity new-entity]}]
              (let [type (if (= :Resource type) (keyword (get new-entity "resourceType")) type)]
                (if (= :Reference type)
                  (upsert-reference context element (:entity (meta old-entity)) new-entity)
                  (upsert context type (:entity (meta old-entity)) new-entity)))))
          reuse)
        (into
          (mapcat #(add-non-primitive-element context element id %))
          add))))


(defn upsert-single-typed-element
  {:arglists '([db element old-entity new-entity new-value])}
  [context {:element/keys [primitive?] :db/keys [cardinality] :as element}
   old-entity new-value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (upsert-primitive-card-one-element
        context element old-entity new-value)
      (upsert-primitive-card-many-element
        context element old-entity new-value))
    (if (= :db.cardinality/one cardinality)
      (upsert-non-primitive-card-one-element
        context element old-entity new-value)
      (upsert-non-primitive-card-many-element
        context element old-entity new-value))))


(declare retract-single-typed-element)


(defn upsert-choice-typed-element
  [{:keys [db] :as context} {:db/keys [ident]} old-entity new-value
   new-value-element]
  (if-let [old-value-ident (ident old-entity)]
    (let [old-value-element (util/cached-entity db old-value-ident)]
      (if (= old-value-element new-value-element)
        (upsert-single-typed-element
          context old-value-element old-entity new-value)
        (into
          (if-let [old-value (old-value-ident old-entity)]
            (retract-single-typed-element context old-value-element old-entity old-value)
            [])
          (add-element
            context new-value-element (:db/id old-entity) new-value))))
    (add-element
      context new-value-element (:db/id old-entity) new-value)))


(defn upsert-element
  [context {:element/keys [choice-type?] :as element} old-entity new-value
   new-value-element]
  (if choice-type?
    (upsert-choice-typed-element
      context element old-entity new-value new-value-element)
    (upsert-single-typed-element
      context element old-entity new-value)))



;; ---- Retract Element -------------------------------------------------------


(defn- retract-primitive-card-one-element
  [{:element/keys [type-code] :db/keys [ident]} {:db/keys [id]} value]
  (assert (some? value))
  (case type-code
    "code"
    (do (assert (:db/id value)) [[:db/retract id ident (:db/id value)]])
    [[:db/retract id ident value]]))


(defn- retract-primitive-card-many-element
  [{:db/keys [ident]} {:db/keys [id]} value]
  (mapv (fn [value] (assert (some? value)) [:db/retract id ident value]) value))


(defn- retract-non-primitive-card-one-element
  [context {:element/keys [type-code] :db/keys [ident]} {:db/keys [id]} value]
  (assert (:db/id value))
  (conj
    (upsert context (keyword type-code) value nil)
    [:db/retract id ident (:db/id value)]))


(defn- retract-non-primitive-card-many-element
  [context {:db/keys [ident]} {:db/keys [id]} value]
  (into
    []
    (mapcat
      (fn [value]
        (assert (:db/id value))
        (conj
          (upsert context (keyword (util/resource-type value)) value nil)
          [:db/retract id ident (:db/id value)])))
    value))


(defn- retract-single-typed-element
  {:arglists '([db element entity value])}
  [context {:element/keys [primitive?] :db/keys [cardinality] :as element} entity
   value]
  (if primitive?
    (if (= :db.cardinality/one cardinality)
      (retract-primitive-card-one-element
        element entity value)
      (retract-primitive-card-many-element
        element entity value))
    (if (= :db.cardinality/one cardinality)
      (retract-non-primitive-card-one-element
        context element entity value)
      (retract-non-primitive-card-many-element
        context element entity value))))


(defn- retract-choice-typed-element
  [{:keys [db] :as context} {:db/keys [ident]} {:db/keys [id] :as entity}]
  (when-let [type-ident (ident entity)]
    (-> (if-some [value (type-ident entity)]
          (retract-single-typed-element context (util/cached-entity db type-ident) entity value)
          [])
        (conj [:db/retract id ident type-ident]))))


(defn- retract-element
  "Returns tx-data for retracting `element` from `entity` if it holds some
  value."
  {:arglists '([db element entity])}
  [context {:db/keys [ident] :element/keys [choice-type?] :as element} entity]
  (if choice-type?
    (retract-choice-typed-element context element entity)
    (when-some [value (ident entity)]
      (retract-single-typed-element context element entity value))))


(def ^:private remove-contained-resource-element
  (remove
    (fn [{:element/keys [type-code]}]
      (= "Resource" type-code))))


(s/fdef upsert
  :args (s/cat :context (s/keys :req-un [::ds/db])
               :type-ident (s/and keyword? #(not (#{:BackboneElement :Resource} %)))
               :old-entity some?
               :new-entity (s/nilable (s/map-of string? any?))))

(defn- upsert
  "Takes a `db` and `type-ident`, the old version of an entity as Datomic entity
  and the new version as JSON with string keys and returns Datomic transaction
  data for the update."
  [{:keys [db] :as context} type-ident old-entity new-entity]
  (let [context
        (cond-> context
          (= :Coding type-ident)
          (assoc :code new-entity)
          (= :CodeSystem type-ident)
          (assoc :code
                 (cond-> {"system" (get new-entity "url")}
                   (contains? new-entity "version")
                   (assoc "version" (get new-entity "version"))))
          (= :ConceptMap/group type-ident)
          (assoc :code
                 (cond-> {"system" (get new-entity "source")}
                   (contains? new-entity "sourceVersion")
                   (assoc "version" (get new-entity "sourceVersion")))
                 :target-code
                 (cond-> {"system" (get new-entity "target")}
                   (contains? new-entity "targetVersion")
                   (assoc "version" (get new-entity "targetVersion"))))
          (= :ConceptMap.group.element/target type-ident)
          (assoc :code (:target-code context))
          (= :ValueSet.compose/include type-ident)
          (assoc :code new-entity)
          (= :ValueSet.expansion/contains type-ident)
          (assoc :code new-entity))]
    (into
      []
      (comp
        (map #(util/cached-entity db %))
        remove-contained-resource-element
        (remove
          (fn [{:db/keys [ident]}]
            (#{:Coding/system :Coding/version} ident)))
        (mapcat
          (fn [element]
            (if-let [[new-value value-element] (find-json-value db element new-entity)]
              (upsert-element context element old-entity new-value value-element)
              (retract-element context element old-entity)))))
      (:type/elements (util/cached-entity db type-ident)))))


(defn- upsert-contained-resource
  [context old-entity new-entity]
  (if-let [id (get new-entity "id")]
    (let [type (keyword (util/resource-type old-entity))]
      {:tx-data (upsert context type old-entity (dissoc new-entity "id"))
       :id id
       :eid (:db/id old-entity)})
    (throw (ex-info "Invalid contained resource without `id` element."
                    {::anom/category ::anom/incorrect
                     :fhir/issue "value"
                     :contained-resource new-entity}))))


(defn- upsert-contained-resources
  [{:keys [db] :as context} type {container-id :db/id :as old-container} new-container]
  (when-let [{:db/keys [ident] :as element} (util/cached-entity db (keyword type "contained"))]
    (if-let [[new-values] (find-json-value db element new-container)]
      (let [old-entities (postwalk vector->set (second (pull/pull-element db element old-container)))
            new-entities (postwalk vector->set new-values)
            retract (set/difference old-entities new-entities)
            add (set/difference new-entities old-entities)
            num-reuse (min (count retract) (count add))
            reuse (extract-upsert-pairs num-reuse (gen-contained-resource-upsert-pairs context retract add))
            retract (set/difference retract (into #{} (map :old-entity) reuse))
            add (set/difference add (into #{} (map :new-entity) reuse))
            tx-data
            (into
              []
              (mapcat
                (fn [old-entity]
                  (let [old-entity (:entity (meta old-entity))]
                    (conj
                      (upsert context (keyword (util/resource-type old-entity)) old-entity nil)
                      [:db/retract container-id ident (:db/id old-entity)]))))
              retract)
            res
            (reduce
              (fn [res {:keys [old-entity new-entity]}]
                (let [{:keys [tx-data id eid]}
                      (upsert-contained-resource
                        context (:entity (meta old-entity)) new-entity)]
                  (-> res
                      (update :tx-data into tx-data)
                      (assoc-in [:eids id] eid))))
              {:tx-data tx-data}
              reuse)]
        (reduce
          (fn [res value]
            (if-let [{:keys [tx-data id eid]}
                     (add-contained-resource context element container-id value)]
              (-> res
                  (update :tx-data into tx-data)
                  (assoc-in [:eids id] eid))
              res))
          res
          add))
      {:tx-data
       (into
         []
         (mapcat
           (fn [{resource-id :db/id :as resource}]
             (conj
               (upsert context (keyword (util/resource-type resource)) resource
                       nil)
               [:db/retract resource-id :local-id (:local-id resource)]
               [:db/retract container-id ident resource-id])))
         (ident old-container))})))


(defn- upsert-resource
  [context type old-resource new-resource]
  (let [{:keys [tx-data eids]}
        (upsert-contained-resources context type old-resource new-resource)
        context
        (cond-> context
          eids
          (assoc :contained-resource-eids eids))]
    (into
      (or tx-data [])
      (upsert context (keyword type) old-resource new-resource))))


(defn- prepare-resource
  "Removes versionId and lastUpdated from meta because both will be set at read
  time from :version attribute and its transaction."
  [resource]
  (update resource "meta" dissoc "versionId" "lastUpdated"))


(defn- upsert-decrement [version]
  (if (util/deleted? version)
    (- version 1)
    (- version 2)))


(s/def ::resource
  (s/map-of string? some?))


(s/def ::tempids
  (s/map-of string? (s/map-of string? ::ds/tempid)))


(s/fdef resource-upsert
  :args (s/cat :db ::ds/db :tempids (s/nilable ::tempids) :initial-version #{0 -2}
               :resource ::resource)
  :ret ::ds/tx-data)

(defn resource-upsert
  "Generates transaction data for creating or updating `resource`.

  The resource has to have at least a `resourceType` and an `id`.

  Resources have an internal :version attribute that will be incremented on each
  update using CAS for optimistic locking. The function `transact-async` will
  return an error deferred with ::anom/conflict on optimistic locking conflicts.

  In order to differentiate between an internally generated `id` and an
  externally given one, resources created with the former will start with
  version 0 where resources created with a externally given `id` will start with
  version 1.

  Resources are maps taken straight from there parsed JSON with string keys."
  {:arglists '([db tempids initial-version resource])}
  [db tempids initial-version {type "resourceType" id "id" :as resource}]
  (assert type)
  (assert id)
  (let [resource (prepare-resource resource)]
    (if-let [old-resource (util/resource db type id)]

      (let [tx-data (upsert-resource {:db db :tempids tempids} type
                                     old-resource resource)
            {:db/keys [id] :keys [version]} old-resource]
        (when (or (not (empty? tx-data)) (util/deleted? version))
          (conj tx-data [:db.fn/cas id :version version (upsert-decrement version)])))

      (let [tempid (get-in tempids [type id])]
        (assert tempid)
        (conj (upsert-resource {:db db :tempids tempids} type
                               {:db/id tempid} resource)
              [:db.fn/cas tempid :version nil initial-version])))))


(defn- version-increment-delete [{:db/keys [id] :keys [version]}]
  (assert version)
  [:db.fn/cas id :version version (dec version)])


(s/fdef resource-deletion
  :args (s/cat :db ::ds/db :type string? :id string?)
  :ret ::ds/tx-data)

(defn resource-deletion
  "Takes a `db` and the `type` and `id` of a resource and returns Datomic
  transaction data for the deletion."
  [db type id]
  (when-let [resource (util/resource db type id)]
    (when-not (util/deleted? (:version resource))
      [(version-increment-delete resource)])))


(defn- category [e]
  (case (:db/error (ex-data e))
    :db.error/cas-failed ::anom/conflict
    ::anom/fault))


(s/fdef transact-async
  :args (s/cat :conn ::ds/conn :tx-data ::ds/tx-data)
  :ret md/deferred?)

(defn transact-async [conn tx-data]
  (-> (d/transact-async conn tx-data)
      (md/catch ExecutionException #(md/error-deferred (.getCause ^Exception %)))
      (md/catch
        (fn [e]
          (md/error-deferred
            (assoc (ex-data e)
              ::anom/category (category e)
              ::anom/message (.getMessage ^Exception e)))))))


(s/fdef resource-tempid
  :args (s/cat :db ::ds/db :resource ::resource))

(defn resource-tempid
  "Returns a triple of resource type, logical id and tempid for `resources`
  which have to be created in `db`."
  {:arglists '([db resource])}
  [db {type "resourceType" id "id"}]
  (when-not (util/resource db type id)
    [type id (d/tempid (keyword "part" type))]))


(comment
  (def term-base-uri "http://test.fhir.org/r4")

  (defn- fetch-code-system-info
    "Returns a map with a least :system and optionally :version if something was found."
    [value-set-binding code]
    (log/info "fetch-code-system-url" value-set-binding code)
    (let [[uri version] (str/split value-set-binding #"\|")]
      (-> (client/fetch (str term-base-uri "/ValueSet/$expand")
                        {:query-params
                         {"url" uri
                          "valueSetVersion" version
                          "filter" code}})
          (md/chain'
            (fn [value-set]
              (->> (-> value-set :expansion :contains)
                   (some #(when (= code (:code %)) %))))))))

  @(fetch-code-system-info "http://hl7.org/fhir/ValueSet/codesystem-content-mode|4.0.0" "complete")
  )
