(ns uno.game-test
  (:require [clojure.test :refer :all]
            [uno.game :as game]
            [uno.schema :as schema])
  (:import (uno GameRulesViolated)))

(def red-1 {:card/type 1, :card/color :red})
(def red-2 {:card/type 2, :card/color :red})
(def red-3 {:card/type 3, :card/color :red})
(def green-1 {:card/type 1, :card/color :green})
(def green-2 {:card/type 2, :card/color :green})
(def green-3 {:card/type 3, :card/color :green})
(def yellow-1 {:card/type 1, :card/color :yellow})
(def yellow-2 {:card/type 2, :card/color :yellow})
(def yellow-3 {:card/type 3, :card/color :yellow})
(def blue-1 {:card/type 1, :card/color :blue})
(def blue-2 {:card/type 2, :card/color :blue})
(def blue-3 {:card/type 3, :card/color :blue})
(def wild {:card/type :wild})

(defn- apply-events [events]
  (->> events
       (map schema/validate-event)
       (reduce game/projection nil)))

(defn- handle-command [command events]
  (->> (game/handle-command (schema/validate-command command)
                            (apply-events events))
       (map schema/validate-event)))

;; Rules for the Uno game
;; https://www.unorules.com/

(deftest all-cards-test
  (is (= 108 (count game/all-cards)))
  (doseq [card game/all-cards]
    (is (schema/validate-card card))))

(deftest remove-card-test
  (testing "removes from any position"
    (is (= [:b :c] (game/remove-card [:a :b :c] :a)))
    (is (= [:a :c] (game/remove-card [:a :b :c] :b)))
    (is (= [:a :b] (game/remove-card [:a :b :c] :c))))

  (testing "removes only one card"
    (is (= [:a] (game/remove-card [:a :a] :a)))
    (is (= [:a :a :b] (game/remove-card [:a :b :a :b] :b))
        "removes the first match"))

  (testing "fails if card is not in the deck"
    (is (thrown-with-msg?
         IllegalArgumentException #"^card not found: :b$"
         (game/remove-card [:a] :b)))))

;;;; Read model

(deftest game-was-started-test
  ;; TODO: separate events for dealing cards and initializing the draw pile; see rules for when the card turned up at the beginning of play is special
  (let [game {:game/players {:player1 {:player/hand [red-1 red-2 red-3]}
                             :player2 {:player/hand [green-1 green-2 green-3]}
                             :player3 {:player/hand [yellow-1 yellow-2 yellow-3]}}
              :game/discard-pile [blue-1]
              :game/draw-pile [blue-2 blue-3]
              :game/current-player :player1
              :game/next-players [:player2 :player3]}
        events [(assoc game :event/type :game.event/game-was-started)]]
    (is (= game (apply-events events)))))

(deftest card-was-played-test
  (let [[game-was-started :as events] (handle-command {:command/type :game.command/start-game
                                                       :game/players [:player1 :player2 :player3]}
                                                      nil)
        expected (apply-events events)]

    (testing "card is removed from the player's hand and added to the top of the discard pile"
      (let [events [(-> game-was-started
                        (assoc-in [:game/players :player1 :player/hand] [red-1 red-2 red-3])
                        (assoc :game/discard-pile [blue-1]))
                    {:event/type :game.event/card-was-played
                     :event/player :player1
                     :card/type 1
                     :card/color :red}]
            expected (-> expected
                         (assoc-in [:game/players :player1 :player/hand] [red-2 red-3])
                         (assoc :game/discard-pile [red-1 blue-1]))]
        (is (= expected (apply-events events)))))

    (testing "wild card represents the color the player says it does"
      (let [events [(-> game-was-started
                        (assoc-in [:game/players :player1 :player/hand] [red-1 wild])
                        (assoc :game/discard-pile [blue-1]))
                    {:event/type :game.event/card-was-played
                     :event/player :player1
                     :card/type :wild
                     :card/color :yellow}]
            expected (-> expected
                         (assoc-in [:game/players :player1 :player/hand] [red-1])
                         (assoc :game/discard-pile [(assoc wild :card/color :yellow) ; TODO: separate the current color from the discard pile
                                                    blue-1]))]
        (is (= expected (apply-events events)))))))

(deftest card-was-not-played-test
  (let [[game-was-started :as events] (handle-command {:command/type :game.command/start-game
                                                       :game/players [:player1 :player2 :player3]}
                                                      nil)
        expected (apply-events events)]

    (testing "no matching card in hand or player decides to not play"
      (let [events [game-was-started
                    {:event/type :game.event/card-was-not-played
                     :event/player :player1}]
            expected (-> expected
                         (assoc :game/draw-penalty-card? true))]
        (is (= expected (apply-events events)))))))

(deftest card-was-drawn-test
  (let [[game-was-started :as events] (handle-command {:command/type :game.command/start-game
                                                       :game/players [:player1 :player2 :player3]}
                                                      nil)
        expected (apply-events events)]

    (testing "card is removed from the draw pile and added to the player's hand"
      (let [events [(-> game-was-started
                        (assoc-in [:game/players :player1 :player/hand] [red-1 red-2 red-3])
                        (assoc :game/draw-pile [blue-1 blue-2 blue-3]))
                    {:event/type :game.event/card-was-drawn
                     :event/player :player1
                     :card/type 1
                     :card/color :blue}]
            expected (-> expected
                         (assoc-in [:game/players :player1 :player/hand] [blue-1 red-1 red-2 red-3])
                         (assoc :game/draw-pile [blue-2 blue-3])
                         (assoc :game/last-drawn-card blue-1))]
        (is (= expected (apply-events events)))))))

(deftest player-turn-has-ended-test
  (let [events (handle-command {:command/type :game.command/start-game
                                :game/players [:player1 :player2 :player3]}
                               nil)
        expected (apply-events events)]

    (testing "player turn advances to the next player"
      (let [events (concat events [{:event/type :game.event/player-turn-has-ended
                                    :event/player :player1
                                    :game/next-players [:player2 :player3 :player1]}])
            expected (-> expected
                         (assoc :game/current-player :player2)
                         (assoc :game/next-players [:player3 :player1]))]
        (is (= expected (apply-events events)))))))


;;;; Commands

(deftest start-game-test
  (testing "the game is for 2-10 players"
    (let [players [:player1 :player2 :player3 :player4 :player5
                   :player6 :player7 :player8 :player9 :player10 :player11]]
      (is (thrown-with-msg?
           GameRulesViolated #"^expected 2-10 players, but was 1$"
           (handle-command {:command/type :game.command/start-game
                            :game/players (take 1 players)}
                           [])))
      (is (not (empty? (handle-command {:command/type :game.command/start-game
                                        :game/players (take 2 players)}
                                       []))))
      (is (not (empty? (handle-command {:command/type :game.command/start-game
                                        :game/players (take 10 players)}
                                       []))))
      (is (thrown-with-msg?
           GameRulesViolated #"^expected 2-10 players, but was 11$"
           (handle-command {:command/type :game.command/start-game
                            :game/players (take 11 players)}
                           [])))))

  (let [game (apply-events (handle-command {:command/type :game.command/start-game
                                            :game/players [:player1 :player2 :player3]}
                                           []))
        player1-hand (get-in game [:game/players :player1 :player/hand])
        player2-hand (get-in game [:game/players :player2 :player/hand])
        player3-hand (get-in game [:game/players :player3 :player/hand])
        draw-pile (:game/draw-pile game)
        discard-pile (:game/discard-pile game)]

    (testing "every player starts with 7 cards, face down"
      (is (= 7 (count player1-hand)))
      (is (= 7 (count player2-hand)))
      (is (= 7 (count player3-hand))))

    (testing "one card is placed in the discard pile, face up"
      (is (= 1 (count discard-pile))))

    (testing "the rest of the cards are placed in a draw pile, face down"
      (is (= (frequencies game/all-cards)
             (frequencies (concat player1-hand player2-hand player3-hand discard-pile draw-pile)))))

    (testing "first player and gameplay direction"
      (is (= :player1 (:game/current-player game)))
      (is (= [:player2 :player3] (:game/next-players game))))))

(deftest play-card-test
  (let [game-was-started {:event/type :game.event/game-was-started
                          :game/players {:player1 {:player/hand [blue-1 wild]}
                                         :player2 {:player/hand [blue-2]}}
                          :game/discard-pile [red-2]
                          :game/draw-pile []
                          :game/current-player :player1
                          :game/next-players [:player2]}
        player-turn-has-ended {:event/type :game.event/player-turn-has-ended
                               :event/player :player1
                               :game/next-players [:player2 :player1]}]

    (testing "players cannot play out of turn"
      (is (thrown-with-msg?
           GameRulesViolated #"^not current player; expected :player1, but was :player2$"
           (handle-command {:command/type :game.command/play-card
                            :command/player :player2
                            :card/type 2
                            :card/color :blue}
                           [game-was-started]))))

    (testing "players cannot play cards that are not in their hand"
      (is (thrown-with-msg?
           GameRulesViolated #"^card not in hand; tried to play .*:card/type 2.*, but hand was .*:card/type 1.*$"
           (handle-command {:command/type :game.command/play-card
                            :command/player :player1
                            :card/type 2
                            :card/color :blue}
                           [game-was-started]))))

    (testing "players can match the card in discard pile by number"
      (is (= [{:event/type :game.event/card-was-played
               :event/player :player1
               :card/type 1
               :card/color :blue}
              player-turn-has-ended]
             (handle-command {:command/type :game.command/play-card
                              :command/player :player1
                              :card/type 1
                              :card/color :blue}
                             [(assoc game-was-started :game/discard-pile [red-1])]))))

    (testing "players can match the card in discard pile by color"
      (is (= [{:event/type :game.event/card-was-played
               :event/player :player1
               :card/type 1
               :card/color :blue}
              player-turn-has-ended]
             (handle-command {:command/type :game.command/play-card
                              :command/player :player1
                              :card/type 1
                              :card/color :blue}
                             [(assoc game-was-started :game/discard-pile [blue-2])]))))

    (testing "cards with different number and color will not match"
      (is (thrown-with-msg?
           GameRulesViolated #"^card \{.*blue.*} does not match the card \{.*red.*} in discard pile$"
           (handle-command {:command/type :game.command/play-card
                            :command/player :player1
                            :card/type 1
                            :card/color :blue}
                           [(assoc game-was-started :game/discard-pile [red-2])]))))

    (testing "players can match any color with a wild card"
      (is (= [{:event/type :game.event/card-was-played
               :event/player :player1
               :card/type :wild
               :card/color :yellow}
              player-turn-has-ended]
             (handle-command {:command/type :game.command/play-card
                              :command/player :player1
                              :card/type :wild
                              :card/color :yellow}
                             [(assoc game-was-started :game/discard-pile [blue-2])]))))))

(deftest do-not-play-card-test
  (let [game-was-started {:event/type :game.event/game-was-started
                          :game/players {:player1 {:player/hand [blue-1 red-3]}
                                         :player2 {:player/hand [blue-2]}}
                          :game/discard-pile [red-2]
                          :game/draw-pile []
                          :game/current-player :player1
                          :game/next-players [:player2]}
        player-turn-has-ended {:event/type :game.event/player-turn-has-ended
                               :event/player :player1
                               :game/next-players [:player2 :player1]}]

    (testing "players cannot not play out of turn"
      (is (thrown-with-msg?
           GameRulesViolated #"^not current player; expected :player1, but was :player2$"
           (handle-command {:command/type :game.command/do-not-play-card
                            :command/player :player2}
                           [game-was-started]))))

    (testing "if there are no matches or player chooses not to play, player must draw a card from the draw pile"
      (is (= [{:event/type :game.event/card-was-not-played
               :event/player :player1}
              {:event/type :game.event/card-was-drawn
               :event/player :player1
               :card/type 2
               :card/color :blue}]
             (handle-command {:command/type :game.command/do-not-play-card
                              :command/player :player1}
                             [(assoc game-was-started :game/draw-pile [blue-2])]))))

    (testing "if the drawn card matches,"
      (let [events [(assoc game-was-started :game/draw-pile [blue-2])
                    {:event/type :game.event/card-was-not-played
                     :event/player :player1}
                    {:event/type :game.event/card-was-drawn
                     :event/player :player1
                     :card/type 2
                     :card/color :blue}]]

        (testing "player can play it immediately"
          (is (= [{:event/type :game.event/card-was-played
                   :event/player :player1
                   :card/type 2
                   :card/color :blue}
                  player-turn-has-ended]
                 (handle-command {:command/type :game.command/play-card
                                  :command/player :player1
                                  :card/type 2
                                  :card/color :blue}
                                 events))))

        (testing "player cannot play other cards"
          (is (thrown-with-msg?
               GameRulesViolated #"^can only play the card that was just drawn; tried to play .*:card/type 3.*, but just drew .*:card/type 2.*$"
               (handle-command {:command/type :game.command/play-card
                                :command/player :player1
                                :card/type 3
                                :card/color :red}
                               events))))

        (testing "player cannot avoid playing it"
          (is (thrown-with-msg?
               GameRulesViolated #"^the card that was just drawn can be played, so it must be played$"
               (handle-command {:command/type :game.command/do-not-play-card
                                :command/player :player1}
                               events))))))

    (testing "if the drawn card does not match, player keeps it and turn goes to the next player"
      (let [events [(assoc game-was-started :game/draw-pile [blue-3])
                    {:event/type :game.event/card-was-not-played
                     :event/player :player1}
                    {:event/type :game.event/card-was-drawn
                     :event/player :player1
                     :card/type 3
                     :card/color :blue}]]
        (is (= [{:event/type :game.event/card-was-not-played
                 :event/player :player1}
                player-turn-has-ended]
               (handle-command {:command/type :game.command/do-not-play-card
                                :command/player :player1}
                               events)))))))
