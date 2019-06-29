(ns blaze.handler.fhir.history.util-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.handler.fhir.history.util :refer [build-entry]]
    [clojure.test :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [datomic-spec.test :as dst]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`build-entry]
    {:spec
     {`build-entry
      (s/fspec
        :args (s/cat :base-uri some? :db some? :transaction some?
                     :resource-eid some?))}})
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(def base-uri "http://localhost:8080")
(def transaction {:db/id 0})


(deftest build-entry-test
  (testing "Initial version with server assigned id"
    (datomic-test-util/stub-as-of ::db 0 ::as-of-db)
    (datomic-test-util/stub-entity ::as-of-db #{::resource-eid} #{::resource})
    (datomic-test-util/stub-literal-reference ::resource ["Patient" "0"])
    (datomic-test-util/stub-initial-version? ::resource true?)
    (datomic-test-util/stub-initial-version-server-assigned-id? ::resource true?)
    (datomic-test-util/stub-tx-instant transaction "last-modified")
    (datomic-test-util/stub-deleted? ::resource false?)
    (datomic-test-util/stub-pull-resource*
      ::as-of-db "Patient" ::resource #{::pulled-resource})

    (is
      (=
        (build-entry base-uri ::db transaction ::resource-eid)
        {:fullUrl "http://localhost:8080/fhir/Patient/0"
         :request
         {:method "POST"
          :url "http://localhost:8080/fhir/Patient"}
         :resource ::pulled-resource
         :response
         {:etag "W/\"0\""
          :lastModified "last-modified"
          :status "201"}})))


  (testing "Initial version with client assigned id"
    (datomic-test-util/stub-as-of ::db 0 ::as-of-db)
    (datomic-test-util/stub-entity ::as-of-db #{::resource-eid} #{::resource})
    (datomic-test-util/stub-literal-reference ::resource ["Patient" "0"])
    (datomic-test-util/stub-initial-version? ::resource true?)
    (datomic-test-util/stub-initial-version-server-assigned-id? ::resource false?)
    (datomic-test-util/stub-tx-instant transaction "last-modified")
    (datomic-test-util/stub-deleted? ::resource false?)
    (datomic-test-util/stub-pull-resource*
      ::as-of-db "Patient" ::resource #{::pulled-resource})

    (is
      (=
        (build-entry base-uri ::db transaction ::resource-eid)
        {:fullUrl "http://localhost:8080/fhir/Patient/0"
         :request
         {:method "PUT"
          :url "http://localhost:8080/fhir/Patient/0"}
         :resource ::pulled-resource
         :response
         {:etag "W/\"0\""
          :lastModified "last-modified"
          :status "201"}})))


  (testing "Non-initial version"
    (datomic-test-util/stub-as-of ::db 0 ::as-of-db)
    (datomic-test-util/stub-entity ::as-of-db #{::resource-eid} #{::resource})
    (datomic-test-util/stub-literal-reference ::resource ["Patient" "0"])
    (datomic-test-util/stub-initial-version? ::resource false?)
    (datomic-test-util/stub-initial-version-server-assigned-id? ::resource false?)
    (datomic-test-util/stub-tx-instant transaction "last-modified")
    (datomic-test-util/stub-deleted? ::resource false?)
    (datomic-test-util/stub-pull-resource*
      ::as-of-db "Patient" ::resource #{::pulled-resource})

    (is
      (=
        (build-entry base-uri ::db transaction ::resource-eid)
        {:fullUrl "http://localhost:8080/fhir/Patient/0"
         :request
         {:method "PUT"
          :url "http://localhost:8080/fhir/Patient/0"}
         :resource ::pulled-resource
         :response
         {:etag "W/\"0\""
          :lastModified "last-modified"
          :status "200"}})))


  (testing "Deleted version"
    (datomic-test-util/stub-as-of ::db 0 ::as-of-db)
    (datomic-test-util/stub-entity ::as-of-db #{::resource-eid} #{::resource})
    (datomic-test-util/stub-literal-reference ::resource ["Patient" "0"])
    (datomic-test-util/stub-initial-version? ::resource false?)
    (datomic-test-util/stub-initial-version-server-assigned-id? ::resource false?)
    (datomic-test-util/stub-tx-instant transaction "last-modified")
    (datomic-test-util/stub-deleted? ::resource true?)
    (datomic-test-util/stub-pull-resource*
      ::as-of-db "Patient" ::resource #{::pulled-resource})

    (is
      (=
        (build-entry base-uri ::db transaction ::resource-eid)
        {:fullUrl "http://localhost:8080/fhir/Patient/0"
         :request
         {:method "DELETE"
          :url "http://localhost:8080/fhir/Patient/0"}
         :response
         {:etag "W/\"0\""
          :lastModified "last-modified"
          :status "204"}}))))
