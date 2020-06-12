(ns uno.game)

(def all-cards
  (concat
   (for [color [:red :yellow :green :blue]
         type (flatten (cons 0 (repeat 2 [1 2 3 4 5 6 7 8 9 :skip :reverse :draw-two])))]
     {:card/type type
      :card/color color})
   (for [type (flatten (repeat 4 [:wild :wild-draw-four]))]
     {:card/type type
      :card/color :wild})))

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

(defn handle-command [state command]
  (let [players (:game/players command)]
    (when-not (<= 2 (count players) 10)
      (throw (IllegalArgumentException. (str "expected 2-10 players, but was " (count players)))))
    (let [game {:game/draw-pile (shuffle all-cards)}
          game (reduce #(deal-cards %1 %2 7) game players)
          game (initialize-discard-pile game)]
      [(assoc game :event/type :game-started)])))
