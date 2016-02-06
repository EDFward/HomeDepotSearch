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


class SearchFeature extends FeatureBase {
  protected final float score(Long productId, String searchTerms, String field, SearchSimilarity sim)
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
    QueryParser fieldParser = new QueryParser(field, analyzer);
    Query fieldQuery = fieldParser.parse(QueryParser.escape(searchTerms));

    BooleanQuery query = new BooleanQuery.Builder()
        .add(idQuery, BooleanClause.Occur.FILTER)
        .add(fieldQuery, BooleanClause.Occur.SHOULD)
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