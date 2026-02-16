package ch.heig.mac;

import java.util.List;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

public class Requests {
  private final Driver driver;

  public Requests(Driver driver) {
    this.driver = driver;
  }

  public List<String> getDbLabels() {
    var dbVisualizationQuery =
        """
                CALL db.labels
                """;

    try (var session = driver.session()) {
      var result = session.run(dbVisualizationQuery);
      return result.list(t -> t.get("label").asString());
    }
  }

  public List<Record> possibleSpreaders() {
    // Here DISTINCT would also remove people with the same name so I chose not to use it
    var query =
        """
                MATCH (sick:Person {healthstatus: "Sick"})-[v1:VISITS]->()<-[v2:VISITS]-(healthy:Person {healthstatus: "Healthy"})
                WHERE v1.starttime >= sick.confirmedtime
                  AND v2.starttime >= v1.starttime
                  AND v2.starttime >= healthy.confirmedtime
                RETURN sick.name AS sickName
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> possibleSpreadCounts() {
    var query =
        """
                MATCH (sick:Person {healthstatus: "Sick"})-[v1:VISITS]->()<-[v2:VISITS]-(healthy:Person {healthstatus: "Healthy"})
                WHERE v1.starttime >= sick.confirmedtime
                  AND v2.starttime >= v1.starttime
                  AND v2.starttime >= healthy.confirmedtime
                RETURN sick.name AS sickName,
                       count(healthy) AS nbHealthy
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> carelessPeople() {
    var query =
        """
                MATCH (sick:Person {healthstatus: "Sick"})-[sickVisit:VISITS]->(place:Place)
                WHERE sickVisit.starttime >= sick.confirmedtime
                WITH sick, count(DISTINCT place) AS nbPlaces
                WHERE nbPlaces > 10
                RETURN sick.name as sickName, nbPlaces
                ORDER BY nbPlaces DESC
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> sociallyCareful1() {
    var query =
        """
                MATCH (sick:Person {healthstatus: "Sick"})
                WHERE NOT EXISTS {
                  MATCH (sick)-[v:VISITS]->(:Place {type: "Bar"})
                  WHERE v.starttime >= sick.confirmedtime
                }
                RETURN sick.name AS sickName
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> sociallyCareful2() {
    /* It's wrong and a simple test can prove it. A request to match the sick people that don't have any visits to a bar
      return different people than the ones that the request made by the LLM returns. What is wrong is the scope of the
      first match as it already asks for a pattern where a sick person would have visited a bar. We need to only have
      the match on the healthstatus of the person in the match and then in the optional match have the relation.

      Then the first where check only if the person visited the bar after their confirmedtime so it's the opposite of
      what we want ! Again, the solution I have with the not exists seems to be the best as we have to work on a set
      of relation and we cannot handle it within a where clause that checks line by line according to my comprehension...
    */
    // TODO CHECK IF SAME
    var query =
        """
                MATCH (p:Person {healthstatus: "Sick"})
                OPTIONAL MATCH (p)-[v:VISITS]->(:Place{type:"Bar"})
                WHERE NOT v.starttime > p.confirmedtime
                    WITH p, v
                   WHERE v IS NULL
                RETURN p.name AS sickName
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> peopleToInform() {
    var query =
        """
                MATCH (sick:Person {healthstatus: "Sick"})-[sickVisit:VISITS]->()<-[healthyVisit:VISITS]-(healthy:Person {healthstatus: "Healthy"})
                WHERE sickVisit.starttime > sick.confirmedtime
                  AND healthyVisit.starttime > healthy.confirmedtime
                WITH *,
                     duration.inSeconds(apoc.coll.max([sickVisit.starttime, healthyVisit.starttime]),
                                     apoc.coll.min([sickVisit.endtime, healthyVisit.endtime])).hours AS timeSpentTogether
                WHERE timeSpentTogether >= 2
                RETURN sick.name AS sickName,
                       collect(DISTINCT healthy.name) AS peopleToInform
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> setHighRisk() {
    var query =
        """
                MATCH (sick:Person {healthstatus: "Sick"})-[sickVisit:VISITS]->()<-[healthyVisit:VISITS]-(healthy:Person {healthstatus: "Healthy"})
                WHERE sickVisit.starttime > sick.confirmedtime
                  AND healthyVisit.starttime > healthy.confirmedtime
                WITH *,
                     duration.inSeconds(apoc.coll.max([sickVisit.starttime, healthyVisit.starttime]),
                                     apoc.coll.min([sickVisit.endtime, healthyVisit.endtime])).hours AS timeSpentTogether
                WHERE timeSpentTogether >= 2
                SET healthy.risk = "high"
                RETURN DISTINCT healthy.id as highRiskId,
                                healthy.name as highRiskName
                """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.list();
    }
  }

  public List<Record> healthyCompanionsOf(String name) {
    // As there is two paths from one person to another, max is 6
    var query =
        """
                MATCH (given:Person {name: $name})-[:VISITS*..6]-(companion:Person {healthstatus: "Healthy"})
                WHERE given <> companion
                RETURN DISTINCT companion.name AS healthyName
                """;

    try (var session = driver.session()) {
      var result = session.run(query, Values.parameters("name", name));
      return result.list();
    }
  }

  public Record topSickSite() {
    var query =
        """
        MATCH (sick:Person {healthstatus: "Sick"})-[sickVisit:VISITS]->(place:Place)
        WHERE sickVisit.starttime > sick.confirmedtime
        RETURN place.type as placeType, COUNT(sickVisit) AS nbOfSickVisits
        ORDER BY nbOfSickVisits DESC
        LIMIT 1
        """;

    try (var session = driver.session()) {
      var result = session.run(query);
      return result.single();
    }
  }

  public List<Record> sickFrom(List<String> names) {
    var query =
        """
              MATCH (sick:Person {healthstatus: "Sick"})
              WHERE sick.name IN $names
              RETURN sick.name as sickName
              """;

    try (var session = driver.session()) {
      var result = session.run(query, Values.parameters("names", names));
      return result.list();
    }
  }

  public void mutualFriends() {
    /*
       The c is not correct as the relation `:FRIENDS_WITH` is symmetrical and here we have oriented vertices in the
       request, even though it does check for two different friends with `b <> d`.

       The a is not correct as well as the b and d people can be the same which would not be correct for the given
       requirements.

       The b is not correct as well as it searches for a cycle between a and c. A cycle between a and c does guarantee
       the fact that a and c would share two common friends however, there is no comparison between b and d nodes.
       The two nodes may be the same, from my comprehension, if they have a `:FRIENDS_WITH` relationship with themselves.

       After testing the request b, within the dataset, outputs the correct answer. I would still argue for my case where b and
       d need to be compared as there is no hard rules on the schema.

    */
  }

  public void indirectFriends() {
    /*
       The a is not correct. The reason why is that it does not check that pA is friend or not with pB. The output of
       this request will be wrong as there will be couple of direct friends as well. And we're looking for paths of length
       2-6 as there must be 5 nodes in between pA and pC.

       Example: (Pierre)-[:FRIENDS_WITH]-(Charlie)-[:FRIENDS_WITH]-(Samantha)
                (Pierre)-[:FRIENDS_WITH]-(Samantha)
                Here the output would be (Pierre, Samantha) even thought Pierre is a direct friend of Samantha

       The b seems correct to me

       The c seems correct to me

    */
  }
}
