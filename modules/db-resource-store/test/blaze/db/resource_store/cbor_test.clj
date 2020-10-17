(ns blaze.db.resource-store.cbor-test
  "Tests to understand CBOR in order to implement a more efficient CBOR based
  encoding for the Resource Store."
  (:require
    [blaze.fhir.spec]
    [cheshire.core :as cheshire]
    [clj-cbor.core :as cbor]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest encode-cbor-cheshire-test
  (testing "primitive types"
    (are [x hex] (= hex (mapv #(bit-and % 0xFF) (cheshire/generate-cbor x)))
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
      [] [2r10011111 0xff]                                  ; Indefinite lengths variant

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
      nil [2r11110110]))

  (testing "Observation"
    ))

(comment
  (require '[criterium.core :refer [bench quick-bench]]
           '[blaze.fhir.spec :as fhir-spec])

  (def observation
    {:category
     [{:coding
       [{:code #fhir/code"vital-signs"
         :system #fhir/uri"http://terminology.hl7.org/CodeSystem/observation-category"
         :fhir/type :fhir/Coding}]
       :fhir/type :fhir/CodeableConcept}]
     :meta
     {:profile [#fhir/canonical"https://fhir.bbmri.de/StructureDefinition/Bmi"]
      :fhir/type :fhir/Meta}
     :fhir/type :fhir/Observation
     :value
     {:code #fhir/code"kg/m2"
      :system #fhir/uri"http://unitsofmeasure.org"
      :unit "kg/m2"
      :value 36.6M
      :fhir/type :fhir/Quantity}
     :status #fhir/code"final"
     :effective #fhir/dateTime"2005-06-17"
     :id "0-bmi"
     :code
     {:coding
      [{:code #fhir/code"39156-5" :system #fhir/uri"http://loinc.org" :fhir/type :fhir/Coding}]
      :fhir/type :fhir/CodeableConcept}
     :subject
     {:reference "Patient/0" :fhir/type :fhir/Reference}})

  (count (cheshire/generate-cbor (fhir-spec/unform-cbor observation)))
  (count (cbor/encode (fhir-spec/unform-cbor observation)))

  )
