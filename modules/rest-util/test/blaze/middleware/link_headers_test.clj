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

  (testing "a single link not exceeding 2048 bytes is generated"
    (let [url (apply str (repeat 2035 "a"))]
      (given @((wrap-link-headers handler) (bundle {:relation "next" :url url}))
        [:headers "Link" count] := 2048
        [:body :fhir/type] := :fhir/Bundle)))

  (testing "a single link exceeding 2048 bytes is not generated"
    (given @((wrap-link-headers handler) (bundle {:relation "next" :url (apply str (repeat 2036 "a"))}))
      [:headers "Link"] := nil
      [:body :fhir/type] := :fhir/Bundle))

  (testing "links are dropped incrementally so the value fits into 2048 bytes"
    (let [self {:relation "self" :url (apply str (repeat 600 "s"))}
          first {:relation "first" :url (apply str (repeat 600 "f"))}
          previous {:relation "previous" :url (apply str (repeat 600 "p"))}
          next {:relation "next" :url (apply str (repeat 600 "n"))}]

      (testing "the previous link is dropped first"
        (given @((wrap-link-headers handler) (bundle self first previous next))
          [:headers "Link"] := (str "<" (:url self) ">;rel=\"self\","
                                    "<" (:url first) ">;rel=\"first\","
                                    "<" (:url next) ">;rel=\"next\"")
          [:body :fhir/type] := :fhir/Bundle))))

  (testing "the previous and first links are dropped to fit into 2048 bytes"
    (let [self {:relation "self" :url (apply str (repeat 700 "s"))}
          first {:relation "first" :url (apply str (repeat 700 "f"))}
          previous {:relation "previous" :url (apply str (repeat 700 "p"))}
          next {:relation "next" :url (apply str (repeat 700 "n"))}]
      (given @((wrap-link-headers handler) (bundle self first previous next))
        [:headers "Link"] := (str "<" (:url self) ">;rel=\"self\","
                                  "<" (:url next) ">;rel=\"next\"")
        [:body :fhir/type] := :fhir/Bundle)))

  (testing "the previous, first and self links are dropped to fit into 2048 bytes"
    (let [self {:relation "self" :url (apply str (repeat 1100 "s"))}
          first {:relation "first" :url (apply str (repeat 1100 "f"))}
          previous {:relation "previous" :url (apply str (repeat 1100 "p"))}
          next {:relation "next" :url (apply str (repeat 1100 "n"))}]
      (given @((wrap-link-headers handler) (bundle self first previous next))
        [:headers "Link"] := (str "<" (:url next) ">;rel=\"next\"")
        [:body :fhir/type] := :fhir/Bundle)))

  (testing "the link header is omitted when even the next link alone is too large"
    (let [self {:relation "self" :url (apply str (repeat 2100 "s"))}
          first {:relation "first" :url (apply str (repeat 2100 "f"))}
          previous {:relation "previous" :url (apply str (repeat 2100 "p"))}
          next {:relation "next" :url (apply str (repeat 2100 "n"))}]
      (given @((wrap-link-headers handler) (bundle self first previous next))
        [:headers "Link"] := nil
        [:body :fhir/type] := :fhir/Bundle))))
