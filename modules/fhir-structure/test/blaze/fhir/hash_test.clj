(ns blaze.fhir.hash-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.structure-definition-repo]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]))

(set! *warn-on-reflection* true)
(st/instrument)
(ig/init-key :blaze.fhir/structure-definition-repo {})

(test/use-fixtures :each tu/fixture)

(deftest from-hex
  (satisfies-prop 10000
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (= hash (hash/from-hex (str hash))))))

(deftest equals-test
  (let [hash (hash/generate {:fhir/type :fhir/Patient :id "0"})]
    (is (.equals hash hash))))

(deftest hashCode-test
  (is (= 1473621365 (.hashCode (hash/generate {:fhir/type :fhir/Patient :id "0"})))))

(deftest str-test
  (is (= "C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F"
         (str (hash/generate {:fhir/type :fhir/Patient :id "0"})))))

(deftest byte-buffer-test
  (satisfies-prop 10000
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [bb (bb/allocate hash/size)]
        (hash/into-byte-buffer! bb hash)
        (bb/flip! bb)
        (= hash (hash/from-byte-buffer! bb))))))

(deftest byte-array-test
  (satisfies-prop 10000
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [ba (hash/to-byte-array hash)]
        (= hash (hash/from-byte-buffer! (bb/wrap ba)))))))

(deftest prefix-test
  (satisfies-prop 10000
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (= (subs (str hash) 0 8)
         (str/upper-case (Long/toHexString (hash/prefix hash))))

      (= (hash/prefix hash)
         (apply hash/prefix [hash])))))

(deftest prefix-into-byte-buffer-test
  (satisfies-prop 10000
    (prop/for-all [hash (s/gen :blaze.resource/hash)]
      (let [bb (bb/allocate hash/prefix-size)]
        (hash/prefix-into-byte-buffer! bb hash)
        (bb/flip! bb)
        (= (hash/prefix hash) (apply hash/prefix-from-byte-buffer! [bb]))))))

(deftest prefix-from-hex
  (satisfies-prop 10000
    (prop/for-all [hash-prefix (gen/fmap hash/prefix (s/gen :blaze.resource/hash))]
      (= hash-prefix (hash/prefix-from-hex (Long/toHexString hash-prefix))))))

(deftest generate-test
  (testing "hashes are stable"
    (is (= (hash/generate {:fhir/type :fhir/Patient :id "0"})
           (hash/generate {:fhir/type :fhir/Patient :id "0"}))))

  (testing "hashes from different resource types are different"
    (is (not= (hash/generate {:fhir/type :fhir/Patient :id "0"})
              (hash/generate {:fhir/type :fhir/Observation :id "0"})))))

(deftest print-test
  (is (= (pr-str (hash/generate {:fhir/type :fhir/Patient :id "0"}))
         (pr-str #blaze/hash"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F")
         "#blaze/hash\"C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F\"")))
