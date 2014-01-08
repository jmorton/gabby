(ns gabby.rabbit
  "AMQP related functions"
  (:gen-class)
  (:require [langohr.core       :as lhcore]
            [langohr.channel    :as lch]
            [langohr.queue      :as lhq]
            [langohr.exchange   :as lhe]
            [langohr.consumers  :as lhc]
            [langohr.basic      :as lhb]
            [clojure.stacktrace :as st]
            [clojure.tools.logging :as log]
            [gabby.jabber       :as jabber]))

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
    (jabber/say xmpp-conn id msg))
  Watcher
  (watch [this exchange]
    (lhq/bind amqp-chan (.getQueue amqp-queue) exchange :routing-key "#"))
  (unwatch [this exchange]
    (lhq/bind amqp-chan (.getQueue amqp-queue) exchange :routing-key "#")
    (lhq/unbind amqp-chan (.getQueue amqp-queue) exchange "#")))

(defn create-relay
  "Turns AMQP messages into an XMPP message"
  [session]
  (log/debug "creating a relay")
  (fn [amqp-chan metadata ^bytes payload]
    (chat session (str metadata))
    (chat session (String. payload "UTF-8"))))

(defn find-session [sessions packet]
  "Retrive something that exists corresponding to identifying
   information in the packet: who sent the message."
  (log/debug "finding session")
  (find @sessions (:from packet)))

(defn create-session
  "Establishes an AMQP queue and channel.  A queue may be bound
   to multiple exchanges.  A dedicated channel isolates errors to
   a single person.  If shared, one person could interrupt channels
   used by other people!"
  [sessions packet xmpp amqp]
  (log/debug "creating session for the user")
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
  (log/debug "binding queue from an exchange")
  (io!
   (if-let [exchange (last (re-matches #"^bind: ([a-z0-9\/\.\-\_]+)$" (:body packet)))]
     (do (watch session exchange)
         (chat session (str "Binding to " exchange)))))
  [session packet])

(defn unbind-handler
  "Unbind a queue and exchange so that a person no longer has
   messages relayed for that binding."
  [session packet]
  (log/debug "unbinding queue from an exchange")
  (io!
   (if-let [exchange (last (re-matches #"^unbind: ([a-z0-9\/\.\-\_]+)$" (:body packet)))]
     (do (unwatch session exchange)
         (chat session (str "Unbinding from " exchange)))))
  [session packet])

(defn help-handler
  "I'd like to help, but I don't know how yet.  This should tell people
   how to bind and unbind."
  [session packet]
  (log/debug "sending help message")
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
  (log/debug "routing packet ")
  (try
    (let [session (val (or (find-session sessions packet)
                           (create-session sessions packet xmpp amqp)))]
      (->> [session packet]
           (apply bind-handler)
           (apply unbind-handler)
           (apply help-handler)))
    (catch Exception e (clojure.stacktrace/print-stack-trace e))))

(defn connect [conf]
  (log/debug "connecting to rabbitmq")
  (lhcore/connect conf))

(defn close [conn]
  (log/debug "disconnecting from rabbitmq")
  (lhcore/close conn))

(def ^:dynamic *default-config* lhcore/*default-config*)
