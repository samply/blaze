(ns blaze.db.resource-store.cbor-test
  "Tests to understand CBOR in order to implement a more efficient CBOR based
  encoding for the Resource Store."
  (:require
   [blaze.fhir.spec]
   [blaze.test-util :as tu]
   [clj-cbor.core :as cbor]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest testing]]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.dataformat.cbor CBORFactory]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private cbor-object-mapper
  (j/object-mapper {:factory (CBORFactory.)}))

(deftest encode-cbor-jsonista-test
  (testing "primitive types"
    (are [x hex] (= hex (mapv #(bit-and % 0xFF) (j/write-value-as-bytes x cbor-object-mapper)))
      ;; Major type 0: unsigned integer
      0 [2r00000000]
      1 [2r00000001]
      23 [2r00010111]
      24 [2r00011000 24]
      25 [2r00011000 25]
      37 [2r00011000 37]

      ;; Major type 1: negative integer
      -1 [2r00100000]
      -2 [2r00100001]

      ;; Major type 2: byte string
      (byte-array 0) [2r01000000]
      (byte-array 1) [2r01000001 2r00000000]

      ;; Major type 3: text string
      "" [2r01100000]
      "\n" [2r01100001 0x0a]

      ;; Major type 4: array of data items
      [] [2r10000000]

      ;; Major type 5: map of pairs of data items
      {} [2r10111111 0xff]                                  ; Indefinite lengths variant

      ;; Major type 6: optional semantic tagging of other major types
      1M [2r11000100                                        ; Tag 4 (Decimal fractions)
          2r10000010                                        ; Array of length 2
          2r00000000                                        ; Scaling factor of 0
          2r00000001]                                       ; mantissa of 1
      0.1M [2r11000100                                      ; Tag 4 (Decimal fractions)
            2r10000010                                      ; Array of length 2
            2r00100000                                      ; Scaling factor of -1
            2r00000001]                                     ; mantissa of 1

      ;; Major type 7: floating-point numbers and simple data types
      false [2r11110100]
      true [2r11110101]
      nil [2r11110110])))

(deftest encode-cbor-clj-cbor-test
  (testing "primitive types"
    (are [x hex] (= hex (mapv #(bit-and % 0xFF) (cbor/encode x)))
      ;; Major type 0: unsigned integer
      0 [2r00000000]
      1 [2r00000001]
      23 [2r00010111]
      24 [2r00011000 24]
      25 [2r00011000 25]
      37 [2r00011000 37]

      ;; Major type 1: negative integer
      -1 [2r00100000]
      -2 [2r00100001]

      ;; Major type 2: byte string
      (byte-array 0) [2r01000000]
      (byte-array 1) [2r01000001 2r00000000]

      ;; Major type 3: text string
      "" [2r01100000]
      "\n" [2r01100001 0x0a]

      ;; Major type 4: array of data items
      [] [2r10000000]
      [1] [2r10000001 2r00000001]

      ;; Major type 5: map of pairs of data items
      {} [2r10100000]

      ;; Major type 6: optional semantic tagging of other major types
      1M [2r11000100                                        ; Tag 4 (Decimal fractions)
          2r10000010                                        ; Array of length 2
          2r00000000                                        ; Scaling factor of 0
          2r00000001]                                       ; mantissa of 1
      0.1M [2r11000100                                      ; Tag 4 (Decimal fractions)
            2r10000010                                      ; Array of length 2
            2r00100000                                      ; Scaling factor of -1
            2r00000001]                                     ; mantissa of 1

      ;; Major type 7: floating-point numbers and simple data types
      false [2r11110100]
      true [2r11110101]
      nil [2r11110110])))

(comment
  (require '[criterium.core :refer [bench quick-bench]]
           '[blaze.fhir.spec :as fhir-spec]
           '[blaze.fhir.spec.type :as type])

  (def observation
    {:category
     [#fhir/CodeableConcept
       {:coding
        [#fhir/Coding
          {:code #fhir/code "vital-signs"
           :system #fhir/uri "http://terminology.hl7.org/CodeSystem/observation-category"}]}]
     :meta
     #fhir/Meta
      {:profile [#fhir/canonical "https://fhir.bbmri.de/StructureDefinition/Bmi"]}
     :fhir/type :fhir/Observation
     :value
     #fhir/Quantity
      {:code #fhir/code "kg/m2"
       :system #fhir/uri "http://unitsofmeasure.org"
       :unit #fhir/string "kg/m2"
       :value #fhir/decimal 36.6M}
     :status #fhir/code "final"
     :effective #fhir/dateTime #system/date-time "2005-06-17"
     :id "0-bmi"
     :code
     #fhir/CodeableConcept
      {:coding
       [#fhir/Coding
         {:code #fhir/code "39156-5" :system #fhir/uri "http://loinc.org"}]}
     :subject #fhir/Reference{:reference #fhir/string "Patient/0"}})

  ;; 418
  (count (fhir-spec/unform-cbor observation)))
