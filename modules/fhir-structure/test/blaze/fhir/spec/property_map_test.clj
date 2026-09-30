(ns blaze.fhir.spec.property-map-test
  (:require
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [juxt.iota :refer [given]])
  (:import
   [blaze.fhir.spec PropertyMap PropertyMap$NullElement]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- property-map
  "Returns a property map with `values` put into their slots, leaving slots of
  nil values empty."
  ^PropertyMap [& values]
  (let [property-map (PropertyMap. (count values))]
    (doseq [[slot value] (map-indexed vector values) :when (some? value)]
      (.put property-map slot value))
    property-map))

(defn- keys-of [n]
  (object-array (map #(keyword (str "k" %)) (range n))))

(deftest put-test
  (testing "putting a null value fails"
    (let [property-map (PropertyMap. 1)]
      (is (thrown? NullPointerException (.put property-map 0 nil)))

      (testing "and leaves the property map unchanged"
        (is (zero? (.count property-map)))
        (is (nil? (.get property-map 0)))))))

(deftest count-test
  (testing "a new property map has no entries"
    (is (zero? (.count (PropertyMap. 2)))))

  (testing "putting values into empty slots increments the count"
    (is (= 2 (.count (property-map 0 nil 2)))))

  (testing "replacing a value keeps the count"
    (is (= 1 (.count (.put (property-map 0 nil) 0 1)))))

  (testing "the value is replaced"
    (is (= 1 (.get (.put (property-map 0 nil) 0 1) 0)))))

(deftest to-persistent-map-test
  (testing "empty slots"
    (is (= {:fhir/type :fhir/Foo}
           (.toPersistentMap (property-map nil nil) (keys-of 2) :fhir/Foo))))

  (testing "only non-empty slots are included"
    (are [values m] (= m (.toPersistentMap ^PropertyMap (apply property-map values)
                                           (keys-of (count values)) :fhir/Foo))
      [0] {:k0 0 :fhir/type :fhir/Foo}
      [0 nil] {:k0 0 :fhir/type :fhir/Foo}
      [nil 1] {:k1 1 :fhir/type :fhir/Foo}
      [0 nil 2] {:k0 0 :k2 2 :fhir/type :fhir/Foo})))

(deftest first-null-element-test
  (testing "without marked slots there is no null element"
    (is (nil? (.firstNullElement (property-map [0 nil])))))

  (testing "a null element is found"
    (is (= (PropertyMap$NullElement. 1 1 "string")
           (.firstNullElement (doto (property-map [0 1] [0 nil])
                                (.markNullElements 1 "string"))))))

  (testing "an empty single slot is found without index"
    (is (= (PropertyMap$NullElement. 0 -1 "date")
           (.firstNullElement (doto (property-map nil 1)
                                (.markNullElements 0 "date"))))))

  (testing "a non-empty single slot isn't a null element"
    (is (nil? (.firstNullElement (doto (property-map 0)
                                   (.markNullElements 0 "date")))))))

(deftest remove-null-elements-test
  (testing "without marked slots the property map is unchanged"
    (let [property-map (property-map [0 nil] 1)]
      (is (identical? property-map (.removeNullElements property-map)))
      (is (= [0 nil] (.get property-map 0)))
      (is (= 2 (.count property-map)))))

  (testing "null elements of marked slots are removed"
    (let [property-map (doto (property-map [nil 0 nil 1] [nil 2])
                         (.markNullElements 0 "string"))]
      (is (= [0 1] (.get (.removeNullElements property-map) 0)))

      (testing "unmarked slots are unchanged"
        (is (= [nil 2] (.get property-map 1))))

      (testing "no null element remains"
        (is (nil? (.firstNullElement property-map))))))

  (testing "empty marked single slots stay empty"
    (let [property-map (doto (property-map nil 1)
                         (.markNullElements 0 "date"))]
      (.removeNullElements property-map)
      (is (nil? (.get property-map 0)))
      (is (= 1 (.count property-map)))))

  (testing "non-empty marked single slots are unchanged"
    (let [property-map (doto (property-map 0)
                         (.markNullElements 0 "date"))]
      (is (= 0 (.get (.removeNullElements property-map) 0)))
      (is (= 1 (.count property-map)))))

  (testing "marked slots without null elements are unchanged"
    (let [list [0 1]
          property-map (doto (property-map list)
                         (.markNullElements 0 "string"))]
      (is (identical? list (.get (.removeNullElements property-map) 0)))))

  (testing "empty marked slots are emptied"
    (let [property-map (doto (property-map [] 1)
                         (.markNullElements 0 "string"))]
      (.removeNullElements property-map)
      (is (nil? (.get property-map 0)))
      (is (= 1 (.count property-map)))))

  (testing "marked slots containing only null elements are emptied"
    (let [property-map (doto (property-map [nil nil] 1)
                         (.markNullElements 0 "string"))]
      (.removeNullElements property-map)
      (is (nil? (.get property-map 0)))
      (is (= 1 (.count property-map)))
      (given (.toPersistentMap property-map (keys-of 2) :fhir/Foo)
        count := 2
        :fhir/type := :fhir/Foo
        :k1 := 1))))
