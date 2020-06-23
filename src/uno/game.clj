(ns uno.game
  (:import (uno GameRulesViolated)))

(def all-cards
  (let [color-cards-x1 [0]
        color-cards-x2 [1 2 3 4 5 6 7 8 9 :skip :reverse :draw-two]
        wild-cards [:wild :wild-draw-four]]
    (concat
     (for [color [:red :yellow :green :blue]
           type (concat color-cards-x1
                        color-cards-x2
                        color-cards-x2)]
       {:card/type type
        :card/color color})
     (for [type (flatten (repeat 4 wild-cards))]
       {:card/type type}))))

(def starting-hand-size 7)

(defn wild-card? [card]
  (contains? #{:wild :wild-draw-four} (:card/type card)))

;; TODO: avoid the need for this function; don't reuse :card/color for wild cards?
(defn- normalize-wild-card [card]
  (if (wild-card? card)
    (dissoc card :card/color)
    card))

(defn remove-card [deck card]
  (cond
    (empty? deck) (throw (IllegalArgumentException. (str "card not found: " (pr-str card))))
    (= card (first deck)) (rest deck)
    :else (cons (first deck) (remove-card (rest deck) card))))

(defn- draw-cards [game n]
  (let [draw-pile (:game/draw-pile game)
        [drawn remaining] (split-at n draw-pile)
        game (assoc game :game/draw-pile remaining)]
    [game drawn]))

(defn- deal-cards [game player n]
  (let [[game dealt-cards] (draw-cards game n)]
    (update-in game [:game/players player :player/hand] concat dealt-cards)))

(defn- initialize-discard-pile [game]
  (let [[game discard-pile] (draw-cards game 1)]
    (assoc game :game/discard-pile discard-pile)))

(defn- check-is-current-player [player game]
  (let [current-player (:game/current-player game)]
    (when-not (= current-player player)
      (throw (GameRulesViolated. (str "not current player; expected " (pr-str current-player)
                                      ", but was " (pr-str player)))))))

(defn- card-matches? [card previous-card]
  (or (= (:card/type previous-card)
         (:card/type card))
      (= (:card/color previous-card)
         (:card/color card))
      (= :wild (:card/type card))))

(defn- card-can-be-played? [card game]
  (let [top-card (first (:game/discard-pile game))]
    (card-matches? card top-card)))


;;;; Read model

(defmulti projection (fn [_game event]
                       (:event/type event)))

(defmethod projection :game.event/game-was-started
  [game event]
  (assert (nil? game) {:game game})
  (select-keys event [:game/players :game/discard-pile :game/draw-pile
                      :game/current-player :game/next-players]))

(defmethod projection :game.event/card-was-played
  [game event]
  (let [player (:event/player event)
        card (:event/card event)
        color (:card/effective-color event)]
    (-> game
        (update-in [:game/players player :player/hand] remove-card card)
        (update :game/discard-pile #(cons (assoc card :card/color color) %))))) ; TODO next: separate the current color from the discard pile

(defmethod projection :game.event/card-was-not-played
  [game _event]
  (assoc game :game/draw-penalty-card? true))

(defmethod projection :game.event/card-was-drawn
  [game event]
  (let [player (:event/player event)
        card (:event/card event)]
    (-> game
        (update :game/draw-pile remove-card card)
        (update-in [:game/players player :player/hand] #(cons card %))
        (assoc :game/last-drawn-card card))))

(defmethod projection :game.event/player-turn-has-ended
  [game event]
  (let [players (:game/next-players event)]
    ;; TODO: clear turn specific state (e.g. :game/draw-penalty-card? :game/last-drawn-card)
    ;; TODO: store turn specific state under one key so that it can be cleared easily
    (-> game
        (assoc :game/current-player (first players))
        (assoc :game/next-players (rest players)))))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _game _injections]
                                      (:command/type command)))

(defmethod command-handler :game.command/start-game
  [command game _injections]
  (assert (nil? game))
  (let [players (:game/players command)]
    (when-not (<= 2 (count players) 10)
      (throw (GameRulesViolated. (str "expected 2-10 players, but was " (count players)))))
    (let [game {:game/draw-pile (shuffle all-cards)}
          game (reduce #(deal-cards %1 %2 starting-hand-size) game players)
          game (initialize-discard-pile game)]
      [(assoc game
              :game/current-player (first players)
              :game/next-players (rest players)
              :event/type :game.event/game-was-started)])))

(defmethod command-handler :game.command/play-card
  [command game _injections]
  (let [player (:command/player command)
        hand (get-in game [:game/players player :player/hand])
        card (:command/card command)
        color (:card/effective-color command)
        top-card (first (:game/discard-pile game))
        last-drawn-card (:game/last-drawn-card game)]
    (check-is-current-player player game)
    (when-not (contains? (set hand) (normalize-wild-card card))
      (throw (GameRulesViolated. (str "card not in hand; tried to play " (pr-str card)
                                      ", but hand was " (pr-str hand)))))
    (when-not (card-matches? card top-card)
      (throw (GameRulesViolated. (str "card " (pr-str card)
                                      " does not match the card " (pr-str top-card)
                                      " in discard pile"))))
    (when (and (:game/draw-penalty-card? game)
               (not= last-drawn-card card))
      (throw (GameRulesViolated. (str "can only play the card that was just drawn; tried to play " (pr-str card)
                                      ", but just drew " (pr-str last-drawn-card)))))
    [{:event/type :game.event/card-was-played
      :event/player player
      :event/card (normalize-wild-card card)
      :card/effective-color color}
     {:event/type :game.event/player-turn-has-ended
      :event/player player
      :game/next-players (concat (:game/next-players game) [player])}]))

(defmethod command-handler :game.command/do-not-play-card
  [command game _injections]
  (let [player (:command/player command)]
    (check-is-current-player player game)
    (cond
      ;; passing the first time
      (not (:game/draw-penalty-card? game))
      ;; TODO: reshuffle if the draw pile is empty
      (let [card (first (:game/draw-pile game))]
        [{:event/type :game.event/card-was-not-played
          :event/player player}
         {:event/type :game.event/card-was-drawn
          :event/player player
          :event/card card}])

      ;; cannot pass a second time if the drawn card can be played
      (card-can-be-played? (:game/last-drawn-card game) game)
      (throw (GameRulesViolated. "the card that was just drawn can be played, so it must be played"))

      ;; passing a second time
      :else
      [{:event/type :game.event/card-was-not-played
        :event/player player}
       {:event/type :game.event/player-turn-has-ended
        :event/player player
        :game/next-players (concat (:game/next-players game) [player])}])))

(defn handle-command [command game]
  (command-handler command game {}))
