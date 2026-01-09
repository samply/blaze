(ns blaze.fhir.spec.type-test-mem
  (:require
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.memory :as mem]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.test-util]
   [clojure.alpha.spec :as s2]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [jsonista.core :as j])
  (:import
   [blaze.fhir.spec.type Base]))

(def ^:private parsing-context
  (ig/init-key
   :blaze.fhir/parsing-context
   {:structure-definition-repo structure-definition-repo}))

(defn- parse-json
  ([data]
   (fhir-spec/parse-json parsing-context (j/write-value-as-string data)))
  ([type data]
   (fhir-spec/parse-json parsing-context type (j/write-value-as-string data))))

(comment
  (mem/print-total-layout #fhir/string "12345678")
  (mem/print-layout #fhir/string "12345678")
  (mem/print-class-layout #fhir/string{:id "foo"})
  (mem/print-total-layout {:a 1})
  (mem/print-footprint
   {:a 1 :b 1 :c 1 :e 1 :f 1 :g 1 :h 1 :i 1 :j 1}
   :a :b :c :d :e :f :g :h :i :j 1)
  (mem/print-layout (parse-json {:resourceType "Observation" :id "192116"}))
  (mem/print-footprint
   (parse-json {:resourceType "Observation" :id "192116"})
   :fhir/type :fhir/Observation :id)
  (total-size #fhir/decimal 1.1M))

(defn- total-size [x]
  (if (Base/isInterned x) 0 (mem/total-size x)))

(deftest mem-test
  (are [x size] (= (total-size x) (Base/memSize x) size)
    #fhir/integer 1 24

    #fhir/string "" 0
    #fhir/string "a" 0
    #fhir/string{:value "a"} 0
    (type/string (str/join (repeat 12 "a"))) 64
    (type/string (str/join (repeat 13 "a"))) 72
    #fhir/string{:id "0" :value "foo"} 112

    (type/string "あいうえおか") 64
    (type/string "あいうえおかき") 72

    #fhir/decimal 1.1M 48

    #fhir/uri "" 0
    #fhir/uri "a" 0

    #fhir/url "" 56
    #fhir/url "a" 56

    #fhir/canonical "" 0
    #fhir/canonical "a" 0

    #fhir/base64Binary "" 56
    #fhir/base64Binary "YQo=" 56
    #fhir/base64Binary "MTA1NjE0Cg==" 64

    #fhir/date #system/date "2020" 32
    #fhir/date #system/date "2020-01" 32
    #fhir/date #system/date "2020-01-01" 32

    #fhir/dateTime #system/date-time "2020" 32
    #fhir/dateTime #system/date-time "2020-01" 32
    #fhir/dateTime #system/date-time "2020-01-01" 32

    #fhir/dateTime #system/date-time "2020-01-01T00:00:00" 64
    #fhir/dateTime #system/date-time "2020-01-01T00:00:00.000" 64

    #fhir/time #system/time "13:53:21" 32

    #fhir/code "" 0
    #fhir/code "175718" 0

    #fhir/oid "" 56
    #fhir/oid "175718" 64

    #fhir/id "" 56
    #fhir/id "175718" 64

    #fhir/markdown "" 56
    #fhir/markdown "175718" 64

    #fhir/unsignedInt 0 16
    #fhir/unsignedInt 175718 16

    #fhir/positiveInt 1 16
    #fhir/positiveInt 175718 16

    #fhir/uuid "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" 40

    #fhir/xhtml "" 56
    #fhir/xhtml "175718" 64

    #fhir/Attachment{} 48

    #fhir/Extension{} 0

    #fhir/Coding{} 0

    #fhir/CodeableConcept{} 0

    #fhir/Quantity{} 0

    #fhir/Ratio{} 0

    #fhir/Period{} 0

    #fhir/Identifier{} 40

    #fhir/HumanName{} 40

    #fhir/Address{} 56

    #fhir/Reference{} 32

    #fhir/Meta{} 0
    #fhir/Meta{:profile [#fhir/canonical "foo"]} 0

    #fhir.Bundle.entry/search{} 24)

  (testing "interning"
    (are [x y] (= (mem/total-size x) (mem/total-size x y))
      #fhir/Meta{:profile [#fhir/canonical "foo"]}
      #fhir/Meta{:profile [#fhir/canonical "foo"]}

      #fhir/Extension{:url "url-191107"}
      #fhir/Extension{:url "url-191107"}

      (parse-json "Extension" {:url "url-191107"})
      (parse-json "Extension" {:url "url-191107"})

      (parse-json "Quantity" {:unit "unit-191622"})
      (parse-json "Quantity" {:unit "unit-191622"})

      (parse-json "Coding" {:display "display-191622"})
      (parse-json "Coding" {:display "display-191622"})

      #fhir/Coding{:system #fhir/uri-interned "foo" :code #fhir/code "bar"}
      #fhir/Coding{:system #fhir/uri-interned "foo" :code #fhir/code "bar"}))

  (testing "Observation"
    (are [x excludes size] (= (apply mem/total-size-exclude x excludes) (Base/memSize x) size)
      (parse-json {:resourceType "Observation" :id "192116"})
      [:fhir/type :fhir/Observation :id]
      104

      (parse-json {:resourceType "Observation" :status "final"})
      [:fhir/type :fhir/Observation :status #fhir/code "final"]
      56

      (parse-json {:resourceType "Observation" :category [{:text "vital-signs"}]})
      [:fhir/type :fhir/Observation :category [#fhir/CodeableConcept{:text #fhir/string-interned "vital-signs"}]]
      112)))
