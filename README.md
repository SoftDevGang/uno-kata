
# Uno Kata

This is a code kata for practising writing readable tests.


## Tools

- Java 11
- [Leiningen](https://leiningen.org/), a build tool for Clojure projects
- IDE or editor with Clojure support
    - [Cursive](https://cursive-ide.com/userguide/) plugin for IntelliJ IDEA
        - It has 30 day trial, which is enough for this exercise, and you can
          get a free license for non-commercial stuff.
        - It's recommended to enable the [Parinfer](https://shaunlebron.github.io/parinfer/)
          structural editing mode. When a Clojure file is open, select
          `Edit > Structural Editing > Toggle Structural Editing Style`
          a couple of times, until you see the message *"Parinfer Indent Mode"*
          pop up next to your cursor.
    - [appropriate plugin for some other editor](https://shaunlebron.github.io/parinfer/#editor-plugins) 


## Commands

Run tests once 

    lein test

Run tests always when a file is changed

    lein autotest


## The Exercise

This project implements the [rules of Uno](https://www.unorules.com/), a card game.
Your assignment is to focus on writing as readable tests as possible. You have two options.  

Option 1: Write new tests without looking at the existing tests in this project.
 
Option 2: Refactor the existing tests to be better.
