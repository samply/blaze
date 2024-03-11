(ns blaze.handler.app-test
  (:require
   [blaze.handler.app]
   [blaze.module.test-util :refer [with-system]]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.handler/app nil})
      :key := :blaze.handler/app
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.handler/app {}})
      :key := :blaze.handler/app
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rest-api))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :health-handler))))

  (testing "invalid rest-api"
    (given-thrown (ig/init {:blaze.handler/app {:rest-api ::invalid}})
      :key := :blaze.handler/app
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :health-handler))
      [:explain ::s/problems 1 :pred] := `fn?
      [:explain ::s/problems 1 :val] := ::invalid))

  (testing "invalid health"
    (given-thrown (ig/init {:blaze.handler/app {:health-handler ::invalid}})
      :key := :blaze.handler/app
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rest-api))
      [:explain ::s/problems 1 :pred] := `fn?
      [:explain ::s/problems 1 :val] := ::invalid)))

(defn- rest-api [_ respond _]
  (respond (ring/response ::rest-api)))

(defn- health-handler [_ respond _]
  (respond (ring/response ::health-handler)))

(def config
  {:blaze.handler/app
   {:rest-api rest-api
    :health-handler health-handler}})

(deftest handler-test
  (with-system [{handler :blaze.handler/app} config]
    (testing "rest-api"
      (given (call handler {:uri "/" :request-method :get})
        :status := 200
        :body := ::rest-api))

    (testing "health-handler"
      (given (call handler {:uri "/health" :request-method :get})
        :status := 200
        :body := ::health-handler))

    (testing "options request"
      (given (call handler {:uri "/health" :request-method :options})
        :status := 405))))
