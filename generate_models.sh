#!/bin/zsh

i=100000;
while ((i < 110000)); do
    lein run imdb $i
    i=$((i + 100000))
done
