(ns blaze.operation.graph-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.search-clauses-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.fhir.test-util.graph-definition-spec]
   [blaze.fhir.util-spec]
   [blaze.graph-definition-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.util-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.page-id :as page-id]
   [blaze.middleware.fhir.page-id-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.operation.graph :as graph]
   [blaze.operation.graph.cache-spec]
   [blaze.operation.graph.compiler-spec]
   [blaze.page-id-cipher.spec]
   [blaze.test-util :as tu]
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

(def ^:private config
  (assoc
   api-stub/mem-node-config
   :blaze.operation/graph
   {:compiled-graph-cache (ig/ref ::graph/compiled-graph-cache)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::graph/compiled-graph-cache {}
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

  (testing "missing clock"
    (given-failed-system (update config :blaze.operation/graph dissoc :clock)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "missing rng-fn"
    (given-failed-system (update config :blaze.operation/graph dissoc :rng-fn)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "missing page-id-cipher"
    (given-failed-system (update config :blaze.operation/graph dissoc :page-id-cipher)
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

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

(deftest compiled-graph-cache-init-test
  (testing "invalid num-nodes"
    (given-failed-system {::graph/compiled-graph-cache {:num-nodes ::invalid}}
      :key := ::graph/compiled-graph-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::graph/num-nodes]
      [:cause-data ::s/problems 0 :pred] := `nat-int?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def router
  (reitit/router
   (into
    [["/Patient/{id}/__graph-page/{page-id}" {:name :Patient.operation/graph-page}]]
    (map (fn [type] [(str "/" type) {:name (keyword type "type")}]))
    ["Patient" "Condition" "Observation" "Encounter" "Practitioner" "Organization"
     "Bundle"])
   {:syntax :bracket
    :path context-path}))

(defn- type-match [type]
  (reitit/map->Match
   {:data {:fhir.resource/type type}
    :path (str context-path "/" type "/0/$graph")}))

(def match
  (type-match "Patient"))

(def page-match
  (reitit/map->Match
   {:path (str context-path "/Patient/0/__graph-page")}))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router
            ::reitit/match (::reitit/match request match)))))

(defn- wrap-db [handler node page-id-cipher]
  (fn [{::reitit/keys [match] :as request}]
    (if (= page-match match)
      ((page-id/wrap-decrypt-page-id
        (db/wrap-snapshot-db handler node)
        page-id-cipher)
       request)
      ((db/wrap-db handler node) request))))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn- with-handler-form
  [config [handler-binding & [node-binding page-id-cipher-binding]] more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         page-id-cipher# :blaze.test/page-id-cipher
                         handler# :blaze.operation/graph} ~config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults
                                  (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defmacro with-handler [bindings & more]
  (with-handler-form `config bindings more))

(defmacro with-handler-non-ref [bindings & more]
  (with-handler-form
    `(assoc-in config [:blaze.db/node :enforce-referential-integrity] false)
    bindings more))

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

  (testing "invalid GraphDefinition"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put (g-tu/graph-def
               :start nil
               :nodes
               [(g-tu/node "patient" "Patient")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "144200"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Invalid GraphDefinition without start node."))))

  (testing "missing param graph"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "145711"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "Missing param `graph`"))))

  (testing "only returning the patient itself"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")])]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "144200"}})]

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
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "returning the patient and one observation"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/Observation :id "144115"
               :subject #fhir/Reference{:reference #fhir/string "Patient/145711"}}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")])]]]

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
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            :fhir/type := :fhir.Bundle.entry/search
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
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")
                (g-tu/node "encounter" "Encounter")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "encounter" :path "encounter")])]]]

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
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            :fhir/type := :fhir.Bundle.entry/search
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
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")
                (g-tu/node "encounter" "Encounter")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "encounter" :path "encounter")])]]]

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
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the fourth entry has the right search mode"
          (given (:search fourth-entry)
            :fhir/type := :fhir.Bundle.entry/search
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
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "condition" "Condition")
                (g-tu/node "encounter" "Encounter")]
               :link
               [(g-tu/link "patient" "condition" :params "patient={ref}")
                (g-tu/link "condition" "encounter" :path "encounter")
                (g-tu/link "encounter" "condition" :path "diagnosis.condition")])]]]

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
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            :fhir/type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))))))

(def ^:private patient-observation-practitioner-graph-def
  (g-tu/graph-def
   :nodes
   [(g-tu/node "patient" "Patient")
    (g-tu/node "observation" "Observation")
    (g-tu/node "practitioner" "Practitioner")]
   :link
   [(g-tu/link "patient" "observation" :params "patient={ref}")
    (g-tu/link "observation" "practitioner" :path "performer")]))

(defn- entry-urls [{:keys [entry]}]
  (mapv (comp :value :fullUrl) entry))

(defn- url [type id]
  (str base-url context-path "/" type "/" id))

(deftest handler-link-path-test
  (testing "follows all references returned by the path"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:reference #fhir/string "Practitioner/0"}
                #fhir/Reference{:reference #fhir/string "Practitioner/1"}]}]
        [:put patient-observation-practitioner-graph-def]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 4
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")
                         (url "Practitioner" "0")
                         (url "Practitioner" "1")]))))

  (testing "only follows references to resources of the target node type"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Organization :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:reference #fhir/string "Organization/0"}
                #fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
        [:put patient-observation-practitioner-graph-def]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 3
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")
                         (url "Practitioner" "0")]))))

  (testing "follows references of any type for target node type Resource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")
                (g-tu/node "performer" "Resource")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "performer" :path "performer")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 3
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")
                         (url "Practitioner" "0")]))))

  (testing "follows references to domain resources for target node type DomainResource"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Bundle :id "0" :type #fhir/code "collection"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :focus
               [#fhir/Reference{:reference #fhir/string "Bundle/0"}
                #fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")
                (g-tu/node "focus" "DomainResource")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "focus" :path "focus")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 3
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")
                         (url "Practitioner" "0")]))))

  (testing "skips a single reference to a missing resource"
    (with-handler-non-ref [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
        [:put patient-observation-practitioner-graph-def]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 2
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")]))))

  (testing "skips references without a relative literal reference"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:display #fhir/string "Dr. Foo"}
                (type/reference
                 {:reference
                  (type/string {:extension [g-tu/data-absent-reason-unsupported]})})
                #fhir/Reference{:reference #fhir/string "http://example.com/Practitioner/1"}
                #fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
        [:put patient-observation-practitioner-graph-def]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 3
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")
                         (url "Practitioner" "0")]))))

  (testing "skips values which aren't references"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code "final"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")
                (g-tu/node "practitioner" "Practitioner")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "practitioner" :path "status")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 2
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")]))))

  (testing "fails on FHIRPath evaluation error"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")
                (g-tu/node "practitioner" "Practitioner")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "practitioner" :path "performer.where(reference)")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "non-boolean result `#fhir/string \"Practitioner/0\"` of type `:fhir/string` while evaluating where function criteria"))))

  (testing "skips references to missing or deleted resources"
    (with-handler-non-ref [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "0"}]
        [:put {:fhir/type :fhir/Practitioner :id "1"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :performer
               [#fhir/Reference{:reference #fhir/string "Practitioner/0"}
                #fhir/Reference{:reference #fhir/string "Practitioner/1"}
                #fhir/Reference{:reference #fhir/string "Practitioner/2"}]}]
        [:put patient-observation-practitioner-graph-def]]
       [[:delete "Practitioner" "0"]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 3
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")
                         (url "Practitioner" "1")])))))

(deftest handler-multiple-nodes-test
  (testing "follows the links of every node a resource is reached through"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :diagnosis
               [{:fhir/type :fhir.Encounter/diagnosis
                 :condition #fhir/Reference{:reference #fhir/string "Condition/0"}}]}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/0"}}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "encounter-a" "Encounter")
                (g-tu/node "observation" "Observation")
                (g-tu/node "encounter-b" "Encounter")
                (g-tu/node "condition" "Condition")]
               :link
               [(g-tu/link "patient" "encounter-a" :params "patient={ref}")
                (g-tu/link "patient" "observation" :params "patient={ref}")
                (g-tu/link "observation" "encounter-b" :path "encounter")
                (g-tu/link "encounter-b" "condition" :path "diagnosis.condition")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 4
          entry-urls := [(url "Patient" "0")
                         (url "Encounter" "0")
                         (url "Observation" "0")
                         (url "Condition" "0")])))))

(deftest handler-link-params-test
  (testing "applies non-reference search clauses"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :status #fhir/code "final"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :status #fhir/code "preliminary"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")
                (g-tu/node "observation" "Observation")]
               :link
               [(g-tu/link "patient" "observation" :params "patient={ref}&status=final")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 2
          entry-urls := [(url "Patient" "0")
                         (url "Observation" "0")])))))

(deftest handler-start-node-type-test
  (testing "fails if the type of the resource doesn't match the type of the start node"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put (g-tu/graph-def
               :start "observation"
               :nodes
               [(g-tu/node "observation" "Observation")
                (g-tu/node "patient" "Patient")]
               :link
               [(g-tu/link "observation" "patient" :path "subject")])]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "The type `Patient` of the resource `Patient/0` doesn't match the type `Observation` of the start node `observation`."))))

  (testing "works on an Observation with a start node of type Observation"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put (g-tu/graph-def
               :start "observation"
               :nodes
               [(g-tu/node "observation" "Observation")
                (g-tu/node "patient" "Patient")]
               :link
               [(g-tu/link "observation" "patient" :path "subject")])]]]

      (let [{:keys [status body]}
            @(handler {::reitit/match (type-match "Observation")
                       :path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 200 status))

        (given body
          :total := #fhir/unsignedInt 2
          entry-urls := [(url "Observation" "0")
                         (url "Patient" "0")]))))

  (testing "fails on an Observation with a start node of type Patient"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Observation :id "0"}]
        [:put (g-tu/graph-def
               :nodes
               [(g-tu/node "patient" "Patient")])]]]

      (let [{:keys [status body]}
            @(handler {::reitit/match (type-match "Observation")
                       :path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "The type `Observation` of the resource `Observation/0` doesn't match the type `Patient` of the start node `patient`."))))

  (testing "a start node of type Resource matches all resource types"
    (doseq [[type resource] [["Patient" {:fhir/type :fhir/Patient :id "0"}]
                             ["Observation" {:fhir/type :fhir/Observation :id "0"}]
                             ["Bundle" {:fhir/type :fhir/Bundle :id "0"
                                        :type #fhir/code "collection"}]]]
      (with-handler [handler]
        [[[:put resource]
          [:put (g-tu/graph-def
                 :start "resource"
                 :nodes
                 [(g-tu/node "resource" "Resource")])]]]

        (let [{:keys [status body]}
              @(handler {::reitit/match (type-match type)
                         :path-params {:id "0"}
                         :query-params {"graph" "144200"}})]

          (is (= 200 status))

          (given body
            :total := #fhir/unsignedInt 1
            entry-urls := [(url type "0")])))))

  (testing "a start node of type DomainResource matches domain resource types"
    (doseq [[type resource] [["Patient" {:fhir/type :fhir/Patient :id "0"}]
                             ["Observation" {:fhir/type :fhir/Observation :id "0"}]]]
      (with-handler [handler]
        [[[:put resource]
          [:put (g-tu/graph-def
                 :start "domain-resource"
                 :nodes
                 [(g-tu/node "domain-resource" "DomainResource")])]]]

        (let [{:keys [status body]}
              @(handler {::reitit/match (type-match type)
                         :path-params {:id "0"}
                         :query-params {"graph" "144200"}})]

          (is (= 200 status))

          (given body
            :total := #fhir/unsignedInt 1
            entry-urls := [(url type "0")])))))

  (testing "a start node of type DomainResource doesn't match a Bundle"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Bundle :id "0" :type #fhir/code "collection"}]
        [:put (g-tu/graph-def
               :start "domain-resource"
               :nodes
               [(g-tu/node "domain-resource" "DomainResource")])]]]

      (let [{:keys [status body]}
            @(handler {::reitit/match (type-match "Bundle")
                       :path-params {:id "0"}
                       :query-params {"graph" "144200"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "The type `Bundle` of the resource `Bundle/0` doesn't match the type `DomainResource` of the start node `domain-resource`.")))))
