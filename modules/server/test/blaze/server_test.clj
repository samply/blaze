(ns blaze.server-test
  (:refer-clojure :exclude [error-handler])
  (:require
   [blaze.anomaly :as ba]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.server]
   [blaze.server.spec]
   [blaze.test-util :as tu]
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
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn ok-handler [_]
  (-> (ring/response "OK")
      (ring/content-type "text/plain")))

(defn- find-free-port! []
  (with-open [s (ServerSocket. 0)]
    (.getLocalPort s)))

(defn config
  ([port]
   (config port ok-handler))
  ([port handler]
   {:blaze/server {:port port :handler handler :version "1.0"}}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze/server nil}
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze/server {}}
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :port))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :handler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :version))))

  (testing "invalid port"
    (given-failed-system (config ::invalid)
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.server/port]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid handler"
    (given-failed-system (assoc-in (config (find-free-port!)) [:blaze/server :handler] ::invalid)
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.server/handler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid version"
    (given-failed-system (assoc-in (config (find-free-port!)) [:blaze/server :version] ::invalid)
      :key := :blaze/server
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.server/version]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn async-ok-handler [_ respond _]
  (-> (ring/response "OK")
      (ring/content-type "text/plain")
      (respond)))

(defn error-handler [_]
  (throw (Exception. "msg-163147")))

(defn async-error-handler [_ _ raise]
  (raise (Exception. "msg-163147")))

(defn async-config
  ([port]
   (async-config port async-ok-handler))
  ([port handler]
   {:blaze/server {:port port :handler handler :version "1.0" :async? true}}))

(deftest server-test
  (let [port (find-free-port!)]
    (testing "successful response"
      (with-system [_ (config port)]
        (given (hc/get (str "http://localhost:" port))
          :status := 200
          :body := "OK"
          [:headers "content-type"] := "text/plain"
          [:headers "server"] := "Blaze/1.0"))

      (testing "async"
        (with-system [_ (async-config port)]
          (given (hc/get (str "http://localhost:" port))
            :status := 200
            :body := "OK"
            [:headers "content-type"] := "text/plain"
            [:headers "server"] := "Blaze/1.0"))))

    (testing "error"
      (with-system [_ (config port error-handler)]
        (given (ba/try-anomaly (hc/get (str "http://localhost:" port)))
          :status := 500))

      (testing "async"
        (with-system [_ (async-config port async-error-handler)]
          (given (ba/try-anomaly (hc/get (str "http://localhost:" port)))
            :status := 500))))

    (testing "with already bound port"
      (with-system [_ (config port)]
        (given (ba/try-anomaly (ig/init (config port)))
          ::anom/category := ::anom/fault
          :key := :blaze/server,
          :reason := :integrant.core/build-threw-exception
          ::anom/message := "Error on key :blaze/server when building system"
          [:blaze.anomaly/cause ::anom/message] := (str "Failed to bind to 0.0.0.0/0.0.0.0:" port))))))
