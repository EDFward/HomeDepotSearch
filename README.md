# Predict Home Depot Search Relevance

This repo is for kaggle competition [Home Depot Product Search Relevance](https://www.kaggle.com/c/home-depot-product-search-relevance).

## Feature

Feature generation is mostly from Microsoft's paper [LETOR: Benchmark Dataset for Research on Learning to Rank for Information Retrieval](http://research.microsoft.com/en-us/people/taoqin/qin-lr4ir.pdf). I use [Lucene](https://lucene.apache.org/) to extract those features, including (for now):

- TF-IDF or BM25 scores between a given query and a document
- Term frequency and its variations
- Term overlaps between the query and the document

Note that all those features are field-specific (only *title* and *description* fields for now).

## Learning

[DL4J](http://deeplearning4j.org/) is used but my results are bad (worse than random forest in `scikit-learn`). Still working on it.
