(ns blaze.rest-api.batch-handler-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.handler.util :as handler-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.batch-handler]
   [blaze.rest-api.routes-spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- handler [key]
  (fn [_] (ac/completed-future key)))

(def ^:private minimal-config
  {::rest-api/batch-handler
   {:structure-definition-repo structure-definition-repo
    :capabilities-handler (handler ::capabilities)
    :resource-patterns
    [#:blaze.rest-api.resource-pattern
      {:type :default
       :interactions
       {:read
        #:blaze.rest-api.interaction
         {:handler (fn [_])}}}]}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::rest-api/batch-handler nil})
      :key := ::rest-api/batch-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rest-api/batch-handler {}})
      :key := ::rest-api/batch-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :capabilities-handler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :resource-patterns))))

  (testing "invalid structure-definition-repo"
    (given-thrown (ig/init {::rest-api/batch-handler {:structure-definition-repo ::invalid}})
      :key := ::rest-api/batch-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :capabilities-handler))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-patterns))
      [:cause-data ::s/problems 2 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid capabilities-handler"
    (given-thrown (ig/init {::rest-api/batch-handler {:capabilities-handler ::invalid}})
      :key := ::rest-api/batch-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :resource-patterns))
      [:cause-data ::s/problems 2 :pred] := `fn?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "minimal config"
    (with-system [{::rest-api/keys [batch-handler]} minimal-config]
      (is (fn? batch-handler)))))

(def ^:private config
  {::rest-api/batch-handler
   {:structure-definition-repo structure-definition-repo
    :capabilities-handler (handler ::capabilities)
    :resource-patterns
    [#:blaze.rest-api.resource-pattern
      {:type "Patient"
       :interactions
       {:read
        #:blaze.rest-api.interaction
         {:handler (handler ::read)}
        :vread
        #:blaze.rest-api.interaction
         {:handler (handler ::vread)}
        :update
        #:blaze.rest-api.interaction
         {:handler (handler ::update)}
        :delete
        #:blaze.rest-api.interaction
         {:handler (handler ::delete)}
        :history-instance
        #:blaze.rest-api.interaction
         {:handler (handler ::history-instance)}
        :history-type
        #:blaze.rest-api.interaction
         {:handler (handler ::history-type)}
        :create
        #:blaze.rest-api.interaction
         {:handler (handler ::create)}
        :search-type
        #:blaze.rest-api.interaction
         {:handler (handler ::search-type)}}}]}})

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding] & body]
  `(with-system [{handler# ::rest-api/batch-handler} config]
     (let [~handler-binding handler#]
       ~@body)))

(deftest batch-handler-test
  (with-handler [handler]
    (are [uri method key] (= key @(handler {:request-method method :uri uri}))
      "/Patient" :get ::search-type
      "/Patient" :post ::create
      "/Patient/_history" :get ::history-type
      "/Patient/0" :get ::read
      "/Patient/0" :put ::update
      "/Patient/0" :delete ::delete
      "/Patient/0/_history" :get ::history-instance
      "/Patient/0/_history/42" :get ::vread)))
