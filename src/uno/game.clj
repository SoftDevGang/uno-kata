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
