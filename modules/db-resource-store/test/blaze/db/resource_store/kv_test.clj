(ns blaze.db.resource-store.kv-test
  (:refer-clojure :exclude [hash])
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store-spec]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.resource-store.kv-spec]
    [blaze.executors :as ex]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.test-util :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def executor (ex/single-thread-executor))


(defn hash [s]
  (assert (= 1 (count s)))
  (bs/from-hex (str/repeat s 64)))


(defn invalid-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(defn encode-resource [resource]
  (fhir-spec/unform-cbor resource))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::rs/kv nil})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::rs/kv {}})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))))

  (testing "invalid kv-store"
    (given-thrown (ig/init {::rs/kv {:kv-store ::kv-store}})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 1 :pred] := `kv/store?
      [:explain ::s/problems 1 :val] := ::kv-store))

  (testing "invalid executor"
    (given-thrown (ig/init {::rs/kv {:executor ::invalid}})
      :key := ::rs/kv
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :kv-store))
      [:explain ::s/problems 1 :pred] := `ex/executor?
      [:explain ::s/problems 1 :val] := ::invalid)))


(def system
  {::rs/kv
   {:kv-store (ig/ref ::kv/mem)
    :executor (ig/ref ::rs-kv/executor)}
   ::kv/mem {:column-families {}}
   ::rs-kv/executor {}})


(defmethod ig/init-key ::failing-kv-store [_ {:keys [msg]}]
  (reify kv/KvStore
    (-get [_ _]
      (throw (Exception. ^String msg)))
    (-multi-get [_ _]
      (throw (Exception. ^String msg)))))


(defn failing-kv-store-system [msg]
  {::failing-kv-store {:msg msg}
   ::rs-kv/executor {}
   ::rs/kv
   {:kv-store (ig/ref ::failing-kv-store)
    :executor (ig/ref ::rs-kv/executor)}})


(deftest get-test
  (testing "success"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (kv/put! kv-store (bs/to-byte-array hash) (fhir-spec/unform-cbor content))

        (is (= content @(rs/get store hash))))))

  (testing "parsing error"
    (let [hash (hash "0")]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (kv/put! kv-store (bs/to-byte-array hash) (invalid-content))

        (given-failed-future (rs/get store hash)
          ::anom/message :# "Error while parsing resource content(.|\\s)*"))))

  (testing "not-found"
    (with-system [{store ::rs/kv} system]
      (is (nil? @(rs/get store (hash "0"))))))

  (testing "error"
    (with-system [{store ::rs/kv} (failing-kv-store-system "msg-154312")]
      (given-failed-future (rs/get store (hash "0"))
        ::anom/message := "msg-154312"))))


(deftest multi-get-test
  (testing "success with one hash"
    (let [content {:fhir/type :fhir/Patient :id "0"}
          hash (hash/generate content)]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (kv/put! kv-store (bs/to-byte-array hash) (fhir-spec/unform-cbor content))

        (is (= {hash content} @(rs/multi-get store [hash]))))))

  (testing "success with two hashes"
    (let [content-0 {:fhir/type :fhir/Patient :id "0"}
          hash-0 (hash/generate content-0)
          content-1 {:fhir/type :fhir/Patient :id "1"}
          hash-1 (hash/generate content-1)]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (kv/put! kv-store (bs/to-byte-array hash-0) (fhir-spec/unform-cbor content-0))
        (kv/put! kv-store (bs/to-byte-array hash-1) (fhir-spec/unform-cbor content-1))

        (is (= {hash-0 content-0 hash-1 content-1}
               @(rs/multi-get store [hash-0 hash-1]))))))

  (testing "parsing error"
    (let [hash (hash "0")]
      (with-system [{store ::rs/kv kv-store ::kv/mem} system]
        (kv/put! kv-store (bs/to-byte-array hash) (invalid-content))

        (given-failed-future (rs/multi-get store [hash])
          ::anom/message :# "Error while parsing resource content(.|\\s)*"))))

  (testing "not-found"
    (with-system [{store ::rs/kv} system]
      (is (= {} @(rs/multi-get store [(hash "0")])))))

  (testing "error"
    (with-system [{store ::rs/kv} (failing-kv-store-system "msg-154312")]
      (given-failed-future (rs/multi-get store [(hash "0")])
        ::anom/message := "msg-154312"))))


(deftest put-test
  (let [content {:fhir/type :fhir/Patient :id "0"}
        hash (hash/generate content)]
    (with-system [{store ::rs/kv} system]
      @(rs/put! store {hash content})

      (is (= content @(rs/get store hash))))))
