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

abstract class TermFeature extends FieldFeatureBase {
  abstract double calculateStatistics(Long productID, Term term) throws IOException;

  private final int getDocID(Long productID) throws IOException {
    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productID.toString()));
    TopDocs docs = searcher.search(idQuery, 1);

    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productID);
    }

    return docs.scoreDocs[0].doc;
  }

  protected final long getDocLen(Long productID) throws IOException {
    NumericDocValues norms = MultiDocValues.getNormValues(searcher.getIndexReader(), getField());
    int docID = getDocID(productID);
    return norms.get(docID);
  }

  protected final double getTF(Long productID, Term term) throws IOException {
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

  protected final double getIDF(Term term) throws IOException {
    double numDoc = searcher.getIndexReader().numDocs();
    int df = searcher.getIndexReader().docFreq(term) + 1;
    return numDoc / df;
  }

  protected final float sum(Long productID, String searchQuery)
      throws ParseException, IOException {
    String[] terms = Arrays.stream(searchQuery.toLowerCase().split("\\s+"))
        .map(QueryParser::escape)
        .filter(s -> !s.equals("and") && !s.equals("or"))
        .toArray(String[]::new);

    double sumVal = 0;

    QueryBuilder queryBuilder = new QueryBuilder(analyzer);
    for (String term : terms) {
      Query query = queryBuilder.createBooleanQuery(getField(), term);
      if (query != null && query instanceof TermQuery) {
        Term t = ((TermQuery) query).getTerm();
        sumVal += calculateStatistics(productID, t);
      }
    }

    return (float) sumVal;
  }
}

/**********
 * TF based features.
 **********/

class TFFeature extends TermFeature implements Feature {
  protected final String field;

  TFFeature(String field) {
    this.field = field;
  }

  protected String getField() {
    return field;
  }

  @Override
  public String getName() {
    return "tf_" + field;
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return sum(productID, searchTerms);
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return getTF(productID, term);
  }
}


class TFSIGIRFeature extends TFFeature implements Feature {
  TFSIGIRFeature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "tf_" + field + "_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(1 + getTF(productID, term));
  }
}


class TFNormalizedFeature extends TFFeature implements Feature {
  TFNormalizedFeature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "tf_" + field + "_norm";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    long docLen = getDocLen(productID);
    return getTF(productID, term) / docLen;
  }
}


class TFNormalizedSIGIRFeature extends TFFeature implements Feature {
  TFNormalizedSIGIRFeature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "tf_" + field + "_norm_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    long docLen = getDocLen(productID);
    return Math.log(getTF(productID, term) / docLen + 1);
  }
}


/**********
 * IDF based features.
 **********/


class IDFFeature extends TermFeature implements Feature {
  protected String field;

  IDFFeature(String field) {
    this.field = field;
  }

  protected String getField() {
    return field;
  }

  @Override
  public String getName() {
    return "idf_" + field;
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return sum(productID, searchTerms);
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(getIDF(term));
  }
}


class IDFSIGIRFeature extends IDFFeature implements Feature {
  IDFSIGIRFeature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "idf_" + field + "_sigir";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(Math.log(getIDF(term)));
  }
}


class IDFSIGIR2Feature extends IDFFeature implements Feature {
  IDFSIGIR2Feature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "idf_" + field + "_sigir2";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    return Math.log(getIDF(term) + 1);
  }
}


class IDFSIGIR3Feature extends IDFFeature implements Feature {
  IDFSIGIR3Feature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "idf_" + field + "_sigir3";
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    long docLen = getDocLen(productID);
    double tf = getTF(productID, term);
    return Math.log(tf / docLen * Math.log(getIDF(term)) + 1);
  }
}


class TermSIGIRFeature extends IDFFeature implements Feature {
  TermSIGIRFeature(String field) {
    super(field);
  }

  @Override
  public String getName() {
    return "tfidf_sigir_" + field;
  }

  @Override
  double calculateStatistics(Long productID, Term term) throws IOException {
    long docLen = getDocLen(productID);
    double tf = getTF(productID, term);
    double totalTF = searcher.getIndexReader().totalTermFreq(term);
    double numDoc = searcher.getIndexReader().numDocs();
    return Math.log(tf / docLen * totalTF / numDoc + 1);
  }
}