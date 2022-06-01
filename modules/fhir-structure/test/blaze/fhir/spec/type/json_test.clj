(ns blaze.fhir.spec.type.json-test
  (:require
    [blaze.fhir.spec.type.json :as json]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop])
  (:import
    [java.nio.charset StandardCharsets]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest field-name-test
  (testing "getValue"
    (satisfies-prop 100
      (prop/for-all [value gen/string-ascii]
        (= value (.getValue (json/field-name value))))))

  (testing "charLength"
    (satisfies-prop 100
      (prop/for-all [value gen/string-ascii]
        (= (count value) (.charLength (json/field-name value))))))

  (testing "appendQuotedUTF8"
    (satisfies-prop 100
      (prop/for-all [value gen/string-ascii]
        (let [buffer (byte-array (count value))]
          (.appendQuotedUTF8 (json/field-name value) buffer 0)
          (= value (String. buffer StandardCharsets/UTF_8))))))

  (testing "asUnquotedUTF8"
    (satisfies-prop 100
      (prop/for-all [value gen/string-ascii]
        (= value (String. (.asUnquotedUTF8 (json/field-name value)) StandardCharsets/UTF_8)))))

  (testing "asQuotedUTF8"
    (satisfies-prop 100
      (prop/for-all [value gen/string-ascii]
        (= value (String. (.asQuotedUTF8 (json/field-name value)) StandardCharsets/UTF_8))))))
