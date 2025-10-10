(ns blaze.interaction.update-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#update
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.db.kv :as kv]
   [blaze.db.kv.protocols :as kv-p]
   [blaze.db.node :as node]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.fhir.response.create-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.interaction.update]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-134013")

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]
    ["/Observation" {:name :Observation/type}]]
   {:syntax :bracket}))

(def operation-outcome
  #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome")

(def patient-match
  (reitit/map->Match {:data {:fhir.resource/type "Patient"}}))

(def observation-match
  (reitit/map->Match {:data {:fhir.resource/type "Observation"}}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/update nil}
      :key := :blaze.interaction/update
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/update {}}
      :key := :blaze.interaction/update
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))))

  (testing "invalid node"
    (given-failed-system {:blaze.interaction/update {:node ::invalid}}
      :key := :blaze.interaction/update
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  (assoc api-stub/mem-node-config
         :blaze.interaction/update
         {:node (ig/ref :blaze.db/node)
          :executor (ig/ref :blaze.test/executor)}
         :blaze.test/executor {}))

(def disabled-referential-integrity-check-config
  (assoc-in config [:blaze.db/node :enforce-referential-integrity] false))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router))))

(defn- decode-more [more]
  (if (symbol? (first more))
    (into [(first more)] (api-stub/extract-txs-body (next more)))
    (into [`config] (api-stub/extract-txs-body more))))

(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[config txs body] (decode-more more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.interaction/update} ~config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))

(deftest handler-test
  (testing "errors on"
    (testing "missing body"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "invalid"
              [:issue 0 :diagnostics] := #fhir/string "Missing HTTP body.")))))

    (testing "type mismatch"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :body {:fhir/type :fhir/Observation}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "invariant"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_TYPE_MISMATCH"
              [:issue 0 :diagnostics] := #fhir/string "Invalid update interaction of a Observation at a Patient endpoint.")))))

    (testing "missing id"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :body {:fhir/type :fhir/Patient}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "required"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_ID_MISSING"
              [:issue 0 :diagnostics] := #fhir/string "Missing resource id.")))))

    (testing "subsetted"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :body {:fhir/type :fhir/Patient :id "0"
                        :meta (type/meta {:tag [fu/subsetted]})}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "processing"
              [:issue 0 :diagnostics] := #fhir/string "Resources with tag SUBSETTED may be incomplete and so can't be used in updates.")))))

    (testing "ID mismatch"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :body {:fhir/type :fhir/Patient :id "1"}})]

          (testing "returns error"
            (is (= 400 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "invariant"
              [:issue 0 :details :coding 0 :system] := operation-outcome
              [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_ID_MISMATCH"
              [:issue 0 :diagnostics] := #fhir/string "The resource id `1` doesn't match the endpoints id `0`.")))))

    (testing "arbitrary If-Match header fails"
      (with-handler [handler]
        (satisfies-prop 1000
          (prop/for-all [if-match gen/string]
            (let [{:keys [status]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-match" if-match}
                     :body {:fhir/type :fhir/Patient :id "0"}})]

              (= 412 status))))))

    (testing "optimistic locking failure"
      (testing "with different content"
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"
                      :gender #fhir/code "female"}]]
           [[:put {:fhir/type :fhir/Patient :id "0"
                   :gender #fhir/code "male"}]]]

          (let [{:keys [status body]}
                @(handler
                  {:path-params {:id "0"}
                   ::reitit/match patient-match
                   :headers {"if-match" "W/\"1\""}
                   :body {:fhir/type :fhir/Patient :id "0"
                          :gender #fhir/code "female"}})]

            (testing "returns error"
              (is (= 412 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`.")))))

      (testing "with identical content"
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"
                      :gender #fhir/code "male"}]]
           [[:put {:fhir/type :fhir/Patient :id "0"
                   :gender #fhir/code "female"}]]]

          (let [{:keys [status body]}
                @(handler
                  {:path-params {:id "0"}
                   ::reitit/match patient-match
                   :headers {"if-match" "W/\"1\""}
                   :body {:fhir/type :fhir/Patient :id "0"
                          :gender #fhir/code "female"}})]

            (testing "returns error"
              (is (= 412 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code "error"
                [:issue 0 :code] := #fhir/code "conflict"
                [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`."))))

        (testing "and content changing transaction in between"
          (with-redefs [kv/put!
                        (fn [store entries]
                          (Thread/sleep 20)
                          (kv-p/-put store entries))]
            (with-handler [handler node]
              [[[:create {:fhir/type :fhir/Patient :id "0"
                          :gender #fhir/code "female"}]]]

              ;; don't wait for the transaction to be finished because the handler
              ;; call should see the first version of the patient
              @(node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"
                                            :gender #fhir/code "male"}]])

              (let [{:keys [status body]}
                    @(handler
                      {:path-params {:id "0"}
                       ::reitit/match patient-match
                       :headers {"if-match" "W/\"1\""}
                       :body {:fhir/type :fhir/Patient :id "0"
                              :gender #fhir/code "female"}})]

                (testing "returns error"
                  (is (= 412 status))

                  (given body
                    :fhir/type := :fhir/OperationOutcome
                    [:issue 0 :severity] := #fhir/code "error"
                    [:issue 0 :code] := #fhir/code "conflict"
                    [:issue 0 :diagnostics] := #fhir/string "Precondition `W/\"1\"` failed on `Patient/0`.")))

              (testing "we did not retry to the error transaction is 3"
                (is (= 3 (:error-t @(:state node))))))))))

    (testing "violated referential integrity"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match observation-match
                 :body {:fhir/type :fhir/Observation :id "0"
                        :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}})]

          (testing "returns error"
            (is (= 409 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "conflict"
              [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Patient/0` doesn't exist."))))))

  (testing "missing resource content"
    (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :body {:fhir/type :fhir/Patient :id "0"}})]

          (is (= 500 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "incomplete"
            [:issue 0 :diagnostics] := #fhir/string "The resource `Patient/0` was successfully updated but it's content with hash `C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F` was not found during response creation.")))))

  (testing "on newly created resource"
    (testing "with no Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :body {:fhir/type :fhir/Patient :id "0"}})]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Location header"
            (is (= (str base-url "/Patient/0/_history/1") (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "VersionId in ETag header"
            (is (= "W/\"1\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= (str base-url "/Patient/0/_history/1") (get headers "Location"))))

          (testing "Contains the resource as body"
            (given body
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH)))))

    (testing "with return=minimal Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :headers {"prefer" "return=minimal"}
                 :body {:fhir/type :fhir/Patient :id "0"}})]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Location header"
            (is (= (str base-url "/Patient/0/_history/1") (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "VersionId in ETag header"
            (is (= "W/\"1\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= (str base-url "/Patient/0/_history/1") (get headers "Location"))))

          (testing "Contains no body"
            (is (nil? body))))))

    (testing "with return=representation Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :headers {"prefer" "return=representation"}
                 :body {:fhir/type :fhir/Patient :id "0"}})]

          (testing "Returns 201"
            (is (= 201 status)))

          (testing "Location header"
            (is (= (str base-url "/Patient/0/_history/1") (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "VersionId in ETag header"
            (is (= "W/\"1\"" (get headers "ETag"))))

          (testing "Location header"
            (is (= (str base-url "/Patient/0/_history/1") (get headers "Location"))))

          (testing "Contains body"
            (given body
              :fhir/type := :fhir/Patient
              :id := "0"))))))

  (testing "on recreated, previously deleted resource"
    (testing "with no Prefer header"
      (doseq [if-match [nil "W/\"2\"" "W/\"1\",W/\"2\""]]
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"}]]
           [[:delete "Patient" "0"]]]

          (let [{:keys [status headers body]}
                @(handler
                  {:path-params {:id "0"}
                   ::reitit/match patient-match
                   :headers {"if-match" if-match}
                   :body {:fhir/type :fhir/Patient :id "0"}})]

            (testing "Returns 201"
              (is (= 201 status)))

            (testing "Location header"
              (is (= (str base-url "/Patient/0/_history/3") (get headers "Location"))))

            (testing "Transaction time in Last-Modified header"
              (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

            (testing "VersionId in ETag header"
              (is (= "W/\"3\"" (get headers "ETag"))))

            (testing "Location header"
              (is (= (str base-url "/Patient/0/_history/3") (get headers "Location"))))

            (testing "Contains the resource as body"
              (given body
                :fhir/type := :fhir/Patient
                :id := "0"
                [:meta :versionId] := #fhir/id "3"
                [:meta :lastUpdated] := Instant/EPOCH)))))))

  (testing "on successful update of an existing resource"
    (testing "with no Prefer header"
      (doseq [if-match [nil "W/\"1\"" "W/\"1\",W/\"2\""]]
        (with-handler [handler]
          [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

          (let [{:keys [status headers body]}
                @(handler
                  {:path-params {:id "0"}
                   ::reitit/match patient-match
                   :headers {"if-match" if-match}
                   :body {:fhir/type :fhir/Patient :id "0"
                          :birthDate #fhir/date "2020"}})]

            (testing "Returns 200"
              (is (= 200 status)))

            (testing "Transaction time in Last-Modified header"
              (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

            (testing "VersionId in ETag header"
              (is (= "W/\"2\"" (get headers "ETag"))))

            (testing "Contains the resource as body"
              (given body
                :fhir/type := :fhir/Patient
                :id := "0"
                :birthDate := #fhir/date "2020"
                [:meta :versionId] := #fhir/id "2"
                [:meta :lastUpdated] := Instant/EPOCH)))))))

  (testing "on update of an existing resource with identical content"
    (doseq [if-match [nil "W/\"1\"" "W/\"1\",W/\"2\""]]
      (with-handler [handler]
        [[[:create {:fhir/type :fhir/Patient :id "0"
                    :birthDate #fhir/date "2020"}]]]

        (let [{:keys [status headers body]}
              @(handler
                {:path-params {:id "0"}
                 ::reitit/match patient-match
                 :headers {"if-match" if-match}
                 :body {:fhir/type :fhir/Patient :id "0"
                        :meta (type/meta {:versionId #fhir/id "1"
                                          :lastUpdated Instant/EPOCH})
                        :birthDate #fhir/date "2020"}})]

          (testing "Returns 200"
            (is (= 200 status)))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "VersionId in ETag header is not incremented"
            (is (= "W/\"1\"" (get headers "ETag"))))

          (testing "Contains the resource as body with the non-incremented versionId"
            (given body
              :fhir/type := :fhir/Patient
              :id := "0"
              :birthDate := #fhir/date "2020"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH)))))

    (testing "and content changing transaction in between"
      (with-redefs [kv/put!
                    (fn [store entries]
                      (Thread/sleep 20)
                      (kv-p/-put store entries))]
        (doseq [if-match [nil "W/\"1\",W/\"2\""]]
          (with-handler [handler node]
            [[[:create {:fhir/type :fhir/Patient :id "0"
                        :birthDate #fhir/date "2020"}]]]

            ;; don't wait for the transaction to be finished because the handler
            ;; call should see the first version of the patient
            @(node/submit-tx node [[:put {:fhir/type :fhir/Patient :id "0"
                                          :birthDate #fhir/date "2021"}]])

            (let [{:keys [status headers body]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-match" if-match}
                     :body {:fhir/type :fhir/Patient :id "0"
                            :meta (type/meta {:versionId #fhir/id "1"
                                              :lastUpdated Instant/EPOCH})
                            :birthDate #fhir/date "2020"}})]

              (testing "Returns 200"
                (is (= 200 status)))

              (testing "Transaction time in Last-Modified header"
                (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

              (testing "VersionId in ETag header shows one retry"
                (is (= "W/\"4\"" (get headers "ETag"))))

              (testing "Contains the resource as body with the non-incremented versionId"
                (given body
                  :fhir/type := :fhir/Patient
                  :id := "0"
                  :birthDate := #fhir/date "2020"
                  [:meta :versionId] := #fhir/id "4"
                  [:meta :lastUpdated] := Instant/EPOCH))))))))

  (testing "with disabled referential integrity check"
    (with-handler [handler]
      disabled-referential-integrity-check-config

      (let [{:keys [status headers body]}
            @(handler
              {:path-params {:id "0"}
               ::reitit/match observation-match
               :body {:fhir/type :fhir/Observation :id "0"
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}})]

        (testing "Returns 201"
          (is (= 201 status)))

        (testing "Location header"
          (is (= (str base-url "/Observation/0/_history/1") (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "VersionId in ETag header"
          (is (= "W/\"1\"" (get headers "ETag"))))

        (testing "Location header"
          (is (= (str base-url "/Observation/0/_history/1") (get headers "Location"))))

        (testing "Contains the resource as body"
          (given body
            :fhir/type := :fhir/Observation
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH)))))

  (testing "conditional update"
    (testing "if-none-match"
      (testing "*"
        (testing "with existing resource"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-none-match" "*"}
                     :body {:fhir/type :fhir/Patient :id "0"}})]

              (testing "returns error"
                (is (= 412 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` already exists.")))))

        (testing "with no existing resource"
          (with-handler [handler]

            (let [{:keys [status]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-none-match" "*"}
                     :body {:fhir/type :fhir/Patient :id "0"}})]

              (testing "Returns 201"
                (is (= 201 status))))))

        (testing "with deleted resource"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"}]]
             [[:delete "Patient" "0"]]]

            (let [{:keys [status body]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-none-match" "*"}
                     :body {:fhir/type :fhir/Patient :id "0"}})]

              (testing "returns error"
                (is (= 412 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` already exists."))))))

      (testing "W/\"1\""
        (testing "with existing resource"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

            (let [{:keys [status body]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-none-match" "W/\"1\""}
                     :body {:fhir/type :fhir/Patient :id "0"}})]

              (testing "returns error"
                (is (= 412 status))

                (given body
                  :fhir/type := :fhir/OperationOutcome
                  [:issue 0 :severity] := #fhir/code "error"
                  [:issue 0 :code] := #fhir/code "conflict"
                  [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/0` with version 1 already exists."))))))

      (testing "W/\"2\""
        (testing "with existing resource"
          (with-handler [handler]
            [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

            (let [{:keys [status]}
                  @(handler
                    {:path-params {:id "0"}
                     ::reitit/match patient-match
                     :headers {"if-none-match" "W/\"2\""}
                     :body {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}})]

              (testing "Returns 200"
                (is (= 200 status))))))))))
