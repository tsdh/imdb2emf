#!/bin/sh

i=5000;
while ((i < 800000)); do
    lein run imdb $i
    i=$((i + 5000))
done
