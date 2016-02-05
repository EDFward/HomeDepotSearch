package com.edfward.homedepot;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

class Product {
  String title = "";
  String description = "";
  // TODO: Init attributes. Ignore for now.
  Map<String, String> attributes;

  @Override
  public String toString() {
    final int TITLE_LEN = 30, DESC_LEN = 50;
    String slicedTitle = title.length() < TITLE_LEN ? title : (title.substring(0, TITLE_LEN) + "...");
    String slicedDescription = description.length() < DESC_LEN ? description : (description.substring(0, DESC_LEN) + "...");
    return "title: '" + slicedTitle + "', desc: '" + slicedDescription + "'";
  }
}

public class Indexing {

  public static final Path INDEX_PATH = FileSystems.getDefault().getPath("index");

  public static void main(String[] args) throws IOException {
    Indexing indexing = new Indexing();
    indexing.index();
  }

  private void parse(
      String filePath,
      Map<Long, Product> products,
      // Pass a lambda to modify the product.
      BiFunction<Product, CSVRecord, Void> productModifier) throws IOException {
    Reader reader = new InputStreamReader((new FileInputStream(filePath)));
    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());
    for (CSVRecord record : csvParser) {
      long id = Long.parseLong(record.get(Constant.CSV_PRODUCT_ID));
      Product product = products.get(id);
      if (product == null) {
        product = new Product();
        products.put(id, product);
      }
      productModifier.apply(product, record);
    }
  }

  // Parse `train.csv` and `test.csv` for product titles.
  public void parseTitle(Map<Long, Product> products) throws IOException {
    BiFunction<Product, CSVRecord, Void> modifier = (product, record) -> {
      product.title = record.get(Constant.CSV_TITLE);
      return null;
    };

    parse("data/train.csv", products, modifier);
    parse("data/test.csv", products, modifier);
  }

  // Parse `product_descriptions.csv` for product descriptions.
  public void parseDescription(Map<Long, Product> products) throws IOException {
    BiFunction<Product, CSVRecord, Void> modifier = (product, record) -> {
      product.description = record.get(Constant.CSV_DESCRIPTION);
      return null;
    };
    parse("data/product_descriptions.csv", products, modifier);
  }

  public void index() throws IOException {
    Map<Long, Product> products = new HashMap<>();
    // 'Decorate' the product hashmap.
    parseTitle(products);
    parseDescription(products);
    // TODO: Parse attributes.

    // Init Lucene stuff.
    Analyzer analyzer = new EnglishAnalyzer();
    Directory directory = FSDirectory.open(INDEX_PATH);
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    IndexWriter writer = new IndexWriter(directory, config);

    Document doc = new Document();
    // Do not analyze ID.
    StringField idField = new StringField(Constant.FIELD_ID, "", Field.Store.YES);
    doc.add(idField);
    // Analyze title and description.
    TextField titleField = new TextField(Constant.FIELD_TITLE, "", Field.Store.YES);
    doc.add(titleField);
    TextField descriptionField = new TextField(Constant.FIELD_DESCRIPTION, "", Field.Store.YES);
    doc.add(descriptionField);
    // Write index.
    for (Map.Entry<Long, Product> entry : products.entrySet()) {
      Long id = entry.getKey();
      Product product = entry.getValue();
      idField.setStringValue(id.toString());
      titleField.setStringValue(product.title);
      descriptionField.setStringValue(product.description);
      writer.addDocument(doc);
    }
    writer.close();
  }
}
