(ns blaze.middleware.link-headers-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.middleware.link-headers :refer [wrap-link-headers]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- handler [bundle]
  (ac/completed-future (ring/response bundle)))

(defn- bundle-link [{:keys [relation url]}]
  {:fhir/type :fhir.Bundle/link
   :relation (type/string relation)
   :url (type/uri url)})

(defn- bundle [& links]
  (cond-> {:fhir/type :fhir/Bundle}
    (seq links)
    (assoc :link (mapv bundle-link links))))

(deftest wrap-link-headers-test
  (testing "no links"
    (given @((wrap-link-headers handler) (bundle))
      [:headers "Link"] := nil
      [:body :fhir/type] := :fhir/Bundle))

  (testing "one link"
    (given @((wrap-link-headers handler) (bundle {:relation "rel-151002" :url "url-150952"}))
      [:headers "Link"] := "<url-150952>;rel=\"rel-151002\""
      [:body :fhir/type] := :fhir/Bundle))

  (testing "two links"
    (given @((wrap-link-headers handler) (bundle {:relation "rel-0" :url "url-0"}
                                                 {:relation "rel-1" :url "url-1"}))
      [:headers "Link"] := "<url-0>;rel=\"rel-0\",<url-1>;rel=\"rel-1\""
      [:body :fhir/type] := :fhir/Bundle))

  (testing "link headers longer than 4096 bytes are not generated"
    (given @((wrap-link-headers handler) (bundle {:relation "rel" :url (apply str (repeat 4084 "a"))}))
      [:headers "Link"] := nil
      [:body :fhir/type] := :fhir/Bundle)))
