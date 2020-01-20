(ns blaze.interaction.history.test-util
  (:require
    [blaze.interaction.history.util :as util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]))


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
  [match query-params t transaction eid-spec replace-fn]
  (st/instrument
    [`util/nav-url]
    {:spec
     {`util/nav-url
      (s/fspec
        :args (s/cat :match #{match}
                     :query-params #{query-params}
                     :t #{t}
                     :transaction #{transaction}
                     :eid eid-spec))}
     :replace
     {`util/nav-url replace-fn}}))


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


(defn stub-page-t [query-params t-spec]
  (st/instrument
    [`util/page-t]
    {:spec
     {`util/page-t
      (s/fspec
        :args (s/cat :query-params #{query-params})
        :ret t-spec)}
     :stub
     #{`util/page-t}}))


(defn stub-since-t [db query-params t-spec]
  (st/instrument
    [`util/since-t]
    {:spec
     {`util/since-t
      (s/fspec
        :args (s/cat :db #{db} :query-params #{query-params})
        :ret t-spec)}
     :stub
     #{`util/since-t}}))


(defn stub-tx-db [db since-t-spec page-t-spec tx-db]
  (st/instrument
    [`util/tx-db]
    {:spec
     {`util/tx-db
      (s/fspec
        :args (s/cat :db #{db} :since-t since-t-spec :page-t page-t-spec)
        :ret #{tx-db})}
     :stub
     #{`util/tx-db}}))
