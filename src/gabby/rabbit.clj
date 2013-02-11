(ns gabby.rabbit
  "Bridge between XMPP and AMQP.  Intended for observation (read) only."
  (:gen-class)
  (:use [gabby.core             :as g])
  (:require [langohr.core       :as rmq]
            [langohr.channel    :as lch]
            [langohr.queue      :as lq]
            [langohr.exchange   :as le]
            [langohr.consumers  :as lc]
            [langohr.basic      :as lb]))

; I'm still learning how to structure programs in Clojure...
; I don't want to connect to AMQP or XMPP servers quite yet.
(declare connection channel bot)

(defn create-queue
  "Creates a queue and binds it to an exchange.  When the queue receives a message,
   it invokes f.  You can use create-relay to build a suitable function."
  [exchange route-key f]
  (let [queue (lq/declare channel "" :exclusive true :auto-delete true)
        queue-name (.getQueue queue)]
    (future ; non-blocking... make sense?
      (lq/bind channel queue-name exchange :routing-key route-key)
      (lc/subscribe channel queue-name f :auto-ack true))))

(defn create-generic-relay [from to]
  "Returns a function for turning AMQP messages into an XMPP message."
  (fn [ch metadata ^bytes payload]
    (g/say from to (String. payload "UTF-8"))))

(defn handle-subscription [from message]
  "Process a message from an XMPP user.  If it 'understands' what someone
   wants, then it will create a queue with a generic relay to notify them
   when AMQP messages are being received."
  (let [to (:from message)
        pattern #"subscribe: ([0-9a-z\.\-\_]+)/([0-9a-z\.\-\_\#\*]+)"
        [match exchange routing-key] (re-matches pattern (:body message))]
    (if match
      (do
        (create-queue exchange routing-key (create-generic-relay from to))
        (g/say from to (str "You've been subscribed."))
        true))))

(defn start
  []
  (let [bot (g/auth (read-string (slurp "config.clj")))]
        (def connection
          (rmq/connect (merge rmq/*default-config* (read-string (slurp "amqp.clj")))))
        (def channel
          (lch/open connection))
        (g/listen bot (fn [packet] (handle-subscription bot packet)))))

(defn main [& args]
  (start)
  (loop [] (Thread/sleep 10000) (recur)))
