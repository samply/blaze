(ns blaze.db.impl.search-param.reference.impl-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.search-param.reference.impl :as impl]
   [blaze.fhir.spec.generators :as fg]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest testing]]
   [clojure.test.check.properties :as prop]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- escape [s]
  (-> s (str/replace "\\" "\\\\") (str/replace "|" "\\|")))

(deftest compile-value-new-test
  (testing "url|version"
    (satisfies-prop 100
      (prop/for-all [url fg/uri-value
                     version fg/string-value]
        (= (impl/compile-value-new {} (str (escape url) "|" (escape version)))
           {:url (bs/from-utf8-string url)
            :version (bs/from-utf8-string version)}))))

  (testing "url"
    (satisfies-prop 100
      (prop/for-all [url fg/uri-value]
        (= (impl/compile-value-new {} (str "http://" (escape url)))
           {:url (bs/from-utf8-string (str "http://" url))}))))

  (testing "type/id"
    (satisfies-prop 100
      (prop/for-all [id fg/id-value]
        (= (impl/compile-value-new {"Patient" 1} (str "Patient/" id))
           {:ref-id (bs/from-utf8-string id)
            :ref-tb 1}))))

  (testing "id"
    (satisfies-prop 100
      (prop/for-all [id fg/id-value]
        (= (impl/compile-value-new {} id)
           {:ref-id (bs/from-utf8-string id)})))))
