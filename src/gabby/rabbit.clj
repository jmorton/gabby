(ns gabby.rabbit
  ^{:author "Jon Morton"}
  (:gen-class)
  (:use [gabby.core :only (say) :as g])
  (:require [langohr.core       :as rmq]
            [langohr.channel    :as lch]
            [langohr.queue      :as lq]
            [langohr.exchange   :as le]
            [langohr.consumers  :as lc]
            [langohr.basic      :as lb]))

(def xmpp-credentials (read-string (slurp "config.clj")))
(def amqp-credentials (merge rmq/*default-config* (read-string (slurp "amqp.clj"))))

; These are shared between everyone that wants to get notified.
; I'm not sure if this is proper or not...
(def conn (atom (rmq/connect)))
(def chan (atom (lch/open @conn)))
(def bot (g/authenticate xmpp-credentials))

(defn start-consumer
  [channel queue exchange-name routing-key callback]
  (let [queue' (.getQueue (lq/declare channel "" :exclusive true :auto-delete true))]
    (lq/bind channel queue' exchange-name :routing-key "#")
    (.start (Thread. (fn [] (lc/subscribe channel queue' callback :auto-ack true))))))

; Builds a function that causes queue messages to send a Jabber message
; to the person associated with the chat.
(defn handler [chat message]
  (fn [ch {:keys [routing-keys] :as meta} ^bytes payload]
    (g/say chat (String. payload "UTF-8"))))

(defn subscribe [chat message]
  (let [pattern #"subscribe: ([0-9a-z\.\-]+)/([0-9a-z\.\-\#]+)"
        [match exchange routing-key] (re-matches pattern (:body message))]
    (cond
     (nil? match) (g/say chat "Sorry, I don't understand that, I'm new here.")
     :else (do
             (start-consumer @chan "" "foo" "#" (handler chat message))
             (g/say chat (str "I want to subscribe you to '" exchange "' with the key '" routing-key "'"))))))

; not sure if this is the right way to set values or not...
(defn live [])

; ...or the best way to cleanup.
(defn die []
  (g/logout bot)
  (rmq/close @chan)
  (rmq/close @conn))

(g/chat-started bot subscribe)

(defn -main [& args]
(let []
  (g/chat-started bot subscribe)
  (loop [] (Thread/sleep 1000) (recur))))
