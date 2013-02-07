(ns gabby.crq
  (:use [gabby.core :as g]))

(def credentials (read-string (slurp "config.clj")))

(defn reverser [chat message]
  (g/say chat (str "Reverse it! " (apply str (reverse (:body message))))))

(def bot (g/authenticate credentials))

(def jon (g/chat bot "jmorton@customink.com"))

(defn- main []
  (g/say jon "Ahoy")
  (g/listen jon reverser)
  (future
    (loop []
      (Thread/sleep 60000)
      (g/say jon "hi again...")
      (recur))))