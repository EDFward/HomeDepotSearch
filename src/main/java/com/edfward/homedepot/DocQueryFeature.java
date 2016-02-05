package com.edfward.homedepot;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class DocQueryFeature {

  private IndexSearcher searcher;

  private Analyzer analyzer;

  public DocQueryFeature() throws IOException {
    Directory directory = FSDirectory.open(Indexing.INDEX_PATH);
    DirectoryReader indexReader = DirectoryReader.open(directory);
    searcher = new IndexSearcher(indexReader);
    analyzer = new EnglishAnalyzer();
  }

  public static void main(String[] args) throws IOException, ParseException {
    DocQueryFeature scoring = new DocQueryFeature();
    scoring.featureTransform("data/train.csv", "data/train-feature-6.csv");
//    scoring.featureTransform("/test.csv", "data/test-feature.csv");
  }

  // Transform `train.csv` or `test.csv` to feature vector files.
  public void featureTransform(String inputFilePath, String outputFilePath) throws IOException, ParseException {
    Reader reader = new InputStreamReader(new FileInputStream(inputFilePath));
    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());
    boolean hasRelevance = csvParser.getHeaderMap().containsKey(Constant.CSV_RELEVANCE);

    try (
        FileWriter writer = new FileWriter(outputFilePath);
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)
    ) {
      for (CSVRecord record : csvParser) {
        Long id = Long.parseLong(record.get(Constant.CSV_PRODUCT_ID));
        String terms = record.get(Constant.CSV_SEARCH_TERM);

        List featureRecord = new ArrayList();
        // Feature 1 - BM25 on title.
        featureRecord.add(getSearchScore(id, terms, Constant.FIELD_TITLE, SearchSimilarity.BM25));
        // Feature 2 - BM25 on description.
        featureRecord.add(getSearchScore(id, terms, Constant.FIELD_DESCRIPTION, SearchSimilarity.BM25));
        // Feature 3 - TF/IDF on title.
        featureRecord.add(getSearchScore(id, terms, Constant.FIELD_TITLE, SearchSimilarity.CLASSIC));
        // Feature 4 - TF/IDF on description.
        featureRecord.add(getSearchScore(id, terms, Constant.FIELD_DESCRIPTION, SearchSimilarity.CLASSIC));

        TermOverlap overlap = getOverlap(id, terms);
        // Feature 5 - term overlap in title.
        featureRecord.add(overlap.titleOverlap);
        // Feature 6 - term overlap in description.
        featureRecord.add(overlap.descriptionOverlap);

        // Relevance.
        if (hasRelevance) {
          featureRecord.add(record.get(Constant.CSV_RELEVANCE));
//          featureRecord.add(Math.round(Float.parseFloat(record.get(Constant.CSV_RELEVANCE))) - 1);
        }

        csvPrinter.printRecord(featureRecord);
      }
    }
  }

  private float getSearchScore(Long productId, String terms, String field, SearchSimilarity sim)
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
    Query fieldQuery = fieldParser.parse(QueryParser.escape(terms));

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

  private TermOverlap getOverlap(Long productId, String terms) throws IOException {
    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, productId.toString()));
    TopDocs docs = searcher.search(idQuery, 1);

    if (docs.totalHits != 1) {
      throw new AssertionError("Couldn't find document with ID " + productId);
    }

    return new TermOverlap(searcher.doc(docs.scoreDocs[0].doc), terms);
  }

  enum SearchSimilarity {
    BM25, CLASSIC
  }

  class TermOverlap {

    int titleOverlap;

    int descriptionOverlap;

    TermOverlap(Document doc, String terms) {
      terms = terms.toLowerCase();
      String title = doc.get(Constant.FIELD_TITLE).toLowerCase();
      String description = doc.get(Constant.FIELD_DESCRIPTION).toLowerCase();
      titleOverlap = StringUtils.countMatches(title, terms);
      descriptionOverlap = StringUtils.countMatches(description, terms);
    }
  }

}
