(ns blaze.fhir.spec.property-map-test
  (:require
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]])
  (:import
   [blaze.fhir.spec PropertyMap]))

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
