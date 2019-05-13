(ns blaze.handler.cql-evaluation
  "A CQL evaluation handler modeled after the evaluate endpoint of

  https://github.com/DBCG/cql_execution_service"
  (:require
    [cheshire.core :as json]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [blaze.cql-translator :as cql]
    [blaze.datomic.pull :as pull]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.evaluator :as evaluator]
    [blaze.elm.util :as elm-util]
    [blaze.middleware.cors :refer [wrap-cors]]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time OffsetDateTime]))


(defn- location [locator]
  (if-let [l (first (str/split locator #"-"))]
    (str "[" l "]")
    "[?:?]"))


(defn- primitive? [type-name]
  (or (Character/isLowerCase ^char (first type-name)) (= "Quantity" type-name)))


(defn- bundle [{:keys [result type locator] :as full-result}]
  (case (:type type)
    "ListTypeSpecifier"
    (let [[_ type-name] (elm-util/parse-qualified-name (:name (:elementType type)))]
      (if (primitive? type-name)
        {:result (pr-str (into [] (comp (take 2) (map str)) result))
         :location (location locator)
         :resultType type-name}
        {:result
         (json/generate-string
           (into
             []
             (comp
               (take 2)
               (map #(pull/pull-summary type-name %))
               (map #(assoc % :resourceType type-name)))
             result)
           {:key-fn name
            :pretty true})
         :location (location locator)
         :resultType "Bundle"}))
    "NamedTypeSpecifier"
    {:result result
     :location (location locator)
     :resultType (second (elm-util/parse-qualified-name (:name type)))}
    (throw (ex-info (str "Unexpected type `" (:type type) "`.") full-result))))


(defn- to-error [deferred]
  (md/chain'
    deferred
    (fn [result]
      (if (::anom/category result)
        (md/error-deferred result)
        (md/success-deferred result)))))


(defn- handler-intern [conn cache]
  (fn [{:keys [body]}]
    (if-let [code (get body "code")]
      (let [db (d/db conn)]
        (-> (md/let-flow'
              [elm (to-error (md/future (cql/translate code :locators? true)))
               compiled-library (to-error (md/future (compiler/compile-library db elm {})))
               results (evaluator/evaluate db (OffsetDateTime/now) compiled-library)]
              (ring/response
                (mapcat
                  (fn [[name result]]
                    (if (instance? Exception result)
                      [{:name name
                        :error (.getMessage ^Exception result)
                        :location "[?:?]"}]
                      (if-let [bundle (bundle result)]
                        [(assoc bundle :name name)]
                        [])))
                  results)))
            (md/catch'
              (fn [e]
                (log/error e)
                (ring/response
                  [{:translation-error
                    (cond
                      (::anom/category e)
                      (or (::anom/message e)
                          (name (::anom/category e)))

                      (instance? Exception e)
                      (.getMessage ^Exception e)

                      :else
                      "Unknown error")}])))))
      (ring/response [{:translation-error "Missing CQL code"}]))))


(s/def :handler/cql-evaluation fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn :cache some?)
  :ret :handler/cql-evaluation)

(defn handler
  "Takes a Datomic `conn` and aa `cache` atom and returns a CQL evaluation
  Ring handler."
  [conn cache]
  (-> (handler-intern conn cache)
      (wrap-exception)
      (wrap-json)
      (wrap-cors)))
