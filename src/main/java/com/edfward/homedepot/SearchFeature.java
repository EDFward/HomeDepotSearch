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


class SearchFeature extends FeatureBase {
  // Build query of sequential dependence model.
  private final Query buildSDMQuery(String searchQuery, String field) throws ParseException {
    String[] terms = Arrays.stream(searchQuery.toLowerCase().split("\\s+"))
        .map(QueryParser::escape)
        .filter(s -> !s.equals("and") && !s.equals("or"))
        .toArray(String[]::new);

    // Also serve as a query builder.
    QueryParser queryParser = new QueryParser(field, analyzer);

    if (terms.length == 0) {
      // Simply parse the original search terms.
      return queryParser.parse(QueryParser.escape(searchQuery));
    }

    // Query part 1: 'OR' connected terms.
    Query concatQuery = queryParser.createBooleanQuery(
        field,
        Arrays.stream(terms).collect(Collectors.joining(" ")),
        BooleanClause.Occur.SHOULD);

    BooleanQuery.Builder nearQueryBuilder = new BooleanQuery.Builder(),
        windowQueryBuilder = new BooleanQuery.Builder();
    for (int i = 0; i < terms.length - 1; ++i) {
      // Near query requires exact phrase match.
      Query nearQuery = queryParser.createPhraseQuery(field, terms[i] + ' ' + terms[i + 1]);
      if (nearQuery != null) {
        nearQueryBuilder.add(nearQuery, BooleanClause.Occur.SHOULD);
      }
      // Window size is 8.
      Query windowQuery = queryParser.createPhraseQuery(field, terms[i] + ' ' + terms[i + 1], 8);
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

  protected final float score(Long productId, String searchQuery, String field, SearchSimilarity sim)
      throws ParseException, IOException {

    switch (sim) {
      case BM25:
        searcher.setSimilarity(new BM25Similarity());
        break;
      case CLASSIC:
        searcher.setSimilarity(new ClassicSimilarity());
        break;
    }

    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productId.toString()));
    Query sdmQuery = buildSDMQuery(searchQuery, field);

    BooleanQuery query = new BooleanQuery.Builder()
        .add(idQuery, BooleanClause.Occur.FILTER)
        .add(sdmQuery, BooleanClause.Occur.SHOULD)
        .build();

    TopDocs docs = searcher.search(query, 1);
    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productId);
    }

    return docs.scoreDocs[0].score;
  }

  enum SearchSimilarity {
    BM25, CLASSIC
  }
}


class BM25TitleFeature extends SearchFeature implements Feature {
  @Override
  public String getName() {
    return "bm25_title";
  }

  @Override
  public float getValue(Long productId, String searchTerms) throws IOException, ParseException {
    return super.score(productId, searchTerms, Constant.FIELD_TITLE, SearchSimilarity.BM25);
  }
}


class BM25DescriptionFeature extends SearchFeature implements Feature {
  @Override
  public String getName() {
    return "bm25_description";
  }

  @Override
  public float getValue(Long productId, String searchTerms) throws IOException, ParseException {
    return super.score(productId, searchTerms, Constant.FIELD_DESCRIPTION, SearchSimilarity.BM25);
  }
}


class TFIDFTitleFeature extends SearchFeature implements Feature {
  @Override
  public String getName() {
    return "tfidf_title";
  }

  @Override
  public float getValue(Long productId, String searchTerms) throws IOException, ParseException {
    return super.score(productId, searchTerms, Constant.FIELD_TITLE, SearchSimilarity.CLASSIC);
  }
}


class TFIDFDescriptionFeature extends SearchFeature implements Feature {
  @Override
  public String getName() {
    return "tfidf_description";
  }

  @Override
  public float getValue(Long productId, String searchTerms) throws IOException, ParseException {
    return super.score(productId, searchTerms, Constant.FIELD_DESCRIPTION, SearchSimilarity.CLASSIC);
  }
}