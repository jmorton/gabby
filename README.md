# Hello! I'm Gabby.

&hellip;and I *love* to chat.

## Officially&hellip;

I don't know how we can work together quite yet.

## Unofficially&hellip;

We can play at the REPL.

Update `config.clj` with credentials that I can use to send and receive messages.

```
{ :user     "your-user-name"
  :pass     "your-password"
  :host     "where-to-chief"
  :port     5222
  :domain   "not-sure-you-need-this... maybe"
}
```

Fire up a REPL.

```
lein repl
```

Send yourself a message.

```clojure
(require '[gabby.core :as g])
(def credentials-for-gabby (read-string (slurp "config.clj")))
(def gab (g/authenticate credentials-for-gabby))
(def you (g/chat gab "chatty-chuck@example.com"))
(g/say you "hello world")
```

## License

Copyright © 2013 Jonathan Morton

Distributed under the Eclipse Public License, the same as Clojure.
