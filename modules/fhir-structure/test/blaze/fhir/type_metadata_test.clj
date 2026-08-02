(ns blaze.fhir.type-metadata-test
  "Tests of the four map classes and the type metadata they share.

  The most important property here is that a value of one of those classes
  hashes byte-identical to the plain map it replaces. Content hashes are stored,
  not recomputed, so changing them would invalidate the content-addressed
  resource store."
  (:require
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.type-metadata :as tm]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop])
  (:import
   [blaze.fhir.spec.type BackboneElementMap DomainResourceMap ElementMap
    FhirMap ResourceMap]
   [blaze.fhir.writing PropertyIndex]
   [clojure.lang IKeywordLookup Keyword]))

(st/instrument)
(set! *warn-on-reflection* true)

(test/use-fixtures :each tu/fixture)

(def ^:private patient
  #fhir/map{:fhir/type :fhir/Patient
            :id "0"
            :meta #fhir/Meta{:versionId #fhir/id "1"}
            :active #fhir/boolean true
            :name [#fhir/HumanName{:family #fhir/string "Doe"}]})

(def ^:private component
  #fhir/map{:fhir/type :fhir.Observation/component
            :code #fhir/CodeableConcept{:text #fhir/string "code-102432"}
            :value #fhir/string "value-102446"})

(deftest registry-test
  (testing "one metadata instance per FHIR type that is represented as a map"
    (is (= 619 (count (tm/registry)))))

  (testing "complex types with a Java implementation have no metadata"
    (is (nil? (tm/type-metadata :fhir/Coding)))
    (is (nil? (tm/type-metadata :fhir.Timing/repeat))))

  (testing "unknown types have no metadata"
    (is (nil? (tm/type-metadata :fhir/Foo))))

  (testing "the metadata of a type is one shared instance"
    (is (identical? (tm/type-metadata :fhir/Patient)
                    (.metadata ^FhirMap patient)))))

;; ---- Class per FHIR Abstract Type ------------------------------------------

(deftest class-test
  (testing "resources that are no domain resources"
    (is (instance? ResourceMap #fhir/map{:fhir/type :fhir/Bundle}))
    (is (instance? ResourceMap #fhir/map{:fhir/type :fhir/Parameters}))
    (is (instance? ResourceMap #fhir/map{:fhir/type :fhir/Binary}))
    (is (not (instance? DomainResourceMap #fhir/map{:fhir/type :fhir/Bundle}))))

  (testing "domain resources"
    (is (instance? DomainResourceMap patient)))

  (testing "backbone elements"
    (is (instance? BackboneElementMap component))
    (testing "including the named complex types without a Java implementation"
      (is (instance? BackboneElementMap #fhir/map{:fhir/type :fhir/ElementDefinition}))))

  (testing "elements nested in a complex type"
    (is (instance? ElementMap #fhir/map{:fhir/type :fhir.ElementDefinition/slicing}))

    (testing "answer modifierExtension the way a plain map does, unlike a
              backbone element"
      (is (nil? (:modifierExtension #fhir/map{:fhir/type :fhir.ElementDefinition/slicing})))
      (is (not (contains? #fhir/map{:fhir/type :fhir.ElementDefinition/slicing}
                          :modifierExtension)))
      (is (= [] (:modifierExtension component))))))

;; ---- Element Order ---------------------------------------------------------

(deftest element-order-test
  (testing "the properties are in element-definition order"
    (is (= [:id :meta :implicitRules :language :text :contained :extension
            :modifierExtension :identifier :active :name]
           (take 11 (vec (.keys (tm/type-metadata :fhir/Patient)))))))

  (testing "a backbone element starts with the properties of BackboneElement"
    (is (= [:id :extension :modifierExtension :code :value :dataAbsentReason
            :interpretation :referenceRange]
           (vec (.keys (tm/type-metadata :fhir.Observation/component))))))

  (testing "an element starts with the properties of Element"
    (is (= [:id :extension :discriminator :description :ordered :rules]
           (vec (.keys (tm/type-metadata :fhir.ElementDefinition/slicing))))))

  (testing "seq is in element order, with the type first"
    (is (= [[:fhir/type :fhir/Patient]
            [:id "0"]
            [:meta #fhir/Meta{:versionId #fhir/id "1"}]
            [:active #fhir/boolean true]
            [:name [#fhir/HumanName{:family #fhir/string "Doe"}]]]
           (vec (seq patient))))))

;; ---- Hash Compatibility ----------------------------------------------------

(defn- plain
  "Returns `x` with every value of one of the four map classes replaced by the
  plain map it would have been before."
  [x]
  (cond
    (instance? FhirMap x) (into {} (map (fn [[k v]] [k (plain v)])) x)
    (vector? x) (mapv plain x)
    :else x))

(defn- hash-agrees?
  "Whether `value` hashes exactly the way the plain map it replaces does."
  [value]
  (= (hash/generate value) (hash/generate (plain value))))

(deftest hash-test
  (testing "hashes stay byte-identical to the ones of the equivalent plain map"
    (is (hash-agrees? patient))
    (is (hash-agrees? component))

    (testing "for every generated value of"
      (doseq [gen [fg/patient fg/observation fg/bundle fg/consent fg/library
                   fg/code-system fg/imaging-study fg/activity-definition
                   fg/medication-administration]]
        (satisfies-prop 20
          (prop/for-all [value (gen)]
            (hash-agrees? value))))))

  (testing ":fhir/type participates and sorts last"
    (is (not= (hash/generate #fhir/map{:fhir/type :fhir/Patient :id "0"})
              (hash/generate #fhir/map{:fhir/type :fhir/Practitioner :id "0"}))))

  (testing "a nil value is indistinguishable from an absent one"
    (is (= (hash/generate patient) (hash/generate (assoc patient :gender nil))))))

;; ---- Map Protocol ----------------------------------------------------------

(deftest val-at-test
  (testing "properties held in the values array"
    (is (= #fhir/boolean true (:active patient))))

  (testing "leading properties of a resource"
    (is (= "0" (:id patient)))
    (is (= #fhir/Meta{:versionId #fhir/id "1"} (:meta patient)))
    (is (nil? (:implicitRules patient)))
    (is (nil? (:language patient)))
    (is (nil? (:text patient)))
    (is (nil? (:contained patient))))

  (testing "the type comes from the metadata"
    (is (= :fhir/Patient (:fhir/type patient))))

  (testing "unknown keys"
    (is (nil? (:foo patient)))
    (is (= ::not-found (get patient :foo ::not-found)))))

(deftest count-test
  (testing "the type counts as one entry, absent properties don't"
    (is (= 5 (count patient)))
    (is (= 1 (count #fhir/map{:fhir/type :fhir/Patient})))
    (is (= 3 (count component)))))

(deftest assoc-test
  (testing "a property held in the values array"
    (is (= #fhir/boolean false (:active (assoc patient :active #fhir/boolean false)))))

  (testing "a leading property of a resource"
    (is (= "1" (:id (assoc patient :id "1")))))

  (testing "a leading property of a backbone element"
    (is (= "1" (:id (assoc component :id "1")))))

  (testing "everything else stays"
    (is (= (dissoc (assoc patient :active #fhir/boolean false) :active)
           (dissoc patient :active))))

  (testing "an unknown key is ignored, the way `Coding` ignores it"
    (is (identical? patient (assoc patient :foo "bar"))))

  (testing "nil clears the property"
    (is (nil? (:active (assoc patient :active nil))))
    (is (= 4 (count (assoc patient :active nil)))))

  (testing "dissoc falls out of that"
    (is (= (assoc patient :active nil) (dissoc patient :active))))

  (testing "the values array isn't shared with the new value"
    (let [other (assoc patient :active #fhir/boolean false)]
      (is (= #fhir/boolean true (:active patient)))
      (is (= #fhir/boolean false (:active other))))))

(deftest empty-test
  (testing "returns a value of the same type with no properties"
    (is (= #fhir/map{:fhir/type :fhir/Patient} (empty patient)))
    (is (= :fhir/Patient (:fhir/type (empty patient))))
    (is (= #fhir/map{:fhir/type :fhir.Observation/component} (empty component))))

  (testing "is a fully valid value that can be filled again"
    (is (= #fhir/map{:fhir/type :fhir/Patient :id "0"}
           (assoc (empty patient) :id "0")))))

(deftest kv-reduce-test
  (testing "walks the type and all present properties"
    (is (= [:fhir/type :id :meta :active :name]
           (reduce-kv (fn [r k _] (conj r k)) [] patient))))

  (testing "seeding with the value itself keeps the type, like `links.clj` does"
    (is (= patient (reduce-kv assoc patient patient)))))

(deftest iterator-test
  (testing "into a plain map"
    (is (= {:fhir/type :fhir/Patient
            :id "0"
            :meta #fhir/Meta{:versionId #fhir/id "1"}
            :active #fhir/boolean true
            :name [#fhir/HumanName{:family #fhir/string "Doe"}]}
           (into {} patient))))

  (testing "keys"
    (is (= #{:fhir/type :id :meta :active :name} (set (keys patient)))))

  (testing "vals"
    (is (= 5 (count (vals patient))))))

(deftest contains-key-test
  (is (contains? patient :id))
  (is (contains? patient :active))
  (is (not (contains? patient :gender)))
  (is (not (contains? patient :foo))))

(deftest invoke-test
  (testing "a value can be used as function of its keys, like `#(% :id)` does"
    (is (= "0" (patient :id)))
    (is (= ::not-found (patient :foo ::not-found)))))

(deftest meta-test
  (testing "the Clojure metadata is unrelated to the FHIR meta element"
    (let [patient (with-meta patient {:blaze.db/t 1})]
      (is (= {:blaze.db/t 1} (meta patient)))
      (is (= #fhir/Meta{:versionId #fhir/id "1"} (:meta patient)))))

  (testing "on a backbone element"
    (is (= {:blaze.db/t 1} (meta (with-meta component {:blaze.db/t 1})))))

  (testing "isn't part of equality"
    (is (= patient (with-meta patient {:blaze.db/t 1})))))

(deftest equals-test
  (testing "values of the same type with the same properties are equal"
    (is (= #fhir/map{:fhir/type :fhir/Patient :id "0"}
           #fhir/map{:fhir/type :fhir/Patient :id "0"})))

  (testing "the type is part of equality"
    (is (not= #fhir/map{:fhir/type :fhir/Patient :id "0"}
              #fhir/map{:fhir/type :fhir/Practitioner :id "0"})))

  (testing "hasheq is stable"
    (is (= (hash #fhir/map{:fhir/type :fhir/Patient :id "0"})
           (hash #fhir/map{:fhir/type :fhir/Patient :id "0"})))))

(deftest mem-size-test
  (testing "is never zero, because the resource cache is weighed by it"
    (is (pos? (.memSize ^FhirMap patient)))
    (is (pos? (.memSize ^FhirMap component)))
    (is (pos? (.memSize ^FhirMap #fhir/map{:fhir/type :fhir/Bundle}))))

  (testing "grows with the properties"
    (is (< (.memSize ^FhirMap #fhir/map{:fhir/type :fhir/Patient})
           (.memSize ^FhirMap patient)))))

(deftest references-test
  (testing "collects the references of all properties"
    (is (= [["Patient" "0"]]
           (type/references
            #fhir/map{:fhir/type :fhir/Observation
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}))))

  (testing "a bundle entry contributes none, because its resources are new"
    (is (= []
           (type/references
            #fhir/map{:fhir/type :fhir.Bundle/entry
                      :resource
                      #fhir/map{:fhir/type :fhir/Observation
                                :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}})))))

;; ---- Lookup Thunks ---------------------------------------------------------

(defn- thunk [value key]
  (.getLookupThunk ^IKeywordLookup value key))

(defn- thunk-get [thunk target]
  (let [result (.get ^clojure.lang.ILookupThunk thunk target)]
    (if (identical? result thunk) ::miss result)))

(deftest lookup-thunk-test
  (testing "every property has a thunk"
    (doseq [key (.keys (tm/type-metadata :fhir/Patient))]
      (is (some? (thunk patient key)))))

  (testing "a thunk returns the value of its property"
    (is (= #fhir/boolean true (thunk-get (thunk patient :active) patient)))
    (is (= "0" (thunk-get (thunk patient :id) patient)))
    (is (= #fhir/Meta{:versionId #fhir/id "1"} (thunk-get (thunk patient :meta) patient)))
    (is (= :fhir/Patient (thunk-get (thunk patient :fhir/type) patient)))
    (is (= #fhir/CodeableConcept{:text #fhir/string "code-102432"}
           (thunk-get (thunk component :code) component))))

  ;; a guard that is too wide would read the slot of a different property out of
  ;; a value of another type sharing the same Java class, silently returning a
  ;; completely unrelated value
  (testing "a thunk of one type misses on a value of another type of the same
            class"
    (let [observation #fhir/map{:fhir/type :fhir/Observation :status #fhir/code "final"}
          practitioner #fhir/map{:fhir/type :fhir/Practitioner :gender #fhir/code "male"}]
      (is (= ::miss (thunk-get (thunk observation :status) practitioner)))
      (is (= ::miss (thunk-get (thunk observation :status) patient)))
      (is (= ::miss (thunk-get (thunk observation :fhir/type) practitioner)))
      (is (= ::miss (thunk-get (thunk component :code) patient)))))

  (testing "the leading fields of a resource are answered for every resource
            type, so polymorphic call sites stay fast"
    (is (= "0" (thunk-get (thunk patient :id) #fhir/map{:fhir/type :fhir/Bundle :id "0"})))
    (is (= "0" (thunk-get (thunk patient :id) #fhir/map{:fhir/type :fhir/Observation :id "0"}))))

  (testing "the leading fields of an element are answered for every element,
            including the complex types with a Java implementation"
    (is (= "0" (thunk-get (thunk component :id) #fhir/Coding{:id "0"})))
    (is (= "0" (thunk-get (thunk component :id) (assoc component :id "0"))))))

;; ---- Data Reader -----------------------------------------------------------

(defn- thrown-message [f]
  (try
    (f)
    nil
    (catch Exception e (ex-message e))))

(deftest fhir-map-test
  (testing "the type has to be known"
    (is (= "Unknown FHIR type `:fhir/Foo`."
           (thrown-message #(type/fhir-map {:fhir/type :fhir/Foo})))))

  (testing "the type is required"
    (is (= "Missing `:fhir/type` property."
           (thrown-message #(type/fhir-map {:id "0"})))))

  (testing "unknown keys are rejected, unlike on `assoc`"
    (is (= "Unknown property `:foo` in type `:fhir/Patient`."
           (thrown-message #(type/fhir-map {:fhir/type :fhir/Patient :foo "bar"})))))

  (testing "printing round-trips"
    (is (= "#fhir/map{:fhir/type :fhir/Patient :id \"0\" :active #fhir/boolean true}"
           (pr-str #fhir/map{:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true})))
    (is (= patient (read-string (pr-str patient))))
    (is (= component (read-string (pr-str component))))))

;; ---- Property Index --------------------------------------------------------

(defn- property-index ^PropertyIndex [keys]
  (PropertyIndex. (into-array Keyword keys)))

(def ^:private distinct-keys
  (gen/vector-distinct gen/keyword {:min-elements 1 :max-elements 64}))

(deftest property-index-test
  (testing "every key is found at the index of its property handler"
    (satisfies-prop 1000
      (prop/for-all [keys distinct-keys]
        (let [index (property-index keys)]
          (every? (fn [[i key]] (= i (.get index key))) (map-indexed vector keys))))))

  (testing "keys without a property handler are not found"
    (satisfies-prop 1000
      (prop/for-all [keys distinct-keys
                     other-keys distinct-keys]
        (let [index (property-index keys)]
          (every? #(= -1 (.get index %)) (remove (set keys) other-keys))))))

  (testing "non-keyword keys are not found"
    (let [index (property-index [:foo])]
      (is (= -1 (.get index "foo")))
      (is (= -1 (.get index nil)))))

  (testing "duplicate keys are rejected"
    (is (thrown? IllegalArgumentException (property-index [:foo :foo])))))
