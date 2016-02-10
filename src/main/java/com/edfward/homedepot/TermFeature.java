package com.edfward.homedepot;

import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.QueryBuilder;

import java.io.IOException;
import java.util.Arrays;

abstract class TermFeature extends FeatureBase {
  abstract double calculateStatistics(Long productID, Term term) throws IOException;

  public final int getDocID(Long productID) throws IOException {
    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productID.toString()));
    TopDocs docs = searcher.search(idQuery, 1);

    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productID);
    }

    return docs.scoreDocs[0].doc;
  }

  public final double getTF(Long productID, Term term) throws IOException {
    int docID = getDocID(productID);
    double tf = 0;

    PostingsEnum postingsEnum = MultiFields.getTermDocsEnum(searcher.getIndexReader(), term.field(), term.bytes());
    if (postingsEnum != null) {
      while (postingsEnum.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
        if (postingsEnum.docID() == docID) {
          tf = postingsEnum.freq();
          break;
        }
      }
    }
    return tf;
  }

  public final float sum(Long productID, String searchQuery, String field)
      throws ParseException, IOException {
    String[] terms = Arrays.stream(searchQuery.toLowerCase().split("\\s+"))
        .map(QueryParser::escape)
        .filter(s -> !s.equals("and") && !s.equals("or"))
        .toArray(String[]::new);

    double sumVal = 0;

    QueryBuilder queryBuilder = new QueryBuilder(analyzer);
    for (String term : terms) {
      Query query = queryBuilder.createBooleanQuery(field, term);
      if (query != null && query instanceof TermQuery) {
        Term t = ((TermQuery) query).getTerm();
        sumVal += calculateStatistics(productID, t);
      }
    }

    return (float) sumVal;
  }
}

class TFTitleFeature extends TermFeature implements Feature {
  protected String getField() {
    return Constant.FIELD_TITLE;
  }

  @Override
  public String getName() {
    return "tf_title";
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return sum(productID, searchTerms, getField());
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return getTF(productID, term);
  }
}

class TFDescriptionFeature extends TermFeature implements Feature {
  protected String getField() {
    return Constant.FIELD_DESCRIPTION;
  }

  @Override
  public String getName() {
    return "tf_description";
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return sum(productID, searchTerms, getField());
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return getTF(productID, term);
  }
}

class TFTitleSIGIRFeature extends TFTitleFeature implements Feature {
  @Override
  public String getName() {
    return "tf_title_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(1 + getTF(productID, term));
  }
}

class TFDescriptionSIGIRFeature extends TFDescriptionFeature implements Feature {
  @Override
  public String getName() {
    return "tf_description_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(1 + getTF(productID, term));
  }
}

class TFTitleNormalizedFeature extends TFTitleFeature implements Feature {
  @Override
  public String getName() {
    return "tf_title_norm";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    NumericDocValues norms = MultiDocValues.getNormValues(searcher.getIndexReader(), getField());
    int docID = getDocID(productID);
    long docLen = norms.get(docID);
    return getTF(productID, term) / docLen;
  }
}

class TFDescriptionNormalizedFeature extends TFDescriptionFeature implements Feature {
  @Override
  public String getName() {
    return "tf_description_norm";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    NumericDocValues norms = MultiDocValues.getNormValues(searcher.getIndexReader(), getField());
    int docID = getDocID(productID);
    long docLen = norms.get(docID);
    return getTF(productID, term) / docLen;
  }
}

class TFTitleNormalizedSIGIRFeature extends TFTitleNormalizedFeature implements Feature {
  @Override
  public String getName() {
    return "tf_title_norm_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(super.calculateStatistics(productID, term) + 1);
  }
}

class TFDescriptionNormalizedSIGIRFeature extends TFDescriptionNormalizedFeature implements Feature {
  @Override
  public String getName() {
    return "tf_description_norm_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(super.calculateStatistics(productID, term) + 1);
  }
}