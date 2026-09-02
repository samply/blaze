(ns blaze.fhir.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type]
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

(deftest split-canonical-test
  (are [canonical expected] (= expected (fu/split-canonical canonical))
    "" [""]
    "|" [""]
    "url" ["url"]
    "url|" ["url"]
    "url| " ["url"]
    "url|1.2.3" ["url" "1.2.3"]
    "|1.2.3" ["" "1.2.3"]
    "url|1.2.3|extra" ["url" "1.2.3|extra"]))

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

(deftest coerce-integer-test
  (testing "valid"
    (is (= 1 (fu/coerce-integer #fhir/integer 1))))

  (testing "invalid"
    (doseq [x [#fhir/string "1" #fhir/boolean true nil]]
      (given (fu/coerce-integer x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Has to be an integer.")))

  (testing "missing value"
    (given (fu/coerce-integer #fhir/integer{:id "0"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing value.")))

(deftest coerce-boolean-test
  (testing "valid"
    (is (true? (fu/coerce-boolean #fhir/boolean true)))

    (testing "a false value isn't confused with a missing one"
      (is (false? (fu/coerce-boolean #fhir/boolean false)))))

  (testing "invalid"
    (doseq [x [#fhir/string "true" #fhir/integer 1 nil]]
      (given (fu/coerce-boolean x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Has to be a boolean.")))

  (testing "missing value"
    (given (fu/coerce-boolean #fhir/boolean{:id "0"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing value.")))

(deftest coerce-string-test
  (testing "valid"
    (is (= "1" (fu/coerce-string #fhir/string "1"))))

  (testing "invalid"
    (doseq [x [#fhir/integer 1 #fhir/uri "1" nil]]
      (given (fu/coerce-string x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Has to be a string.")))

  (testing "missing value"
    (given (fu/coerce-string #fhir/string{:id "0"})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing value.")))

(deftest coerce-uri-test
  (testing "valid"
    (testing "any FHIR type with a string-valued value is accepted, not just
              uri, for robustness reasons"
      (are [x s] (= s (fu/coerce-uri x))
        #fhir/uri "1" "1"
        #fhir/url "1" "1"
        #fhir/canonical "1" "1"
        #fhir/code "1" "1"
        #fhir/id "1" "1"
        #fhir/oid "urn:oid:1.2.3" "urn:oid:1.2.3"
        #fhir/uuid "urn:uuid:53fefa32-fcbb-4ff8-8a92-55ee120877b7" "urn:uuid:53fefa32-fcbb-4ff8-8a92-55ee120877b7"
        #fhir/markdown "1" "1"
        #fhir/string "1" "1")))

  (testing "invalid"
    (doseq [x [#fhir/integer 1 #fhir/boolean true]]
      (given (fu/coerce-uri x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Has to be a uri.")))

  (testing "missing value"
    (doseq [x [#fhir/uri{:id "0"} nil]]
      (given (fu/coerce-uri x)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing value."))))

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
                 (fu/parameters "b" #fhir/string "c")))))

  (testing "required param"
    (testing "is present"
      (given (fu/coerce-params
              {"a" {:action :copy :required true}}
              (fu/parameters "a" #fhir/string "b"))
        :a := "b"))

    (testing "is missing"
      (given (fu/coerce-params
              {"a" {:action :copy :required true}}
              (fu/parameters "b" #fhir/string "c"))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing required parameter `a`."
        :http/status := 400))))
