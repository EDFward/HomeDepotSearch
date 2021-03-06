package com.edfward.homedepot;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class FeatureExtraction {
  public static void main(String[] args) throws IOException, ParseException {
    FeatureExtraction extractor = new FeatureExtraction();

    // To create a feature CSV with all features.
    // extractor.createFeatureCSV("data/train.csv", "data/train-feature-14.csv");
    // extractor.createFeatureCSV("data/test.csv", "data/test-feature-14.csv");

    // Or, add features to existing feature CSV file.
    // extractor.addFeatureCSV("data/test.csv", "data/test-feature-14.csv", "data/test-feature-24.csv",
    extractor.addFeatureCSV("data/train.csv", "data/train-feature-14.csv", "data/train-feature-24.csv",
        Arrays.asList(new IDFFeature(Constant.FIELD_TITLE), new IDFFeature(Constant.FIELD_DESCRIPTION),
            new IDFSIGIRFeature(Constant.FIELD_TITLE), new IDFSIGIRFeature(Constant.FIELD_DESCRIPTION),
            new IDFSIGIR2Feature(Constant.FIELD_TITLE), new IDFSIGIR2Feature(Constant.FIELD_DESCRIPTION),
            new IDFSIGIR3Feature(Constant.FIELD_TITLE), new IDFSIGIR3Feature(Constant.FIELD_DESCRIPTION),
            new TermSIGIRFeature(Constant.FIELD_TITLE), new TermSIGIRFeature(Constant.FIELD_DESCRIPTION)));
  }

  public void createFeatureCSV(String inputQueryFilePath, String outputFeatureFilePath)
      throws IOException, ParseException {
    List<Feature> features = new ArrayList<>();
    for (String field : Arrays.asList(Constant.FIELD_TITLE, Constant.FIELD_DESCRIPTION)) {
      features.add(new BM25Feature(field));
      features.add(new TFIDFFeature(field));
      features.add(new OverlapFeature(field));
      features.add(new TFFeature(field));
      features.add(new TFSIGIRFeature(field));
      features.add(new TFNormalizedFeature(field));
      features.add(new TFNormalizedSIGIRFeature(field));
    }

    // Automatic Resource Management.
    try (
        CSVParser queryFileParser = CSVParser.parse(
            new File(inputQueryFilePath), Charset.defaultCharset(), CSVFormat.DEFAULT.withHeader());
        FileWriter outputWriter = new FileWriter(outputFeatureFilePath);
        CSVPrinter outputCSVPrinter = new CSVPrinter(outputWriter, CSVFormat.DEFAULT)
    ) {
      List<String> headers = features.stream().map(Feature::getName).collect(Collectors.toCollection(ArrayList::new));
      boolean hasRelevance = queryFileParser.getHeaderMap().containsKey(Constant.CSV_RELEVANCE);
      if (hasRelevance) {
        headers.add(Constant.CSV_RELEVANCE);
      }

      // Print header.
      outputCSVPrinter.printRecord(headers);

      for (CSVRecord queryRecord : queryFileParser) {
        Long productID = Long.parseLong(queryRecord.get(Constant.CSV_PRODUCT_ID));
        String searchTerms = queryRecord.get(Constant.CSV_SEARCH_TERM);

        List<Float> featureRecord = new ArrayList<>(headers.size());
        for (Feature feature : features) {
          float featureVal = feature.getValue(productID, searchTerms);
          featureRecord.add(featureVal);
        }
        if (hasRelevance) {
          featureRecord.add(Float.valueOf(queryRecord.get(Constant.CSV_RELEVANCE)));
        }
        outputCSVPrinter.printRecord(featureRecord);
      }
    }
  }

  public void addFeatureCSV(
      String inputQueryFilePath,
      String existingFeatureFilePath,
      String outputFeatureFilePath,
      List<Feature> features) throws IOException, ParseException {
    // Automatic Resource Management.
    try (
        CSVParser queryFileParser = CSVParser.parse(
            new File(inputQueryFilePath), Charset.defaultCharset(), CSVFormat.DEFAULT.withHeader());
        CSVParser featureFileParser = CSVParser.parse(
            new File(existingFeatureFilePath), Charset.defaultCharset(), CSVFormat.DEFAULT.withHeader());
        FileWriter outputWriter = new FileWriter(outputFeatureFilePath);
        CSVPrinter outputCSVPrinter = new CSVPrinter(outputWriter, CSVFormat.DEFAULT)
    ) {
      List<String> headers = new ArrayList<>(featureFileParser.getHeaderMap().keySet());
      // Make sure relevance column is at the last position or non-existent.
      assert !headers.contains(Constant.CSV_RELEVANCE) || headers.get(headers.size() - 1) == Constant.CSV_RELEVANCE;

      // Prepend new feature names.
      for (Feature feature : features) {
        String featureName = feature.getName();
        if (!headers.contains(featureName)) {
          headers.add(0, featureName);
        }
      }
      // Print header.
      outputCSVPrinter.printRecord(headers);

      // Assume query file and feature file have same number of lines.
      Iterator<CSVRecord> qIt = queryFileParser.iterator(), fIt = featureFileParser.iterator();
      while (qIt.hasNext() && fIt.hasNext()) {
        CSVRecord queryRecord = qIt.next();
        CSVRecord existingFeatureRecord = fIt.next();

        Long productID = Long.parseLong(queryRecord.get(Constant.CSV_PRODUCT_ID));
        String searchTerms = queryRecord.get(Constant.CSV_SEARCH_TERM);

        Map<String, String> featureMap = existingFeatureRecord.toMap();
        for (Feature feature : features) {
          float featureVal = feature.getValue(productID, searchTerms);
          featureMap.put(feature.getName(), String.valueOf(featureVal));
        }

        Object[] featureRecord = headers
            .stream()
            .map(h -> featureMap.get(h))
            .toArray(String[]::new);
        outputCSVPrinter.printRecord(featureRecord);
      }
    }
  }
}
