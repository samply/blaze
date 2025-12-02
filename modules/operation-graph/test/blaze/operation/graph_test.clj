(ns blaze.operation.graph-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.util-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.operation.graph :as graph]
   [blaze.operation.graph.compiler-spec]
   [blaze.operation.graph.test-util :as g-tu]
   [blaze.page-id-cipher.spec]
   [blaze.test-util :as tu]
   [blaze.util.clauses-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.operation/graph
   {:compiled-graph-cache (ig/ref ::graph/compiled-graph-cache)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::graph/compiled-graph-cache {}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/page-id-cipher {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.operation/graph nil}
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.operation/graph {}}
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :compiled-graph-cache))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid graph-cache"
    (given-failed-system (assoc-in config [:blaze.operation/graph :compiled-graph-cache] ::invalid)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::graph/compiled-graph-cache]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.operation/graph :clock] ::invalid)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `time/clock?
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.operation/graph :rng-fn] ::invalid)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `fn?
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-failed-system (assoc-in config [:blaze.operation/graph :page-id-cipher] ::invalid)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def router
  (reitit/router
   (into
    [["/Patient/{id}/__graph-page/{page-id}" {:name :Patient.operation/graph-page}]]
    (map (fn [type] [(str "/" type) {:name (keyword type "type")}]))
    ["Patient" "Condition" "Observation" "Encounter"])
   {:syntax :bracket
    :path context-path}))

(def match
  (reitit/map->Match
   {:data {:fhir.resource/type "Patient"}
    :path (str context-path "/Patient/0/$graph")}))

(def page-match
  (reitit/map->Match
   {:path (str context-path "/Patient/0/__graph-page")}))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router
            ::reitit/match match))))

(defn- wrap-db [handler node page-id-cipher]
  (fn [{::reitit/keys [match] :as request}]
    (if (= page-match match)
      ((decrypt-page-id/wrap-decrypt-page-id
        (db/wrap-snapshot-db handler node 100)
        page-id-cipher)
       request)
      ((db/wrap-db handler node 100) request))))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding & [node-binding page-id-cipher-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         page-id-cipher# :blaze.test/page-id-cipher
                         handler# :blaze.operation/graph} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults
                                  (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(deftest handler-test
  (testing "Patient not found"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "145633"}
                       :query-params {"graph" "151647"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/145633` was not found."))))

  (testing "Patient deleted"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]]
       [[:delete "Patient" "145711"]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "151647"}})]

        (is (= 410 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "deleted"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/145711` was deleted."))))

  (testing "GraphDefinition not-found"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "151647"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "The graph definition `151647` was not found."))))

  (testing "only returning the patient itself"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                  #fhir/Extension{:url "type" :value #fhir/code "Patient"}])]
               :url #fhir/uri "151647"
               :name #fhir/string "patient-only"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "151647"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/145711")
                 (-> first-entry :fullUrl :value))))

        (testing "the entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "145711"))

        (testing "the entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "returning the patient and one observation"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/Observation :id "144115"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}}]
        [:put {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                  #fhir/Extension{:url "type" :value #fhir/code "Patient"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "observation"}
                  #fhir/Extension{:url "type" :value #fhir/code "Observation"}])]
               :url #fhir/uri "144200"
               :name #fhir/string "patient-observation"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "patient")
                  (g-tu/extension-link-target-id :value #fhir/id "observation")
                  (g-tu/extension-link-params :value #fhir/string "patient={ref}")]}]}]]]

      (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/145711")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/144115")
                 (-> second-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "145711"))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "144115"))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "returning the patient with one observation and one encounter"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/Observation :id "134129"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/134144"}}]
        [:put {:fhir/type :fhir/Encounter :id "134144"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}}]
        [:put {:fhir/type :fhir/Encounter :id "other-144453"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}}]
        [:put {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                  #fhir/Extension{:url "type" :value #fhir/code "Patient"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "observation"}
                  #fhir/Extension{:url "type" :value #fhir/code "Observation"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "encounter"}
                  #fhir/Extension{:url "type" :value #fhir/code "Encounter"}])]
               :url #fhir/uri "144200"
               :name #fhir/string "patient-observation-encounter"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "patient")
                  (g-tu/extension-link-target-id :value #fhir/id "observation")
                  (g-tu/extension-link-params :value #fhir/string "patient={ref}")]}
                {:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "observation")
                  (g-tu/extension-link-target-id :value #fhir/id "encounter")]
                 :path #fhir/string "encounter"}]}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry] :entry :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 3"
          (is (= #fhir/unsignedInt 3 (:total body))))

        (testing "the bundle contains three entries"
          (is (= 3 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/145711")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/134129")
                 (-> second-entry :fullUrl :value))))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Encounter/134144")
                 (-> third-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "145711"))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "134129"))

        (testing "the third entry has the right resource"
          (given (:resource third-entry)
            :fhir/type := :fhir/Encounter
            :id := "134144"))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "returning the patient with two observations and only one encounter"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/Observation :id "134129"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/134144"}}]
        [:put {:fhir/type :fhir/Observation :id "184545"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/134144"}}]
        [:put {:fhir/type :fhir/Encounter :id "134144"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}}]
        [:put {:fhir/type :fhir/Encounter :id "other-144453"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}}]
        [:put {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                  #fhir/Extension{:url "type" :value #fhir/code "Patient"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "observation"}
                  #fhir/Extension{:url "type" :value #fhir/code "Observation"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "encounter"}
                  #fhir/Extension{:url "type" :value #fhir/code "Encounter"}])]
               :url #fhir/uri "144200"
               :name #fhir/string "patient-observation-encounter"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "patient")
                  (g-tu/extension-link-target-id :value #fhir/id "observation")
                  (g-tu/extension-link-params :value #fhir/string "patient={ref}")]}
                {:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "observation")
                  (g-tu/extension-link-target-id :value #fhir/id "encounter")]
                 :path #fhir/string "encounter"}]}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry fourth-entry] :entry
              :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 4"
          (is (= #fhir/unsignedInt 4 (:total body))))

        (testing "the bundle contains four entries"
          (is (= 4 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/145711")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/134129")
                 (-> second-entry :fullUrl :value))))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Encounter/134144")
                 (-> third-entry :fullUrl :value))))

        (testing "the fourth entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/184545")
                 (-> fourth-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "145711"))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "134129"))

        (testing "the third entry has the right resource"
          (given (:resource third-entry)
            :fhir/type := :fhir/Encounter
            :id := "134144"))

        (testing "the fourth entry has the right resource"
          (given (:resource fourth-entry)
            :fhir/type := :fhir/Observation
            :id := "184545"))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the fourth entry has the right search mode"
          (given (:search fourth-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "circle between condition and encounter is not a problem"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/Condition :id "191241"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/134144"}}]
        [:put {:fhir/type :fhir/Encounter :id "134144"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference #fhir/string "Condition/191241"}}]}]
        [:put {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [(g-tu/extension-start :value #fhir/id "patient")
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "patient"}
                  #fhir/Extension{:url "type" :value #fhir/code "Patient"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "condition"}
                  #fhir/Extension{:url "type" :value #fhir/code "Condition"}])
                (g-tu/extension-node
                 :extension
                 [#fhir/Extension{:url "nodeId" :value #fhir/id "encounter"}
                  #fhir/Extension{:url "type" :value #fhir/code "Encounter"}])]
               :url #fhir/uri "144200"
               :name #fhir/string "patient-condition-encounter"
               :status #fhir/code "active"
               :start (type/code {:extension [g-tu/data-absent-reason-unsupported]})
               :link
               [{:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "patient")
                  (g-tu/extension-link-target-id :value #fhir/id "condition")
                  (g-tu/extension-link-params :value #fhir/string "patient={ref}")]}
                {:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "condition")
                  (g-tu/extension-link-target-id :value #fhir/id "encounter")]
                 :path #fhir/string "encounter"}
                {:fhir/type :fhir.GraphDefinition/link
                 :extension
                 [(g-tu/extension-link-source-id :value #fhir/id "encounter")
                  (g-tu/extension-link-target-id :value #fhir/id "condition")]
                 :path #fhir/string "diagnosis.condition"}]}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry] :entry :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 3"
          (is (= #fhir/unsignedInt 3 (:total body))))

        (testing "the bundle contains three entries"
          (is (= 3 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/145711")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Condition/191241")
                 (-> second-entry :fullUrl :value))))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Encounter/134144")
                 (-> third-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "145711"))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Condition
            :id := "191241"))

        (testing "the third entry has the right resource"
          (given (:resource third-entry)
            :fhir/type := :fhir/Encounter
            :id := "134144"))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))))))
