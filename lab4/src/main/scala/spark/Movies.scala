package spark

import org.apache.spark.SparkContext
import org.apache.spark.sql._
import org.apache.spark.rdd._
import org.apache.spark.sql.SparkSession


case class Movie(id: Int, title: String, genres: Seq[String],
                 description: String, director: String, actors: Seq[String],
                 year: Int, rating: Float, votes: Int)

object Movie {
    def parseRow(row: Row): Movie = {
        val id = row.getInt(0)
        val title = row.getString(1)
        val genres = row.getString(2).split(",").toList
        val description = row.getString(3)
        val director = row.getString(4)
        val actors = row.getString(5).split(",").toList
        val year = row.getInt(6)
        val rating = row.getDouble(8).toFloat
        val votes = row.getInt(9)

        Movie(id, title, genres, description, director, actors, year, rating, votes)
    }

    def loadDataset(): RDD[Movie] = {
        val filename = "data/IMDB-Movie-Data.csv"
        val moviesDF = Spark.spark.read.format("csv")
            .option("sep", ",")
            .option("inferSchema", "true")
            .option("header", "true")
            .load(filename)
        val rddMovies = moviesDF.rdd.map(parseRow)
        rddMovies
    }
}

object Spark {
    val spark: SparkSession = 
        SparkSession.builder()
        .appName("Movies")
        .master("local[*]")
        .getOrCreate()

    val sc: SparkContext = 
        spark.sparkContext

    // Change to INFO or DEBUG if need more information
    sc.setLogLevel("WARN")

    def main(args: Array[String]): Unit = {
        val rddMovies = Movie.loadDataset()
        rddMovies.take(10).map(m => m.title).foreach(println)

        spark.close()
    }
}
