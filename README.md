# imdb2emf: an IMDb parser creating EMF models or TGraphs

This little tool creates an EMF model (or a
[JGraLab TGraph](http://jgralab.github.io/jgralab/)) from the IMDb files.

## Usage

You can either clone this repository and install Leiningen to parse IMDb files,
or you can download a pre-compiled JAR with no dependencies.

### Usage: Git

Download the files actors.list.gz, actresses.list.gz, movies.list.gz, and
ratings.list.gz from [the IMDb download site](http://www.imdb.com/interfaces),
and put them in some directory.  You don't need to unzip them (but you may if
you want).

Download the `lein` script from
[the Leiningen homepage](http://www.leiningen.org) and put it into your `PATH`.

Then clone this project and run it:

````
$ git clone https://github.com/tsdh/imdb2emf.git
$ cd imdb2emf
$ lein run
Usage: lein run <kind> <imdb-dir>
       lein run <kind> <imdb-dir> <max-movie-count>
<kind> should either be EMF to create EMF models or TG to create TGraphs.
<imdb-dir> is the directory containing the IMDb files.
<max-movie-count> is the maximum number of movies to be parsed.
$ lein run EMF path/to/imdb/ 1000
````

The last command will parse the first 1000 movies, all ratings of those movies,
all actors and actresses that act at least in one of these movies, the movies'
directors, and genres into an EMF model.  (First, the movies.list.gz file is
parsed.  Thereafter, the actors.list.gz, actresses.list.gz, directors.list.gz,
genres.list.gz and ratings.list.gz are parsed in parallel).

If you don't provide a maximum movie number, it'll parse the complete database.
That will be about one million movies (300k of which are rated), 1.7 million
actors, 900k actresses, 330k directors, and nearly 8 million references between
objects.

The resulting XMI model files are named
`imdb-<max-movie-count>-<element-count>.movies` if a maximum movie number was
provided and `imdb-all-<element-count>.movies` when the complete database is
parsed.

Additionally, the models are saved in a binary coding which is more space
efficient and much faster to load.  Those files have the ending `.movies.bin`.

### Usage: pre-compiled JAR

You need the very same IMDb files as explained above, but you don't need the
`lein` script or clone this repository.  Instead, download
[this precompiled JAR](http://userpages.uni-koblenz.de/~horn/imdb2emf-1.4.0-standalone.jar).

Using that, you can parse the IMDb files by executing the JAR:

````
$ java -jar imdb2emf-1.4.0-standalone.jar
Usage: lein run <kind> <imdb-dir>
       lein run <kind> <imdb-dir> <max-movie-count>
<kind> should either be EMF to create EMF models or TG to create TGraphs.
<imdb-dir> is the directory containing the IMDb files.
<max-movie-count> is the maximum number of movies to be parsed.
````

### Loading binary movie models

The JAR file includes a Java class `BinaryUtil` that can be used to load the
binary movie models.


```` java
Resource r = imdb2emf.serialize.BinaryUtil.readBinaryMoviesFile("path/to/imdb.movies.bin");
````

It returns a standard EMF resource containing all movies and persons.

## License

Copyright © 2014, 2015 Tassilo Horn <horn@uni-koblenz.de>

Distributed under the GNU General Public License, version 3 (or later).
