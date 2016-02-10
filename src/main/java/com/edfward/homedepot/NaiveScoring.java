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
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;


public class NaiveScoring {
  private IndexSearcher searcher;

  private Analyzer analyzer;

  public NaiveScoring() throws IOException {
    Directory directory = FSDirectory.open(Indexing.INDEX_PATH);
    DirectoryReader indexReader = DirectoryReader.open(directory);
    searcher = new IndexSearcher(indexReader);
    searcher.setSimilarity(new BM25Similarity());
    analyzer = new EnglishAnalyzer();
  }

  public static void main(String[] args) throws IOException, ParseException {
    NaiveScoring scoring = new NaiveScoring();
    scoring.score("data/test.csv", "data/result-naive-scoring.csv");
  }

  public void score(String inputFilePath, String outputFilePath) throws IOException, ParseException {
    Reader reader = new InputStreamReader(new FileInputStream(inputFilePath));
    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());
    boolean hasRelevance = csvParser.getHeaderMap().containsKey(Constant.CSV_RELEVANCE);

    try (
        FileWriter writer = new FileWriter(outputFilePath);
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)
    ) {
      // Print header.
      csvPrinter.printRecord(new Object[]{"id", "relevance"});

      for (CSVRecord record : csvParser) {
        String id = record.get("id");  // Row ID.
        Long productID = Long.parseLong(record.get(Constant.CSV_PRODUCT_ID));
        String terms = record.get(Constant.CSV_SEARCH_TERM);
        int score = scoreTerms(productID, terms);
        csvPrinter.printRecord(new Object[]{id, score});
      }
    }
  }

  private int scoreTerms(long productID, String terms) throws ParseException, IOException {
    String escapedTerms = QueryParser.escape(terms);

    // Find internal document ID.
    Query idQuery = new TermQuery(new Term(Constant.FIELD_ID, String.valueOf(productID)));
    int internalID = searcher.search(idQuery, 1).scoreDocs[0].doc;

    // Search by terms.
    QueryParser titleParser = new QueryParser(Constant.FIELD_TITLE, analyzer);
    Query titleQuery = titleParser.parse(escapedTerms);
    QueryParser descriptionParser = new QueryParser(Constant.FIELD_DESCRIPTION, analyzer);
    Query descriptionQuery = descriptionParser.parse(escapedTerms);

    BooleanQuery query = new BooleanQuery.Builder()
        .add(titleQuery, BooleanClause.Occur.SHOULD)
        .add(descriptionQuery, BooleanClause.Occur.SHOULD)
        .setMinimumNumberShouldMatch(1)
        .build();

    TopDocs docs = searcher.search(query, 1000);
    int rank = 0;
    boolean found = false;
    for (ScoreDoc scoreDoc : docs.scoreDocs) {
      if (internalID == scoreDoc.doc) {
        found = true;
        break;
      }
      rank++;
    }

    // Heuristic based scoring.
    if (!found) {
      return 1;
    } else if (rank < 100) {
      return 3;
    } else {
      return 2;
    }
  }
}
