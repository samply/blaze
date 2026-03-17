(ns blaze.fhir.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo]
   [blaze.fhir.util :as fu]
   [blaze.fhir.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]))

(st/instrument)
(ig/init-key :blaze.fhir/structure-definition-repo {})

(test/use-fixtures :each tu/fixture)

(deftest parameters-test
  (given (fu/parameters)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (fu/parameters "foo" #fhir/string "bar")
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :value] := #fhir/string "bar")

  (given (fu/parameters "foo" nil)
    :fhir/type := :fhir/Parameters
    :parameter :? empty?)

  (given (fu/parameters "foo" {:fhir/type :fhir/ValueSet})
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :resource] := {:fhir/type :fhir/ValueSet})

  (given (fu/parameters "foo" [#fhir/string "bar"])
    :fhir/type := :fhir/Parameters
    [:parameter count] := 1
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :value] := #fhir/string "bar")

  (given (fu/parameters "foo" [#fhir/string "bar"
                               #fhir/string "buz"])
    :fhir/type := :fhir/Parameters
    [:parameter count] := 2
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :value] := #fhir/string "bar"
    [:parameter 1 :name] := #fhir/string "foo"
    [:parameter 1 :value] := #fhir/string "buz")

  (given (fu/parameters "foo" [["bar" #fhir/string "buz"]])
    :fhir/type := :fhir/Parameters
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :part count] := 1
    [:parameter 0 :part 0 :name] := #fhir/string "bar"
    [:parameter 0 :part 0 :value] := #fhir/string "buz")

  (given (fu/parameters "foo" [["param-1" #fhir/string "param-1-value"
                                "param-2" #fhir/string "param-2-value"]])
    :fhir/type := :fhir/Parameters
    [:parameter count] := 1
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :part count] := 2
    [:parameter 0 :part 0 :name] := #fhir/string "param-1"
    [:parameter 0 :part 0 :value] := #fhir/string "param-1-value"
    [:parameter 0 :part 1 :name] := #fhir/string "param-2"
    [:parameter 0 :part 1 :value] := #fhir/string "param-2-value")

  (given (fu/parameters "foo" [["param-1-1" #fhir/string "param-1-1-value"
                                "param-1-2" #fhir/string "param-1-2-value"]
                               ["param-2-1" #fhir/string "param-2-1-value"
                                "param-2-2" #fhir/string "param-2-2-value"]])
    :fhir/type := :fhir/Parameters
    [:parameter count] := 2
    [:parameter 0 :name] := #fhir/string "foo"
    [:parameter 0 :part count] := 2
    [:parameter 0 :part 0 :name] := #fhir/string "param-1-1"
    [:parameter 0 :part 0 :value] := #fhir/string "param-1-1-value"
    [:parameter 0 :part 1 :name] := #fhir/string "param-1-2"
    [:parameter 0 :part 1 :value] := #fhir/string "param-1-2-value"
    [:parameter 1 :name] := #fhir/string "foo"
    [:parameter 1 :part count] := 2
    [:parameter 1 :part 0 :name] := #fhir/string "param-2-1"
    [:parameter 1 :part 0 :value] := #fhir/string "param-2-1-value"
    [:parameter 1 :part 1 :name] := #fhir/string "param-2-2"
    [:parameter 1 :part 1 :value] := #fhir/string "param-2-2-value"))

(deftest subsetted-test
  (are [coding] (fu/subsetted? coding)
    {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
     :code #fhir/code "SUBSETTED"}
    {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"
     :code #fhir/code {:id "foo" :value "SUBSETTED"}}
    {:system #fhir/uri {:id "foo" :value "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"}
     :code #fhir/code "SUBSETTED"}
    fu/subsetted)

  (are [coding] (not (fu/subsetted? coding))
    {:code #fhir/code "SUBSETTED"}
    {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ObservationValue"}))

(deftest version-cmp-test
  (is (zero? (fu/version-cmp nil nil)))
  (is (= -1 (fu/version-cmp nil "")))
  (is (= 1 (fu/version-cmp "" nil)))
  (is (zero? (fu/version-cmp "" "")))
  (is (= -1 (fu/version-cmp "1" "2")))
  (is (zero? (fu/version-cmp "1" "1")))
  (is (= 1 (fu/version-cmp "2" "1")))
  (is (= -1 (fu/version-cmp "a" "b")))
  (is (zero? (fu/version-cmp "a" "a")))
  (is (= 1 (fu/version-cmp "b" "a")))
  (is (= -1 (fu/version-cmp "1" "a")))
  (is (= 1 (fu/version-cmp "a" "1")))
  (is (= -1 (fu/version-cmp "1.2" "1.10")))
  (is (zero? (fu/version-cmp "1.2" "1.2")))
  (is (= 1 (fu/version-cmp "1.10" "1.2")))
  (is (= -1 (fu/version-cmp "1" "1.1")))
  (is (= 1 (fu/version-cmp "1.1" "1"))))

(deftest sort-by-priority-test
  (testing "empty"
    (is (empty? (fu/sort-by-priority []))))

  (testing "one code-system"
    (is (= (fu/sort-by-priority [{:fhir/type :fhir/CodeSystem}])
           [{:fhir/type :fhir/CodeSystem}])))

  (testing "two code-systems"
    (testing "active comes first"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem
                :status #fhir/code "draft"}
               {:fhir/type :fhir/CodeSystem
                :status #fhir/code "active"}])
             [{:fhir/type :fhir/CodeSystem
               :status #fhir/code "active"}
              {:fhir/type :fhir/CodeSystem
               :status #fhir/code "draft"}])))

    (testing "without status comes last"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem}
               {:fhir/type :fhir/CodeSystem
                :status #fhir/code "draft"}])
             [{:fhir/type :fhir/CodeSystem
               :status #fhir/code "draft"}
              {:fhir/type :fhir/CodeSystem}])))

    (testing "active 1.0.0 comes before draft 2.0.0-alpha.1"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem
                :version #fhir/string "2.0.0-alpha.1"
                :status #fhir/code "draft"}
               {:fhir/type :fhir/CodeSystem
                :version #fhir/string "1.0.0"
                :status #fhir/code "active"}])
             [{:fhir/type :fhir/CodeSystem
               :version #fhir/string "1.0.0"
               :status #fhir/code "active"}
              {:fhir/type :fhir/CodeSystem
               :version #fhir/string "2.0.0-alpha.1"
               :status #fhir/code "draft"}])))

    (testing "newest comes first"
      (is (= (fu/sort-by-priority
              [(with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})
               (with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 2}})])
             [(with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 2}})
              (with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})])))

    (testing "resource without t (external resource) comes first"
      (is (= (fu/sort-by-priority
              [(with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})
               {:fhir/type :fhir/CodeSystem}])
             [{:fhir/type :fhir/CodeSystem}
              (with-meta {:fhir/type :fhir/CodeSystem} {:blaze.db/tx {:blaze.db/t 1}})])))

    (testing "largest id comes first"
      (is (= (fu/sort-by-priority
              [{:fhir/type :fhir/CodeSystem :id "1"}
               {:fhir/type :fhir/CodeSystem :id "2"}])
             [{:fhir/type :fhir/CodeSystem :id "2"}
              {:fhir/type :fhir/CodeSystem :id "1"}])))))

(deftest coerce-params-test
  (testing "simple copy"
    (given (fu/coerce-params
            {"a" {:action :copy}}
            (fu/parameters "a" #fhir/string "b"))
      :a := "b")

    (testing "camelCase name"
      (given (fu/coerce-params
              {"fooBar" {:action :copy}}
              (fu/parameters "fooBar" #fhir/string "a"))
        :foo-bar := "a")))

  (testing "multiple copy"
    (given (fu/coerce-params
            {"a" {:action :copy :cardinality :many}}
            (fu/parameters "a" #fhir/string "b" "a" #fhir/string "c"))
      :as := ["b" "c"])

    (given (fu/coerce-params
            {"property" {:action :copy :cardinality :many}}
            (fu/parameters "property" #fhir/string "b" "property" #fhir/string "c"))
      :properties := ["b" "c"])

    (given (fu/coerce-params
            {"a" {:action :copy :coerce (comp #(str/split % #",") :value) :cardinality :many}}
            (fu/parameters "a" #fhir/string "b" "a" #fhir/string "c,d"))
      :as := ["b" "c" "d"])

    (given (fu/coerce-params
            {"a" {:action :copy :coerce (comp #(str/split % #",") :value) :cardinality :many}}
            (fu/parameters "a" #fhir/string "b,c" "a" #fhir/string "d"))
      :as := ["b" "c" "d"]))

  (testing "coercion"
    (given (fu/coerce-params
            {"a" {:action :copy :coerce (comp parse-long :value)}}
            (fu/parameters "a" #fhir/string "1"))
      :a := 1)

    (testing "error"
      (given (fu/coerce-params
              {"a" {:action :copy :coerce (constantly (ba/incorrect "msg-183537"))}}
              (fu/parameters "a" #fhir/string "1"))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `a`. msg-183537")))

  (testing "complex type copy"
    (given (fu/coerce-params
            {"a" {:action :copy-complex-type}}
            (fu/parameters "a" #fhir/Coding{:system #fhir/uri"a" :code #fhir/code"b"}))
      :a := #fhir/Coding{:system #fhir/uri"a" :code #fhir/code"b"}))

  (testing "resource copy"
    (given (fu/coerce-params
            {"a" {:action :copy-resource}}
            (fu/parameters "a" {:fhir/type :fhir/Patient :id "0"}))
      :a := {:fhir/type :fhir/Patient :id "0"}))

  (testing "unsupported param"
    (given (fu/coerce-params
            {"a" {}}
            (fu/parameters "a" #fhir/string "b"))
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported parameter `a`."))

  (testing "undefined param is ignored"
    (is (empty? (fu/coerce-params
                 {"a" {}}
                 (fu/parameters "b" #fhir/string "c"))))))

(deftest validate-query-params-test
  (testing "empty parameter spec"
    (given (fu/validate-query-params
            {}
            {"param" "param-165900"})
      [:parameter count] := 0))

  (testing "param not in spec ignored"
    (given (fu/validate-query-params
            {"code" {:action :copy :coerce #(type/code %2)}}
            {"different-param" "code-165900"})
      [:parameter count] := 0))

  (testing "param action :complex not supported with GET"
    (given (fu/validate-query-params
            {"coding" {:action :complex}}
            {"coding" #fhir/Coding {:system #fhir/uri "system-115910"
                                    :version #fhir/string "version-152300"
                                    :code #fhir/code "code-115927"}})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported parameter `coding` in GET request. Please use POST."))

  (testing "param not supported"
    (given (fu/validate-query-params
            {"code" {}}
            {"code" "code-165900"})
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported parameter `code`."))

  (testing "type/code"
    (given (fu/validate-query-params
            {"code" {:action :copy :coerce #(type/code %2)}}
            {"code" "code-165900"})
      [:parameter count] := 1
      [:parameter 0 :name] := #fhir/string "code"
      [:parameter 0 :value] := #fhir/code "code-165900"))

  (testing "type/boolean"
    (doseq [value [true false]]
      (given (fu/validate-query-params
              {"param-boolean" {:action :copy :coerce fu/coerce-boolean}}
              {"param-boolean" (str value)})
        [:parameter count] := 1
        [:parameter 0 :name] := #fhir/string "param-boolean"
        [:parameter 0 :value] := (type/boolean value)))

    (doseq [value ["True" "False" "123" "1.5" "nil"]]
      (given (fu/validate-query-params
              {"param-boolean" {:action :copy :coerce fu/coerce-boolean}}
              {"param-boolean" value})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `param-boolean`. Has to be a boolean.")))

  (testing "type/integer"
    (doseq [value [123 -123]]
      (given (fu/validate-query-params
              {"param-integer" {:action :copy :coerce fu/coerce-integer}}
              {"param-integer" (str value)})
        [:parameter count] := 1
        [:parameter 0 :name] := #fhir/string "param-integer"
        [:parameter 0 :value] := (type/integer value)))

    (doseq [value ["true" "false" "1.5" "nil"]]
      (given (fu/validate-query-params
              {"param-integer" {:action :copy :coerce fu/coerce-integer}}
              {"param-integer" value})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid value for parameter `param-integer`. Has to be an integer."))))