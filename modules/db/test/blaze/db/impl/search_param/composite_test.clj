(ns blaze.db.impl.search-param.composite-test
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.nio ByteBuffer]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def code-value-quantity-param
  (sr/get search-param-registry "code-value-quantity" "Observation"))


(deftest code-test
  (is (= "code-value-quantity" (:code code-value-quantity-param))))


(deftest name-test
  (is (= "code-value-quantity" (:name code-value-quantity-param))))


(defn- split-value [hex]
  [(subs hex 0 8) (subs hex 8)])


(defn compile-code-quantity-value [value]
  (let [[[op lower-bound exact-value upper-bound]]
        (search-param/compile-values code-value-quantity-param [value])]
    [op
     (split-value (codec/hex lower-bound))
     (split-value (codec/hex exact-value))
     (split-value (codec/hex upper-bound))]))


(defn- hex-v-hash [value]
  (codec/hex (codec/v-hash value)))


(defn- hex-quantity [unit value]
  (codec/hex (codec/quantity unit value)))


(deftest compile-value-test
  (are [value op lower-bound exact-value upper-bound]
    (= [op lower-bound exact-value upper-bound] (compile-code-quantity-value value))

    "8480-6$23.4"
    :eq
    [(hex-v-hash "8480-6") (hex-quantity nil 23.35M)]
    [(hex-v-hash "8480-6") (hex-quantity nil 23.40M)]
    [(hex-v-hash "8480-6") (hex-quantity nil 23.45M)]

    "8480-6$ge23|kg/m2"
    :ge
    [(hex-v-hash "8480-6") (hex-quantity "kg/m2" 22.5M)]
    [(hex-v-hash "8480-6") (hex-quantity "kg/m2" 23.00M)]
    [(hex-v-hash "8480-6") (hex-quantity "kg/m2" 23.5M)])

  (testing "invalid quantity decimal value"
    (given (search-param/compile-values code-value-quantity-param ["a$a"])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid decimal value `a` in search parameter `code-value-quantity`."))

  (testing "unsupported quantity prefix"
    (given (search-param/compile-values code-value-quantity-param ["a$ne1"])
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported prefix `ne` in search parameter `code-value-quantity`.")))


(defn- decode-sp-value-resource-key [bs]
  (codec/decode-sp-value-resource-key-human (ByteBuffer/wrap bs)))


(defn- decode-resource-sp-value-key [bs]
  (codec/decode-resource-sp-value-key-human (ByteBuffer/wrap bs)))


(def ^:private observation-code
  (codec/hex (codec/v-hash "8480-6")))


(def ^:private observation-system
  (codec/hex (codec/v-hash "http://loinc.org|")))


(def ^:private observation-system-code
  (codec/hex (codec/v-hash "http://loinc.org|8480-6")))


(def ^:private value
  (codec/hex (codec/quantity nil 100M)))


(def ^:private value-code
  (codec/hex (codec/quantity "mm[Hg]" 100M)))


(def ^:private value-system-code
  (codec/hex (codec/quantity "http://unitsofmeasure.org|mm[Hg]" 100M)))


(deftest index-entries-test
  (testing "Observation code-value-quantity"
    (let [observation
          {:fhir/type :fhir/Observation
           :id "id-155558"
           :status #fhir/code"final"
           :code
           {:fhir/type :fhir/CodeableConcept
            :coding
            [{:fhir/type :fhir/Coding
              :system #fhir/uri"http://loinc.org"
              :code #fhir/code"8480-6"}]}
           :value
           {:fhir/type :fhir/Quantity
            :value 100M
            :code #fhir/code"mm[Hg]"
            :system #fhir/uri"http://unitsofmeasure.org"}}
          hash (hash/generate observation)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]
           [_ k6] [_ k7] [_ k8] [_ k9] [_ k10] [_ k11]
           [_ k12] [_ k13] [_ k14] [_ k15] [_ k16] [_ k17]]
          (search-param/index-entries code-value-quantity-param
                                      hash observation [])]

      (testing "`code` followed by `value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k0)
            :code := "code-value-quantity"
            :type := "Observation"
            [:value split-value 0] := observation-code
            [:value split-value 1] := value
            :id := "id-155558"
            :hash-prefix := (codec/hex (codec/hash-prefix hash))))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k1)
            :type := "Observation"
            :id := "id-155558"
            :hash-prefix := (codec/hex (codec/hash-prefix hash))
            :code := "code-value-quantity"
            [:value split-value 0] := observation-code
            [:value split-value 1] := value)))

      (testing "`code` followed by `code value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k2)
            [:value split-value 0] := observation-code
            [:value split-value 1] := value-code))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k3)
            [:value split-value 0] := observation-code
            [:value split-value 1] := value-code)))

      (testing "`code` followed by `system|code value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k4)
            [:value split-value 0] := observation-code
            [:value split-value 1] := value-system-code))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k5)
            [:value split-value 0] := observation-code
            [:value split-value 1] := value-system-code)))

      (testing "`system|` followed by `value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k6)
            [:value split-value 0] := observation-system
            [:value split-value 1] := value))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k7)
            [:value split-value 0] := observation-system
            [:value split-value 1] := value)))

      (testing "`system|` followed by `code value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k8)
            [:value split-value 0] := observation-system
            [:value split-value 1] := value-code))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k9)
            [:value split-value 0] := observation-system
            [:value split-value 1] := value-code)))

      (testing "`system|` followed by `system|code value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k10)
            [:value split-value 0] := observation-system
            [:value split-value 1] := value-system-code))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k11)
            [:value split-value 0] := observation-system
            [:value split-value 1] := value-system-code)))

      (testing "`system|code` followed by `value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k12)
            [:value split-value 0] := observation-system-code
            [:value split-value 1] := value))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k13)
            [:value split-value 0] := observation-system-code
            [:value split-value 1] := value)))

      (testing "`system|code` followed by `code value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k14)
            [:value split-value 0] := observation-system-code
            [:value split-value 1] := value-code))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k15)
            [:value split-value 0] := observation-system-code
            [:value split-value 1] := value-code)))

      (testing "`system|code` followed by `system|code value`"
        (testing "search-param-value-key"
          (given (decode-sp-value-resource-key k16)
            [:value split-value 0] := observation-system-code
            [:value split-value 1] := value-system-code))

        (testing "resource-value-key"
          (given (decode-resource-sp-value-key k17)
            [:value split-value 0] := observation-system-code
            [:value split-value 1] := value-system-code))))))
