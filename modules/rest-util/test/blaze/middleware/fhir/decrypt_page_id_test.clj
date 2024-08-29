(ns blaze.middleware.fhir.decrypt-page-id-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.middleware.fhir.decrypt-page-id :refer [encrypt wrap-decrypt-page-id]]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom])
  (:import
   [com.google.crypto.tink Aead KeysetHandle]
   [com.google.crypto.tink.aead AeadConfig PredefinedAeadParameters]))

(set! *warn-on-reflection* true)
(st/instrument)
(AeadConfig/register)

(test/use-fixtures :each tu/fixture)

(def page-id-cipher
  (-> (KeysetHandle/generateNew PredefinedAeadParameters/AES128_GCM)
      (.getPrimitive Aead)))

(def handler
  (wrap-decrypt-page-id ac/completed-future page-id-cipher))

(deftest wrap-decrypt-page-id-test
  (testing "random string results in an anomaly with category incorrect"
    (satisfies-prop 1000
      (prop/for-all [page-id gen/string]
        (let [{::anom/keys [category message]} (ba/try-anomaly @(handler {:path-params {:page-id page-id}}))]
          (and (= category ::anom/not-found)
               (= message (format "Page with id `%s` not found." page-id)))))))

  (testing "random query params can be encrypted"
    (satisfies-prop 100
      (prop/for-all [query-params (s/gen :ring.request/query-params)]
        (let [page-id (encrypt page-id-cipher query-params)]
          (= query-params (:params @(handler {:path-params {:page-id page-id}}))))))))
