# Report Lab03 - Indexing and Search with Elasticsearch

authors: Quentin Surdez, REDACTED

## D1

```
PUT /cacm_standard
{
    "mappings" : {
        "properties" : {
            "id" : {
                "type" : "keyword",
                "index" : "false"
                },
            "author" : {
                "type" : "keyword"
                },
            "title" : {
                "type" : "text",
                "fielddata" : "true"
                },
            "date" : {
                "type" : "date"
                },
            "summary" : {
                "type" : "text",
                "fielddata" : "true",
                "index_options" : "offsets"
                }
        }
    }
}
```

```
POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_standard"
  }
}
```

## D2

```
PUT /cacm_termvector
{
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword",
        "index": false
      },
      "author": {
        "type": "keyword"
      },
      "title": {
        "type": "text",
        "fielddata": true
      },
      "date": {
        "type": "date"
      },
      "summary": {
        "type": "text",
        "fielddata": true,
        "index_options" : "offsets",
        "term_vector": "with_offsets"
      }
    }
  }
}
```

```
POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_termvector"
  }
}
```

## D3

On peut vérifier la présence de termvectors via la requête générique suivante:
`GET /cacm_termvectors/_termvectors/<_id>`.
En parcourant notre data view, on choisit l'id `TbeXwJoBsd1xOr3_RlhA` et exécutons la requête suivante pour savoir
si la création s'est bien effectuée:

```
GET /cacm_termvector/_termvectors/TbeXwJoBsd1xOr3_RlhA
```

Voici un extrait de la réponse qui montre qu'on a bien un termvector dans notre index

```
{
  "_index": "cacm_termvector",
  "_id": "TbeXwJoBsd1xOr3_RlhA",
  "_version": 1,
  "found": true,
  "took": 3,
  "term_vectors": {
    "summary": {
      "field_statistics": {
        "sum_doc_freq": 97730,
        "doc_count": 1585,
        "sum_ttf": 150220
      },
      "terms": {
        "a": {
          "term_freq": 3,
          "tokens": [
            {
              "start_offset": 0,
              "end_offset": 1
            },
            {
              "start_offset": 383,
              "end_offset": 384
            },
            {
              "start_offset": 603,
              "end_offset": 604
            }
          ]
        },
        "an": {
          "term_freq": 2,
          "tokens": [
            {
              "start_offset": 399,
              "end_offset": 401
            },
            {
              "start_offset": 651,
              "end_offset": 653
            }
          ]
        },
```

## D4

Term vectors contain information about the terms produced by the analysis process, including:

- a list of terms. 
- the position (or order) of each term.
- the start and end character offsets mapping the term to its origin in the original string.

These term vectors can be stored so that they can be retrieved for a particular document.

## D5

The index size of `cacm_standard` is 1.77mb. The index size of `cacm_termvector` is 2.48mb.
The difference between the sizes are easily explained by the presence of 
term vectors in `cacm_termvector`. These stock additional info as discussed in D4, this will
inevitably take more space as there is more information.

## D6

Request:

```
GET /cacm_standard/_search
{
  "size": 0,
  "aggs": {
    "best_author": {
      "terms": {
        "field": "author",
        "size": 1
      }
    }
  }
}
```

Response:

```
{
  "took": 2,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 3204,
      "relation": "eq"
    },
    "max_score": null,
    "hits": []
  },
  "aggregations": {
    "best_author": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 4268,
      "buckets": [
        {
          "key": "Thacher Jr., H. C.",
          "doc_count": 38
        }
      ]
    }
  }
}
```

On observe que Thacher Jr., H. C. est l'auteur le plus prolifique avec 38 publications.

## D7

Request:

```
GET /cacm_standard/_search
{
  "size": 0,
  "aggs": {
    "top_10_terms": {
      "terms": {
        "field": "title",
        "size": 10
      }
    }
  }
}
```

Response:

```
{
  "took": 37,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 3204,
      "relation": "eq"
    },
    "max_score": null,
    "hits": []
  },
  "aggregations": {
    "top_10_terms": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 17309,
      "buckets": [
        {
          "key": "of",
          "doc_count": 1138
        },
        {
          "key": "algorithm",
          "doc_count": 975
        },
        {
          "key": "a",
          "doc_count": 895
        },
        {
          "key": "for",
          "doc_count": 714
        },
        {
          "key": "the",
          "doc_count": 645
        },
        {
          "key": "and",
          "doc_count": 434
        },
        {
          "key": "in",
          "doc_count": 416
        },
        {
          "key": "on",
          "doc_count": 340
        },
        {
          "key": "an",
          "doc_count": 275
        },
        {
          "key": "computer",
          "doc_count": 275
        }
      ]
    }
  }
}
```

## D8

### Whitespace analyser

```
PUT /cacm_whitespace
{
    "settings": {
      "analysis": {
        "analyzer": {
          "default": {
            "type": "whitespace"
          }
        }
      }
    },
    "mappings" : {
        "properties" : {
            "id" : {
                "type" : "keyword",
                "index" : "false"
                },
            "author" : {
                "type" : "keyword"
                },
            "title" : {
                "type" : "text",
                "fielddata" : "true"
                },
            "date" : {
                "type" : "date"
                },
            "summary" : {
                "type" : "text",
                "fielddata" : "true",
                "index_options" : "offsets"
                }
        }
    }
}

POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_whitespace"
  }
}

```

### English analyser

```
PUT /cacm_english
{
    "settings": {
      "analysis": {
        "analyzer": {
          "default": {
            "type": "english"
          }
        }
      }
    },
    "mappings" : {
        "properties" : {
            "id" : {
                "type" : "keyword",
                "index" : "false"
                },
            "author" : {
                "type" : "keyword"
                },
            "title" : {
                "type" : "text",
                "fielddata" : "true"
                },
            "date" : {
                "type" : "date"
                },
            "summary" : {
                "type" : "text",
                "fielddata" : "true",
                "index_options" : "offsets"
                }
        }
    }
}

POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_english"
  }
}
```

### Custom analyser with shingles of size 1 and 2

Default config of `shingle` filter outputs shingles of size 1 and 2, so no need to findle with the configuration.

```
PUT /cacm_shingles_1_2_lowercase
{
    "settings": {
      "analysis": {
        "analyzer": {
          "default": {
            "type": "custom",
            "tokenizer": "standard",
            "filter": [ "lowercase", "shingle" ]
          }
        }
      }
    },
    "mappings" : {
        "properties" : {
            "id" : {
                "type" : "keyword",
                "index" : "false"
                },
            "author" : {
                "type" : "keyword"
                },
            "title" : {
                "type" : "text",
                "fielddata" : "true"
                },
            "date" : {
                "type" : "date"
                },
            "summary" : {
                "type" : "text",
                "fielddata" : "true",
                "index_options" : "offsets"
                }
        }
    }
}

POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_shingles_1_2_lowercase"
  }
}

```

### Custom analyser with shingles of size 3 only

Here, we have to set up a custom filter of type shingle and
set the `min_shingle_size` and `max_shingle_size` to 3 as well
as set `output_unigrams` to false. This will output only shingles
of size 3.

```
PUT /cacm_shingles_3_lowercase
{
    "settings": {
      "analysis": {
        "analyzer": {
          "default": {
            "type": "custom",
            "tokenizer": "standard",
            "filter": [ "lowercase", "shingle_3" ]
          }
        },
        "filter": {
          "shingle_3": {
            "type": "shingle",
            "min_shingle_size": 3,
            "max_shingle_size": 3,
            "output_unigrams": false
          }
        }
      }
    },
    "mappings" : {
        "properties" : {
            "id" : {
                "type" : "keyword",
                "index" : "false"
                },
            "author" : {
                "type" : "keyword"
                },
            "title" : {
                "type" : "text",
                "fielddata" : "true"
                },
            "date" : {
                "type" : "date"
                },
            "summary" : {
                "type" : "text",
                "fielddata" : "true",
                "index_options" : "offsets"
                }
        }
    }
}

POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_shingles_3_lowercase"
  }
}
```

### Custom stop analyser

```
PUT /cacm_stop
{
    "settings": {
      "analysis": {
        "analyzer": {
          "default": {
            "type": "stop",
            "stopwords_path": "data/common_words.txt"
          }
        }
      }
    },
    "mappings" : {
        "properties" : {
            "id" : {
                "type" : "keyword",
                "index" : "false"
                },
            "author" : {
                "type" : "keyword"
                },
            "title" : {
                "type" : "text",
                "fielddata" : "true"
                },
            "date" : {
                "type" : "date"
                },
            "summary" : {
                "type" : "text",
                "fielddata" : "true",
                "index_options" : "offsets"
                }
        }
    }
}

POST _reindex
{
  "source": {
    "index": "cacm_dynamic"
  },
  "dest": {
    "index": "cacm_stop"
  }
}
```

## D9

### Whitespace

The whitespace analyser divides text into terms whenever
it encounters any whitespace character. It does not lowercase the terms.

### English

This analyser is created for the english language.

It does the following:

- lowercase the terms
- removes curent stopwords
- stemms the terms (eating -> eat)

### Shingles of size 1 and 2

This analyser is based on the `standard analyser` and
it does the following:

- lowercase the terms
- creates unigrams (simple words)
- create bigrams (group of 2 words)

### Shingles of 3

This analyser is based on the `standard analyser` and
it does the following:

- lowercase the terms
- create trigrams (group of 3 words)

### Stop

This analyser is based on the `standard analyser` and it filters
according to a given word list (common_words.txt) while conserving
the ones that are not within this list.

## D10

### Request for nb of indexed documents

```
GET /<index>/_count
```

### Request for the nb of indexed terms in summary field

```
GET /<index>/_search
{
  "size": 0,
  "aggs": {
    "total_terms": {
      "value_count": {
        "field": "summary"
      }
    }
  }
}
```

### Request for top 10 frequent terms of the summary

```
GET /<index>/_search
{
  "size": 0,
  "aggs": {
    "top_terms_summary": {
      "terms": {
        "field": "summary",
        "size": 10
      }
    }
  }
}
```

### Request for stats (to see disk size)

```
GET /<index>/_stats
```

### Recap

The outputs of the `_reindex` are within the `D10_output.md` doc.

| Analyser     | Nb indexed doc | Nb indexed terms | Index size (mb) | Indexing time |
|--------------|----------------|------------------|-----------------|---------------|
| Whitespace   | 3204           | 103275           | 1.88            | 120           |
| English      | 3204           | 72298            | 1.58            | 130           |
| Shingles 1,2 | 3204           | 237189           | 3.49            | 175           |
| Shingles 3   | 3204           | 144518           | 3.90            | 177           |
| Stop         | 3204           | 59988            | 1.53            | 91            |

### Tab of 10 most frequent terms

| Rang | Whitespace | English       | Shingles 1,2 | Shingles 3          | Stop            |
|------|------------|---------------|--------------|---------------------|-----------------|
| 1    | of (1534)  | which (781)   | the (1541)   | in this paper (111) | computer (460)  |
| 2    | the (1501) | us (778)      | of (1534)    | the use of (108)    | system (446)    |
| 3    | is (1382)  | comput (663)  | a (1426)     | the number of (106) | paper (421)     |
| 4    | and (1369) | program (635) | is (1384)    | it is shown (97)    | presented (381) |
| 5    | a (1321)   | system (586)  | and (1376)   | a set of (88)       | time (357)      |
| 6    | to (1293)  | present (514) | to (1301)    | in terms of (87)    | program (344)   |
| 7    | in (1188)  | describ (505) | in (1234)    | the problem of (82) | data (318)      |
| 8    | for (1167) | paper (428)   | for (1182)   | is shown that (71)  | method (308)    |
| 9    | The (1072) | can (421)     | are (1025)   | a number of (67)    | algorithm (289) |
| 10   | are (1022) | gener (411)   | of the (938) | as well as (63)     | discussed (278) |

## D11

- We can see that all analysers have indexed the same number of documents which is normal as they all have been applied
  to the same collection `cacm`
- The shingles indexes have a bigger size than the others(3.49mb, 3.9mb when the others are about ~1.3mb). This means
  that the shingles generate a big number of term's combination, which thus make the size of the indexes bigger
- The shingles create more indexed terms than the others analyser. This can be explained as they create n-grams which
  augment the diversity of the terms
- The analyser `stop` has the smallest size and the smallest number of indexed terms. This is expected behavior as it
  removes all the stop words from the index which thus reduce the size and the number of indexes.
- For the most common terms, we can see that the analysers that don't have a stopwords list (whitespace, shingles 1,2)
  and those who have (english, stop) have very different outputs. This is expected as the words that are most common in
  a language are the prepositions. Removing them as stopwords will create indexes that are more specific.

## D12

### 1

```
GET cacm_english/_search			
{
  "_source": ["id"], 
  "query": {
    "query_string": {
      "query": "\"Information Retrieval\"",
      "default_field": "summary"
    }
  }
}
```

### 2

```
GET cacm_english/_search			
{
  "_source": ["id"], 
  "query": {
    "query_string": {
      "query": "Information AND Retrieval",
      "default_field": "summary"
    }
  }
}
```

### 3

```
GET cacm_english/_search			
{
  "_source": ["id"], 
  "query": {
    "query_string": {
      "query": "Information +Retrieval -Database",
      "default_field": "summary"
    }
  }
}
```

### 4

```
GET /cacm_english/_search
{
  "_source": ["id"], 
  "query": {
    "query_string": {
      "query": "Info*",
      "fields": ["summary"]
    }
  }
}
```

### 5

```
GET /cacm_english/_search
{
  "_source": ["id"], 
  "query": {
    "query_string": {
      "query": "\"Information Retrieval\"~5",
      "fields": ["summary"]
    }
  }
}
```



## D13

| Query | Nb results |
|-------|------------|
| 1     | 20         |
| 2     | 36         |
| 3     | 69         |
| 4     | 205        |
| 5     | 30         |


## D14

```
GET /cacm_english/_search
{
  "query": {
    "function_score": {
      "query": {
        "query_string": {
          "query": "compiler program",
          "fields": ["summary"]
        }
      },
      "functions": [
        {
          "linear": {
            "date": {
              "origin": "1970-01",
              "scale": "90d",
              "offset": "0d",
              "decay": 0.5
            }
          }
        }
      ]
    }
  }
}
```