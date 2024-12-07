(ns blaze.terminology-service.local-test
  (:require
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.node :refer [node?]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local]
   [blaze.terminology-service.local.concept-spec]
   [blaze.terminology-service.local.filter-spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
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
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid node"
    (given-thrown (ig/init {::ts/local {:node ::invalid}})
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 1 :pred] := `node?
      [:cause-data ::s/problems 1 :val] := ::invalid))

  (testing "invalid clock"
    (given-thrown (ig/init {::ts/local {:clock ::invalid}})
      :key := ::ts/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :via] := [:blaze/clock]
      [:cause-data ::s/problems 1 :pred] := `time/clock?
      [:cause-data ::s/problems 1 :val] := ::invalid)))

(def config
  (assoc
   mem-node-config
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)}
   :blaze.test/fixed-clock {}))

(defn- uuid-urn? [s]
  (some? (re-matches #"urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" s)))

(deftest terminology-service-test-fails
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
          ::anom/message := "The value set with URL `url-194718` was not found."
          :t := 0))

      (testing "url and version"
        (given-failed-future (ts/expand-value-set ts {:url "url-144258" :value-set-version "version-144244"})
          ::anom/category := ::anom/not-found
          ::anom/message := "The value set with URL `url-144258` and version `version-144244` was not found."
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
        ::anom/message := "Error while expanding the value set with URL `value-set-135750`. Missing system or valueSet."
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
        ::anom/message := "Error while expanding the value set with URL `value-set-135750`. The code system with URL `system-115910` was not found."
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
          ::anom/message := "Error while expanding the value set with URL `value-set-135750`. The code system with URL `system-115910` and version `version-093818` was not found."
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
            ::anom/message := "Error while expanding the value set with URL `value-set-135750`. Expanding the code system with URL `system-115910` in all versions is unsupported."
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
        ::anom/message := "Error while expanding the value set with URL `value-set-161213`. Incorrect combination of system and valueSet."
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
        ::anom/message := "Error while expanding the value set with URL `value-set-161213`. Incorrect combination of concept and filter."
        :t := 1)))

  (testing "fails on non-complete code system"
    (with-system-data [{ts ::ts/local} config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "0"
               :url #fhir/uri"system-180814"
               :content #fhir/code"example"
               :concept
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code"code-180828"}]}]
        [:put {:fhir/type :fhir/ValueSet :id "1"
               :url #fhir/uri"value-set-161213"
               :compose
               {:fhir/type :fhir.ValueSet/compose
                :include
                [{:fhir/type :fhir.ValueSet.compose/include
                  :system #fhir/uri"system-180814"}]}}]]]

      (given-failed-future (ts/expand-value-set ts {:url "value-set-161213"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Error while expanding the value set with URL `value-set-161213`. Can't expand the code system with URL `system-180814` because it is not complete. It's content is `example`."
        :http/status := 409
        :t := 1))))

(deftest terminology-service-test-existing-expansion
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

(deftest terminology-service-test-include-concept
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

      (testing "with version"
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
        [:expansion :contains 0 :code] := #fhir/code"code-135827"))))

(deftest terminology-service-test-value-set-refs
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

(deftest terminology-service-test-include-filter
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
        ::anom/message := "Error while expanding the value set with URL `value-set-160118`. The filter operation `op-unknown-160011` is not supported."))))

(defn- sort-expansion [value-set]
  (update-in value-set [:expansion :contains] (partial sort-by (comp type/value :code))))

(deftest terminology-service-test-include-filter-is-a
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

(deftest terminology-service-test-include-filter-exists
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
        ::anom/message := "Error while expanding the value set with URL `value-set-182905`. Missing filter property.")))

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
        ::anom/message := "Error while expanding the value set with URL `value-set-182905`. The filter value should be one of `true` or `false` but was `invalid-162128`.")))

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

(deftest terminology-service-test-include-filter-multiple
  (testing "is-a and exists (and the other way around"
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

(deftest terminology-service-test-other
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
