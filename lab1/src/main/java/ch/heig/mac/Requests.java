package ch.heig.mac;

import java.util.List;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;


public class Requests {
    private final Cluster ctx;

    public Requests(Cluster cluster) {
        this.ctx = cluster;
    }

    public List<String> getCollectionNames() {
        var result = ctx.query("""
                SELECT RAW r.name
                  FROM system:keyspaces r
                 WHERE r.`bucket` = "mflix-sample";
                """
        );
        return result.rowsAs(String.class);
    }

    public List<JsonObject> inconsistentRating() {
        var result = ctx.query("""
                    SELECT imdb.id AS imdb_id, imdb.rating AS imdb_rating, tomatoes.viewer.rating AS tomatoes_rating
                    FROM `mflix-sample`.`_default`.`movies`
                    WHERE ISNUMBER(imdb.rating) AND ISNUMBER(tomatoes.viewer.rating)
                        AND ABS(imdb.rating - tomatoes.viewer.rating) > 8;
                """
        );
        return result.rowsAs(JsonObject.class);
    }

    public List<JsonObject> hiddenGem() {

        var result = ctx.query("""
                SELECT title,
                       year
                FROM `mflix-sample`.`_default`.`movies`
                WHERE tomatoes.viewer.numReviews IS NOT VALUED
                    AND tomatoes.critic.rating = 10;
                """
        );
        return result.rowsAs(JsonObject.class);
    }

    public List<JsonObject> topReviewers() {
        var result = ctx.query("""
                    SELECT email,
                           COUNT(*) cnt
                    FROM `mflix-sample`.`_default`.`comments`
                    GROUP BY email
                    ORDER BY cnt DESC,
                             email ASC
                    LIMIT 6;
                """
        );
        return result.rowsAs(JsonObject.class);
    }

    public List<String> greatReviewers() {
        var result = ctx.query("""
                     SELECT RAW email
                     FROM `mflix-sample`.`_default`.`comments`
                     GROUP BY email
                     HAVING COUNT(*) > 250;
                """
        );
        return result.rowsAs(String.class);
    }

    public List<JsonObject> bestMoviesOfActor(String actor) {
        var result = ctx.query("""
                            SELECT imdb.id AS imdb_id,
                                   imdb.rating AS rating,
                                   `cast`
                            FROM `mflix-sample`._default.movies
                            WHERE ISNUMBER(imdb.rating) AND imdb.rating > 9
                                AND ANY actor IN `cast` SATISFIES LOWER(actor) == LOWER($actor) END;
                        """,
                QueryOptions.queryOptions()
                        .parameters(JsonObject.create().put("actor", actor))
        );
        /*
        Another way of doing it would be: SELECT imdb.id AS imdb_id,
                                                 imdb.rating AS imdb_rating,
                                                 `cast`
                                          FROM movies
                                          WHERE ISNUMBER(imdb.rating) AND imdb.rating > 9
                                            AND ARRAY_CONTAINS(`cast`, "Al Pacino")
         Which is faster but case-sensitive in this implementation
         */
        return result.rowsAs(JsonObject.class);
    }

    public List<JsonObject> mystery() {
        // x is the name of a writer that has worked on more than 30 different titles, z is the number
        // of films the writer x has worked on
        // The request is composed of a subquery that will extract all the films a writer has worked on by unnesting
        // the writer array and creating an array of every film they worked on. The films are grouped by the writer.
        // Then we use the array previously created and count the number of elements in it.
        var result = ctx.query("""
                SELECT x,
                       COUNT(*) AS z
                FROM `mflix-sample`.`_default`.`movies` AS m
                UNNEST m.writers AS x
                GROUP BY x
                HAVING COUNT(*) > 30;
                """
        );
        return result.rowsAs(JsonObject.class);
    }

    public List<JsonObject> confusingMovies() {
        var result = ctx.query("""
                    SELECT _id AS movie_id, title
                    FROM `mflix-sample`.`_default`.`movies`
                    WHERE ARRAY_LENGTH(directors) > 25;
                """
        );
        return result.rowsAs(JsonObject.class);
    }

    public List<JsonObject> commentsOfDirector1(String director) {
        // The subquery in the WHERE clause returns an array of the form ["_id": m.id], so key/value
        // IN searches for a direct value in the right-hand array
        // IN cannot find m.id DIRECTLY in this array
        // 2 possible corrections: change IN to WITHIN or add RAW in the subquery (used below)
        var result = ctx.query("""
                        SELECT c.movie_id,
                               c.text
                        FROM `mflix-sample`.`_default`.`comments` AS c
                        WHERE c.movie_id in (
                            SELECT RAW m._id
                            FROM `mflix-sample`.`_default`.`movies` AS m
                            WHERE ANY d IN m.directors SATISFIES d == $director END );
                        """,
                QueryOptions.queryOptions().parameters(JsonObject.create().put("director", director))
        );
        return result.rowsAs(JsonObject.class);
    }

    public List<JsonObject> commentsOfDirector2(String director) {
        var result = ctx.query("""
                SELECT c.movie_id,
                       c.text
                FROM `mflix-sample`.`_default`.`comments` AS c
                    JOIN `mflix-sample`.`_default`.`movies` AS m ON c.movie_id = m._id
                UNNEST m.directors AS d
                WHERE LOWER(d) == LOWER($director)
                """,
                QueryOptions.queryOptions().parameters(JsonObject.create().put("director", director))
        );
        return result.rowsAs(JsonObject.class);
    }

    // Returns the number of documents updated.
    public long removeEarlyProjection(String movieId) {
        // Boolean logic (s.movieId == $movieId AND s.hourBegin > 19h) OR s.movieId != $movieId
        // -> s.movieId != $movieId OR s.hourBegin > 19h
        var result = ctx.query("""
                            UPDATE `mflix-sample`.`_default`.theaters
                            SET theaters.schedule = ARRAY s FOR s IN theaters.schedule WHEN s.movieId != $movieId
                                OR s.hourBegin >= "19:00:00" END
                            WHERE ANY s IN theaters.schedule SATISFIES s.movieId == $movieId
                                AND s.hourBegin < "19:00:00" END
                        """,
                QueryOptions.queryOptions().metrics(true).parameters(JsonObject.create().put("movieId", movieId))
        );

        return result.metaData().metrics().get().mutationCount();
    }

    public List<JsonObject> nightMovies() {
        /*
        BEFORE PR
        It seems strange to me to join on itself, since schedule is part of theaters. I think this is where
        the fault in the LLM's proposal lies. I would rather do an IN with all the schedules that have a movie
        that is present only after 7 PM. Moreover, I'm not certain that the test to check if there is at least one
        is necessary. However, this idea may come from my naivety regarding the language and how it behaves when
        there are no cases.

        To correct this, we must, in a subquery, unnest the schedules, select the movie-ids and use
        the same test as in the query proposed by the LLM, to reuse what is present.

        While trying to work with the ARRAY_MIN and ARRAY condition, I'm not sure I understand what it's for
        especially in my solution. A simple MIN after UNNEST the schedules and GROUP BY on movieId allows achieving the same thing and
        seems more familiar at this level of SQL++ knowledge

        AFTER PR
        After the PR, I must reconsider my reasoning since the JOIN on the same document works.
        The problem seems quite obvious here, the fact is that we're searching by theater and not among all theaters which
        is what we're asked for. Thus a movie that only has showings after 7 PM in theater A, but a showing at
        6 PM in theater B will be in the returned list which is obviously an error. If we want to stay in the
        abstruse logic of the LLM, we can add a condition where we check if there exists a showing before 7 PM in another
        theater. I really don't like the logic proposed by the LLM, an UNNEST and a GROUP BY with a HAVING MIN would do the job...
        The solution seems ugly and slow to me to be honest compared to a simple:

        SELECT m._id AS movie_id, m.title
                FROM `mflix-sample`.`_default`.`movies` m
                WHERE m._id IN (
                    SELECT DISTINCT RAW schedule.movieId
                    FROM `mflix-sample`.`_default`.`theaters`
                    UNNEST schedule
                    GROUP BY schedule.movieId
                    HAVING MIN(schedule.hourBegin) >= "19:00:00"
                )

         */
        var result = ctx.query("""
                     SELECT m._id AS movie_id,
                            m.title
                     FROM `mflix-sample`.`_default`.`movies` AS m
                         JOIN `mflix-sample`.`_default`.`theaters` AS t ON ANY s IN t.schedule SATISFIES s.movieId = m._id END
                     WHERE ( ARRAY_MIN( ARRAY s.hourBegin FOR s IN t.schedule WHEN s.movieId = m. _id END ) >= "19:00:00" )
                         AND ( ARRAY_LENGTH( ARRAY s FOR s IN t.schedule WHEN s.movieId = m._id END ) > 0 )
                         AND NOT EXISTS (
                         SELECT 1
                         FROM `mflix-sample`.`_default`.`theaters` AS t1
                         WHERE ANY s IN t1.schedule SATISFIES s.movieId = m._id
                             AND s.hourBegin < "19:00:00" END)
                     GROUP BY m._id,
                              m.title
                """

        );
        return result.rowsAs(JsonObject.class);
    }
}
