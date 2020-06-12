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

(defn draw-cards [game n]
  (let [draw-pile (:game/draw-pile game)
        [drawn remaining] (split-at n draw-pile)
        game (assoc game :game/draw-pile remaining)]
    [game drawn]))

(defn handle-command [state command]
  (let [players (:game/players command)]
    (when-not (<= 2 (count players) 10)
      (throw (IllegalArgumentException. (str "expected 2-10 players, but was " (count players)))))
    [(reduce (fn [game player]
               (let [[game dealt-cards] (draw-cards game 7)]
                 (assoc-in game [:game/players player :player/hand] dealt-cards)))
             {:event/type :game-started
              :game/draw-pile (shuffle all-cards)}
             players)]))
