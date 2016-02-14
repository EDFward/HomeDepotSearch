package com.edfward.homedepot;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;


abstract class SearchFeature extends FieldFeatureBase {

  // Build query of sequential dependence model.
  private final Query buildSDMQuery(String searchQuery) throws ParseException {
    String[] terms = Arrays.stream(searchQuery.toLowerCase().split("\\s+"))
        .map(QueryParser::escape)
        .filter(s -> !s.equals("and") && !s.equals("or"))
        .toArray(String[]::new);

    // Also serve as a query builder.
    QueryParser parser = new QueryParser(getField(), analyzer);

    if (terms.length == 0) {
      // Simply parse the original search terms.
      return parser.parse(QueryParser.escape(searchQuery));
    }

    // Query part 1: 'OR' connected terms.
    Query concatQuery = parser.createBooleanQuery(
        getField(),
        Arrays.stream(terms).collect(Collectors.joining(" ")),
        BooleanClause.Occur.SHOULD);
    if (concatQuery == null) {
      // Fallback, again.
      concatQuery = parser.parse(QueryParser.escape(searchQuery));
    }

    BooleanQuery.Builder nearQueryBuilder = new BooleanQuery.Builder(),
        windowQueryBuilder = new BooleanQuery.Builder();
    for (int i = 0; i < terms.length - 1; ++i) {
      // Near query requires exact phrase match.
      Query nearQuery = parser.createPhraseQuery(getField(), terms[i] + ' ' + terms[i + 1]);
      if (nearQuery != null) {
        nearQueryBuilder.add(nearQuery, BooleanClause.Occur.SHOULD);
      }
      // Window size is 8.
      Query windowQuery = parser.createPhraseQuery(getField(), terms[i] + ' ' + terms[i + 1], 8);
      if (windowQuery != null) {
        windowQueryBuilder.add(windowQuery, BooleanClause.Occur.SHOULD);
      }
    }

    Query finalQuery = new BooleanQuery.Builder()
        .add(concatQuery, BooleanClause.Occur.SHOULD)
        // Query part 2: Near query, match exact bigrams.
        .add(nearQueryBuilder.build(), BooleanClause.Occur.SHOULD)
        // Query part 3: Window query, match two terms inside a window of size 8.
        .add(windowQueryBuilder.build(), BooleanClause.Occur.SHOULD)
        .build();
    return finalQuery;
  }

  protected final float score(Long productID, String searchQuery, SearchSimilarity sim)
      throws ParseException, IOException {

    switch (sim) {
      case BM25:
        searcher.setSimilarity(new BM25Similarity());
        break;
      case CLASSIC:
        searcher.setSimilarity(new ClassicSimilarity());
        break;
    }

    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productID.toString()));
    Query sdmQuery = buildSDMQuery(searchQuery);

    BooleanQuery query = new BooleanQuery.Builder()
        .add(idQuery, BooleanClause.Occur.FILTER)
        .add(sdmQuery, BooleanClause.Occur.SHOULD)
        .build();

    TopDocs docs = searcher.search(query, 1);
    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productID);
    }

    return docs.scoreDocs[0].score;
  }

  enum SearchSimilarity {
    BM25, CLASSIC
  }
}


class BM25Feature extends SearchFeature implements Feature {
  private final String field;

  BM25Feature(String field) {
    this.field = field;
  }

  @Override
  public String getName() {
    return "bm25_" + field;
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return score(productID, searchTerms, SearchSimilarity.BM25);
  }

  @Override
  protected String getField() {
    return field;
  }
}


class TFIDFFeature extends SearchFeature implements Feature {
  private final String field;

  TFIDFFeature(String field) {
    this.field = field;
  }

  @Override
  public String getName() {
    return "tfidf_" + field;
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return score(productID, searchTerms, SearchSimilarity.CLASSIC);
  }

  @Override
  protected String getField() {
    return field;
  }
}
