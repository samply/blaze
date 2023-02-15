(ns blaze.server-test
  (:refer-clojure :exclude [error-handler])
  (:require
    [blaze.anomaly :as ba]
    [blaze.server]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [hato.client :as hc]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.net ServerSocket]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/server nil})
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/server {}})
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :port))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :handler))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :version))))

  (testing "invalid port"
    (given-thrown (ig/init {:blaze/server {:port ::invalid}})
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :handler))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:explain ::s/problems 2 :pred] := `nat-int?
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "invalid handler"
    (given-thrown (ig/init {:blaze/server {:handler ::invalid}})
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :port))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :version))
      [:explain ::s/problems 2 :pred] := `fn?
      [:explain ::s/problems 2 :val] := ::invalid))

  (testing "invalid version"
    (given-thrown (ig/init {:blaze/server {:version ::invalid}})
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :port))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :handler))
      [:explain ::s/problems 2 :pred] := `string?
      [:explain ::s/problems 2 :val] := ::invalid)))


(defn ok-handler [_]
  (ring/response "OK"))


(defn async-ok-handler [_ respond _]
  (respond (ring/response "OK")))


(defn error-handler [_]
  (throw (Exception. "msg-163147")))


(defn async-error-handler [_ _ raise]
  (raise (Exception. "msg-163147")))


(defn system
  ([port]
   (system port ok-handler))
  ([port handler]
   {:blaze/server {:port port :handler handler :version "1.0"}}))


(defn async-system
  ([port]
   (async-system port async-ok-handler))
  ([port handler]
   {:blaze/server {:port port :handler handler :version "1.0" :async? true}}))


(defn- find-free-port []
  (with-open [s (ServerSocket. 0)]
    (.getLocalPort s)))


(deftest server-test
  (let [port (find-free-port)]
    (testing "successful response"
      (with-system [_ (system port)]
        (given (hc/get (str "http://localhost:" port))
          :status := 200
          :body := "OK"
          [:headers "server"] := "Blaze/1.0"))

      (testing "async"
        (with-system [_ (async-system port)]
          (given (hc/get (str "http://localhost:" port))
            :status := 200
            :body := "OK"
            [:headers "server"] := "Blaze/1.0"))))

    (testing "error"
      (with-system [_ (system port error-handler)]
        (given (ba/try-anomaly (hc/get (str "http://localhost:" port)))
          :status := 500))

      (testing "async"
        (with-system [_ (async-system port async-error-handler)]
          (given (ba/try-anomaly (hc/get (str "http://localhost:" port)))
            :status := 500))))

    (testing "with already bound port"
      (with-system [_ (system port)]
        (given (ba/try-anomaly (ig/init (system port)))
          ::anom/category := ::anom/fault
          :key := :blaze/server,
          :reason := :integrant.core/build-threw-exception
          ::anom/message := "Error on key :blaze/server when building system"
          [:blaze.anomaly/cause ::anom/message] := (str "Failed to bind to 0.0.0.0/0.0.0.0:" port))))))
