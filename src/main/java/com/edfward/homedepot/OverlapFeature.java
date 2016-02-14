package com.edfward.homedepot;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;

class OverlapFeature extends FieldFeatureBase implements Feature {
  private final String field;

  OverlapFeature(String field) {
    this.field = field;
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productID.toString()));
    TopDocs docs = searcher.search(idQuery, 1);

    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productID);
    }

    Document doc = searcher.doc(docs.scoreDocs[0].doc);

    searchTerms = searchTerms.toLowerCase();
    String text = doc.get(field).toLowerCase();
    return StringUtils.countMatches(text, searchTerms);
  }

  @Override
  protected String getField() {
    return field;
  }

  @Override
  public String getName() {
    return "overlap_" + field;
  }
}
