(ns blaze.interaction.middleware.type-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.interaction.middleware.type :refer [wrap-type]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest wrap-type-test
  (st/unstrument `wrap-type)
  (datomic-test-util/stub-db ::conn ::db)

  (testing "Returns Not Found on Non-Existing Resource Type"
    (datomic-test-util/stub-cached-entity ::db #{:Patient} nil?)

    (let [{:keys [status body]}
          ((wrap-type (fn [_]) ::conn) {:path-params {:type "Patient"}})]

      (is (= 404 status))

      (is (= "OperationOutcome" (:resourceType body)))

      (is (= "error" (-> body :issue first :severity)))

      (is (= "not-found" (-> body :issue first :code)))))


  (testing "Calls Handler on Existing Resource Type"
    (datomic-test-util/stub-cached-entity ::db #{:Patient} some?)

    (let [request {:path-params {:type "Patient"}}
          response ((wrap-type identity ::conn) request)]

      (is (= request response)))))
