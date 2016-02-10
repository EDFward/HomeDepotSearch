package com.edfward.homedepot;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;

class OverlapFeature extends FeatureBase {
  protected final float count(Long productID, String searchQuery, String field) throws IOException {
    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productID.toString()));
    TopDocs docs = searcher.search(idQuery, 1);

    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productID);
    }

    Document doc = searcher.doc(docs.scoreDocs[0].doc);

    searchQuery = searchQuery.toLowerCase();
    String text = doc.get(field).toLowerCase();
    return StringUtils.countMatches(text, searchQuery);
  }
}


class OverlapTitleFeature extends OverlapFeature implements Feature {
  @Override
  public String getName() {
    return "overlap_title";
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return super.count(productID, searchTerms, Constant.FIELD_TITLE);
  }
}


class OverlapDescriptionFeature extends OverlapFeature implements Feature {
  @Override
  public String getName() {
    return "overlap_description";
  }

  @Override
  public float getValue(Long productID, String searchTerms) throws IOException, ParseException {
    return super.count(productID, searchTerms, Constant.FIELD_DESCRIPTION);
  }
}
