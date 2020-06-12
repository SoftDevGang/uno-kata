(ns uno.game-test
  (:require [clojure.test :refer :all]
            [uno.game :as game]
            [uno.schema :as schema]))

(defn handle-command [state command]
  (->> (game/handle-command state (schema/validate-command command))
       (map schema/validate-event)))

(deftest all-cards-test
  (is (= 108 (count game/all-cards)))
  (doseq [card game/all-cards]
    (is (schema/validate-card card))))

(deftest game-setup-test
  (testing "the game is for 2-10 players"
    (let [players [:player1 :player2 :player3 :player4 :player5
                   :player6 :player7 :player8 :player9 :player10 :player11]]
      (is (thrown-with-msg?
           IllegalArgumentException #"^expected 2-10 players, but was 1$"
           (handle-command nil {:command/type :game.command/start-game
                                :game/players (take 1 players)})))
      (is (handle-command nil {:command/type :game.command/start-game
                               :game/players (take 2 players)}))
      (is (handle-command nil {:command/type :game.command/start-game
                               :game/players (take 10 players)}))
      (is (thrown-with-msg?
           IllegalArgumentException #"^expected 2-10 players, but was 11$"
           (handle-command nil {:command/type :game.command/start-game
                                :game/players (take 11 players)})))))

  (let [events (handle-command nil {:command/type :game.command/start-game
                                    :game/players [:player1 :player2]})
        [game-started] events
        player1-hand (get-in game-started [:game/players :player1 :player/hand])
        player2-hand (get-in game-started [:game/players :player2 :player/hand])
        draw-pile (:game/draw-pile game-started)
        discard-pile (:game/discard-pile game-started)]
    (is (= 1 (count events)))

    (testing "every player starts with 7 cards, face down"
      (is (= 7 (count player1-hand)))
      (is (= 7 (count player2-hand))))

    (testing "one card is placed in the discard pile, face up"
      (is (= 1 (count discard-pile))))

    (testing "the rest of the cards are placed in a draw pile, face down"
      (is (= (frequencies game/all-cards)
             (frequencies (concat player1-hand player2-hand discard-pile draw-pile)))))))
