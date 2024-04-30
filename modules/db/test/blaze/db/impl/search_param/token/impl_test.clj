(ns blaze.db.impl.search-param.token.impl-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.search-param.system-registry :as system-registry]
   [blaze.db.impl.search-param.token.impl :as impl]
   [blaze.fhir.spec.generators :as fg]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- escape [s]
  (-> s (str/replace "\\" "\\\\") (str/replace "|" "\\|")))

(deftest compile-value-new-test
  (with-redefs
   [system-registry/id-of
    (fn [kv-store system]
      (assert (= ::kv-store kv-store))
      ({"system-162025" #blaze/byte-string"000001"
        "system-164145" #blaze/byte-string"000002"
        "|" #blaze/byte-string"000003"} system))]

    (testing "system|code"
      (testing "generated codes"
        (satisfies-prop 100
          (prop/for-all [code fg/code-value]
            (= (impl/compile-value-new ::kv-store (str "system-162025|" (escape code)))
               {:system-id #blaze/byte-string"000001"
                :value (bs/from-utf8-string code)}))))

      (testing "generated strings (for Identifier.value)"
        (satisfies-prop 100
          (prop/for-all [value fg/string-value]
            (= (impl/compile-value-new ::kv-store (str "system-162025|" (escape value)))
               {:system-id #blaze/byte-string"000001"
                :value (bs/from-utf8-string value)}))))

      (testing "special values"
        (are [value] (= (impl/compile-value-new ::kv-store (str "system-162025|" (escape value)))
                        {:system-id #blaze/byte-string"000001"
                         :value (bs/from-utf8-string value)})
          "|"
          "\\"
          "\\|"
          "\\\\|")

        (is (= (impl/compile-value-new ::kv-store (str (escape "|") "|" (escape "|")))
               {:system-id #blaze/byte-string"000003"
                :value (bs/from-utf8-string "|")}))))

    (testing "code"
      (testing "generated codes"
        (satisfies-prop 100
          (prop/for-all [code fg/code-value]
            (= (impl/compile-value-new ::kv-store (escape code))
               {:value (bs/from-utf8-string code)}))))

      (testing "generated strings (for Identifier.value)"
        (satisfies-prop 100
          (prop/for-all [value fg/string-value]
            (= (impl/compile-value-new ::kv-store (escape value))
               {:value (bs/from-utf8-string value)}))))

      (testing "special values"
        (are [value] (= (impl/compile-value-new ::kv-store (escape value))
                        {:value (bs/from-utf8-string value)})
          "|"
          "\\"
          "\\|"
          "\\\\|")))

    (testing "|code"
      (testing "generated codes"
        (satisfies-prop 100
          (prop/for-all [code fg/code-value]
            (= (impl/compile-value-new ::kv-store (str "|" (escape code)))
               {:system-id codec/null-system-id
                :value (bs/from-utf8-string code)}))))

      (testing "generated strings (for Identifier.value)"
        (satisfies-prop 100
          (prop/for-all [value fg/string-value]
            (= (impl/compile-value-new ::kv-store (str "|" (escape value)))
               {:system-id codec/null-system-id
                :value (bs/from-utf8-string value)}))))

      (testing "special values"
        (are [value] (= (impl/compile-value-new ::kv-store (str "|" (escape value)))
                        {:system-id codec/null-system-id
                         :value (bs/from-utf8-string value)})
          "|"
          "\\"
          "\\|"
          "\\\\|")))

    (testing "system|"
      (is (= (impl/compile-value-new ::kv-store "system-164145|")
             {:system-id #blaze/byte-string"000002"}))))

  (testing "exhausted system identifiers"
    (with-redefs
     [system-registry/id-of
      (fn [kv-store _system]
        (assert (= ::kv-store kv-store))
        (ba/conflict "out of range"))]

      (given (impl/compile-value-new ::kv-store "a|")
        ::anom/category := ::anom/conflict
        ::anom/message := "out of range")

      (given (impl/compile-value-new ::kv-store "a|b")
        ::anom/category := ::anom/conflict
        ::anom/message := "out of range"))))
