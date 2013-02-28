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
  "Yes, you can send an XMPP message."
  (chat [this] [this msg]))

(defprotocol Watcher
  "Yes, you can watch (or unwatch) an exchange."
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
    (chat session (str metadata))
    (chat session (String. payload "UTF-8"))))

(defn find-session [sessions packet]
  "Retrive something that exists corresponding to identifying
   information in the packet: who sent the message."
  (find @sessions (:from packet)))

(defn create-session
  "Establishes an AMQP queue and channel.  A queue may be bound
   to multiple exchanges.  A dedicated channel isolates errors to
   a single person.  If shared, one person could interrupt channels
   used by other people!"
  [sessions packet xmpp amqp]
  (let [channel  (lch/open amqp)
        queue    (lhq/declare channel (:from packet))
        session  (Session. (:from packet) xmpp channel queue)
        relay    (create-relay session)]
    (lhc/subscribe channel (.getQueue queue) relay :auto-ack true)
    (swap! sessions assoc (:from packet) session)))

(defn bind-handler
  "Bind the queue corresponding to the session to the specified
   exchange.  Return `[session packet]` so that the handler can
   be chained (this might be a dumb idea)."
  [session packet]
  (io!
   (if-let [exchange (last (re-matches #"^bind: ([a-z0-9\/\.\-\_]+)$" (:body packet)))]
     (do (watch session exchange)
         (chat session (str "Binding to " exchange)))))
  [session packet])

(defn unbind-handler
  "Unbind a queue and exchange so that a person no longer has
   messages relayed for that binding."
  [session packet]
  (io!
   (if-let [exchange (last (re-matches #"^unbind: ([a-z0-9\/\.\-\_]+)$" (:body packet)))]
     (do (unwatch session exchange)
         (chat session (str "Unbinding from " exchange)))))
  [session packet])

(defn help-handler
  "I'd like to help, but I don't know how yet.  This should tell people
   how to bind and unbind."
  [session packet]
  (io!
   (if-let [match (re-seq #"^help$" (:body packet))]
     (chat session "I'd like to help...")))
  [session packet])

(defn route
  "Use the packet to find or create a session and then apply
   handling functions to the message.  This evaluates all
   functions, not just the best one!  In the future, maybe
   metadata can be used to pick the most appropriate one..."
  [sessions packet & {:keys [xmpp amqp]}]
  (try
    (let [session (val (or (find-session sessions packet)
                           (create-session sessions packet xmpp amqp)))]
      (->> [session packet]
           (apply bind-handler)
           (apply unbind-handler)
           (apply help-handler)))
    (catch Exception e (clojure.stacktrace/print-stack-trace e))))

(defn start [amqp-conf xmpp-conf]
  "Connect to the XMPP and AMQP servers, and create a session map
   to keep track of people that have talked to the system."
  (let [amqp (lhcore/connect amqp-conf)
        xmpp (g/auth xmpp-conf)
        sessions (atom {})]
    (g/listen xmpp (fn [packet]
                     (route sessions packet :amqp amqp :xmpp xmpp)))
    [amqp xmpp sessions]))

(defn stop [amqp-conn xmpp-conn & more]
  "Close the XMPP and AMQP connections.  Channels will disconnect
   automatically."
  (lhcore/close amqp-conn)
  (g/close xmpp-conn))

(defn -main [& args]
  (let [amqp-config (merge lhcore/*default-config*
                           (read-string (slurp "config/amqp.clj")))
        xmpp-config (merge g/*default-config*
                           (read-string (slurp "config/xmpp.clj")))]
    (start amqp-config xmpp-config))
  (loop [] (Thread/sleep 10000) (recur)))


; let there be something like a command...
; this is a function that does something...
; but it requires certain input and maybe confirmation...
; the command has some example (prototypical) input
; all commands can be cancelled with a certain phrase
; commands can require confirmation
