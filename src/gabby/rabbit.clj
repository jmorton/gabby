(ns gabby.rabbit
  "Bridge between XMPP and AMQP.  Intended for observation (read) only."
  (:gen-class)
  (:use [gabby.core             :as g])
  (:require [langohr.core       :as lhcore]
            [langohr.channel    :as lch]
            [langohr.queue      :as lhq]
            [langohr.exchange   :as lhe]
            [langohr.consumers  :as lhc]
            [langohr.basic      :as lhb]
            [clojure.stacktrace :as st]))

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
    (lhq/bind amqp-chan (.getQueue amqp-queue) exchange :routing-key "#"))
  (unwatch [this exchange]
    (lhq/bind amqp-chan (.getQueue amqp-queue) exchange :routing-key "#")
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
        queue    (lhq/declare channel (:from packet))
        session  (Session. (:from packet) xmpp channel queue)
        relay    (create-relay session)]
    (lhc/subscribe channel (.getQueue queue) relay :auto-ack true)
    (swap! sessions assoc (:from packet) session)))

(defn extract-exchange [str]
  (last (re-matches #"^bind: ([a-z0-9\/\.\-\_]+)$" str)))

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
   (if-let [exchange (last (re-matches #"^unbind: ([a-z0-9\/\.\-\_]+)$" (:body packet)))]
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
    (let [session (val (or (find-session sessions packet)
                           (create-session sessions packet xmpp amqp)))]
      (println session)
      (->> [session packet]
           (apply bind-handler)
           (apply unbind-handler)
           (apply help-handler)))
    (catch Exception e (clojure.stacktrace/print-stack-trace e))))

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

(defn main [& args]
  (let [amqp-config (merge lhcore/*default-config*
                           (read-string (slurp "config/amqp.clj")))
        xmpp-config (merge g/*default-config*
                           (read-string (slurp "config/xmpp.clj")))]
    (start amqp-config xmpp-config))
  (loop [] (Thread/sleep 10000) (recur)))
