(ns blaze.handler.app-test
  (:require
    [blaze.handler.app]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [blaze.test-util.ring :refer [call]]
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


(defn- frontend [_ respond _]
  (respond (ring/response ::frontend)))


(defn- health-handler [_ respond _]
  (respond (ring/response ::health-handler)))


(def config
  {:blaze.handler/app
   {:rest-api rest-api
    :health-handler health-handler
    :context-path "/fhir"}})


(deftest handler-test
  (testing "with frontend"
    (with-system [{handler :blaze.handler/app}
                  (assoc-in config [:blaze.handler/app :frontend] frontend)]
      (testing "rest-api"
        (testing "without accept header"
          (given (call handler {:uri "/" :request-method :get})
            :status := 200
            :body := ::rest-api))

        (testing "with text/html as second accept header"
          (given (call handler {:uri "/" :request-method :get
                                :headers {"accept" "application/json,text/html"}})
            :status := 200
            :body := ::rest-api))

        (testing "with _format query param"
          (doseq [format ["xml" "text/xml" "application/xml" "application/fhir%2Bxml"
                          "json" "application/json" "application/fhir%2Bjson"]]
            (given (call handler {:uri "/" :request-method :get
                                  :headers {"accept" "text/html"}
                                  :query-string (str "_format=" format)})
              :status := 200
              :body := ::rest-api))))

      (testing "frontend"
        (testing "with HTML accept header"
          (doseq [accept ["text/html" "application/xhtml+xml"]]
            (given (call handler {:uri "/" :request-method :get
                                  :headers {"accept" accept}})
              :status := 200
              :body := ::frontend)))

        (testing "with _format query param"
          (doseq [format ["html" "text/html"]]
            (given (call handler {:uri "/" :request-method :get
                                  :headers {"accept" "application/fhir+json"}
                                  :query-string (str "_format=" format)})
              :status := 200
              :body := ::frontend))))

      (testing "health-handler"
        (given (call handler {:uri "/health" :request-method :get})
          :status := 200
          :body := ::health-handler))

      (testing "options request"
        (given (call handler {:uri "/health" :request-method :options})
          :status := 405))))

  (testing "without frontend"
    (with-system [{handler :blaze.handler/app} config]
      (testing "rest-api"
        (testing "without accept header"
          (given (call handler {:uri "/" :request-method :get})
            :status := 200
            :body := ::rest-api))

        (testing "with text/html as second accept header"
          (given (call handler {:uri "/" :request-method :get
                                :headers {"accept" "application/json,text/html"}})
            :status := 200
            :body := ::rest-api)))

      (testing "frontend is disabled"
        (doseq [accept ["text/html" "application/xhtml+xml"]]
          (given (call handler {:uri "/" :request-method :get
                                :headers {"accept" accept}})
            :status := 200
            :body := ::rest-api)))

      (testing "health-handler"
        (given (call handler {:uri "/health" :request-method :get})
          :status := 200
          :body := ::health-handler))

      (testing "options request"
        (given (call handler {:uri "/health" :request-method :options})
          :status := 405)))))
