(ns uno.schema
  (:require [schema-refined.core :as refined]
            [schema.core :as s]))

(s/defschema CardType
  (s/enum 0 1 2 3 4 5 6 7 8 9 :skip :reverse :draw-two :wild :wild-draw-four))

(s/defschema CardColor
  (s/enum :red :yellow :green :blue :wild)) ; TODO: remove wild as a color

(s/defschema Card
  ;; TODO: have wild cards without color
  {:card/type CardType
   :card/color CardColor})

(def validate-card (s/validator Card))

(s/defschema PlayerId s/Keyword)

;;;; Events

(s/defschema GameWasStarted
  {:event/type (s/eq :game.event/game-was-started)
   :game/players {PlayerId {:player/hand [Card]}}
   :game/discard-pile [Card]
   :game/draw-pile [Card]
   :game/current-player PlayerId
   :game/next-players [PlayerId]})

(s/defschema CardWasPlayed
  {:event/type (s/eq :game.event/card-was-played)
   :event/player PlayerId
   :card/type CardType
   :card/color CardColor})

(def event-schemas
  {:game.event/card-was-played CardWasPlayed
   :game.event/game-was-started GameWasStarted})

(s/defschema Event
  (apply refined/dispatch-on :event/type (flatten (seq event-schemas))))

(def ^:private event-validator (s/validator Event))

(defn validate-event [event]
  (when-not (contains? event-schemas (:event/type event))
    (throw (ex-info (str "Unknown event type " (pr-str (:event/type event)))
                    {:event event})))
  (event-validator event))


;;;; Commands

(s/defschema StartGame
  {:command/type (s/eq :game.command/start-game)
   :game/players [s/Keyword]})

(s/defschema PlayCard
  {:command/type (s/eq :game.command/play-card)
   :command/player PlayerId
   :card/type CardType
   :card/color CardColor})

(def command-schemas
  {:game.command/play-card PlayCard
   :game.command/start-game StartGame})

(s/defschema Command
  (apply refined/dispatch-on :command/type (flatten (seq command-schemas))))

(def ^:private command-validator (s/validator Command))

(defn validate-command [command]
  (when-not (contains? command-schemas (:command/type command))
    (throw (ex-info (str "Unknown command type " (pr-str (:command/type command)))
                    {:command command})))
  (command-validator command))
