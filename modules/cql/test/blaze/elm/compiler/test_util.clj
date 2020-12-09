(ns blaze.elm.compiler.test-util
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.literal :as elm]
    [blaze.elm.literal-spec]
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [is testing]]
    [clojure.test.check :as tc]
    [cognitect.anomalies :as anom])
  (:import
    [java.time OffsetDateTime ZoneOffset]))


(defn instrument-compile []
  (st/instrument
    `c/compile
    {:spec
     {`c/compile
      (s/fspec
        :args (s/cat :context map? :expression :elm/expression))}}))


(defn code
  ([system code]
   (elm/instance
     ["{urn:hl7-org:elm-types:r1}Code"
      {"system" (elm/string system) "code" (elm/string code)}]))
  ([system version code]
   (elm/instance
     ["{urn:hl7-org:elm-types:r1}Code"
      {"system" (elm/string system)
       "version" (elm/string version)
       "code" (elm/string code)}])))


(defmacro satisfies-prop [num-tests prop]
  `(let [result# (tc/quick-check ~num-tests ~prop)]
     (if (instance? Throwable (:result result#))
       (throw (:result result#))
       (if (true? (:result result#))
         (is :success)
         (is (clojure.pprint/pprint result#))))))


(def patient-retrieve-elm
  {:type "Retrieve" :dataType "{http://hl7.org/fhir}Patient"})


(defmethod test/assert-expr 'thrown-anom? [msg form]
  (let [category (second form)
        body (nthnext form 2)]
    `(try ~@body
          (test/do-report {:type :fail, :message ~msg,
                           :expected '~form, :actual nil})
          (catch Exception e#
            (let [m# (::anom/category (ex-data e#))]
              (if (= ~category m#)
                (test/do-report {:type :pass, :message ~msg,
                                 :expected '~form, :actual e#})
                (test/do-report {:type :fail, :message ~msg,
                                 :expected '~form, :actual e#})))
            e#))))


(def now (OffsetDateTime/now (ZoneOffset/ofHours 0)))


(defn binary-operand [type]
  {:type type :operand [{:type "Null"} {:type "Null"}]})


(defmacro unsupported-binary-operand [type]
  `(is (~'thrown-with-msg? Exception #"Unsupported" (c/compile {} (binary-operand ~type)))))


(defn unary-operand [type]
  {:type type :operand {:type "Null"}})


(defmacro unsupported-unary-operand [type]
  `(is (~'thrown-with-msg? Exception #"Unsupported" (c/compile {} (unary-operand ~type)))))


(defmacro testing-unary-static-null [elm-constructor]
  `(testing "Static Null"
     (is (nil? (c/compile {} (~elm-constructor {:type "Null"}))))))


(defmacro testing-unary-dynamic-null [elm-constructor]
  `(testing "Dynamic Null"
     (with-open [node# (mem-node-with [])]
       (let [context# {:eval-context "Patient" :node node#}
             elm# (~elm-constructor (elm/singleton-from patient-retrieve-elm))
             expr# (c/compile context# elm#)]
         (is (nil? (core/-eval expr# {} nil nil)))))))


(defmacro testing-unary-null [elm-constructor]
  `(do
     (testing-unary-static-null ~elm-constructor)
     (testing-unary-dynamic-null ~elm-constructor)))


(defmacro testing-binary-static-null [elm-constructor non-null-op-1 non-null-op-2]
  `(testing "Static Null"
     (is (nil? (c/compile {} (~elm-constructor [{:type "Null"} {:type "Null"}]))))
     (is (nil? (c/compile {} (~elm-constructor [~non-null-op-1 {:type "Null"}]))))
     (is (nil? (c/compile {} (~elm-constructor [{:type "Null"} ~non-null-op-2]))))))


(defmacro testing-binary-dynamic-null [elm-constructor non-null-op-1 non-null-op-2]
  `(with-open [node# (mem-node-with [])]
     (let [context# {:eval-context "Patient" :node node#}]
       (testing "Dynamic Null"
         (let [elm# (~elm-constructor
                      [(elm/singleton-from patient-retrieve-elm)
                       (elm/singleton-from patient-retrieve-elm)])
               expr# (c/compile context# elm#)]
           (is (nil? (core/-eval expr# {} nil nil))))
         (let [elm# (~elm-constructor
                      [~non-null-op-1
                       (elm/singleton-from patient-retrieve-elm)])
               expr# (c/compile context# elm#)]
           (is (nil? (core/-eval expr# {} nil nil))))
         (let [elm# (~elm-constructor
                      [(elm/singleton-from patient-retrieve-elm)
                       ~non-null-op-2])
               expr# (c/compile context# elm#)]
           (is (nil? (core/-eval expr# {} nil nil))))))))


(defmacro testing-binary-null
  ([elm-constructor non-null-op]
   `(testing-binary-null ~elm-constructor ~non-null-op ~non-null-op))
  ([elm-constructor non-null-op-1 non-null-op-2]
   `(do
      (testing-binary-static-null ~elm-constructor ~non-null-op-1 ~non-null-op-2)
      (testing-binary-dynamic-null ~elm-constructor ~non-null-op-1 ~non-null-op-2))))


(defn compile-unop [constructor op-constructor op]
  (c/compile {} (constructor (op-constructor op))))


(defn compile-unop-precision [constructor op-constructor op precision]
  (c/compile {} (constructor [(op-constructor op) precision])))


(defn compile-binop
  ([constructor op-constructor op-1 op-2]
   (compile-binop constructor op-constructor op-constructor op-1 op-2))
  ([constructor op-constructor-1 op-constructor-2 op-1 op-2]
   (c/compile {} (constructor [(op-constructor-1 op-1) (op-constructor-2 op-2)]))))


(defn compile-binop-precision [constructor op-constructor op-1 op-2 precision]
  (c/compile {} (constructor [(op-constructor op-1) (op-constructor op-2) precision])))


(def dynamic-resource
  "ELM expression returning the current resource."
  (elm/singleton-from patient-retrieve-elm))
