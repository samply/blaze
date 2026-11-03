(ns blaze.middleware.fhir.page-id-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.middleware.fhir.page-id :refer [encrypt wrap-decrypt-page-id]]
   [blaze.middleware.fhir.page-id-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [com.google.crypto.tink Aead KeysetHandle RegistryConfiguration]
   [com.google.crypto.tink.aead AeadConfig PredefinedAeadParameters]
   [java.util Base64 HexFormat]))

(set! *warn-on-reflection* true)
(st/instrument)
(AeadConfig/register)

(test/use-fixtures :each tu/fixture)

(def page-id-cipher
  (-> (KeysetHandle/generateNew PredefinedAeadParameters/AES128_GCM)
      (.getPrimitive (RegistryConfiguration/get) Aead)))

(def paging-params
  {"__token" "token-130842"
   "__t" "1"
   "__page-t" "2"
   "__page-id" "id-130902"
   "__page-offset" "3"
   "__page-type" "Patient"
   "__page-id-stack" ["" "id-130918"]})

(def handler
  (wrap-decrypt-page-id ac/completed-future page-id-cipher))

(def previous-version-clear-text
  "The CBOR encoded query params `{\"_count\" \"1\" \"__page-id-stack\" [\"\"
  \"id-130918\"]}` as issued by previous versions of Blaze, which used maps and
  arrays of indefinite length."
  (.parseHex (HexFormat/of)
             (str "bf665f636f756e7461316f5f5f706167652d69642d737461636b9f60"
                  "6969642d313330393138ffff")))

(defn- encrypt-clear-text [clear-text]
  (.encodeToString
   (.withoutPadding (Base64/getUrlEncoder))
   (.encrypt ^Aead page-id-cipher clear-text nil)))

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
        (let [page-id (encrypt page-id-cipher query-params)
              request @(handler {:path-params {:page-id page-id}})]
          (= query-params (merge (:params request) (:blaze/paging-params request)))))))

  (testing "paging params are separated from the other params"
    (let [page-id (encrypt page-id-cipher (assoc paging-params "_count" "1"))]
      (given @(handler {:path-params {:page-id page-id}})
        :params := {"_count" "1"}
        :query-params := {"_count" "1"}
        :blaze/paging-params := paging-params)))

  (testing "page ids issued by previous versions can be decrypted"
    (let [page-id (encrypt-clear-text previous-version-clear-text)]
      (given @(handler {:path-params {:page-id page-id}})
        :params := {"_count" "1"}
        :blaze/paging-params := {"__page-id-stack" ["" "id-130918"]}))))
