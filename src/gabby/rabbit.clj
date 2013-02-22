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

(defprotocol Chatter
  (chat [this] [this msg]))

(defprotocol Watcher
  (watch [this] [this exchange] [this exchange routing-key])
  (unwatch [this] [this exchange] [this exchange routing-key]))

(defrecord Session [id xmpp-conn amqp-chan amqp-queue]
  Chatter
  (chat [this msg]
    (g/say xmpp-conn id msg))
  Watcher
  (watch [this exchange]
    (lhq/bind amqp-chan (.getQueue amqp-queue) exchange))
  (unwatch [this exchange]
    (lhq/bind amqp-chan (.getQueue amqp-queue) exchange)
    (lhq/unbind amqp-chan (.getQueue amqp-queue) exchange "#")))

(defn create-relay
  "Turns AMQP messages into an XMPP message"
  [session]
  (fn [amqp-chan metadata ^bytes payload]
    (chat session (String. payload "UTF-8"))))

(defn find-session [sessions packet]
  (find @sessions (:from packet)))

(defn create-session
  "Creates a session and adds it to sessions."
  [sessions packet xmpp amqp]
  (let [channel  (lch/open amqp)
        queue    (lhq/declare channel)
        session  (Session. (:from packet) xmpp channel queue)
        relay    (create-relay session)]
    (lhc/subscribe channel (.getQueue queue) relay :auto-ack true)
    (swap! sessions assoc (:from packet) session)))

(defn extract-exchange [str]
  (last (re-matches #"^bind: ([a-z0-9\/\.\-]+)$" str)))

(defn bind-handler
  [session packet]
  (io!
   (if-let [exchange (extract-exchange (:body packet))]
     (do (watch session exchange)
         (chat session (str "Binding to " exchange)))))
  [session packet])

(defn unbind-handler
  [session packet]
  (io!
   (if-let [exchange (last
                      (re-matches
                       #"^unbind: ([a-z0-9\/\.\-]+)$"
                       (:body packet)))]
     (do (unwatch session exchange)
         (chat session "Unbinding"))))
  [session packet])

(defn help-handler
  [session packet]
  (io!
   (if-let [match (re-seq #"^help$" (:body packet))]
     (chat session "I'd like to help...")))
  [session packet])

(defn route [sessions packet & {:keys [xmpp amqp]}]
  (try
    (let [[key session] (or (find-session sessions packet)
                            (create-session sessions packet xmpp amqp))]
      (println key)
      (->> [session packet]
           (apply bind-handler)
           (apply unbind-handler)
           (apply help-handler)))
    (catch Exception e
      (println (.getMessage e)))))

(defn start [amqp-conf xmpp-conf]
  (let [amqp (lhcore/connect amqp-conf)
        xmpp (g/auth xmpp-conf)
        sessions (atom {})]
    (g/listen xmpp (fn [packet]
                     (route sessions packet :amqp amqp :xmpp xmpp)))
    [amqp xmpp sessions]))

(defn stop [r]
  (let [[amqp xmpp sessions] r]
    (lhcore/close amqp)
    (g/deauth xmpp)
    (swap! sessions {})))

; (def ac (merge lhcore/*default-config* (read-string (slurp "amqp.clj"))))
; (def xc (read-string (slurp "config.clj")))
(def r (start ac xc))
(stop r)

(defn main [& args]
  (let [amqp-config (merge lhcore/*default-config* (read-string (slurp "amqp.clj")))
        xmpp-config (read-string (slurp "config.clj"))]
    (start xmpp-config amqp-config))
  (loop [] (Thread/sleep 10000) (recur)))
