(ns blaze.terminology-service.local.graph
  (:require
   [blaze.fhir.spec.type :as type]))

(defn- property-pred [code value]
  #(and (= code (type/value (:code %))) (= value (type/value (:value %)))))

(defn- conj-property [props {:keys [code value] :as prop}]
  (cond-> props
    (not (some (property-pred (type/value code) (type/value value)) props))
    (conj prop)))

(defn- merge-properties
  "Merges `new-props` into `current-props` not producing duplicates."
  [current-props new-props]
  (reduce conj-property current-props new-props))

(defn- hierarchy-codes
  "Returns all parent codes of `concept` as strings."
  {:arglists '([direction concept])}
  [direction {properties :property}]
  (keep #(when (= direction (type/value (:code %))) (type/value (:value %))) properties))

(defn- child-property [code]
  {:fhir/type :fhir.CodeSystem.concept/property
   :code #fhir/code"child"
   :value (type/code code)})

(defn- parent-property [code]
  {:fhir/type :fhir.CodeSystem.concept/property
   :code #fhir/code"parent"
   :value (type/code code)})

(defn- ensure-child-property
  "Ensures that `child-prop` exists for all parents with `parent-codes`."
  [concepts parent-codes child-prop]
  (reduce
   (fn [concepts parent-code]
     (update-in concepts [parent-code :property] (fnil conj-property []) child-prop))
   concepts parent-codes))

(defn- ensure-parent-property
  "Ensures that `parent-prop` exists for all children with `child-codes`."
  [concepts child-codes parent-prop]
  (reduce
   (fn [concepts child-code]
     (update-in concepts [child-code :property] (fnil conj-property []) parent-prop))
   concepts
   child-codes))

(defn- append-child-index [child-index parent-codes child-code]
  (reduce
   (fn [child-index parent-code]
     (update child-index parent-code (fnil conj #{}) child-code))
   child-index parent-codes))

(defn- append-parent-index [child-index child-codes parent-code]
  (reduce
   (fn [child-index child-code]
     (update child-index parent-code (fnil conj #{}) child-code))
   child-index child-codes))

(defn- assoc-child
  "Associates `child-code` to all `parent-codes` in the :children part of `graph`."
  [graph parent-codes child-code]
  (-> (update graph :concepts ensure-child-property parent-codes (child-property child-code))
      (update :child-index append-child-index parent-codes child-code)))

(defn- assoc-parent
  "Associates `parent-code` to all `child-codes` in the :children part of `graph`."
  [graph child-codes parent-code]
  (-> (update graph :concepts ensure-parent-property child-codes (parent-property parent-code))
      (update :child-index append-parent-index child-codes parent-code)))

(defn- merge-concept [{properties :property} concept]
  (cond-> concept
    properties (update :property merge-properties properties)))

(defn build-graph
  "Builds a graph from `concepts` of a code system."
  [concepts]
  (reduce
   (fn [graph {:keys [code] :as concept}]
     (let [code (type/value code)
           parent-codes (hierarchy-codes "parent" concept)
           child-codes (hierarchy-codes "child" concept)]
       (cond-> (update-in graph [:concepts code] merge-concept concept)
         (seq parent-codes) (assoc-child parent-codes code)
         (seq child-codes) (assoc-parent child-codes code))))
   {:concepts {}
    :child-index {}}
   concepts))

(defn- is-a-codes
  "Returns a set of descendant codes of and including `code`."
  [child-index code]
  (let [children (child-index code)]
    (cond-> #{code} children (into (mapcat (partial is-a-codes child-index)) children))))

(defn is-a
  "Returns a list of all descendant concepts of, including the concept with
  `code` itself."
  {:arglists '([graph code])}
  [{:keys [concepts child-index]} code]
  (into [] (map concepts) (is-a-codes child-index code)))
