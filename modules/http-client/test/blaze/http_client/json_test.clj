(ns blaze.http-client.json-test
  (:require
   [blaze.http-client.json :as json]
   [blaze.http-client.json-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [java.io ByteArrayInputStream IOException InputStream]
   [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- input-stream [^String s]
  (ByteArrayInputStream. (.getBytes s StandardCharsets/UTF_8)))

(defn- parse [constraints s]
  (json/parse-json (json/object-mapper constraints) (input-stream s)))

(defn- nested
  "Returns a JSON document of `depth` nested objects."
  [depth]
  (str (str/join (repeat depth "{\"a\":")) "1" (str/join (repeat depth "}"))))

(defn- digits [n]
  (str/join (repeat n "1")))

(defn- letters [n]
  (str/join (repeat n "a")))

(deftest object-mapper-test
  (testing "keyword keys"
    (given (parse {} "{\"a\":{\"b\":[1,\"c\"]}}")
      [:a :b] := [1 "c"]))

  (testing "duplicate keys are rejected"
    (given (parse {} "{\"a\":1,\"a\":2}")
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s)Duplicate field 'a'.*"))

  (testing "trailing tokens are rejected"
    (given (parse {} "{\"a\":1} {}")
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s).*Trailing token.*"))

  (testing "trailing whitespace is accepted"
    (given (parse {} "{\"a\":1} \n")
      :a := 1))

  (testing "duplicate keys are rejected in nested objects"
    (given (parse {} "{\"a\":{\"b\":1,\"b\":1}}")
      ::anom/category := ::anom/incorrect))

  (testing "max-nesting-depth"
    (is (= 1 (get-in (parse {:max-nesting-depth 3} (nested 3)) [:a :a :a])))
    (given (parse {:max-nesting-depth 3} (nested 4))
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s).*nesting depth.*"))

  (testing "max-number-length"
    (is (= 11111 (:a (parse {:max-number-length 5} (format "{\"a\":%s}" (digits 5))))))
    (given (parse {:max-number-length 5} (format "{\"a\":%s}" (digits 6)))
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s).*Number value length.*"))

  (testing "max-name-length"
    (is (= 1 ((keyword (letters 10)) (parse {:max-name-length 10} (format "{\"%s\":1}" (letters 10))))))
    (given (parse {:max-name-length 10} (format "{\"%s\":1}" (letters 11)))
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s).*Name length.*"))

  (testing "max-string-length"
    (is (= (letters 10) (:a (parse {:max-string-length 10} (format "{\"a\":\"%s\"}" (letters 10))))))
    (given (parse {:max-string-length 10} (format "{\"a\":\"%s\"}" (letters 11)))
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s).*String value length.*"))

  (testing "max-document-length"
    (doseq [n [10 8000 8001 100000]]
      (let [doc (format "{\"a\":\"%s\"}" (letters n))]
        (is (= (letters n) (:a (parse {:max-document-length (count doc)} doc))))
        (given (parse {:max-document-length (dec (count doc))} doc)
          ::anom/category := ::anom/incorrect
          ::anom/message :# "(?s).*Document length.*"))))

  (testing "missing keys keep Jackson's defaults"
    (testing "nesting depth of 1000"
      (is (map? (parse {} (nested 999))))
      (given (parse {} (nested 1001))
        ::anom/category := ::anom/incorrect))

    (testing "number length of 1000"
      (is (number? (:a (parse {} (format "{\"a\":%s}" (digits 1000))))))
      (given (parse {} (format "{\"a\":%s}" (digits 1001)))
        ::anom/category := ::anom/incorrect))

    (testing "name length of 50000"
      (is (map? (parse {} (format "{\"%s\":1}" (letters 50000)))))
      (given (parse {} (format "{\"%s\":1}" (letters 50001)))
        ::anom/category := ::anom/incorrect))

    (testing "unlimited document length"
      (is (map? (parse {:max-string-length 3000000} (format "{\"a\":\"%s\"}" (letters 2000000))))))))

(defn- recording-input-stream
  "Returns an input stream of `s` that sets `closed` to true on close."
  [^String s closed]
  (proxy [ByteArrayInputStream] [(.getBytes s StandardCharsets/UTF_8)]
    (close []
      (reset! closed true))))

(deftest parse-response-test
  (testing "a 2xx response is parsed and its body closed"
    (doseq [status [200 201 299]]
      (let [closed (atom false)]
        (given (json/parse-response (json/object-mapper {})
                                    {:status status :body (recording-input-stream "{\"a\":1}" closed)})
          :a := 1)
        (is (true? @closed)))))

  (testing "an invalid 2xx response is an anomaly and its body closed"
    (let [closed (atom false)]
      (given (json/parse-response (json/object-mapper {})
                                  {:status 200 :body (recording-input-stream "{" closed)})
        ::anom/category := ::anom/incorrect)
      (is (true? @closed))))

  (testing "the body of a non-2xx response is closed without parsing"
    (doseq [status [199 302 404 500]]
      (let [closed (atom false)]
        (given (json/parse-response (json/object-mapper {})
                                    {:status status :body (recording-input-stream "{\"a\":1}" closed)})
          ::anom/category := ::anom/fault
          ::anom/message := (str "status: " status))
        (is (true? @closed) (str status)))))

  (testing "a failing close doesn't change the anomaly"
    (given (json/parse-response
            (json/object-mapper {})
            {:status 503
             :body (proxy [ByteArrayInputStream] [(byte-array 0)]
                     (close [] (throw (IOException. "msg-102512"))))})
      ::anom/category := ::anom/fault
      ::anom/message := "status: 503")))

(deftest parse-json-test
  (testing "invalid JSON"
    (given (parse {} "{")
      ::anom/category := ::anom/incorrect))

  (testing "empty input"
    (given (parse {} "")
      ::anom/category := ::anom/incorrect))

  (testing "failing input stream"
    (given (json/parse-json
            (json/object-mapper {})
            (proxy [InputStream] []
              (read
                ([] (throw (IOException. "msg-181021")))
                ([_ _ _] (throw (IOException. "msg-181021"))))))
      ::anom/category := ::anom/fault
      ::anom/message := "msg-181021"))

  (testing "non-object values"
    (is (= [1 2] (parse {} "[1,2]")))
    (is (= "a" (parse {} "\"a\"")))
    (is (nil? (parse {} "null")))))
