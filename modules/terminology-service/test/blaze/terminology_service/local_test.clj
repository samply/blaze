(ns blaze.terminology-service.local-test
  (:require
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.node :refer [node?]]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.fhir.util :as u]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.path :refer [path]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local]
   [blaze.terminology-service.local.code-system-spec]
   [blaze.terminology-service.local.graph-spec]
   [blaze.terminology-service.local.validate-code-spec]
   [blaze.terminology-service.local.value-set-spec]
   [blaze.terminology-service.local.value-set.validate-code.issue-test :refer [tx-issue-type]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::ts/local nil})
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::ts/local {}})
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-thrown (ig/init {::ts/local {:node ::invalid}})
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 2 :pred] := `node?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid clock"
    (given-thrown (ig/init {::ts/local {:clock ::invalid}})
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :via] := [:blaze/clock]
      [:cause-data ::s/problems 2 :pred] := `time/clock?
      [:cause-data ::s/problems 2 :val] := ::invalid)))

(def config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}))

(def loinc-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :enable-loinc true}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}))

(def sct-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/incrementing-rng-fn)
    :sct-release-path (path "sct-release")}
   :blaze.test/fixed-clock {}
   :blaze.test/incrementing-rng-fn {}))

(def ucum-config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :enable-ucum true}
   :blaze.test/fixed-clock {}
   :blaze.test/fixed-rng-fn {}))

(defn- uuid-urn? [s]
  (some? (re-matches #"urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" s)))

(defn- sort-expansion [value-set]
  (update-in value-set [:expansion :contains] (partial sort-by (comp type/value :code))))

(defn- parameter [name]
  (fn [{:keys [parameter]}]
    (filterv #(= name (type/value (:name %))) parameter)))

(defn- concept [code]
  (fn [concepts]
    (filterv #(= code (type/value (:code %))) concepts)))

(deftest code-system-test
  (testing "with no code system"
    (with-system [{ts ::ts/local} config]
      (is (empty? @(ts/code-systems ts)))))

  (testing "with one code system"
    (testing "without version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-192435"
                 :content #fhir/code"complete"}]]]

        (given @(ts/code-systems ts)
          count := 1
          [0 :uri] := #fhir/canonical"system-192435"
          [0 :version count] := 1
          [0 :version 0 :code] := nil
          [0 :version 0 :isDefault] := #fhir/boolean true)))

    (testing "with version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-192435"
                 :version #fhir/string"version-121451"
                 :content #fhir/code"complete"}]]]

        (given @(ts/code-systems ts)
          [count] := 1
          [0 :uri] := #fhir/canonical"system-192435"
          [0 :version count] := 1
          [0 :version 0 :code] := #fhir/string"version-121451"
          [0 :version 0 :isDefault] := #fhir/boolean true)))

    (testing "with two versions"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-192435"
                 :version #fhir/string"1.2.0"
                 :content #fhir/code"complete"}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri"system-192435"
                 :version #fhir/string"1.10.0"
                 :content #fhir/code"complete"}]]]

        (given @(ts/code-systems ts)
          [count] := 1
          [0 :uri] := #fhir/canonical"system-192435"
          [0 :version count] := 2
          [0 :version 0 :code] := #fhir/string"1.10.0"
          [0 :version 0 :isDefault] := #fhir/boolean true
          [0 :version 1 :code] := #fhir/string"1.2.0"
          [0 :version 1 :isDefault] := #fhir/boolean false))

      (testing "but the newest as draft"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-192435"
                   :version #fhir/string"1.2.0"
                   :status #fhir/code"active"
                   :content #fhir/code"complete"}]
            [:put {:fhir/type :fhir/CodeSystem :id "1"
                   :url #fhir/uri"system-192435"
                   :version #fhir/string"1.10.0"
                   :status #fhir/code"draft"
                   :content #fhir/code"complete"}]]]

          (given @(ts/code-systems ts)
            [count] := 1
            [0 :uri] := #fhir/canonical"system-192435"
            [0 :version count] := 2
            [0 :version 0 :code] := #fhir/string"1.2.0"
            [0 :version 0 :isDefault] := #fhir/boolean true
            [0 :version 1 :code] := #fhir/string"1.10.0"
            [0 :version 1 :isDefault] := #fhir/boolean false)))))

  (testing "with two code systems"
    (testing "without version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-192435"
                 :content #fhir/code"complete"}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri"system-174248"
                 :content #fhir/code"complete"}]]]

        (given @(ts/code-systems ts)
          count := 2
          [0 :uri] := #fhir/canonical"system-192435"
          [0 :version count] := 1
          [0 :version 0 :code] := nil
          [0 :version 0 :isDefault] := #fhir/boolean true
          [1 :uri] := #fhir/canonical"system-174248"
          [1 :version count] := 1
          [1 :version 0 :code] := nil
          [1 :version 0 :isDefault] := #fhir/boolean true)))))

(defn- code-system-validate-code [ts & nvs]
  (ts/code-system-validate-code ts (apply u/parameters nvs)))

(deftest code-system-validate-code-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "no parameters"
      (given-failed-future (code-system-validate-code ts)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing one of the parameters `code`, `coding` or `codeableConcept`."))

    (testing "not found"
      (testing "url"
        (given-failed-future (code-system-validate-code ts
                               "url" #fhir/uri"url-194718"
                               "code" #fhir/code"code-083955")
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (code-system-validate-code ts
                               "url" #fhir/uri"url-144258"
                               "code" #fhir/code"code-083955"
                               "version" #fhir/string"version-144244")
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system `url-144258|version-144244` was not found."
          :t := 0)))

    (testing "with non-complete code system"
      (doseq [content ["not-present" "example" "fragment" "supplement"]]
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content (type/code content)}]]]

          (given-failed-future (code-system-validate-code ts
                                 "url" #fhir/uri"system-115910"
                                 "code" #fhir/code"code-115927")
            ::anom/category := ::anom/conflict
            ::anom/message := (format "Can't use the code system `system-115910` because it is not complete. It's content is `%s`." content)))))))

(deftest code-system-validate-code-test
  (testing "with url"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"
                 :display #fhir/string"display-112832"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-154735"
                 :display #fhir/string"display-154737"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"status"
                   :value #fhir/code"retired"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-155156"
                 :display #fhir/string"display-155159"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"inactive"
                   :value #fhir/boolean true}]}]}]]]

      (testing "existing code"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"system-115910"
                  "code" #fhir/code"code-115927")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"code-115927"
          [(parameter "system") 0 :value] := #fhir/uri"system-115910"
          [(parameter "display") 0 :value] := #fhir/string"display-112832")

        (testing "inactive"
          (testing "via status property"
            (given @(code-system-validate-code ts
                      "url" #fhir/uri"system-115910"
                      "code" #fhir/code"code-154735")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code"code-154735"
              [(parameter "system") 0 :value] := #fhir/uri"system-115910"
              [(parameter "display") 0 :value] := #fhir/string"display-154737"
              [(parameter "inactive") 0 :value] := #fhir/boolean true))

          (testing "via inactive property"
            (given @(code-system-validate-code ts
                      "url" #fhir/uri"system-115910"
                      "code" #fhir/code"code-155156")
              :fhir/type := :fhir/Parameters
              [(parameter "result") 0 :value] := #fhir/boolean true
              [(parameter "code") 0 :value] := #fhir/code"code-155156"
              [(parameter "system") 0 :value] := #fhir/uri"system-115910"
              [(parameter "display") 0 :value] := #fhir/string"display-155159"
              [(parameter "inactive") 0 :value] := #fhir/boolean true))))

      (testing "non-existing code"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"system-115910"
                  "code" #fhir/code"code-153948")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string"Unknown code `code-153948` was not found in the code system `system-115910`."))

      (testing "existing coding"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"system-115910"
                  "coding" #fhir/Coding{:system #fhir/uri"system-115910"
                                        :code #fhir/code"code-115927"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"code-115927"
          [(parameter "system") 0 :value] := #fhir/uri"system-115910"))

      (testing "non-existing coding"
        (testing "with non-existing system"
          (given-failed-future (code-system-validate-code ts
                                 "url" #fhir/uri"system-115910"
                                 "coding" #fhir/Coding{:system #fhir/uri"system-170454"
                                                       :code #fhir/code"code-115927"})
            ::anom/category := ::anom/incorrect
            ::anom/message := "Parameter `url` differs from parameter `coding.system`."))

        (testing "with non-existing code"
          (given @(code-system-validate-code ts
                    "url" #fhir/uri"system-115910"
                    "coding" #fhir/Coding{:system #fhir/uri"system-115910"
                                          :code #fhir/code"code-153948"})
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string"Unknown code `code-153948` was not found in the code system `system-115910`.")))))

  (testing "with code-system"
    (with-system [{ts ::ts/local} config]
      (testing "existing code"
        (given @(code-system-validate-code ts
                  "codeSystem"
                  {:fhir/type :fhir/CodeSystem
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}]}
                  "code" #fhir/code"code-115927")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"code-115927")

        (testing "with url"
          (given @(code-system-validate-code ts
                    "codeSystem"
                    {:fhir/type :fhir/CodeSystem
                     :url #fhir/uri"system-115910"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"}]}
                    "code" #fhir/code"code-115927")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean true
            [(parameter "code") 0 :value] := #fhir/code"code-115927"
            [(parameter "system") 0 :value] := #fhir/uri"system-115910")))

      (testing "non-existing code"
        (given @(code-system-validate-code ts
                  "codeSystem"
                  {:fhir/type :fhir/CodeSystem
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}]}
                  "code" #fhir/code"code-153948")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string"Unknown code `code-153948` was not found in the provided code system.")

        (testing "with url"
          (given @(code-system-validate-code ts
                    "codeSystem"
                    {:fhir/type :fhir/CodeSystem
                     :url #fhir/uri"system-115910"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"}]}
                    "code" #fhir/code"code-153948")
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string"Unknown code `code-153948` was not found in the code system `system-115910`.")))

      (testing "existing coding"
        (given @(code-system-validate-code ts
                  "codeSystem"
                  {:fhir/type :fhir/CodeSystem
                   :url #fhir/uri"system-172718"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-172653"}]}
                  "coding" #fhir/Coding{:system #fhir/uri"system-172718"
                                        :code #fhir/code"code-172653"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"code-172653"
          [(parameter "system") 0 :value] := #fhir/uri"system-172718"))))

  (testing "with coding only"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]]]

      (given @(code-system-validate-code ts
                "coding" #fhir/Coding{:system #fhir/uri"system-115910"
                                      :code #fhir/code"code-115927"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code-115927"
        [(parameter "system") 0 :value] := #fhir/uri"system-115910"))

    (testing "with version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri"system-115910"
                 :version #fhir/string"version-124939"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-124951"}]}]]]

        (given @(code-system-validate-code ts
                  "coding" #fhir/Coding{:system #fhir/uri"system-115910"
                                        :version #fhir/string"version-124939"
                                        :code #fhir/code"code-124951"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"code-124951"
          [(parameter "system") 0 :value] := #fhir/uri"system-115910"
          [(parameter "version") 0 :value] := #fhir/string"version-124939")))))

(deftest code-system-validate-code-loinc-test
  (with-system [{ts ::ts/local} loinc-config]
    (testing "existing code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://loinc.org"
                "code" #fhir/code"718-7")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"718-7"
        [(parameter "system") 0 :value] := #fhir/uri"http://loinc.org"
        [(parameter "version") 0 :value] := #fhir/string"2.78"
        [(parameter "display") 0 :value] := #fhir/string"Hemoglobin [Mass/volume] in Blood")

      (testing "wrong display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://loinc.org"
                  "code" #fhir/code"718-7"
                  "display" #fhir/string"wrong")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string"Invalid display `wrong` for code `http://loinc.org#718-7`. A valid display is `Hemoglobin [Mass/volume] in Blood`."
          [(parameter "code") 0 :value] := #fhir/code"718-7"
          [(parameter "system") 0 :value] := #fhir/uri"http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string"2.78"
          [(parameter "display") 0 :value] := #fhir/string"Hemoglobin [Mass/volume] in Blood"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `wrong` for code `http://loinc.org#718-7`. A valid display is `Hemoglobin [Mass/volume] in Blood`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"display"]))

      (testing "inactive"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://loinc.org"
                  "code" #fhir/code"1009-0")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"1009-0"
          [(parameter "system") 0 :value] := #fhir/uri"http://loinc.org"
          [(parameter "version") 0 :value] := #fhir/string"2.78"
          [(parameter "display") 0 :value] := #fhir/string"Deprecated Direct antiglobulin test.poly specific reagent [Presence] on Red Blood Cells"
          [(parameter "inactive") 0 :value] := #fhir/boolean true)))

    (testing "non-existing code"
      (doseq [code ["non-existing" "0815" "718-8"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://loinc.org"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unknown code `%s` was not found in the code system `http://loinc.org|2.78`." code)))))))

(deftest code-system-validate-code-sct-test
  (with-system [{ts ::ts/local} sct-config]
    (testing "existing code"
      (doseq [version [nil
                       "http://snomed.info/sct/900000000000207008"
                       "http://snomed.info/sct/900000000000207008/version/20241001"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://snomed.info/sct"
                  "code" #fhir/code"441510007"
                  "version" (some-> version type/string))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

      (testing "synonym display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://snomed.info/sct"
                  "code" #fhir/code"441510007"
                  "display" #fhir/string"Blood specimen with anticoagulant")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

      (testing "wrong display"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://snomed.info/sct"
                  "code" #fhir/code"441510007"
                  "display" #fhir/string"wrong")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string"Invalid display `wrong` for code `http://snomed.info/sct#441510007`. A valid display is `Blood specimen with anticoagulant (specimen)`."
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `wrong` for code `http://snomed.info/sct#441510007`. A valid display is `Blood specimen with anticoagulant (specimen)`."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"display"]))

      (testing "inactive"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://snomed.info/sct"
                  "code" #fhir/code"860958002")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"860958002"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
          [(parameter "display") 0 :value] := #fhir/string"Temperature of blood (observable entity)"
          [(parameter "inactive") 0 :value] := #fhir/boolean true)))

    (testing "non-existing code"
      (doseq [code ["non-existing" "0815" "441510008"]]
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://snomed.info/sct"
                  "code" (type/code code))
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := (type/string (format "Unknown code `%s` was not found in the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20241001`." code)))))

    (testing "existing coding"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://snomed.info/sct"
                "coding" #fhir/Coding{:system #fhir/uri"http://snomed.info/sct" :code #fhir/code"441510007"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

    (testing "non-existing coding"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://snomed.info/sct"
                "coding" #fhir/Coding{:system #fhir/uri"http://snomed.info/sct" :code #fhir/code"non-existing"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Unknown code `non-existing` was not found in the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20241001`."))))

(deftest code-system-validate-code-ucum-test
  (with-system [{ts ::ts/local} ucum-config]
    (testing "existing code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://unitsofmeasure.org"
                "code" #fhir/code"s")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"s"
        [(parameter "system") 0 :value] := #fhir/uri"http://unitsofmeasure.org"
        [(parameter "version") 0 :value] := #fhir/string"2013.10.21"))

    (testing "non-existing code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://unitsofmeasure.org"
                "code" #fhir/code"non-existing")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Unknown code `non-existing` was not found in the code system `http://unitsofmeasure.org|2013.10.21`."))

    (testing "existing coding"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://unitsofmeasure.org"
                "coding" #fhir/Coding{:system #fhir/uri"http://unitsofmeasure.org" :code #fhir/code"km"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"km"
        [(parameter "system") 0 :value] := #fhir/uri"http://unitsofmeasure.org"
        [(parameter "version") 0 :value] := #fhir/string"2013.10.21"))

    (testing "non-existing coding"
      (testing "with non-existing code"
        (given @(code-system-validate-code ts
                  "url" #fhir/uri"http://unitsofmeasure.org"
                  "coding" #fhir/Coding{:system #fhir/uri"http://unitsofmeasure.org" :code #fhir/code"non-existing"})
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string"Unknown code `non-existing` was not found in the code system `http://unitsofmeasure.org|2013.10.21`.")))))

(defn- expand-value-set [ts & nvs]
  (ts/expand-value-set ts (apply u/parameters nvs)))

(deftest expand-value-set-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "no parameters"
      (given-failed-future (expand-value-set ts)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing both parameters `url` and `valueSet`."))

    (testing "invalid non-zero parameter offset"
      (given-failed-future (expand-value-set ts
                             "offset" #fhir/integer 1)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid non-zero value for parameter `offset`."))

    (testing "not found"
      (testing "url"
        (given-failed-future (expand-value-set ts
                               "url" #fhir/uri"url-194718")
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (expand-value-set ts
                               "url" #fhir/uri"url-144258"
                               "valueSetVersion" #fhir/string"version-144244")
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-144258|version-144244` was not found."
          :t := 0))))

  (testing "empty include"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-135750")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-135750`. Missing system or valueSet."
        :t := 1)))

  (testing "code system not found"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-135750")
        ::anom/category := ::anom/not-found
        ::anom/message := "Error while expanding the value set `value-set-135750`. The code system `system-115910` was not found."
        :t := 1))

    (testing "with version"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"
                    :version "version-093818"}]}}]]]

        (given-failed-future (expand-value-set ts
                               "url" #fhir/uri"value-set-135750")
          ::anom/category := ::anom/not-found
          ::anom/message := "Error while expanding the value set `value-set-135750`. The code system `system-115910|version-093818` was not found."
          :t := 1))

      (testing "special * value is unsupported"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :version "*"}]}}]]]

          (given-failed-future (expand-value-set ts
                                 "url" #fhir/uri"value-set-135750")
            ::anom/category := ::anom/unsupported
            ::anom/message := "Error while expanding the value set `value-set-135750`. Expanding the code system `system-115910` in all versions is unsupported."
            :t := 1)))))

  (testing "fails with value set ref and system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"
                  :valueSet [#fhir/canonical"value-set-135750"]}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-161213")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-161213`. Incorrect combination of system and valueSet."
        :t := 1)))

  (testing "fails with concept and filter"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"code-163444"}]
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-160019"
                    :op #fhir/code"op-unknown-160011"
                    :value #fhir/string"value-160032"}]}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-161213")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-161213`. Incorrect combination of concept and filter."
        :t := 1)))

  (testing "fails on non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :content #fhir/code"example"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-170447"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"code-163824"}]}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri"value-set-170829"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-160019"
                    :op #fhir/code"op-unknown-160011"
                    :value #fhir/string"value-160032"}]}]}}]]]

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-161213")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-161213`. Can't use the code system `system-180814` because it is not complete. It's content is `example`."
        :t := 1)

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-170447")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-170447`. Can't use the code system `system-180814` because it is not complete. It's content is `example`."
        :t := 1)

      (given-failed-future (expand-value-set ts
                             "url" #fhir/uri"value-set-170829")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `value-set-170829`. Can't use the code system `system-180814` because it is not complete. It's content is `example`."
        :t := 1))))

(deftest expand-value-set-existing-expansion-test
  (testing "retains already existing expansion"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "id-144002"
               :url #fhir/uri"value-set-135750"
               :version #fhir/string"version-143955"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}
               :expansion
               {:fhir/type :fhir.ValueSet/expansion
                :identifier #fhir/uri"urn:uuid:b01db38a-3ec8-4167-a279-0bb1200624a8"
                :timestamp #fhir/dateTime"1970-01-01T00:00:00Z"
                :contains
                [{:fhir/type :fhir.ValueSet.expansion/contains
                  :system #fhir/uri"system-115910"
                  :code #fhir/code"code-115927"
                  :display #fhir/string"display-115927"}]}}]]]

      (doseq [params [["url" #fhir/uri"value-set-135750"]
                      ["url" #fhir/uri"value-set-135750"
                       "valueSetVersion" #fhir/string"version-143955"]]]
        (given @(apply expand-value-set ts params)
          :fhir/type := :fhir/ValueSet
          [:expansion :identifier] := #fhir/uri"urn:uuid:b01db38a-3ec8-4167-a279-0bb1200624a8"
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 :display] := #fhir/string"display-115927")))))

(deftest expand-value-set-include-concept-test
  (testing "with one code system"
    (testing "with one code"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :parameter count] := 1
          [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri"system-115910"
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false))

      (testing "including designations"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"
                     :designation
                     [{:fhir/type :fhir.CodeSystem.concept/designation
                       :value #fhir/string"designation-011441"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"}]}}]]]

          (given @(expand-value-set ts
                    "url" #fhir/uri"value-set-135750"
                    "includeDesignations" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion (parameter "includeDesignations") 0 :value] := #fhir/boolean true
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
            [:expansion :contains 0 :designation 0 :value] := #fhir/string"designation-011441")))

      (testing "including properties"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code"status"
                       :value #fhir/code"active"}
                      {:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code"property-034158"
                       :value #fhir/code"value-034206"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"}]}}]]]

          (given @(expand-value-set ts
                    "url" #fhir/uri"value-set-135750"
                    "property" "status"
                    "property" "property-034158")
            :fhir/type := :fhir/ValueSet
            [:expansion :property count] := 2
            [:expansion :property 0 :code] := #fhir/code"status"
            [:expansion :property 0 :uri] := #fhir/uri"http://hl7.org/fhir/concept-properties#status"
            [:expansion :property 1 :code] := #fhir/code"property-034158"
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
            [:expansion :contains 0 :property count] := 2
            [:expansion :contains 0 :property 0 :code] := #fhir/code"status"
            [:expansion :contains 0 :property 0 :value] := #fhir/code"active"
            [:expansion :contains 0 :property 1 :code] := #fhir/code"property-034158"
            [:expansion :contains 0 :property 1 :value] := #fhir/code"value-034206"))

        (testing "special definition property (FHIR-43519)"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri"system-115910"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"
                       :definition #fhir/string"definition-143747"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri"value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"system-115910"}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri"value-set-135750"
                      "property" "definition")
              :fhir/type := :fhir/ValueSet
              [:expansion :property count] := 1
              [:expansion :property 0 :code] := #fhir/code"definition"
              [:expansion :property 0 :uri] := #fhir/uri"http://hl7.org/fhir/concept-properties#definition"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-115910"
              [:expansion :contains 0 :code] := #fhir/code"code-115927"
              [:expansion :contains 0 :property count] := 1
              [:expansion :contains 0 :property 0 :code] := #fhir/code"definition"
              [:expansion :contains 0 :property 0 :value] := #fhir/string"definition-143747"))))

      (testing "with versions"
        (testing "choosing an explicit version"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri"system-115910"
                     :version "1.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri"system-115910"
                     :version "2.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri"system-115910"
                     :version "3.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri"value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"system-115910"
                        :version "2.0.0"}]}}]]]

            (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "version") 0 :value] := #fhir/uri"system-115910|2.0.0"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-115910"
              [:expansion :contains 0 :code] := #fhir/code"code-092722"
              [:expansion :contains 0 #(contains? % :display)] := false)))

        (testing "choosing the newest version by default"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri"system-115910"
                     :version "1.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri"system-115910"
                     :version "2.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri"system-115910"
                     :version "3.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri"value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"system-115910"}]}}]]]

            (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "version") 0 :value] := #fhir/uri"system-115910|3.0.0"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-115910"
              [:expansion :contains 0 :code] := #fhir/code"code-115357"
              [:expansion :contains 0 #(contains? % :display)] := false)))

        (testing "choosing the version by parameter"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri"system-115910"
                     :version "1.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "1"
                     :url #fhir/uri"system-115910"
                     :version "2.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-092722"}]}]
              [:put {:fhir/type :fhir/CodeSystem :id "2"
                     :url #fhir/uri"system-115910"
                     :version "3.0.0"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115357"}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri"value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"system-115910"}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri"value-set-135750"
                      "system-version" #fhir/canonical"system-115910|2.0.0")
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "version") 0 :value] := #fhir/uri"system-115910|2.0.0"
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-115910"
              [:expansion :contains 0 :code] := #fhir/code"code-092722"
              [:expansion :contains 0 #(contains? % :display)] := false)))))

    (testing "with two codes"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-163444"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri"system-115910"
          [:expansion :contains 1 :code] := #fhir/code"code-163444"
          [:expansion :contains 1 #(contains? % :display)] := false))

      (testing "include only one code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-163444"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-163444"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-163444"
            [:expansion :contains 0 #(contains? % :display)] := false))

        (testing "including designations"
          (with-system-data [{ts ::ts/local} config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                     :url #fhir/uri"system-115910"
                     :content #fhir/code"complete"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-115927"}
                      {:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-163444"
                       :designation
                       [{:fhir/type :fhir.CodeSystem.concept/designation
                         :value #fhir/string"designation-011441"}]}]}]
              [:put {:fhir/type :fhir/ValueSet :id "0"
                     :url #fhir/uri"value-set-135750"
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"system-115910"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"code-163444"}]}]}}]]]

            (given @(expand-value-set ts
                      "url" #fhir/uri"value-set-135750"
                      "includeDesignations" #fhir/boolean true)
              :fhir/type := :fhir/ValueSet
              [:expansion (parameter "includeDesignations") 0 :value] := #fhir/boolean true
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-115910"
              [:expansion :contains 0 :code] := #fhir/code"code-163444"
              [:expansion :contains 0 :designation 0 :value] := #fhir/string"designation-011441"))))

      (testing "exclude one code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-163444"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"}]
                    :exclude
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-163444"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false))))

    (testing "with two hierarchical codes"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-163444"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri"system-115910"
          [:expansion :contains 1 :code] := #fhir/code"code-163444"
          [:expansion :contains 1 #(contains? % :display)] := false))

      (testing "include only the parent code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-163444"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-115927"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false)))

      (testing "include only the child code"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"
                     :concept
                     [{:fhir/type :fhir.CodeSystem/concept
                       :code #fhir/code"code-163444"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-163444"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-163444"
            [:expansion :contains 0 #(contains? % :display)] := false)))))

  (testing "with two code systems"
    (testing "with one code each"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri"system-180814"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-180828"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}
                   {:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-180814"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri"system-180814"
          [:expansion (parameter "used-codesystem") 1 :value] := #fhir/uri"system-115910"
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri"system-180814"
          [:expansion :contains 1 :code] := #fhir/code"code-180828"
          [:expansion :contains 1 #(contains? % :display)] := false)))

    (testing "with two codes each"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-163824"}]}]
          [:put {:fhir/type :fhir/CodeSystem :id "1"
                 :url #fhir/uri"system-180814"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-180828"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-163852"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}
                   {:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-180814"}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri"system-180814"
          [:expansion (parameter "used-codesystem") 1 :value] := #fhir/uri"system-115910"
          [:expansion :contains count] := 4
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 #(contains? % :display)] := false
          [:expansion :contains 1 :system] := #fhir/uri"system-115910"
          [:expansion :contains 1 :code] := #fhir/code"code-163824"
          [:expansion :contains 1 #(contains? % :display)] := false
          [:expansion :contains 2 :system] := #fhir/uri"system-180814"
          [:expansion :contains 2 :code] := #fhir/code"code-180828"
          [:expansion :contains 2 #(contains? % :display)] := false
          [:expansion :contains 3 :system] := #fhir/uri"system-180814"
          [:expansion :contains 3 :code] := #fhir/code"code-163852"
          [:expansion :contains 3 #(contains? % :display)] := false))

      (testing "excluding the second code from the first code system"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-163824"}]}]
            [:put {:fhir/type :fhir/CodeSystem :id "1"
                   :url #fhir/uri"system-180814"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-180828"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-163852"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-135750"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"}
                     {:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-180814"}]
                    :exclude
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-163824"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
            :fhir/type := :fhir/ValueSet
            [:expansion (parameter "used-codesystem") 0 :value] := #fhir/uri"system-180814"
            [:expansion (parameter "used-codesystem") 1 :value] := #fhir/uri"system-115910"
            [:expansion :contains count] := 3
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false
            [:expansion :contains 1 :system] := #fhir/uri"system-180814"
            [:expansion :contains 1 :code] := #fhir/code"code-180828"
            [:expansion :contains 1 #(contains? % :display)] := false
            [:expansion :contains 2 :system] := #fhir/uri"system-180814"
            [:expansion :contains 2 :code] := #fhir/code"code-163852"
            [:expansion :contains 2 #(contains? % :display)] := false)))))

  (testing "with two value sets and only one matching the given version"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri"system-135810"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-135827"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-154043"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-154043"
               :version #fhir/string"version-135747"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-135810"}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri"value-set-154043"
                "valueSetVersion" #fhir/string"version-135747")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-135810"
        [:expansion :contains 0 :code] := #fhir/code"code-135827")))

  (testing "with inactive concepts"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-170702"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-170118"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"foo"
                   :value #fhir/boolean false}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"inactive"
                   :value #fhir/boolean true}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-164637"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"status"
                   :value #fhir/code"retired"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-163445"
                 :display #fhir/string"display-164521"}]}]]]

      (given @(expand-value-set ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-170702"}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri"system-170702"
        [:expansion :contains 0 :inactive] := #fhir/boolean true
        [:expansion :contains 0 :code] := #fhir/code"code-170118"
        [:expansion :contains 1 :system] := #fhir/uri"system-170702"
        [:expansion :contains 1 :inactive] := #fhir/boolean true
        [:expansion :contains 1 :code] := #fhir/code"code-164637"
        [:expansion :contains 2 :system] := #fhir/uri"system-170702"
        [:expansion :contains 2 :code] := #fhir/code"code-163445"
        [:expansion :contains 2 :display] := #fhir/string"display-164521")

      (testing "including only active"
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :inactive #fhir/boolean false
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-170702"}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-170702"
          [:expansion :contains 0 :code] := #fhir/code"code-163445"))

      (testing "including all codes"
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-170702"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-170118"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-164637"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"code-163445"
                        :display #fhir/string"display-165751"}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains 0 :system] := #fhir/uri"system-170702"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"code-170118"
          [:expansion :contains 1 :system] := #fhir/uri"system-170702"
          [:expansion :contains 1 :inactive] := #fhir/boolean true
          [:expansion :contains 1 :code] := #fhir/code"code-164637"
          [:expansion :contains 2 :system] := #fhir/uri"system-170702"
          [:expansion :contains 2 :code] := #fhir/code"code-163445"
          [:expansion :contains 2 :display] := #fhir/string"display-165751")

        (testing "including only active"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"system-170702"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"code-170118"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"code-164637"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"code-163445"
                          :display #fhir/string"display-165751"}]}]}})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-170702"
            [:expansion :contains 0 :code] := #fhir/code"code-163445")))))

  (testing "with not-selectable concepts"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-170702"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-170118"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"foo"
                   :value #fhir/boolean false}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"notSelectable"
                   :value #fhir/boolean true}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-164637"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-163445"
                 :display #fhir/string"display-164521"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-145941"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-170702"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-145941")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri"system-170702"
        [:expansion :contains 0 :abstract] := #fhir/boolean true
        [:expansion :contains 0 :code] := #fhir/code"code-170118"
        [:expansion :contains 1 :system] := #fhir/uri"system-170702"
        [:expansion :contains 1 :code] := #fhir/code"code-164637"
        [:expansion :contains 2 :system] := #fhir/uri"system-170702"
        [:expansion :contains 2 :code] := #fhir/code"code-163445"
        [:expansion :contains 2 :display] := #fhir/string"display-164521")))

  (testing "with externally supplied value set and code system"
    (with-system [{ts ::ts/local} config]
      (given @(expand-value-set ts
                "url" #fhir/uri"value-set-110445"
                "tx-resource"
                {:fhir/type :fhir/CodeSystem
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}]}
                "tx-resource"
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri"value-set-110445"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-115910"
        [:expansion :contains 0 :code] := #fhir/code"code-115927"))

    (testing "with value set version"
      (with-system [{ts ::ts/local} config]
        (given @(expand-value-set ts
                  "url" #fhir/uri"value-set-110445"
                  "valueSetVersion" #fhir/string"version-134920"
                  "tx-resource"
                  {:fhir/type :fhir/CodeSystem
                   :url #fhir/uri"system-115910"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}]}
                  "tx-resource"
                  {:fhir/type :fhir/ValueSet
                   :url #fhir/uri"value-set-110445"
                   :version #fhir/string"version-134920"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927")))

    (testing "with code system version"
      (with-system [{ts ::ts/local} config]
        (given @(expand-value-set ts
                  "url" #fhir/uri"value-set-110445"
                  "tx-resource"
                  {:fhir/type :fhir/CodeSystem
                   :url #fhir/uri"system-115910"
                   :version #fhir/string"version-135221"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-115927"}]}
                  "tx-resource"
                  {:fhir/type :fhir/ValueSet
                   :url #fhir/uri"value-set-110445"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-115910"
                      :version #fhir/string"version-135221"}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927")))))

(deftest expand-value-set-value-include-set-refs-test
  (testing "one value set ref"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet [#fhir/canonical"value-set-135750"]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-161213")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-180814"
        [:expansion :contains 0 :code] := #fhir/code"code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false))

    (testing "retains already existing expansion"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-180814"}]}
                 :expansion
                 {:fhir/type :fhir.ValueSet/expansion
                  :identifier #fhir/uri"urn:uuid:78653bd7-1b0a-4c9c-afa7-0d5ccf8c6a71"
                  :timestamp #fhir/dateTime"1970-01-01T00:00:00Z"
                  :contains
                  [{:fhir/type :fhir.ValueSet.expansion/contains
                    :system #fhir/uri"system-180814"
                    :code #fhir/code"code-180828"
                    :display #fhir/string"display-191917"}]}}]
          [:put {:fhir/type :fhir/ValueSet :id "1"
                 :url #fhir/uri"value-set-161213"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :valueSet [#fhir/canonical"value-set-135750"]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-161213")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-180814"
          [:expansion :contains 0 :code] := #fhir/code"code-180828"
          [:expansion :contains 0 :display] := #fhir/string"display-191917"))))

  (testing "two value set refs"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri"system-162531"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-162551"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-162451"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-162531"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri"value-set-162456"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet
                  [#fhir/canonical"value-set-135750"
                   #fhir/canonical"value-set-162451"]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-162456")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri"system-180814"
        [:expansion :contains 0 :code] := #fhir/code"code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false
        [:expansion :contains 1 :system] := #fhir/uri"system-162531"
        [:expansion :contains 1 :code] := #fhir/code"code-162551"
        [:expansion :contains 1 #(contains? % :display)] := false)))

  (testing "two value set refs including the same code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-162451"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "2"
               :url #fhir/uri"value-set-162456"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet
                  [#fhir/canonical"value-set-135750"
                   #fhir/canonical"value-set-162451"]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-162456")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-180814"
        [:expansion :contains 0 :code] := #fhir/code"code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false)))

  (testing "with externally supplied value set and code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :valueSet [#fhir/canonical"value-set-135750"]}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri"value-set-161213"
                "tx-resource"
                {:fhir/type :fhir/ValueSet
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-180814"}]}})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-180814"
        [:expansion :contains 0 :code] := #fhir/code"code-180828"
        [:expansion :contains 0 #(contains? % :display)] := false))))

(deftest expand-value-set-include-filter-test
  (testing "unknown filter operator"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-160118"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-160019"
                    :op #fhir/code"op-unknown-160011"
                    :value #fhir/string"value-160032"}]}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri"value-set-160118")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-160118`. Unsupported filter operator `op-unknown-160011` in code system `system-182822`."))))

(deftest expand-value-set-include-filter-is-a-test
  (testing "with a single concept"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"is-a"
                    :value #fhir/string"code-182832"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-182832"
        [:expansion :contains 0 :display] := #fhir/string"display-182717"))

    (testing "including designations"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-182822"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-182832"
                   :display #fhir/string"display-182717"
                   :designation
                   [{:fhir/type :fhir.CodeSystem.concept/designation
                     :value #fhir/string"designation-011441"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-182905"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-182822"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code"concept"
                      :op #fhir/code"is-a"
                      :value #fhir/string"code-182832"}]}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri"value-set-182905"
                  "includeDesignations" #fhir/boolean true)
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "includeDesignations") 0 :value] := #fhir/boolean true
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-182832"
          [:expansion :contains 0 :display] := #fhir/string"display-182717"
          [:expansion :contains 0 :designation 0 :value] := #fhir/string"designation-011441"))))

  (testing "with two concepts, a parent and a child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-191445"
                 :display #fhir/string"display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"parent"
                   :value #fhir/code"code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"is-a"
                    :value #fhir/string"code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri"value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-182832"
        [:expansion :contains 0 :display] := #fhir/string"display-182717"
        [:expansion :contains 1 :system] := #fhir/uri"system-182822"
        [:expansion :contains 1 :code] := #fhir/code"code-191445"
        [:expansion :contains 1 :display] := #fhir/string"display-191448"))

    (testing "with inactive child"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-182822"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-182832"
                   :display #fhir/string"display-182717"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-191445"
                   :display #fhir/string"display-191448"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code"parent"
                     :value #fhir/code"code-182832"}
                    {:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code"inactive"
                     :value #fhir/boolean true}]}]}]]]

        (given (sort-expansion
                @(expand-value-set ts
                   "valueSet"
                   {:fhir/type :fhir/ValueSet
                    :compose
                    {:fhir/type :fhir.ValueSet/compose
                     :include
                     [{:fhir/type :fhir.ValueSet.compose/include
                       :system #fhir/uri"system-182822"
                       :filter
                       [{:fhir/type :fhir.ValueSet.compose.include/filter
                         :property #fhir/code"concept"
                         :op #fhir/code"is-a"
                         :value #fhir/string"code-182832"}]}]}}))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-182832"
          [:expansion :contains 0 :display] := #fhir/string"display-182717"
          [:expansion :contains 1 :system] := #fhir/uri"system-182822"
          [:expansion :contains 1 :inactive] := #fhir/boolean true
          [:expansion :contains 1 :code] := #fhir/code"code-191445"
          [:expansion :contains 1 :display] := #fhir/string"display-191448")

        (testing "including only active"
          (given (sort-expansion
                  @(expand-value-set ts
                     "valueSet"
                     {:fhir/type :fhir/ValueSet
                      :compose
                      {:fhir/type :fhir.ValueSet/compose
                       :inactive #fhir/boolean false
                       :include
                       [{:fhir/type :fhir.ValueSet.compose/include
                         :system #fhir/uri"system-182822"
                         :filter
                         [{:fhir/type :fhir.ValueSet.compose.include/filter
                           :property #fhir/code"concept"
                           :op #fhir/code"is-a"
                           :value #fhir/string"code-182832"}]}]}}))
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-182822"
            [:expansion :contains 0 :code] := #fhir/code"code-182832"
            [:expansion :contains 0 :display] := #fhir/string"display-182717")))))

  (testing "with three concepts, a parent, a child and a child of the child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-191445"
                 :display #fhir/string"display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"parent"
                   :value #fhir/code"code-182832"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-192308"
                 :display #fhir/string"display-192313"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"parent"
                   :value #fhir/code"code-191445"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"is-a"
                    :value #fhir/string"code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri"value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-182832"
        [:expansion :contains 0 :display] := #fhir/string"display-182717"
        [:expansion :contains 1 :system] := #fhir/uri"system-182822"
        [:expansion :contains 1 :code] := #fhir/code"code-191445"
        [:expansion :contains 1 :display] := #fhir/string"display-191448"
        [:expansion :contains 2 :system] := #fhir/uri"system-182822"
        [:expansion :contains 2 :code] := #fhir/code"code-192308"
        [:expansion :contains 2 :display] := #fhir/string"display-192313"))

    (testing "works if child of child comes before child"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-182822"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-182832"
                   :display #fhir/string"display-182717"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-192308"
                   :display #fhir/string"display-192313"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code"parent"
                     :value #fhir/code"code-191445"}]}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-191445"
                   :display #fhir/string"display-191448"
                   :property
                   [{:fhir/type :fhir.CodeSystem.concept/property
                     :code #fhir/code"parent"
                     :value #fhir/code"code-182832"}]}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-182905"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-182822"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code"concept"
                      :op #fhir/code"is-a"
                      :value #fhir/string"code-182832"}]}]}}]]]

        (given (sort-expansion @(expand-value-set ts "url" #fhir/uri"value-set-182905"))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-182832"
          [:expansion :contains 0 :display] := #fhir/string"display-182717"
          [:expansion :contains 1 :system] := #fhir/uri"system-182822"
          [:expansion :contains 1 :code] := #fhir/code"code-191445"
          [:expansion :contains 1 :display] := #fhir/string"display-191448"
          [:expansion :contains 2 :system] := #fhir/uri"system-182822"
          [:expansion :contains 2 :code] := #fhir/code"code-192308"
          [:expansion :contains 2 :display] := #fhir/string"display-192313")))))

(deftest expand-value-set-include-filter-descendent-of-test
  (testing "with a single concept"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"descendent-of"
                    :value #fhir/string"code-182832"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 0)))

  (testing "with two concepts, a parent and a child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-191445"
                 :display #fhir/string"display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"parent"
                   :value #fhir/code"code-182832"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"descendent-of"
                    :value #fhir/string"code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri"value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-191445"
        [:expansion :contains 0 :display] := #fhir/string"display-191448")))

  (testing "with three concepts, a parent, a child and a child of the child"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-191445"
                 :display #fhir/string"display-191448"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"parent"
                   :value #fhir/code"code-182832"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-192308"
                 :display #fhir/string"display-192313"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"parent"
                   :value #fhir/code"code-191445"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-182905"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"descendent-of"
                    :value #fhir/string"code-182832"}]}]}}]]]

      (given (sort-expansion @(expand-value-set ts "url" #fhir/uri"value-set-182905"))
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-191445"
        [:expansion :contains 0 :display] := #fhir/string"display-191448"
        [:expansion :contains 1 :system] := #fhir/uri"system-182822"
        [:expansion :contains 1 :code] := #fhir/code"code-192308"
        [:expansion :contains 1 :display] := #fhir/string"display-192313"))))

(deftest expand-value-set-include-filter-exists-test
  (testing "fails"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]]]

      (testing "with missing property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-182905"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :op #fhir/code"exists"
                 :value #fhir/string"true"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-182905`. Missing filter property."))

      (testing "with invalid value"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-182905"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code"property-160622"
                 :op #fhir/code"exists"
                 :value #fhir/string"invalid-162128"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-182905`. The filter value should be one of `true` or `false` but was `invalid-162128`."))))

  (testing "with a single concept"
    (testing "without a property"
      (testing "that shouldn't exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-182822"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-182832"
                     :display #fhir/string"display-182717"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code"property-160622"
                        :op #fhir/code"exists"
                        :value #fhir/string"false"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-182822"
            [:expansion :contains 0 :code] := #fhir/code"code-182832"
            [:expansion :contains 0 :display] := #fhir/string"display-182717")))

      (testing "that should exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-182822"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-182832"
                     :display #fhir/string"display-182717"}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code"property-160622"
                        :op #fhir/code"exists"
                        :value #fhir/string"true"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))

    (testing "with existing property"
      (testing "that shouldn't exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-182822"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-182832"
                     :display #fhir/string"display-182717"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code"property-160631"
                       :value #fhir/string"value-161324"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code"property-160631"
                        :op #fhir/code"exists"
                        :value #fhir/string"false"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0)))

      (testing "that should exist"
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-182822"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-182832"
                     :display #fhir/string"display-182717"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code"property-160631"
                       :value #fhir/string"value-161324"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-182822"
                      :filter
                      [{:fhir/type :fhir.ValueSet.compose.include/filter
                        :property #fhir/code"property-160631"
                        :op #fhir/code"exists"
                        :value #fhir/string"true"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-182822"
            [:expansion :contains 0 :code] := #fhir/code"code-182832"
            [:expansion :contains 0 :display] := #fhir/string"display-182717"))))))

(deftest expand-value-set-include-filter-equals-test
  (testing "fails"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]]]

      (testing "with missing property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-171904"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :op #fhir/code"="
                 :value #fhir/string"value-161324"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-171904`. Missing filter property."))

      (testing "with missing value"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-171904"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code"property-175506"
                 :op #fhir/code"="}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-171904`. Missing filter value."))))

  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri"system-182822"
             :content #fhir/code"complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code"code-175652"
               :display #fhir/string"display-175659"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code"property-175506"
                 :value #fhir/string"value-161324"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code"code-175607"
               :display #fhir/string"display-175610"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code"property-175506"
                 :value #fhir/string"value-175614"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code"code-172215"
               :display #fhir/string"display-172220"
               :property
               [{:fhir/type :fhir.CodeSystem.concept/property
                 :code #fhir/code"property-172030"
                 :value #fhir/string"value-161324"}]}
              {:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code"code-175607"
               :display #fhir/string"display-175610"}]}]
      [:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri"value-set-175628"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri"system-182822"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code"property-175506"
                  :op #fhir/code"="
                  :value #fhir/string"value-161324"}]}]}}]]]

    (given @(expand-value-set ts "url" #fhir/uri"value-set-175628")
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 1
      [:expansion :contains 0 :system] := #fhir/uri"system-182822"
      [:expansion :contains 0 :code] := #fhir/code"code-175652"
      [:expansion :contains 0 :display] := #fhir/string"display-175659")))

(deftest expand-value-set-include-filter-regex-test
  (testing "fails"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]]]

      (testing "with missing property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-171904"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :op #fhir/code"regex"
                 :value #fhir/string"value-161324"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-171904`. Missing filter property."))

      (testing "with missing value"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-171904"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code"property-175506"
                 :op #fhir/code"regex"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-171904`. Missing filter value."))

      (testing "with invalid value"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-171904"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"system-182822"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code"property-175506"
                 :op #fhir/code"regex"
                 :value #fhir/string"["}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-171904`. Invalid regex pattern `[`."))))

  (testing "code"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"a"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"aa"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"ab"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-175628"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"code"
                    :op #fhir/code"regex"
                    :value #fhir/string"a+"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-175628")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"a"
        [:expansion :contains 1 :system] := #fhir/uri"system-182822"
        [:expansion :contains 1 :code] := #fhir/code"aa")))

  (testing "other property"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-145708"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"property-175506"
                   :value #fhir/string"a"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-145731"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"property-175506"
                   :value #fhir/string"aa"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-145738"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"property-150054"
                   :value #fhir/string"aa"}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code"property-175506"
                   :value #fhir/string"ab"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-175628"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-175506"
                    :op #fhir/code"regex"
                    :value #fhir/string"a+"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-175628")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 2
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-145731"
        [:expansion :contains 1 :system] := #fhir/uri"system-182822"
        [:expansion :contains 1 :code] := #fhir/code"code-145708"))))

(deftest expand-value-set-include-filter-multiple-test
  (testing "is-a and exists (and the other way around)"
    (let [is-a-filter {:fhir/type :fhir.ValueSet.compose.include/filter
                       :property #fhir/code"concept"
                       :op #fhir/code"is-a"
                       :value #fhir/string"code-182832"}
          leaf-filter {:fhir/type :fhir.ValueSet.compose.include/filter
                       :property #fhir/code"child"
                       :op #fhir/code"exists"
                       :value #fhir/string"false"}]
      (doseq [filters (tu/permutations [is-a-filter leaf-filter])]
        (with-system-data [{ts ::ts/local} config]
          [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                   :url #fhir/uri"system-182822"
                   :content #fhir/code"complete"
                   :concept
                   [{:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-182832"
                     :display #fhir/string"display-182717"}
                    {:fhir/type :fhir.CodeSystem/concept
                     :code #fhir/code"code-191445"
                     :display #fhir/string"display-191448"
                     :property
                     [{:fhir/type :fhir.CodeSystem.concept/property
                       :code #fhir/code"parent"
                       :value #fhir/code"code-182832"}]}]}]
            [:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"value-set-182905"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"system-182822"
                      :filter filters}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"value-set-182905")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-182822"
            [:expansion :contains 0 :code] := #fhir/code"code-191445"
            [:expansion :contains 0 :display] := #fhir/string"display-191448"))))))

(deftest expand-value-set-provided-value-set-test
  (testing "fails on non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"not-present"}]]]

      (given-failed-future
       (expand-value-set ts
         "valueSet"
         {:fhir/type :fhir/ValueSet
          :compose
          {:fhir/type :fhir.ValueSet/compose
           :include
           [{:fhir/type :fhir.ValueSet.compose/include
             :system #fhir/uri"system-115910"}]}})
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the provided value set. Can't use the code system `system-115910` because it is not complete. It's content is `not-present`."
        :t := 1)))

  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri"system-115910"
             :content #fhir/code"complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code"code-115927"}]}]]]

    (given @(expand-value-set ts
              "valueSet"
              {:fhir/type :fhir/ValueSet
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}})
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 1
      [:expansion :contains 0 :system] := #fhir/uri"system-115910"
      [:expansion :contains 0 :code] := #fhir/code"code-115927"
      [:expansion :contains 0 #(contains? % :display)] := false)))

(deftest expand-value-set-loinc-include-all-test
  (testing "including all of LOINC is too costly"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"system-152015"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri"system-152015")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `system-152015`. Expanding all LOINC concepts is too costly."
        :fhir/issue "too-costly"))))

(deftest expand-value-set-loinc-include-concept-test
  (testing "include one concept"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"system-152546"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"26465-5"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"system-152546")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains 0 #(contains? % :inactive)] := false
        [:expansion :contains 0 :code] := #fhir/code"26465-5"
        [:expansion :contains 0 :display] := #fhir/string"Leukocytes [#/volume] in Cerebral spinal fluid"
        [:expansion :contains 0 #(contains? % :designation)] := false))

    (testing "with inactive concepts"
      (with-system [{ts ::ts/local} loinc-config]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"http://loinc.org"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"1009-0"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"26465-5"}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"http://loinc.org"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"1009-0"
          [:expansion :contains 0 :display] := #fhir/string"Deprecated Direct antiglobulin test.poly specific reagent [Presence] on Red Blood Cells"
          [:expansion :contains 1 :system] := #fhir/uri"http://loinc.org"
          [:expansion :contains 1 #(contains? % :inactive)] := false
          [:expansion :contains 1 :code] := #fhir/code"26465-5"
          [:expansion :contains 1 :display] := #fhir/string"Leukocytes [#/volume] in Cerebral spinal fluid")

        (testing "value set including only active"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"http://loinc.org"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"1009-0"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"26465-5"}]}]}})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"http://loinc.org"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code"26465-5"
            [:expansion :contains 0 :display] := #fhir/string"Leukocytes [#/volume] in Cerebral spinal fluid"))

        (testing "activeOnly param"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"http://loinc.org"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"1009-0"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"26465-5"}]}]}}
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"http://loinc.org"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code"26465-5"
            [:expansion :contains 0 :display] := #fhir/string"Leukocytes [#/volume] in Cerebral spinal fluid"))))))

(deftest expand-value-set-loinc-include-filter-test
  (testing "unknown filter operator"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-162333"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-162318"
                    :op #fhir/code"op-unknown-162321"
                    :value #fhir/string"value-162324"}]}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri"value-set-162333")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-162333`. Unsupported filter operator `op-unknown-162321` in code system `http://loinc.org`."))))

(deftest expand-value-set-loinc-include-filter-equals-test
  (testing "fails"
    (with-system [{ts ::ts/local} loinc-config]
      (testing "with missing property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-162603"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"http://loinc.org"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :op #fhir/code"="
                 :value #fhir/string"value-162629"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-162603`. Missing = filter property in code system `http://loinc.org`."))

      (testing "with unsupported property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-163943"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"http://loinc.org"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code"property-163943"
                 :op #fhir/code"="}]}]}})
          ::anom/category := ::anom/unsupported
          ::anom/message := "Error while expanding the value set `value-set-163943`. Unsupported = filter property `property-163943` in code system `http://loinc.org`."))

      (testing "with missing value"
        (doseq [property ["CLASS" "STATUS" "CLASSTYPE"]]
          (given-failed-future
           (expand-value-set ts
             "valueSet"
             {:fhir/type :fhir/ValueSet
              :url #fhir/uri"value-set-162730"
              :compose
              {:fhir/type :fhir.ValueSet/compose
               :include
               [{:fhir/type :fhir.ValueSet.compose/include
                 :system #fhir/uri"http://loinc.org"
                 :filter
                 [{:fhir/type :fhir.ValueSet.compose.include/filter
                   :property (type/code property)
                   :op #fhir/code"="}]}]}})
            ::anom/category := ::anom/incorrect
            ::anom/message := (format "Error while expanding the value set `value-set-162730`. Missing %s = filter value in code system `http://loinc.org`." property))))

      (testing "with invalid value"
        (doseq [property ["STATUS" "CLASSTYPE"]]
          (given-failed-future
           (expand-value-set ts
             "valueSet"
             {:fhir/type :fhir/ValueSet
              :url #fhir/uri"value-set-162730"
              :compose
              {:fhir/type :fhir.ValueSet/compose
               :include
               [{:fhir/type :fhir.ValueSet.compose/include
                 :system #fhir/uri"http://loinc.org"
                 :filter
                 [{:fhir/type :fhir.ValueSet.compose.include/filter
                   :property (type/code property)
                   :op #fhir/code"="
                   :value #fhir/string"value-165531"}]}]}})
            ::anom/category := ::anom/incorrect
            ::anom/message := (format "Error while expanding the value set `value-set-162730`. Invalid %s = filter value `value-165531` in code system `http://loinc.org`." property))))))

  (testing "CLASS = CYTO"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-183437"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"CLASS"
                    :op #fhir/code"="
                    :value #fhir/string"CYTO"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-183437")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? #(< 10 % 100)
        [:expansion :contains (concept "50971-1") 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains (concept "50971-1") 0 :display] := #fhir/string"Cytology report of Bronchial brush Cyto stain")))

  (testing "CLASS = LABORDERS"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-183437"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"CLASS"
                    :op #fhir/code"="
                    :value #fhir/string"LABORDERS"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-183437")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? #(< 10 % 100)
        [:expansion :contains (concept "82773-3") 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains (concept "82773-3") 0 :display] := #fhir/string"Lab result time reported")))

  (testing "STATUS = DISCOURAGED"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-162809"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"STATUS"
                    :op #fhir/code"="
                    :value #fhir/string"DISCOURAGED"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-162809")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? (partial < 100)
        [:expansion :contains (concept "69349-9") 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains (concept "69349-9") 0 :display] := #fhir/string"Presence of pressure ulcers - acute [CARE]")))

  (testing "CLASSTYPE = 4 (Claims attachments)"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-162809"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"CLASSTYPE"
                    :op #fhir/code"="
                    :value #fhir/string"3"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-162809")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? (partial < 100)
        [:expansion :contains (concept "39215-9") 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains (concept "39215-9") 0 :display] := #fhir/string"Vision screen finding recency CPHS"))))

(deftest expand-value-set-loinc-include-filter-regex-test
  (testing "fails"
    (with-system [{ts ::ts/local} loinc-config]
      (testing "with missing property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-162603"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"http://loinc.org"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :op #fhir/code"regex"
                 :value #fhir/string"value-162629"}]}]}})
          ::anom/category := ::anom/incorrect
          ::anom/message := "Error while expanding the value set `value-set-162603`. Missing regex filter property in code system `http://loinc.org`."))

      (testing "with unsupported property"
        (given-failed-future
         (expand-value-set ts
           "valueSet"
           {:fhir/type :fhir/ValueSet
            :url #fhir/uri"value-set-163943"
            :compose
            {:fhir/type :fhir.ValueSet/compose
             :include
             [{:fhir/type :fhir.ValueSet.compose/include
               :system #fhir/uri"http://loinc.org"
               :filter
               [{:fhir/type :fhir.ValueSet.compose.include/filter
                 :property #fhir/code"property-163943"
                 :op #fhir/code"regex"}]}]}})
          ::anom/category := ::anom/unsupported
          ::anom/message := "Error while expanding the value set `value-set-163943`. Unsupported regex filter property `property-163943` in code system `http://loinc.org`."))

      (testing "with missing value"
        (doseq [property ["CLASS"]]
          (given-failed-future
           (expand-value-set ts
             "valueSet"
             {:fhir/type :fhir/ValueSet
              :url #fhir/uri"value-set-162730"
              :compose
              {:fhir/type :fhir.ValueSet/compose
               :include
               [{:fhir/type :fhir.ValueSet.compose/include
                 :system #fhir/uri"http://loinc.org"
                 :filter
                 [{:fhir/type :fhir.ValueSet.compose.include/filter
                   :property (type/code property)
                   :op #fhir/code"regex"}]}]}})
            ::anom/category := ::anom/incorrect
            ::anom/message := (format "Error while expanding the value set `value-set-162730`. Missing %s regex filter value in code system `http://loinc.org`." property))))))

  (testing "CLASS =~ CYTO|LABORDERS"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-183437"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"CLASS"
                    :op #fhir/code"regex"
                    :value #fhir/string"CYTO|LABORDERS"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-183437")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? #(< 10 % 1000)
        [:expansion :contains (concept "50971-1") 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains (concept "50971-1") 0 :display] := #fhir/string"Cytology report of Bronchial brush Cyto stain"
        [:expansion :contains (concept "82773-3") 0 :system] := #fhir/uri"http://loinc.org"
        [:expansion :contains (concept "82773-3") 0 :display] := #fhir/string"Lab result time reported"))))

(deftest expand-value-set-loinc-include-filter-multiple-test
  (testing "http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips"
    (with-system-data [{ts ::ts/local} loinc-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-190529"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"STATUS"
                    :op #fhir/code"="
                    :value #fhir/string"ACTIVE"}
                   {:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"CLASSTYPE"
                    :op #fhir/code"="
                    :value #fhir/string"1"}]}]
                :exclude
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://loinc.org"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"CLASS"
                    :op #fhir/code"regex"
                    :value #fhir/string"CYTO|HL7\\.CYTOGEN|HL7\\.GENETICS|^PATH(\\..*)?|^MOLPATH(\\..*)?|NR STATS|H&P\\.HX\\.LAB|CHALSKIN|LABORDERS"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-190529")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] :? #(< 50000 % 60000)
        [:expansion :contains (concept "50971-1") count] := 0
        [:expansion :contains (concept "82773-3") count] := 0))))

(deftest expand-value-set-sct-include-all-test
  (testing "including all of Snomed CT is too costly"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"system-182137"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri"system-182137")
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `system-182137`. Expanding all Snomed CT concepts is too costly."
        :fhir/issue "too-costly"))))

(deftest expand-value-set-sct-include-concept-test
  (testing "include one concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"system-151922"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"441510007"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"system-151922")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
        [:expansion :contains 0 #(contains? % :inactive)] := false
        [:expansion :contains 0 :code] := #fhir/code"441510007"
        [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"
        [:expansion :contains 0 #(contains? % :designation)] := false))

    (testing "with inactive concepts"
      (with-system [{ts ::ts/local} sct-config]
        (given @(expand-value-set ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"http://snomed.info/sct"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"860958002"}
                       {:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"441510007"}]}]}})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"860958002"
          [:expansion :contains 0 :display] := #fhir/string"Temperature of blood (observable entity)"
          [:expansion :contains 1 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 1 #(contains? % :inactive)] := false
          [:expansion :contains 1 :code] := #fhir/code"441510007"
          [:expansion :contains 1 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)")

        (testing "value set including only active"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :inactive #fhir/boolean false
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"http://snomed.info/sct"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"860958002"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"441510007"}]}]}})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code"441510007"
            [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

        (testing "activeOnly param"
          (given @(expand-value-set ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"http://snomed.info/sct"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"860958002"}
                         {:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"441510007"}]}]}}
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code"441510007"
            [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))))

    (testing "with version (module)"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"system-152048"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :version #fhir/string"http://snomed.info/sct/900000000000207008"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"system-152048")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"441510007"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

      (testing "german module"
        (with-system-data [{ts ::ts/local} sct-config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :url #fhir/uri"system-152116"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"http://snomed.info/sct"
                      :version #fhir/string"http://snomed.info/sct/11000274103"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"441510007"}]}]}}]]]

          (given @(expand-value-set ts "url" #fhir/uri"system-152116")
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))

    (testing "with version"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"system-152139"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :version #fhir/string"http://snomed.info/sct/900000000000207008/version/20231201"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"system-152139")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"441510007"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

      (testing "non-existing version"
        (with-system [{ts ::ts/local} sct-config]
          (given-failed-future (expand-value-set ts
                                 "valueSet"
                                 {:fhir/type :fhir/ValueSet
                                  :compose
                                  {:fhir/type :fhir.ValueSet/compose
                                   :include
                                   [{:fhir/type :fhir.ValueSet.compose/include
                                     :system #fhir/uri"http://snomed.info/sct"
                                     :version #fhir/string"http://snomed.info/sct/900000000000207008/version/none-existing"
                                     :concept
                                     [{:fhir/type :fhir.ValueSet.compose.include/concept
                                       :code #fhir/code"441510007"}]}]}})
            ::anom/category := ::anom/not-found
            ::anom/message := "Error while expanding the provided value set. The code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` was not found."))))

    (testing "with designations"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"system-174336"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri"system-174336"
                  "includeDesignations" #fhir/boolean true)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"441510007"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"
          [:expansion :contains 0 :designation 0 :language] := #fhir/code"en"
          [:expansion :contains 0 :designation 0 :use :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 :designation 0 :use :code] := #fhir/code"900000000000013009"
          [:expansion :contains 0 :designation 0 :use :display] := #fhir/string"Synonym"
          [:expansion :contains 0 :designation 0 :value] := #fhir/string"Blood specimen with anticoagulant")))))

(deftest expand-value-set-sct-include-filter-test
  (testing "unknown filter operator"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-120544"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-160019"
                    :op #fhir/code"op-unknown-120524"
                    :value #fhir/string"value-160032"}]}]}}]]]

      (given-failed-future (expand-value-set ts "url" #fhir/uri"value-set-120544")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-120544`. Unsupported filter operator `op-unknown-120524` in code system `http://snomed.info/sct`."))))

(deftest expand-value-set-sct-include-filter-is-a-test
  (testing "with a single is-a filter"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-152706"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"is-a"
                    :value #fhir/string"441510007"}]}]}}]]]

      (doseq [request [["url" #fhir/uri"value-set-152706"]
                       ["url" #fhir/uri"value-set-152706"
                        "system-version" #fhir/canonical"http://snomed.info/sct|http://snomed.info/sct/900000000000207008"]]]
        (given @(apply expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"445295009"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with edetic acid (specimen)"
          [:expansion :contains 1 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 1 #(contains? % :inactive)] := false
          [:expansion :contains 1 :code] := #fhir/code"57921000052103"
          [:expansion :contains 1 :display] := #fhir/string"Whole blood specimen with edetic acid (specimen)"
          [:expansion :contains 2 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 2 #(contains? % :inactive)] := false
          [:expansion :contains 2 :code] := #fhir/code"441510007"
          [:expansion :contains 2 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)")))

    (testing "with many children"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-152902"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code"concept"
                      :op #fhir/code"is-a"
                      :value #fhir/string"123038009"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-152902")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1812
          [:expansion :contains 0 :code] := #fhir/code"396807009"
          [:expansion :contains 1 :code] := #fhir/code"433881000124103")))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-152936"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code"concept"
                      :op #fhir/code"is-a"
                      :value #fhir/string"860958002"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-152936")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"860958002"
          [:expansion :contains 0 :display] := #fhir/string"Temperature of blood (observable entity)")

        (testing "active only"
          (given @(expand-value-set ts
                    "url" #fhir/uri"value-set-152936"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))))

(deftest expand-value-set-sct-include-filter-descendent-of-test
  (testing "with a single descendent-of filter"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-152706"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"concept"
                    :op #fhir/code"descendent-of"
                    :value #fhir/string"441510007"}]}]}}]]]

      (doseq [request [["url" #fhir/uri"value-set-152706"]
                       ["url" #fhir/uri"value-set-152706"
                        "system-version" #fhir/canonical"http://snomed.info/sct|http://snomed.info/sct/900000000000207008"]]]
        (given @(apply expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"445295009"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with edetic acid (specimen)"
          [:expansion :contains 1 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 1 #(contains? % :inactive)] := false
          [:expansion :contains 1 :code] := #fhir/code"57921000052103"
          [:expansion :contains 1 :display] := #fhir/string"Whole blood specimen with edetic acid (specimen)")))

    (testing "with many children"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-152706"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :filter
                    [{:fhir/type :fhir.ValueSet.compose.include/filter
                      :property #fhir/code"concept"
                      :op #fhir/code"descendent-of"
                      :value #fhir/string"123038009"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-152706")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1811
          [:expansion :contains 0 :code] := #fhir/code"396807009"
          [:expansion :contains 1 :code] := #fhir/code"433881000124103")))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-152706"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"860958002"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-152706")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"860958002"
          [:expansion :contains 0 :display] := #fhir/string"Temperature of blood (observable entity)")

        (testing "active only"
          (given @(expand-value-set ts
                    "url" #fhir/uri"value-set-152706"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))))

(deftest expand-value-set-ucum-test
  (with-system-data [{ts ::ts/local} ucum-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri"value-set-152706"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri"http://unitsofmeasure.org"
                :concept
                [{:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code"Cel"}
                 {:fhir/type :fhir.ValueSet.compose.include/concept
                  :code #fhir/code"[degF]"}]}]}}]]]

    (given @(expand-value-set ts "url" #fhir/uri"value-set-152706")
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 2
      [:expansion :contains 0 :system] := #fhir/uri"http://unitsofmeasure.org"
      [:expansion :contains 0 :code] := #fhir/code"Cel"
      [:expansion :contains 1 :system] := #fhir/uri"http://unitsofmeasure.org"
      [:expansion :contains 1 :code] := #fhir/code"[degF]")))

(deftest expand-value-set-other-test
  (testing "display from code system is preserved"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"
                 :display #fhir/string"display-182508"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-115910"
        [:expansion :contains 0 :code] := #fhir/code"code-115927"
        [:expansion :contains 0 :display] := #fhir/string"display-182508"))

    (testing "include only one code"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-163444"
                   :display #fhir/string"display-182523"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"code-163444"}]}]}}]]]

        (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-163444"
          [:expansion :contains 0 :display] := #fhir/string"display-182523"))))

  (testing "display from value set is used"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-163444"
                 :display #fhir/string"display-182556"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"code-163444"
                    :display #fhir/string"display-182609"}]}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-115910"
        [:expansion :contains 0 :code] := #fhir/code"code-163444"
        [:expansion :contains 0 :display] := #fhir/string"display-182609")))

  (testing "removes id, meta and compose, retains the url and has an expansion timestamp and total"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :meta #fhir/Meta{:versionId #fhir/id"163523"}
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
        :fhir/type := :fhir/ValueSet
        :id := nil
        :meta := nil
        :url := #fhir/uri"value-set-135750"
        :compose := nil
        [:expansion :timestamp] := #fhir/dateTime"1970-01-01T00:00:00Z"
        [:expansion :identifier type/value] :? uuid-urn?
        [:expansion :total] := #fhir/integer 1
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-115910"
        [:expansion :contains 0 :code] := #fhir/code"code-115927")))

  (testing "including definition"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :meta #fhir/Meta{:versionId #fhir/id"163523"}
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri"value-set-135750"
                "includeDefinition" #fhir/boolean true)
        :fhir/type := :fhir/ValueSet
        :id := nil
        :meta := nil
        :url := #fhir/uri"value-set-135750"
        [:compose :include 0 :system] := #fhir/uri"system-115910"
        [:expansion :timestamp] := #fhir/dateTime"1970-01-01T00:00:00Z"
        [:expansion :identifier type/value] :? uuid-urn?
        [:expansion :total] := #fhir/integer 1
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-115910"
        [:expansion :contains 0 :code] := #fhir/code"code-115927")))

  (testing "retains status and version"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :version #fhir/string"version-132003"
               :status #fhir/code"active"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (given @(expand-value-set ts "url" #fhir/uri"value-set-135750")
        :fhir/type := :fhir/ValueSet
        :url := #fhir/uri"value-set-135750"
        :version := #fhir/string"version-132003"
        :status := #fhir/code"active"
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-115910"
        [:expansion :contains 0 :code] := #fhir/code"code-115927")))

  (testing "supports count"
    (testing "zero"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-153115"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri"value-set-135750"
                  "count" #fhir/integer 0)
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "count") 0 :name] := #fhir/string"count"
          [:expansion :total] := #fhir/integer 2
          [:expansion :contains count] := 0)))

    (testing "one"
      (with-system-data [{ts ::ts/local} config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri"system-115910"
                 :content #fhir/code"complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-115927"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-153115"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-135750"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"system-115910"}]}}]]]

        (given @(expand-value-set ts
                  "url" #fhir/uri"value-set-135750"
                  "count" #fhir/integer 1)
          :fhir/type := :fhir/ValueSet
          [:expansion (parameter "count") 0 :value] := #fhir/integer 1
          [:expansion :total] := #fhir/integer 2
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"))))

  (testing "supports exclude-nested"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}]]]

      (given @(expand-value-set ts
                "url" #fhir/uri"value-set-135750"
                "excludeNested" #fhir/boolean true)
        :fhir/type := :fhir/ValueSet
        [:expansion (parameter "excludeNested") 0 :value] := #fhir/boolean true))))

(defn- value-set-validate-code [ts & nvs]
  (ts/value-set-validate-code ts (apply u/parameters nvs)))

(deftest value-set-validate-code-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "no parameters"
      (given-failed-future (value-set-validate-code ts)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing both parameters `url` and `valueSet`."))

    (testing "not found"
      (testing "url"
        (given-failed-future (value-set-validate-code ts
                               "url" #fhir/uri"url-194718"
                               "code" #fhir/code"code-083955"
                               "inferSystem" #fhir/boolean true)
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (value-set-validate-code ts
                               "url" #fhir/uri"url-144258"
                               "valueSetVersion" #fhir/string"version-144244"
                               "code" #fhir/code"code-083955"
                               "inferSystem" #fhir/boolean true)
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-144258|version-144244` was not found."
          :t := 0))))

  (testing "supplement not found"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-172718"
               :version #fhir/string"version-172730"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :extension
               [#fhir/Extension{:url "http://hl7.org/fhir/StructureDefinition/valueset-supplement"
                                :value #fhir/canonical"system-172718|version-172744"}]
               :url #fhir/uri"value-set-172753"}]]]

      (given-failed-future (value-set-validate-code ts
                             "url" #fhir/uri"value-set-172753"
                             "code" #fhir/code"code-172811"
                             "system" #fhir/uri"system-172822")
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `system-172718|version-172744` was not found."))))

(deftest value-set-validate-code-include-all-test
  (testing "version *"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-105710"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :version "*"}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-105710"
                "code" #fhir/code"code-182832"
                "system" #fhir/uri"system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code-182832"
        [(parameter "system") 0 :value] := #fhir/uri"system-182822"))))

(deftest value-set-validate-code-include-filter-test
  (testing "unknown filter operator"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-105710"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-160019"
                    :op #fhir/code"op-unknown-120524"
                    :value #fhir/string"value-160032"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-105710"
                "code" #fhir/code"code-182832"
                "system" #fhir/uri"system-182822")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Unable to check whether the code is in the value set `value-set-105710` because the value set was invalid. Unsupported filter operator `op-unknown-120524` in code system `system-182822`."
        [(parameter "code") 0 :value] := #fhir/code"code-182832"
        [(parameter "system") 0 :value] := #fhir/uri"system-182822"))))

(deftest value-set-validate-code-sct-include-all-test
  (with-system-data [{ts ::ts/local} sct-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri"value-set-102658"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri"http://snomed.info/sct"}]}}]]]

    (given @(value-set-validate-code ts
              "url" #fhir/uri"value-set-102658"
              "code" #fhir/code"441510007"
              "system" #fhir/uri"http://snomed.info/sct")
      :fhir/type := :fhir/Parameters
      [(parameter "result") 0 :value] := #fhir/boolean true
      [(parameter "code") 0 :value] := #fhir/code"441510007"
      [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
      [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
      [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"))

  (testing "with supplement"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-103927"
               :content #fhir/code"supplement"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"441510007"
                 :designation
                 [{:fhir/type :fhir.CodeSystem.concept/designation
                   :language #fhir/code"de"
                   :value #fhir/string"designation-104319"}]}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :extension
               [#fhir/Extension{:url "http://hl7.org/fhir/StructureDefinition/valueset-supplement"
                                :value #fhir/canonical"system-103927"}]
               :url #fhir/uri"value-set-102658"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"}]}}]]]

      (testing "without displayLanguage"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri"value-set-102658"
                  "code" #fhir/code"441510007"
                  "system" #fhir/uri"http://snomed.info/sct"
                  "display" #fhir/string"designation-104319")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"))

      (testing "with displayLanguage"
        (given @(value-set-validate-code ts
                  "url" #fhir/uri"value-set-102658"
                  "code" #fhir/code"441510007"
                  "system" #fhir/uri"http://snomed.info/sct"
                  "display" #fhir/string"designation-104319"
                  "displayLanguage" #fhir/code"de")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "display") 0 :value] := #fhir/string"designation-104319"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001")))))

(deftest value-set-validate-code-sct-include-concept-test
  (testing "non-matching concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-120641"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"860958002"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-120641"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-120641`.",
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-120641`.",
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"])))

  (testing "active concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-120641"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-120641"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001")))

  (testing "inactive concept"
    (with-system [{ts ::ts/local} sct-config]
      (given @(value-set-validate-code ts
                "valueSet"
                {:fhir/type :fhir/ValueSet
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"860958002"}]}]}}
                "code" #fhir/code"860958002"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"860958002"
        [(parameter "display") 0 :value] := #fhir/string"Temperature of blood (observable entity)"
        [(parameter "inactive") 0 :value] := #fhir/boolean true
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001")

      (testing "with active only"
        (with-system [{ts ::ts/local} sct-config]
          (given @(value-set-validate-code ts
                    "valueSet"
                    {:fhir/type :fhir/ValueSet
                     :compose
                     {:fhir/type :fhir.ValueSet/compose
                      :include
                      [{:fhir/type :fhir.ValueSet.compose/include
                        :system #fhir/uri"http://snomed.info/sct"
                        :concept
                        [{:fhir/type :fhir.ValueSet.compose.include/concept
                          :code #fhir/code"860958002"}]}]}}
                    "code" #fhir/code"860958002"
                    "system" #fhir/uri"http://snomed.info/sct"
                    "activeOnly" #fhir/boolean true)
            :fhir/type := :fhir/Parameters
            [(parameter "result") 0 :value] := #fhir/boolean false
            [(parameter "message") 0 :value] := #fhir/string"The provided code `http://snomed.info/sct#860958002` was not found in the provided value set.",
            [(parameter "code") 0 :value] := #fhir/code"860958002"
            [(parameter "display") 0 :value] := #fhir/string"Temperature of blood (observable entity)"
            [(parameter "inactive") 0 :value] := #fhir/boolean true
            [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
            [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
            [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
            [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
            [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
            [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://snomed.info/sct#860958002` was not found in the provided value set.",
            [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]
            [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
            [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"business-rule"
            [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "code-rule")
            [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"The code `860958002` is valid but is not active."
            [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"code"])))))

  (testing "with version (module)"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-152014"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :version #fhir/string"http://snomed.info/sct/900000000000207008"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-152014"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001")))

  (testing "with version"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-152138"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :version #fhir/string"http://snomed.info/sct/900000000000207008/version/20231201"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"441510007"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-152138"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20231201"))

    (testing "non-existing version"
      (with-system [{ts ::ts/local} sct-config]
        (given @(value-set-validate-code ts
                  "valueSet"
                  {:fhir/type :fhir/ValueSet
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"http://snomed.info/sct"
                      :version #fhir/string"http://snomed.info/sct/900000000000207008/version/none-existing"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"441510007"}]}]}}
                  "code" #fhir/code"441510007"
                  "system" #fhir/uri"http://snomed.info/sct")
          [(parameter "result") 0 :value] := #fhir/boolean false
          [(parameter "message") 0 :value] := #fhir/string"A definition for the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` could not be found, so the code cannot be validated.",
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/none-existing"
          [(parameter "issues") 0 :resource :issue count] := 2
          [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
          [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"not-found"
          [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
          [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"A definition for the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` could not be found, so the code cannot be validated."
          [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"system"]
          [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"warning"
          [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"not-found"
          [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "vs-invalid")
          [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"Unable to check whether the code is in the provided value set because the code system `http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/none-existing` was not found.")))

    (testing "synonym display"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :url #fhir/uri"value-set-120641"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(value-set-validate-code ts
                  "url" #fhir/uri"value-set-120641"
                  "code" #fhir/code"441510007"
                  "system" #fhir/uri"http://snomed.info/sct"
                  "display" #fhir/string"Blood specimen with anticoagulant")
          :fhir/type := :fhir/Parameters
          [(parameter "result") 0 :value] := #fhir/boolean true
          [(parameter "code") 0 :value] := #fhir/code"441510007"
          [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
          [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
          [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001")))))

(deftest value-set-validate-code-sct-include-filter-test
  (testing "unknown filter operator"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-105710"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-160019"
                    :op #fhir/code"op-unknown-120524"
                    :value #fhir/string"value-160032"}]}]}}]]]

      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-105710"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Unable to check whether the code is in the value set `value-set-105710` because the value set was invalid. Unsupported filter operator `op-unknown-120524` in code system `http://snomed.info/sct`."
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"))))

(deftest value-set-validate-code-sct-include-filter-is-a-test
  (with-system-data [{ts ::ts/local} sct-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri"value-set-113851"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri"http://snomed.info/sct"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code"concept"
                  :op #fhir/code"is-a"
                  :value #fhir/string"441510007"}]}]}}]]]

    (testing "direct code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-113851"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"))

    (testing "child code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-113851"
                "code" #fhir/code"445295009"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"445295009"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with edetic acid (specimen)"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"))

    (testing "parent code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-113851"
                "code" #fhir/code"119297000"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "code") 0 :value] := #fhir/code"119297000"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]))))

(deftest value-set-validate-code-sct-include-filter-descendent-of-test
  (with-system-data [{ts ::ts/local} sct-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
             :url #fhir/uri"value-set-113851"
             :compose
             {:fhir/type :fhir.ValueSet/compose
              :include
              [{:fhir/type :fhir.ValueSet.compose/include
                :system #fhir/uri"http://snomed.info/sct"
                :filter
                [{:fhir/type :fhir.ValueSet.compose.include/filter
                  :property #fhir/code"concept"
                  :op #fhir/code"descendent-of"
                  :value #fhir/string"441510007"}]}]}}]]]

    (testing "direct code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-113851"
                "code" #fhir/code"441510007"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
        [(parameter "code") 0 :value] := #fhir/code"441510007"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://snomed.info/sct#441510007` was not found in the value set `value-set-113851`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]))

    (testing "child code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-113851"
                "code" #fhir/code"445295009"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"445295009"
        [(parameter "display") 0 :value] := #fhir/string"Blood specimen with edetic acid (specimen)"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"))

    (testing "parent code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"value-set-113851"
                "code" #fhir/code"119297000"
                "system" #fhir/uri"http://snomed.info/sct")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "code") 0 :value] := #fhir/code"119297000"
        [(parameter "system") 0 :value] := #fhir/uri"http://snomed.info/sct"
        [(parameter "version") 0 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://snomed.info/sct#119297000` was not found in the value set `value-set-113851`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]))))

(defn- load-resource [test name]
  (fhir-spec/conform-json (fhir-spec/parse-json (slurp (io/resource (format "tx-ecosystem/%s/%s.json" test name))))))

(deftest tx-ecosystem-validation-tests
  (with-system-data [{ts ::ts/local} config]
    [[[:put (load-resource "simple" "codesystem-simple")]
      [:put (load-resource "simple" "valueset-all")]
      [:put (load-resource "simple" "valueset-import-bad")]
      [:put (load-resource "simple" "valueset-enumerated")]
      [:put (load-resource "simple" "valueset-filter-isa")]
      [:put (load-resource "simple" "valueset-filter-property")]
      [:put (load-resource "simple" "valueset-filter-regex")]
      [:put (load-resource "language" "codesystem-de-multi")]
      [:put (load-resource "language" "codesystem-de-single")]
      [:put (load-resource "language" "codesystem-en-multi")]
      [:put (load-resource "language" "codesystem-en-single")]
      [:put (load-resource "language" "valueset-de-multi")]
      [:put (load-resource "language" "valueset-de-single")]
      [:put (load-resource "language" "valueset-en-multi")]
      [:put (load-resource "language" "valueset-en-single")]
      [:put (load-resource "language" "valueset-en-enlang-multi")]
      [:put (load-resource "language" "valueset-en-en-multi")]
      [:put (load-resource "version" "codesystem-version-1")]
      [:put (load-resource "version" "codesystem-version-2")]
      [:put (load-resource "version" "valueset-all-version-1")]
      [:put (load-resource "version" "valueset-all-version-2")]
      [:put (load-resource "version" "valueset-all-version")]
      [:put (load-resource "inactive" "valueset-all")]
      [:put (load-resource "inactive" "codesystem-inactive")]]]

    (testing "validation-simple-code-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"))

    (testing "validation-simple-code-implied-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code"code1"
                "inferSystem" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"))

    (testing "validation-simple-coding-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                  :code #fhir/code"code1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"))

    (testing "validation-simple-codeableconcept-good"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                     :code #fhir/code"code1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                                                         :code #fhir/code"code1"}]}))

    (testing "validation-simple-code-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code"code1x"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code"code1x"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple`."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"code"]))

    (testing "validation-simple-code-implied-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code"code1x"
                "inferSystem" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code"code1x"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"not-found"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "cannot-infer")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"The provided code `code1x` is not known to belong to the inferred code system `http://hl7.org/fhir/test/CodeSystem/simple`."))

    (testing "validation-simple-coding-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                  :code #fhir/code"code1x"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code"code1x"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"Coding.code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple`."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"Coding.code"]))

    (testing "validation-simple-coding-bad-code-inactive"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/inactive-all"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/inactive"
                  :code #fhir/code"codeInactive"}
                "activeOnly" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/inactive#codeInactive` was not found in the value set `http://hl7.org/fhir/test/ValueSet/inactive-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code"codeInactive"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/inactive"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"
        [(parameter "display") 0 :value] := #fhir/string"Display inactive"
        [(parameter "inactive") 0 :value] := #fhir/boolean true
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/inactive"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/inactive#codeInactive` was not found in the value set `http://hl7.org/fhir/test/ValueSet/inactive-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"Coding.code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"business-rule"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "code-rule")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"The code `codeInactive` is valid but is not active."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"Coding.code"]))

    (testing "validation-simple-codeableconcept-bad-code"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                     :code #fhir/code"code1x"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                                                         :code #fhir/code"code1x"}]}
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code1x` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"CodeableConcept.coding[0].code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple`."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"CodeableConcept.coding[0].code"]))

    (testing "validation-simple-code-bad-valueSet"
      (given-failed-future (value-set-validate-code ts
                             "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-allX"
                             "code" #fhir/code"code1"
                             "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple")
        ::anom/category := ::anom/not-found
        ::anom/message := "The value set `http://hl7.org/fhir/test/ValueSet/simple-allX` was not found."))

    (testing "validation-simple-code-bad-import"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-import-bad"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"A definition for the value Set `http://hl7.org/fhir/test/ValueSet/simple-filter-isaX` could not be found."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"not-found"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"A definition for the value Set `http://hl7.org/fhir/test/ValueSet/simple-filter-isaX` could not be found."
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"warning"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"not-found"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "vs-invalid")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"Unable to check whether the code is in the value set `http://hl7.org/fhir/test/ValueSet/simple-import-bad|5.0.0` because the value set `http://hl7.org/fhir/test/ValueSet/simple-filter-isaX` was not found."))

    (testing "validation-simple-code-bad-system"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simplex")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simplex` could not be found, so the code cannot be validated."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simplex"
        [(parameter "issues") 0 :resource :issue count] := 2
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simplex#code1` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"not-found"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "not-found")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simplex` could not be found, so the code cannot be validated."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"system"]))

    (testing "validation-simple-coding-no-system"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "coding" #fhir/Coding{:code #fhir/code"code1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `code1` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `code1` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-all|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"Coding.code"]
        [(parameter "issues") 0 :resource :issue 1 :severity] := #fhir/code"warning"
        [(parameter "issues") 0 :resource :issue 1 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 1 :details :coding] :? (tx-issue-type "invalid-data")
        [(parameter "issues") 0 :resource :issue 1 :details :text] := #fhir/string"Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided."
        [(parameter "issues") 0 :resource :issue 1 :expression] := [#fhir/string"Coding"]))

    (testing "validation-simple-code-bad-version1"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-all"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                "systemVersion" #fhir/string"1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simple|1.0.0` could not be found, so the code cannot be validated."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"not-found"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-found")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"A definition for the code system `http://hl7.org/fhir/test/CodeSystem/simple|1.0.0` could not be found, so the code cannot be validated."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"system"]))

    (testing "validation-simple-code-good-version"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                "systemVersion" #fhir/string"1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"))

    (testing "validation-simple-codeableconcept-good-version"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                     :version #fhir/string"1.0.0"
                     :code #fhir/code"code1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                                                         :version #fhir/string"1.0.0"
                                                         :code #fhir/code"code1"}]}))

    (testing "validation-simple-code-good-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code"code1"
                "display" #fhir/string"Display 1 (1.0)"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                "systemVersion" #fhir/string"1.0.0")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"))

    (testing "validation-simple-codeableconcept-good-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                     :display #fhir/string"Display 1 (1.0)"
                     :code #fhir/code"code1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                                                         :display #fhir/string"Display 1 (1.0)"
                                                         :code #fhir/code"code1"}]}))

    (testing "validation-simple-code-bad-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                "display" #fhir/string"Display 1X")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"display"]))

    (testing "validation-simple-code-bad-display-ws"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                "display" #fhir/string"Display  1 (1.0)")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"display"]))

    (testing "validation-simple-coding-bad-display"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                  :code #fhir/code"code1"
                  :display #fhir/string"Display 1X"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Display 1X` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"Coding.display"]))

    (testing "validation-simple-code-bad-display-warning"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/version-all-1"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
                "display" #fhir/string"Display  1 (1.0)"
                "lenient-display-validation" #fhir/boolean true)
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/version"
        [(parameter "version") 0 :value] := #fhir/string"1.0.0"
        [(parameter "display") 0 :value] := #fhir/string"Display 1 (1.0)"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"warning"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Display  1 (1.0)` for code `http://hl7.org/fhir/test/CodeSystem/version#code1`. A valid display is `Display 1 (1.0)`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"display"]))

    (testing "validation-simple-code-good-language"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/en-multi"
                "code" #fhir/code"code1"
                "display" #fhir/string"Anzeige 1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
                "displayLanguage" #fhir/code"de,it,zh")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Anzeige 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"))

    (testing "validation-simple-code-bad-language"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/en-multi"
                "code" #fhir/code"code1"
                "display" #fhir/code"Anzeige 1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
                "displayLanguage" #fhir/code"en")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"display"]))

    (testing "validation-simple-code-good-regex"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-filter-regex"
                "code" #fhir/code"code1"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"))

    (testing "validation-simple-code-bad-regex"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/simple-filter-regex"
                "code" #fhir/code"code2a"
                "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code2a` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-filter-regex|5.0.0`."
        [(parameter "code") 0 :value] := #fhir/code"code2a"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/simple#code2a` was not found in the value set `http://hl7.org/fhir/test/ValueSet/simple-filter-regex|5.0.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]))

    (testing "validation-simple-coding-bad-language-vs"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/en-en-multi"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
                  :code #fhir/code"code1"
                  :display #fhir/string"Anzeige 1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"Coding.display"]))

    (testing "validation-simple-coding-bad-language-vslang"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/en-enlang-multi"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
                  :code #fhir/code"code1"
                  :display #fhir/string"Anzeige 1"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/en-multi"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-display")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Invalid display `Anzeige 1` for code `http://hl7.org/fhir/test/CodeSystem/en-multi#code1`. A valid display is `Display 1`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"Coding.display"]))

    (testing "validation-cs-code-good"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                "code" #fhir/code"code1")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "version") 0 :value] := #fhir/string"0.1.0"))

    (testing "validation-cs-code-bad-code"
      (given @(code-system-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
                "code" #fhir/code"code1x")
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple|0.1.0`."
        [(parameter "code") 0 :value] := #fhir/code"code1x"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/simple"
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "invalid-code")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"Unknown code `code1x` was not found in the code system `http://hl7.org/fhir/test/CodeSystem/simple|0.1.0`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"code"]))))

(deftest tx-ecosystem-other-tests
  (with-system-data [{ts ::ts/local} config]
    [[[:put (load-resource "other" "codesystem-dual-filter")]
      [:put (load-resource "other" "valueset-dual-filter")]]]

    (testing "validation-dual-filter-in"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/dual-filter"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/dual-filter"
                     :code #fhir/code"AA1"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"AA1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/dual-filter"
        [(parameter "display") 0 :value] := #fhir/string"AA1"
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/dual-filter"
                                                         :code #fhir/code"AA1"}]}))

    (testing "validation-dual-filter-out"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/dual-filter"
                "codeableConcept"
                #fhir/CodeableConcept
                 {:coding
                  [#fhir/Coding
                    {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/dual-filter"
                     :code #fhir/code"AA"}]})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean false
        [(parameter "message") 0 :value] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/dual-filter#AA` was not found in the value set `http://hl7.org/fhir/test/ValueSet/dual-filter`."
        [(parameter "codeableConcept") 0 :value] := #fhir/CodeableConcept
                                                     {:coding
                                                      [#fhir/Coding
                                                        {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/dual-filter"
                                                         :code #fhir/code"AA"}]}
        [(parameter "issues") 0 :resource :issue 0 :severity] := #fhir/code"error"
        [(parameter "issues") 0 :resource :issue 0 :code] := #fhir/code"code-invalid"
        [(parameter "issues") 0 :resource :issue 0 :details :coding] :? (tx-issue-type "not-in-vs")
        [(parameter "issues") 0 :resource :issue 0 :details :text] := #fhir/string"The provided code `http://hl7.org/fhir/test/CodeSystem/dual-filter#AA` was not found in the value set `http://hl7.org/fhir/test/ValueSet/dual-filter`."
        [(parameter "issues") 0 :resource :issue 0 :expression] := [#fhir/string"CodeableConcept.coding[0].code"]))))

(deftest tx-ecosystem-extensions-tests
  (with-system-data [{ts ::ts/local} config]
    [[[:put (load-resource "extensions" "codesystem-extensions")]
      [:put (load-resource "extensions" "codesystem-supplement")]
      [:put (load-resource "extensions" "valueset-extensions-all")]
      [:put (load-resource "extensions" "valueset-extensions-bad-supplement")]
      [:put (load-resource "extensions" "valueset-extensions-enumerated")]]]

    (testing "validate-code-bad-supplement"
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/extensions-bad-supplement"
         "code" #fhir/code"code1"
         "system" #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions")
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://hl7.org/fhir/test/CodeSystem/supplementX` was not found."))

    (testing "validate-coding-bad-supplement"
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/extensions-bad-supplement"
         "coding"
         #fhir/Coding
          {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions"
           :code #fhir/code"code1"})
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://hl7.org/fhir/test/CodeSystem/supplementX` was not found."))

    (testing "validate-codeableconcept-bad-supplement"
      (given-failed-future
       (value-set-validate-code ts
         "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/extensions-bad-supplement"
         "codeableConcept"
         #fhir/CodeableConcept
          {:coding
           [#fhir/Coding
             {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions"
              :code #fhir/code"code1"}]})
        ::anom/category := ::anom/not-found
        ::anom/message := "The code system `http://hl7.org/fhir/test/CodeSystem/supplementX` was not found."))

    (testing "validate-coding-good-supplement"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/extensions-enumerated"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions"
                  :code #fhir/code"code1"
                  :display #fhir/string"ectenoot"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"Display 1"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions"))

    (testing "validate-coding-good2-supplement"
      (given @(value-set-validate-code ts
                "url" #fhir/uri"http://hl7.org/fhir/test/ValueSet/extensions-enumerated"
                "displayLanguage" #fhir/code"nl"
                "coding"
                #fhir/Coding
                 {:system #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions"
                  :code #fhir/code"code1"
                  :display #fhir/string"ectenoot"})
        :fhir/type := :fhir/Parameters
        [(parameter "result") 0 :value] := #fhir/boolean true
        [(parameter "code") 0 :value] := #fhir/code"code1"
        [(parameter "display") 0 :value] := #fhir/string"ectenoot"
        [(parameter "system") 0 :value] := #fhir/uri"http://hl7.org/fhir/test/CodeSystem/extensions"))))
