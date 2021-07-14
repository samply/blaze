(ns blaze.elm.compiler.conditional-operators-test
  (:require
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 15.2. If
;;
;; The If operator evaluates a condition, and returns the then argument if the
;; condition evaluates to true; if the condition evaluates to false or null, the
;; result of the else argument is returned. The static type of the then argument
;; determines the result type of the conditional, and the else argument must be
;; of that same type.
(deftest compile-if-test
  (testing "Static"
    (are [elm res] (= res (c/compile {} elm))
      #elm/if[#elm/boolean "true" #elm/integer"1" #elm/integer"2"] 1
      #elm/if[#elm/boolean "false" #elm/integer"1" #elm/integer"2"] 2
      #elm/if[{:type "Null"} #elm/integer"1" #elm/integer"2"] 2))

  (testing "Dynamic"
    (are [elm res] (= res (tu/dynamic-compile-eval elm))
      #elm/if[#elm/parameter-ref"true" #elm/integer"1" #elm/integer"2"] 1
      #elm/if[#elm/parameter-ref"false" #elm/integer"1" #elm/integer"2"] 2
      #elm/if[#elm/parameter-ref"nil" #elm/integer"1" #elm/integer"2"] 2)))
