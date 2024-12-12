(ns blaze.terminology-service.local-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.node :refer [node?]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.path :refer [path]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local]
   [blaze.terminology-service.local.code-system-spec]
   [blaze.terminology-service.local.value-set-spec]
   [blaze.test-util :as tu :refer [given-thrown]]
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

(defn- sct-id [node]
  (:id (coll/first (d/type-query (d/db node) "CodeSystem" [["url" "http://snomed.info/sct"] ["version" "http://snomed.info/sct/900000000000207008/version/20241001"]]))))

(defn- ucum-id [node]
  (:id (coll/first (d/type-query (d/db node) "CodeSystem" [["url" "http://unitsofmeasure.org"]]))))

(defn- sort-expansion [value-set]
  (update-in value-set [:expansion :contains] (partial sort-by (comp type/value :code))))

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

(deftest code-system-validate-code-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "missing id or url"
      (given-failed-future (ts/code-system-validate-code ts {})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing ID or URL."))

    (testing "not found"
      (testing "id"
        (given-failed-future (ts/code-system-validate-code ts {:id "id-175736"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system with id `id-175736` was not found."
          :t := 0))

      (testing "url"
        (given-failed-future (ts/code-system-validate-code ts {:url "url-194718"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system with URL `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (ts/code-system-validate-code ts {:url "url-144258" :version "version-144244"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The code system with URL `url-144258` and version `version-144244` was not found."
          :t := 0)))))

(deftest code-system-validate-code-test
  (testing "with id or url"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]]]

      (testing "existing code"
        (let [request {:code "code-115927"}]
          (doseq [request (mapv (partial merge request) [{:url "system-115910"} {:id "0"}])]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"code-115927"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"system-115910"))))

      (testing "non-existing code"
        (let [request {:code "code-153948"}]
          (doseq [request (mapv (partial merge request) [{:url "system-115910"} {:id "0"}])]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 2
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean false
              [:parameter 1 :name] := #fhir/string"message"
              [:parameter 1 :value] := #fhir/string"The provided code `code-153948` was not found in the code system with URL `system-115910`."))))

      (testing "existing coding"
        (let [request {:coding #fhir/Coding{:system #fhir/uri"system-115910" :code #fhir/code"code-115927"}}]
          (doseq [request (mapv (partial merge request) [{:url "system-115910"} {:id "0"}])]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"code-115927"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"system-115910"))))

      (testing "non-existing coding"
        (testing "with non-existing system"
          (let [request {:coding #fhir/Coding{:system #fhir/uri"system-170454" :code #fhir/code"code-115927"}}]
            (doseq [request (mapv (partial merge request) [{:url "system-115910"} {:id "0"}])]
              (given @(ts/code-system-validate-code ts request)
                :fhir/type := :fhir/Parameters
                [:parameter count] := 2
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean false
                [:parameter 1 :name] := #fhir/string"message"
                [:parameter 1 :value] := #fhir/string"The system of the provided coding `system-170454` does not match the code system with URL `system-115910`."))))

        (testing "with non-existing code"
          (let [request {:coding #fhir/Coding{:system #fhir/uri"system-115910" :code #fhir/code"code-153948"}}]
            (doseq [request (mapv (partial merge request) [{:url "system-115910"} {:id "0"}])]
              (given @(ts/code-system-validate-code ts request)
                :fhir/type := :fhir/Parameters
                [:parameter count] := 2
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean false
                [:parameter 1 :name] := #fhir/string"message"
                [:parameter 1 :value] := #fhir/string"The provided code `code-153948` was not found in the code system with URL `system-115910`.")))))))

  (testing "with non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"not-present"}]]]

      (given @(ts/code-system-validate-code ts {:url "system-115910" :code "code-115927"})
        :fhir/type := :fhir/Parameters
        [:parameter count] := 2
        [:parameter 0 :name] := #fhir/string"result"
        [:parameter 0 :value] := #fhir/boolean false
        [:parameter 1 :name] := #fhir/string"message"
        [:parameter 1 :value] := #fhir/string"Can't use the code system with URL `system-115910` because it is not complete. It's content is `not-present`.")))

  (testing "with code-system"
    (with-system [{ts ::ts/local} config]
      (testing "existing code"
        (let [request
              {:code-system
               {:fhir/type :fhir/CodeSystem
                :content #fhir/code"complete"
                :concept
                [{:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code"code-115927"}]}
               :code "code-115927"}]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"code-115927"))

        (testing "with url"
          (let [request
                {:code-system
                 {:fhir/type :fhir/CodeSystem
                  :url #fhir/uri"system-115910"
                  :content #fhir/code"complete"
                  :concept
                  [{:fhir/type :fhir.CodeSystem/concept
                    :code #fhir/code"code-115927"}]}
                 :code "code-115927"}]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"code-115927"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"system-115910"))))

      (testing "non-existing code"
        (let [request
              {:code-system
               {:fhir/type :fhir/CodeSystem
                :content #fhir/code"complete"
                :concept
                [{:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code"code-115927"}]}
               :code "code-153948"}]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean false
            [:parameter 1 :name] := #fhir/string"message"
            [:parameter 1 :value] := #fhir/string"The provided code `code-153948` was not found in the provided code system."))

        (testing "with url"
          (let [request
                {:code-system
                 {:fhir/type :fhir/CodeSystem
                  :url #fhir/uri"system-115910"
                  :content #fhir/code"complete"
                  :concept
                  [{:fhir/type :fhir.CodeSystem/concept
                    :code #fhir/code"code-115927"}]}
                 :code "code-153948"}]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 2
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean false
              [:parameter 1 :name] := #fhir/string"message"
              [:parameter 1 :value] := #fhir/string"The provided code `code-153948` was not found in the code system with URL `system-115910`."))))

      (testing "existing coding"
        (let [request
              {:code-system
               {:fhir/type :fhir/CodeSystem
                :url #fhir/uri"system-172718"
                :content #fhir/code"complete"
                :concept
                [{:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code"code-172653"}]}
               :coding #fhir/Coding{:system #fhir/uri"system-172718" :code #fhir/code"code-172653"}}]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"code-172653"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"system-172718"))))))

(deftest code-system-validate-code-sct-test
  (with-system [{ts ::ts/local :blaze.db/keys [node]} sct-config]
    (testing "existing code"
      (let [request {:code "441510007"}]
        (doseq [request (mapv (partial merge request)
                              [{:url "http://snomed.info/sct"}
                               {:url "http://snomed.info/sct"
                                :version "http://snomed.info/sct/900000000000207008"}
                               {:url "http://snomed.info/sct"
                                :version "http://snomed.info/sct/900000000000207008/version/20241001"}
                               {:id (sct-id node)}])]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 5
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"441510007"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"http://snomed.info/sct"
            [:parameter 3 :name] := #fhir/string"version"
            [:parameter 3 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
            [:parameter 4 :name] := #fhir/string"display"
            [:parameter 4 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"))))

    (testing "non-existing code"
      (doseq [request [{:code "non-existing"} {:code "0815"} {:code "441510008"}]
              request (mapv (partial merge request) [{:url "http://snomed.info/sct"} {:id (sct-id node)}])]
        (given @(ts/code-system-validate-code ts request)
          :fhir/type := :fhir/Parameters
          [:parameter count] := 2
          [:parameter 0 :name] := #fhir/string"result"
          [:parameter 0 :value] := #fhir/boolean false
          [:parameter 1 :name] := #fhir/string"message"
          [:parameter 1 :value] := (type/string (format "The provided code `%s` was not found in the code system with URL `http://snomed.info/sct`." (:code request))))))

    (testing "existing coding"
      (let [request {:coding #fhir/Coding{:system #fhir/uri"http://snomed.info/sct" :code #fhir/code"441510007"}}]
        (doseq [request (mapv (partial merge request) [{:url "http://snomed.info/sct"} {:id (sct-id node)}])]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 5
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"441510007"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"http://snomed.info/sct"
            [:parameter 3 :name] := #fhir/string"version"
            [:parameter 3 :value] := #fhir/string"http://snomed.info/sct/900000000000207008/version/20241001"
            [:parameter 4 :name] := #fhir/string"display"
            [:parameter 4 :value] := #fhir/string"Blood specimen with anticoagulant (specimen)"))))

    (testing "non-existing coding"
      (let [request {:coding #fhir/Coding{:system #fhir/uri"http://snomed.info/sct" :code #fhir/code"non-existing"}}]
        (doseq [request (mapv (partial merge request) [{:url "http://snomed.info/sct"} {:id (sct-id node)}])]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean false
            [:parameter 1 :name] := #fhir/string"message"
            [:parameter 1 :value] := #fhir/string"The provided code `non-existing` was not found in the code system with URL `http://snomed.info/sct`."))))

    (testing "coding with non-matching code system"
      (let [request {:coding #fhir/Coding{:system #fhir/uri"system-115433" :code #fhir/code"code-115438"}}]
        (doseq [request (mapv (partial merge request) [{:url "http://snomed.info/sct"} {:id (sct-id node)}])]
          (given @(ts/code-system-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean false
            [:parameter 1 :name] := #fhir/string"message"
            [:parameter 1 :value] := #fhir/string"The system of the provided coding `system-115433` does not match the code system with URL `http://snomed.info/sct`."))))))

(deftest code-system-validate-code-ucum-test
  (testing "with id or url"
    (with-system [{ts ::ts/local :blaze.db/keys [node]} ucum-config]
      (testing "existing code"
        (let [request {:code "s"}]
          (doseq [request (mapv (partial merge request) [{:url "http://unitsofmeasure.org"} {:id (ucum-id node)}])]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 4
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"s"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"http://unitsofmeasure.org"
              [:parameter 3 :name] := #fhir/string"version"
              [:parameter 3 :value] := #fhir/string"2013.10.21"))))

      (testing "non-existing code"
        (let [request {:code "non-existing"}]
          (doseq [request (mapv (partial merge request) [{:url "http://unitsofmeasure.org"} {:id (ucum-id node)}])]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 2
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean false
              [:parameter 1 :name] := #fhir/string"message"
              [:parameter 1 :value] := #fhir/string"The provided code `non-existing` was not found in the code system with URL `http://unitsofmeasure.org`."))))

      (testing "existing coding"
        (let [request {:coding #fhir/Coding{:system #fhir/uri"http://unitsofmeasure.org" :code #fhir/code"km"}}]
          (doseq [request (mapv (partial merge request) [{:url "http://unitsofmeasure.org"} {:id (ucum-id node)}])]
            (given @(ts/code-system-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 4
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"km"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"http://unitsofmeasure.org"
              [:parameter 3 :name] := #fhir/string"version"
              [:parameter 3 :value] := #fhir/string"2013.10.21"))))

      (testing "non-existing coding"
        (testing "with non-existing system"
          (let [request {:coding #fhir/Coding{:system #fhir/uri"system-170454" :code #fhir/code"code-115927"}}]
            (doseq [request (mapv (partial merge request) [{:url "http://unitsofmeasure.org"} {:id (ucum-id node)}])]
              (given @(ts/code-system-validate-code ts request)
                :fhir/type := :fhir/Parameters
                [:parameter count] := 2
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean false
                [:parameter 1 :name] := #fhir/string"message"
                [:parameter 1 :value] := #fhir/string"The system of the provided coding `system-170454` does not match the code system with URL `http://unitsofmeasure.org`."))))

        (testing "with non-existing code"
          (let [request {:coding #fhir/Coding{:system #fhir/uri"http://unitsofmeasure.org" :code #fhir/code"non-existing"}}]
            (doseq [request (mapv (partial merge request) [{:url "http://unitsofmeasure.org"} {:id (ucum-id node)}])]
              (given @(ts/code-system-validate-code ts request)
                :fhir/type := :fhir/Parameters
                [:parameter count] := 2
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean false
                [:parameter 1 :name] := #fhir/string"message"
                [:parameter 1 :value] := #fhir/string"The provided code `non-existing` was not found in the code system with URL `http://unitsofmeasure.org`."))))))))

(deftest expand-value-set-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "missing id or url"
      (given-failed-future (ts/expand-value-set ts {})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing ID or URL."))

    (testing "not found"
      (testing "id"
        (given-failed-future (ts/expand-value-set ts {:id "id-175736"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set with id `id-175736` was not found."
          :t := 0))

      (testing "url"
        (given-failed-future (ts/expand-value-set ts {:url "url-194718"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (ts/expand-value-set ts {:url "url-144258" :value-set-version "version-144244"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-144258` and version `version-144244` was not found."
          :t := 0))))

  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/ValueSet :id "id-180012"}]]
     [[:delete "ValueSet" "id-180012"]]]

    (testing "deleted value set not found"
      (given-failed-future (ts/expand-value-set ts {:id "id-180012"})
        ::anom/category := ::anom/not-found
        ::anom/message := "The value set with id `id-180012` was not found."
        :t := 2)))

  (testing "empty include"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-135750"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include}]}}]]]

      (given-failed-future (ts/expand-value-set ts {:url "value-set-135750"})
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

      (given-failed-future (ts/expand-value-set ts {:url "value-set-135750"})
        ::anom/category := ::anom/not-found
        ::anom/message := "Error while expanding the value set `value-set-135750`. The code system with URL `system-115910` was not found."
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

        (given-failed-future (ts/expand-value-set ts {:url "value-set-135750"})
          ::anom/category := ::anom/not-found
          ::anom/message := "Error while expanding the value set `value-set-135750`. The code system with URL `system-115910` and version `version-093818` was not found."
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

          (given-failed-future (ts/expand-value-set ts {:url "value-set-135750"})
            ::anom/category := ::anom/unsupported
            ::anom/message := "Error while expanding the value set `value-set-135750`. Expanding the code system with URL `system-115910` in all versions is unsupported."
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

      (given-failed-future (ts/expand-value-set ts {:url "value-set-161213"})
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

      (given-failed-future (ts/expand-value-set ts {:url "value-set-161213"})
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

      (given-failed-future (ts/expand-value-set ts {:url "value-set-161213"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-161213`. Can't expand the code system with URL `system-180814` because it is not complete. It's content is `example`."
        :http/status := 409
        :t := 1)

      (given-failed-future (ts/expand-value-set ts {:url "value-set-170447"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-170447`. Can't expand the code system with URL `system-180814` because it is not complete. It's content is `example`."
        :http/status := 409
        :t := 1)

      (given-failed-future (ts/expand-value-set ts {:url "value-set-170829"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-170829`. Can't expand the code system with URL `system-180814` because it is not complete. It's content is `example`."
        :http/status := 409
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

      (doseq [request [{:url "value-set-135750"}
                       {:url "value-set-135750" :value-set-version "version-143955"}
                       {:id "id-144002"}]]
        (given @(ts/expand-value-set ts request)
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

        (doseq [request [{:url "value-set-135750"} {:id "0"}]]
          (given @(ts/expand-value-set ts request)
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
            [:expansion :contains 0 #(contains? % :display)] := false)))

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

            (doseq [request [{:url "value-set-135750"} {:id "0"}]]
              (given @(ts/expand-value-set ts request)
                :fhir/type := :fhir/ValueSet
                [:expansion :parameter count] := 1
                [:expansion :parameter 0 :name] := #fhir/string"version"
                [:expansion :parameter 0 :value] := #fhir/uri"system-115910|2.0.0"
                [:expansion :contains count] := 1
                [:expansion :contains 0 :system] := #fhir/uri"system-115910"
                [:expansion :contains 0 :code] := #fhir/code"code-092722"
                [:expansion :contains 0 #(contains? % :display)] := false))))

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

            (doseq [request [{:url "value-set-135750"} {:id "0"}]]
              (given @(ts/expand-value-set ts request)
                :fhir/type := :fhir/ValueSet
                [:expansion :parameter count] := 1
                [:expansion :parameter 0 :name] := #fhir/string"version"
                [:expansion :parameter 0 :value] := #fhir/uri"system-115910|3.0.0"
                [:expansion :contains count] := 1
                [:expansion :contains 0 :system] := #fhir/uri"system-115910"
                [:expansion :contains 0 :code] := #fhir/code"code-115357"
                [:expansion :contains 0 #(contains? % :display)] := false))))

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

            (doseq [request [{:url "value-set-135750"} {:id "0"}]]
              (given @(ts/expand-value-set ts (assoc request :system-version [#fhir/canonical"system-115910|2.0.0"]))
                :fhir/type := :fhir/ValueSet
                [:expansion :parameter count] := 1
                [:expansion :parameter 0 :name] := #fhir/string"version"
                [:expansion :parameter 0 :value] := #fhir/uri"system-115910|2.0.0"
                [:expansion :contains count] := 1
                [:expansion :contains 0 :system] := #fhir/uri"system-115910"
                [:expansion :contains 0 :code] := #fhir/code"code-092722"
                [:expansion :contains 0 #(contains? % :display)] := false))))))

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

        (given @(ts/expand-value-set ts {:url "value-set-135750"})
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

          (given @(ts/expand-value-set ts {:url "value-set-135750"})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-163444"
            [:expansion :contains 0 #(contains? % :display)] := false)))

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

          (given @(ts/expand-value-set ts {:url "value-set-135750"})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"system-115910"
            [:expansion :contains 0 :code] := #fhir/code"code-115927"
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

        (given @(ts/expand-value-set ts {:url "value-set-135750"})
          :fhir/type := :fhir/ValueSet
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

        (given @(ts/expand-value-set ts {:url "value-set-135750"})
          :fhir/type := :fhir/ValueSet
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

          (given @(ts/expand-value-set ts {:url "value-set-135750"})
            :fhir/type := :fhir/ValueSet
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

      (given @(ts/expand-value-set ts {:url "value-set-154043"
                                       :value-set-version "version-135747"})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-135810"
        [:expansion :contains 0 :code] := #fhir/code"code-135827")))

  (testing "with inactive concepts"
    (with-system-data [{ts ::ts/local} sct-config]
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
                 :code #fhir/code"code-164637"}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-163445"
                 :display #fhir/string"display-164521"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-170702"}]}}]]]

      (given @(ts/expand-value-set ts {:id "0"})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 3
        [:expansion :contains 0 :system] := #fhir/uri"system-170702"
        [:expansion :contains 0 :inactive] := #fhir/boolean true
        [:expansion :contains 0 :code] := #fhir/code"code-170118"
        [:expansion :contains 1 :system] := #fhir/uri"system-170702"
        [:expansion :contains 1 :code] := #fhir/code"code-164637"
        [:expansion :contains 2 :system] := #fhir/uri"system-170702"
        [:expansion :contains 2 :code] := #fhir/code"code-163445"
        [:expansion :contains 2 :display] := #fhir/string"display-164521")

      (testing "active only"
        (given @(ts/expand-value-set ts {:id "0" :active-only true})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-170702"
          [:expansion :contains 0 :code] := #fhir/code"code-164637"
          [:expansion :contains 1 :system] := #fhir/uri"system-170702"
          [:expansion :contains 1 :code] := #fhir/code"code-163445")))

    (testing "including all codes"
      (with-system-data [{ts ::ts/local} sct-config]
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
                   :code #fhir/code"code-164637"}
                  {:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code"code-163445"
                   :display #fhir/string"display-164521"}]}]
          [:put {:fhir/type :fhir/ValueSet :id "0"
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
                      :display #fhir/string"display-165751"}]}]}}]]]

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 3
          [:expansion :contains 0 :system] := #fhir/uri"system-170702"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"code-170118"
          [:expansion :contains 1 :system] := #fhir/uri"system-170702"
          [:expansion :contains 1 :code] := #fhir/code"code-164637"
          [:expansion :contains 2 :system] := #fhir/uri"system-170702"
          [:expansion :contains 2 :code] := #fhir/code"code-163445"
          [:expansion :contains 2 :display] := #fhir/string"display-165751")

        (testing "active only"
          (given @(ts/expand-value-set ts {:id "0" :active-only true})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 2
            [:expansion :contains 0 :system] := #fhir/uri"system-170702"
            [:expansion :contains 0 :code] := #fhir/code"code-164637"
            [:expansion :contains 1 :system] := #fhir/uri"system-170702"
            [:expansion :contains 1 :code] := #fhir/code"code-163445"
            [:expansion :contains 1 :display] := #fhir/string"display-165751"))))))

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

      (given @(ts/expand-value-set ts {:url "value-set-161213"})
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

        (given @(ts/expand-value-set ts {:url "value-set-161213"})
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

      (given @(ts/expand-value-set ts {:url "value-set-162456"})
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

      (given @(ts/expand-value-set ts {:url "value-set-162456"})
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

      (given-failed-future (ts/expand-value-set ts {:url "value-set-160118"})
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

      (doseq [request [{:url "value-set-182905"} {:id "0"}]]
        (given @(ts/expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-182832"
          [:expansion :contains 0 :display] := #fhir/string"display-182717"))))

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

      (doseq [request [{:url "value-set-182905"} {:id "0"}]]
        (given (sort-expansion @(ts/expand-value-set ts request))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-182832"
          [:expansion :contains 0 :display] := #fhir/string"display-182717"
          [:expansion :contains 1 :system] := #fhir/uri"system-182822"
          [:expansion :contains 1 :code] := #fhir/code"code-191445"
          [:expansion :contains 1 :display] := #fhir/string"display-191448"))))

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

      (doseq [request [{:url "value-set-182905"} {:id "0"}]]
        (given (sort-expansion @(ts/expand-value-set ts request))
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
          [:expansion :contains 2 :display] := #fhir/string"display-192313")))

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

        (doseq [request [{:url "value-set-182905"} {:id "0"}]]
          (given (sort-expansion @(ts/expand-value-set ts request))
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
            [:expansion :contains 2 :display] := #fhir/string"display-192313"))))))

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

      (doseq [request [{:url "value-set-182905"} {:id "0"}]]
        (given @(ts/expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 0))))

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

      (doseq [request [{:url "value-set-182905"} {:id "0"}]]
        (given (sort-expansion @(ts/expand-value-set ts request))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-191445"
          [:expansion :contains 0 :display] := #fhir/string"display-191448"))))

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

      (doseq [request [{:url "value-set-182905"} {:id "0"}]]
        (given (sort-expansion @(ts/expand-value-set ts request))
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 2
          [:expansion :contains 0 :system] := #fhir/uri"system-182822"
          [:expansion :contains 0 :code] := #fhir/code"code-191445"
          [:expansion :contains 0 :display] := #fhir/string"display-191448"
          [:expansion :contains 1 :system] := #fhir/uri"system-182822"
          [:expansion :contains 1 :code] := #fhir/code"code-192308"
          [:expansion :contains 1 :display] := #fhir/string"display-192313")))))

(deftest expand-value-set-include-filter-exists-test
  (testing "with missing property"
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
                    :op #fhir/code"exists"
                    :value #fhir/string"true"}]}]}}]]]

      (given-failed-future (ts/expand-value-set ts {:url "value-set-182905"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-182905`. Missing filter property.")))

  (testing "with invalid value"
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
                    :value #fhir/string"invalid-162128"}]}]}}]]]

      (given-failed-future (ts/expand-value-set ts {:url "value-set-182905"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-182905`. The filter value should be one of `true` or `false` but was `invalid-162128`.")))

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

          (doseq [request [{:url "value-set-182905"} {:id "0"}]]
            (given @(ts/expand-value-set ts request)
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-182822"
              [:expansion :contains 0 :code] := #fhir/code"code-182832"
              [:expansion :contains 0 :display] := #fhir/string"display-182717"))))

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

          (doseq [request [{:url "value-set-182905"} {:id "0"}]]
            (given @(ts/expand-value-set ts request)
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 0)))))

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

          (doseq [request [{:url "value-set-182905"} {:id "0"}]]
            (given @(ts/expand-value-set ts request)
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 0))))

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

          (doseq [request [{:url "value-set-182905"} {:id "0"}]]
            (given @(ts/expand-value-set ts request)
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-182822"
              [:expansion :contains 0 :code] := #fhir/code"code-182832"
              [:expansion :contains 0 :display] := #fhir/string"display-182717")))))))

(deftest expand-value-set-include-filter-equals-test
  (testing "with missing property"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-171904"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :op #fhir/code"="
                    :value #fhir/string"value-161324"}]}]}}]]]

      (given-failed-future (ts/expand-value-set ts {:url "value-set-171904"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-171904`. Missing filter property.")))

  (testing "with missing value"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-182822"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-182832"
                 :display #fhir/string"display-182717"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "0"
               :url #fhir/uri"value-set-171904"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-182822"
                  :filter
                  [{:fhir/type :fhir.ValueSet.compose.include/filter
                    :property #fhir/code"property-175506"
                    :op #fhir/code"="}]}]}}]]]

      (given-failed-future (ts/expand-value-set ts {:url "value-set-171904"})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while expanding the value set `value-set-171904`. Missing filter value.")))

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

    (doseq [request [{:url "value-set-175628"} {:id "0"}]]
      (given @(ts/expand-value-set ts request)
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"system-182822"
        [:expansion :contains 0 :code] := #fhir/code"code-175652"
        [:expansion :contains 0 :display] := #fhir/string"display-175659"))))

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

          (doseq [request [{:url "value-set-182905"} {:id "0"}]]
            (given @(ts/expand-value-set ts request)
              :fhir/type := :fhir/ValueSet
              [:expansion :contains count] := 1
              [:expansion :contains 0 :system] := #fhir/uri"system-182822"
              [:expansion :contains 0 :code] := #fhir/code"code-191445"
              [:expansion :contains 0 :display] := #fhir/string"display-191448")))))))

(deftest expand-value-set-provided-value-set-test
  (testing "fails on non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"not-present"}]]]

      (given-failed-future (ts/expand-value-set
                            ts
                            {:value-set
                             {:fhir/type :fhir/ValueSet
                              :compose
                              {:fhir/type :fhir.ValueSet/compose
                               :include
                               [{:fhir/type :fhir.ValueSet.compose/include
                                 :system #fhir/uri"system-115910"}]}}})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the provided value set. Can't expand the code system with URL `system-115910` because it is not complete. It's content is `not-present`."
        :t := 1)))

  (with-system-data [{ts ::ts/local} config]
    [[[:put {:fhir/type :fhir/CodeSystem :id "0"
             :url #fhir/uri"system-115910"
             :content #fhir/code"complete"
             :concept
             [{:fhir/type :fhir.CodeSystem/concept
               :code #fhir/code"code-115927"}]}]]]

    (given @(ts/expand-value-set
             ts
             {:value-set
              {:fhir/type :fhir/ValueSet
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}]}}})
      :fhir/type := :fhir/ValueSet
      [:expansion :contains count] := 1
      [:expansion :contains 0 :system] := #fhir/uri"system-115910"
      [:expansion :contains 0 :code] := #fhir/code"code-115927"
      [:expansion :contains 0 #(contains? % :display)] := false)))

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

      (given-failed-future (ts/expand-value-set ts {:id "0"})
        ::anom/category := ::anom/conflict
        ::anom/message := "Error while expanding the value set `system-182137`. Expanding all Snomed CT concepts is too costly."
        :fhir/issue "too-costly"))))

(deftest expand-value-set-sct-include-concept-test
  (testing "include one concept"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"http://snomed.info/sct"
                  :concept
                  [{:fhir/type :fhir.ValueSet.compose.include/concept
                    :code #fhir/code"441510007"}]}]}}]]]

      (given @(ts/expand-value-set ts {:id "0"})
        :fhir/type := :fhir/ValueSet
        [:expansion :contains count] := 1
        [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
        [:expansion :contains 0 #(contains? % :inactive)] := false
        [:expansion :contains 0 :code] := #fhir/code"441510007"
        [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"860958002"}
                     {:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(ts/expand-value-set ts {:id "0"})
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

        (testing "active only"
          (given @(ts/expand-value-set ts {:id "0" :active-only true})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 1
            [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
            [:expansion :contains 0 #(contains? % :inactive)] := false
            [:expansion :contains 0 :code] := #fhir/code"441510007"
            [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))))

    (testing "with version (module)"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :version #fhir/string"http://snomed.info/sct/900000000000207008"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"441510007"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

      (testing "german module"
        (with-system-data [{ts ::ts/local} sct-config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"http://snomed.info/sct"
                      :version #fhir/string"http://snomed.info/sct/11000274103"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"441510007"}]}]}}]]]

          (given @(ts/expand-value-set ts {:id "0"})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))

    (testing "with version"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :version #fhir/string"http://snomed.info/sct/900000000000207008/version/20231201"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"441510007"}]}]}}]]]

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 #(contains? % :inactive)] := false
          [:expansion :contains 0 :code] := #fhir/code"441510007"
          [:expansion :contains 0 :display] := #fhir/string"Blood specimen with anticoagulant (specimen)"))

      (testing "non-existing version"
        (with-system-data [{ts ::ts/local} sct-config]
          [[[:put {:fhir/type :fhir/ValueSet :id "0"
                   :compose
                   {:fhir/type :fhir.ValueSet/compose
                    :include
                    [{:fhir/type :fhir.ValueSet.compose/include
                      :system #fhir/uri"http://snomed.info/sct"
                      :version #fhir/string"http://snomed.info/sct/900000000000207008/version/none-existing"
                      :concept
                      [{:fhir/type :fhir.ValueSet.compose.include/concept
                        :code #fhir/code"441510007"}]}]}}]]]

          (given-failed-future (ts/expand-value-set ts {:id "0"})
            ::anom/category := ::anom/not-found
            ::anom/message := "Error while expanding the provided value set. The code system with URL `http://snomed.info/sct` and version `http://snomed.info/sct/900000000000207008/version/none-existing` was not found."))))))

(deftest expand-value-set-sct-filter-test
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

      (given-failed-future (ts/expand-value-set ts {:url "value-set-120544"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set `value-set-120544`. Unsupported filter operator `op-unknown-120524` in code system `http://snomed.info/sct`."))))

(deftest expand-value-set-sct-filter-is-a-test
  (testing "with a single is-a filter"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
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

      (doseq [request [{} {:system-version [#fhir/canonical"http://snomed.info/sct|http://snomed.info/sct/900000000000207008"]}]]
        (given @(ts/expand-value-set ts (assoc request :id "0"))
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

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1812
          [:expansion :contains 0 :code] := #fhir/code"396807009"
          [:expansion :contains 1 :code] := #fhir/code"433881000124103")))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
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

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"860958002"
          [:expansion :contains 0 :display] := #fhir/string"Temperature of blood (observable entity)")

        (testing "active only"
          (given @(ts/expand-value-set ts {:id "0" :active-only true})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))))

(deftest expand-value-set-sct-filter-descendent-of-test
  (testing "with a single descendent-of filter"
    (with-system-data [{ts ::ts/local} sct-config]
      [[[:put {:fhir/type :fhir/ValueSet :id "0"
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

      (doseq [request [{} {:system-version [#fhir/canonical"http://snomed.info/sct|http://snomed.info/sct/900000000000207008"]}]]
        (given @(ts/expand-value-set ts (assoc request :id "0"))
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

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1811
          [:expansion :contains 0 :code] := #fhir/code"396807009"
          [:expansion :contains 1 :code] := #fhir/code"433881000124103")))

    (testing "with inactive concepts"
      (with-system-data [{ts ::ts/local} sct-config]
        [[[:put {:fhir/type :fhir/ValueSet :id "0"
                 :compose
                 {:fhir/type :fhir.ValueSet/compose
                  :include
                  [{:fhir/type :fhir.ValueSet.compose/include
                    :system #fhir/uri"http://snomed.info/sct"
                    :concept
                    [{:fhir/type :fhir.ValueSet.compose.include/concept
                      :code #fhir/code"860958002"}]}]}}]]]

        (given @(ts/expand-value-set ts {:id "0"})
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"http://snomed.info/sct"
          [:expansion :contains 0 :inactive] := #fhir/boolean true
          [:expansion :contains 0 :code] := #fhir/code"860958002"
          [:expansion :contains 0 :display] := #fhir/string"Temperature of blood (observable entity)")

        (testing "active only"
          (given @(ts/expand-value-set ts {:id "0" :active-only true})
            :fhir/type := :fhir/ValueSet
            [:expansion :contains count] := 0))))))

(deftest expand-value-set-ucum-test
  (with-system-data [{ts ::ts/local} ucum-config]
    [[[:put {:fhir/type :fhir/ValueSet :id "0"
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

    (given @(ts/expand-value-set ts {:id "0"})
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

      (doseq [request [{:url "value-set-135750"} {:id "0"}]]
        (given @(ts/expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"
          [:expansion :contains 0 :display] := #fhir/string"display-182508")))

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

        (given @(ts/expand-value-set ts {:url "value-set-135750"})
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

      (given @(ts/expand-value-set ts {:url "value-set-135750"})
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

      (doseq [request [{:url "value-set-135750"} {:id "0"}]]
        (given @(ts/expand-value-set ts request)
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
          [:expansion :contains 0 :code] := #fhir/code"code-115927"))))

  (testing "retains definition"
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

      (given @(ts/expand-value-set ts {:url "value-set-135750"
                                       :include-definition true})
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

      (doseq [request [{:url "value-set-135750"} {:id "0"}]]
        (given @(ts/expand-value-set ts request)
          :fhir/type := :fhir/ValueSet
          :url := #fhir/uri"value-set-135750"
          :version := #fhir/string"version-132003"
          :status := #fhir/code"active"
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927"))))

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

        (given @(ts/expand-value-set ts {:url "value-set-135750" :count 0})
          :fhir/type := :fhir/ValueSet
          [:expansion :parameter 0 :name] := #fhir/string "count"
          [:expansion :parameter 0 :value] := #fhir/integer 0
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

        (given @(ts/expand-value-set ts {:url "value-set-135750" :count 1})
          :fhir/type := :fhir/ValueSet
          [:expansion :parameter 0 :name] := #fhir/string "count"
          [:expansion :parameter 0 :value] := #fhir/integer 1
          [:expansion :total] := #fhir/integer 2
          [:expansion :contains count] := 1
          [:expansion :contains 0 :system] := #fhir/uri"system-115910"
          [:expansion :contains 0 :code] := #fhir/code"code-115927")))))

(deftest value-set-validate-code-fails-test
  (with-system [{ts ::ts/local} config]
    (testing "missing id or url"
      (given-failed-future (ts/value-set-validate-code ts {})
        ::anom/category := ::anom/incorrect
        ::anom/message := "Missing ID or URL."))

    (testing "not found"
      (testing "id"
        (given-failed-future (ts/value-set-validate-code ts {:id "id-175736"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set with id `id-175736` was not found."
          :t := 0))

      (testing "url"
        (given-failed-future (ts/value-set-validate-code ts {:url "url-194718"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (ts/value-set-validate-code ts {:url "url-144258" :value-set-version "version-144244"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set `url-144258` and version `version-144244` was not found."
          :t := 0)))))

(deftest value-set-validate-code-test
  (testing "with id or url"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri"system-202449"
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
                  :system #fhir/uri"system-115910"}]}}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-203901"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-115910"}
                 {:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-202449"}]}}]]]

      (testing "existing code"
        (let [request {:code "code-115927" :system "system-115910"}]
          (doseq [request (mapv (partial merge request) [{:url "value-set-135750"} {:id "0"}])]
            (given @(ts/value-set-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"code-115927"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"system-115910")))

        (testing "with infer-system"
          (let [request {:code "code-115927" :infer-system true}]
            (doseq [request (mapv (partial merge request) [{:url "value-set-135750"} {:id "0"}])]
              (given @(ts/value-set-validate-code ts request)
                :fhir/type := :fhir/Parameters
                [:parameter count] := 3
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean true
                [:parameter 1 :name] := #fhir/string"code"
                [:parameter 1 :value] := #fhir/code"code-115927"
                [:parameter 2 :name] := #fhir/string"system"
                [:parameter 2 :value] := #fhir/uri"system-115910")))

          (testing "with non-unique code"
            (let [request {:code "code-115927" :infer-system true}]
              (doseq [request (mapv (partial merge request) [{:url "value-set-203901"} {:id "1"}])]
                (given @(ts/value-set-validate-code ts request)
                  :fhir/type := :fhir/Parameters
                  [:parameter count] := 2
                  [:parameter 0 :name] := #fhir/string"result"
                  [:parameter 0 :value] := #fhir/boolean false
                  [:parameter 1 :name] := #fhir/string"message"
                  [:parameter 1 :value] := #fhir/string"While inferring the system was requested, the provided code `code-115927` was not unique in the value set `value-set-203901`."))))))

      (testing "non-existing code"
        (let [request {:code "code-153948" :system "system-115910"}]
          (doseq [request (mapv (partial merge request) [{:url "value-set-135750"} {:id "0"}])]
            (given @(ts/value-set-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 2
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean false
              [:parameter 1 :name] := #fhir/string"message"
              [:parameter 1 :value] := #fhir/string"The provided code `code-153948` of system `system-115910` was not found in the value set `value-set-135750`."))))

      (testing "existing coding"
        (let [request {:coding #fhir/Coding{:system #fhir/uri"system-115910" :code #fhir/code"code-115927"}}]
          (doseq [request (mapv (partial merge request) [{:url "value-set-135750"} {:id "0"}])]
            (given @(ts/value-set-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"code-115927"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"system-115910"))))

      (testing "non-existing coding"
        (let [request {:coding #fhir/Coding{:system #fhir/uri"system-115910" :code #fhir/code"code-153948"}}]
          (doseq [request (mapv (partial merge request) [{:url "value-set-135750"} {:id "0"}])]
            (given @(ts/value-set-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 2
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean false
              [:parameter 1 :name] := #fhir/string"message"
              [:parameter 1 :value] := #fhir/string"The provided code `code-153948` of system `system-115910` was not found in the value set `value-set-135750`."))))))

  (testing "with value-set"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-115910"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]
        [:put {:fhir/type :fhir/CodeSystem :id "1"
               :url #fhir/uri"system-202449"
               :content #fhir/code"complete"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-115927"}]}]]]

      (testing "existing code"
        (let [request
              {:value-set
               {:fhir/type :fhir/ValueSet
                :compose
                {:fhir/type :fhir.ValueSet/compose
                 :include
                 [{:fhir/type :fhir.ValueSet.compose/include
                   :system #fhir/uri"system-115910"}]}}
               :code "code-115927"
               :system "system-115910"}]
          (given @(ts/value-set-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"code-115927"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"system-115910"))

        (testing "with infer-system"
          (let [request
                {:value-set
                 {:fhir/type :fhir/ValueSet
                  :compose
                  {:fhir/type :fhir.ValueSet/compose
                   :include
                   [{:fhir/type :fhir.ValueSet.compose/include
                     :system #fhir/uri"system-115910"}]}}
                 :code "code-115927"
                 :infer-system true}]
            (given @(ts/value-set-validate-code ts request)
              :fhir/type := :fhir/Parameters
              [:parameter count] := 3
              [:parameter 0 :name] := #fhir/string"result"
              [:parameter 0 :value] := #fhir/boolean true
              [:parameter 1 :name] := #fhir/string"code"
              [:parameter 1 :value] := #fhir/code"code-115927"
              [:parameter 2 :name] := #fhir/string"system"
              [:parameter 2 :value] := #fhir/uri"system-115910"))

          (testing "with non-unique code"
            (let [request
                  {:value-set
                   {:fhir/type :fhir/ValueSet
                    :compose
                    {:fhir/type :fhir.ValueSet/compose
                     :include
                     [{:fhir/type :fhir.ValueSet.compose/include
                       :system #fhir/uri"system-115910"}
                      {:fhir/type :fhir.ValueSet.compose/include
                       :system #fhir/uri"system-202449"}]}}
                   :code "code-115927"
                   :infer-system true}]
              (given @(ts/value-set-validate-code ts request)
                :fhir/type := :fhir/Parameters
                [:parameter count] := 2
                [:parameter 0 :name] := #fhir/string"result"
                [:parameter 0 :value] := #fhir/boolean false
                [:parameter 1 :name] := #fhir/string"message"
                [:parameter 1 :value] := #fhir/string"While inferring the system was requested, the provided code `code-115927` was not unique in the provided value set.")))))

      (testing "existing coding"
        (let [request
              {:value-set
               {:fhir/type :fhir/ValueSet
                :compose
                {:fhir/type :fhir.ValueSet/compose
                 :include
                 [{:fhir/type :fhir.ValueSet.compose/include
                   :system #fhir/uri"system-115910"}]}}
               :coding #fhir/Coding{:system #fhir/uri"system-115910"
                                    :code #fhir/code"code-115927"}}]
          (given @(ts/value-set-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 3
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean true
            [:parameter 1 :name] := #fhir/string"code"
            [:parameter 1 :value] := #fhir/code"code-115927"
            [:parameter 2 :name] := #fhir/string"system"
            [:parameter 2 :value] := #fhir/uri"system-115910")))

      (testing "non-existing code"
        (let [request
              {:value-set
               {:fhir/type :fhir/ValueSet
                :compose
                {:fhir/type :fhir.ValueSet/compose
                 :include
                 [{:fhir/type :fhir.ValueSet.compose/include
                   :system #fhir/uri"system-115910"}]}}
               :system "system-115910"
               :code "code-153948"}]
          (given @(ts/value-set-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean false
            [:parameter 1 :name] := #fhir/string"message"
            [:parameter 1 :value] := #fhir/string"The provided code `code-153948` of system `system-115910` was not found in the provided value set.")))

      (testing "non-existing coding"
        (let [request
              {:value-set
               {:fhir/type :fhir/ValueSet
                :compose
                {:fhir/type :fhir.ValueSet/compose
                 :include
                 [{:fhir/type :fhir.ValueSet.compose/include
                   :system #fhir/uri"system-115910"}]}}
               :coding #fhir/Coding{:system #fhir/uri"system-115910"
                                    :code #fhir/code"code-153948"}}]
          (given @(ts/value-set-validate-code ts request)
            :fhir/type := :fhir/Parameters
            [:parameter count] := 2
            [:parameter 0 :name] := #fhir/string"result"
            [:parameter 0 :value] := #fhir/boolean false
            [:parameter 1 :name] := #fhir/string"message"
            [:parameter 1 :value] := #fhir/string"The provided code `code-153948` of system `system-115910` was not found in the provided value set."))))))
