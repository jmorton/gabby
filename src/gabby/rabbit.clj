(ns gabby.rabbit
  "Bridge between XMPP and AMQP.  Intended for observation (read) only."
  (:gen-class)
  (:use [gabby.core             :as g])
  (:require [langohr.core       :as lhcore]
            [langohr.channel    :as lch]
            [langohr.queue      :as lhq]
            [langohr.exchange   :as lhe]
            [langohr.consumers  :as lhc]
            [langohr.basic      :as lhb]))

; I'm still learning how to structure programs in Clojure...

{ "to" { :queue nil :channel nil, :bindings [],  } }

(defn create-queue
  "Creates a queue, and nothing else."
  [channel]
  (try
    (lhq/declare channel "" :exclusive true :auto-delete true)
    (catch Exception e
      (println (.getMessage e)))))

(defn subscribe
  "Creates a binding and subscribes a callback function"
  [channel queue exchange route-key f]
  (let [queue-name (.getQueue queue)]
    (lhq/bind channel queue-name exchange :routing-key route-key)
    (lhc/subscribe channel queue-name f :auto-ack true)))

(defn create-generic-relay [from to]
  "Returns a function for turning AMQP messages into an XMPP message."
  (fn [ch metadata ^bytes payload]
    (g/say from to (String. payload "UTF-8"))))

(defn handle-bind [from message channel relays]
  "Process a message from an XMPP user.  If it 'understands' what someone
   wants, then it will create a queue with a generic relay to notify them
   when AMQP messages are being received."
  (let [to (:from message)
        pattern #"^bind: ([0-9a-z\.\-\_]+)/([0-9a-z\.\-\_\#\*]+)"
        [match exchange routing-key] (re-matches pattern (:body message))
        queue (or (get @relays to) (create-queue channel))] ; has side effect
    (try
      (send relays assoc to queue) ; avoid logic about when to append... good idea?
      (if match
        (let [res (subscribe channel queue exchange routing-key (create-generic-relay from to))]
          (g/say from to (str "You've been subscribed to " exchange " '" routing-key "'"))))
      (catch Exception e
        (g/say from to (str "Errorish: " e))))))

(defn handle-unbind [from message channel relays]
  (let [to (:from message)
        queue (@relays to)
        queue-name (.getQueue queue)
        pattern #"^unbind: ([0-9a-z\.\-\_]+)/([0-9a-z\.\-\_\#\*]+)"
        [match exchange route-key] (re-matches pattern (:body message))]
    ;; if someone attempts to unbind from something they are not bound to
    ;; this tears down the connection... and everyone else's queues along
    ;; with it.
    (try
      (if match (do
        (lhq/bind channel queue-name exchange :routing-key route-key)
        (lhq/unbind channel queue-name exchange route-key)
        (g/say from to (str "Unsubscribe from " exchange " + " route-key))))
      (catch Exception e
        (g/say from to (str "Errorish: " e))))))

(defn handle-info [from message channel relays]
    (let [to (:from message)
          match (re-matches #"^info$" (:body message))]
      (try
        (if match
          (g/say from to (str "info: " (@relays to))))
        (catch Exception e
          (g/say from to (.getMessage e))))))

(defn start [rabbit-cfg xmpp-cfg]
  (let [connection (lhcore/connect rabbit-cfg)
        bot (g/auth xmpp-cfg)
        relays (agent {})
        channel (lch/open connection)
        res { :bot bot :conn connection, :relays relays }]
    (g/listen bot (fn [packet]
                    (handle-bind bot packet channel relays)
                    (handle-unbind bot packet channel relays)
                    (handle-info bot packet channel relays)))
    res))

(defn cleanup [cfg]
  (g/deauth (:bot cfg))
  (lhcore/close (:conn cfg)))


; (defn demo [] (def a (start (merge lhcore/*default-config* (read-string (slurp "amqp.clj"))) (read-string (slurp "config.clj")))))

(defn main [& args]
  (start (merge lhcore/*default-config* (read-string (slurp "amqp.clj")))
         (read-string (slurp "config.clj")))
  (loop [] (Thread/sleep 10000) (recur)))
