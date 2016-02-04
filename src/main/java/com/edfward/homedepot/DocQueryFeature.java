package com.edfward.homedepot;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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
    scoring.featureTransform("/train.csv", "data/train-feature.csv");
  }

  // Transform `train.csv` or `test.csv` to feature vector files.
  public void featureTransform(String inputFilePath, String outputFilePath) throws IOException, ParseException {
    Reader reader = new InputStreamReader(getClass().getResourceAsStream(inputFilePath));
    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());

    try (
        FileWriter writer = new FileWriter(outputFilePath);
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
    ) {
      for (CSVRecord record : csvParser) {
        Long id = Long.parseLong(record.get(Constants.CSV_PRODUCT_ID));
        String terms = record.get(Constants.CSV_SEARCH_TERM);
        Double relevance = Double.valueOf(record.get(Constants.CSV_RELEVANCE));

        List featureRecord = new ArrayList();
        // Feature 1 - BM25 on title.
        featureRecord.add(getSearchScore(id, terms, Constants.FIELD_TITLE, SearchSimilarity.BM25));
        // Feature 2 - BM25 on description.
        featureRecord.add(getSearchScore(id, terms, Constants.FIELD_DESCRIPTION, SearchSimilarity.BM25));
        // Feature 3 - TF/IDF on title.
        featureRecord.add(getSearchScore(id, terms, Constants.FIELD_TITLE, SearchSimilarity.CLASSIC));
        // Feature 4 - TF/IDF on description.
        featureRecord.add(getSearchScore(id, terms, Constants.FIELD_DESCRIPTION, SearchSimilarity.CLASSIC));

        // Relevance.
        featureRecord.add(relevance);

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

    Query idQuery = new TermQuery(new Term(Constants.FIELD_ID, productId.toString()));
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

  enum SearchSimilarity {
    BM25, CLASSIC
  }

}
