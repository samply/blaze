(ns blaze.handler.fhir.history.test-util
  (:require
    [blaze.handler.fhir.history.util :as util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(defn stub-build-entry
  [router db transaction-spec resource-eid-spec replace-fn]
  (st/instrument
    [`util/build-entry]
    {:spec
     {`util/build-entry
      (s/fspec
        :args (s/cat :router #{router} :db #{db}
                     :transaction transaction-spec
                     :resource-eid resource-eid-spec))}
     :replace
     {`util/build-entry replace-fn}}))


(defn stub-nav-link
  [match query-params relation-spec transaction resource-eid-spec
   replace-fn]
  (st/instrument
    [`util/nav-link]
    {:spec
     {`util/nav-link
      (s/fspec
        :args (s/cat :match #{match}
                     :query-params #{query-params}
                     :relation relation-spec
                     :entry (s/tuple #{transaction} resource-eid-spec)))}
     :replace
     {`util/nav-link replace-fn}}))


(defn stub-page-eid [query-params result-spec]
  (st/instrument
    [`util/page-eid]
    {:spec
     {`util/page-eid
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret result-spec)}
     :stub
     #{`util/page-eid}}))


(defn stub-page-t [query-params result-spec]
  (st/instrument
    [`util/page-t]
    {:spec
     {`util/page-t
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret result-spec)}
     :stub
     #{`util/page-t}}))
