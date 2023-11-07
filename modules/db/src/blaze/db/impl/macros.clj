(ns blaze.db.impl.macros
  (:import
    [clojure.lang IReduceInit Sequential]))


(defmacro with-open-coll
  "Like `clojure.core/with-open` but opens and closes the resources on every
  reduce call to `coll`."
  [bindings coll]
  `(reify
     Sequential
     IReduceInit
     (reduce [_ rf# init#]
       (with-open ~bindings
         (reduce rf# init# ~coll)))))
