package com.edfward.homedepot;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;


interface Feature {
  String getName();

  float getValue(Long productID, String searchTerms) throws IOException, ParseException;
}


abstract class FeatureBase {
  protected static IndexSearcher searcher;

  protected static Analyzer analyzer;

  static {
    Directory directory;
    try {
      directory = FSDirectory.open(Indexing.INDEX_PATH);
      DirectoryReader indexReader = DirectoryReader.open(directory);
      searcher = new IndexSearcher(indexReader);
      analyzer = new EnglishAnalyzer();
    } catch (IOException e) {
      e.printStackTrace();
      throw new AssertionError("Failed to read index.");
    }
  }
}

abstract class FieldFeatureBase extends FeatureBase {
  abstract protected String getField();
}
