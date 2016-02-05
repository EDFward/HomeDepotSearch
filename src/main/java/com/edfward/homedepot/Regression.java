package com.edfward.homedepot;

import org.canova.api.records.reader.RecordReader;
import org.canova.api.records.reader.impl.CSVRecordReader;
import org.canova.api.split.FileSplit;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class Regression {

  public static final int TRAINING_SIZE = 74067;

  public static final int TESTING_SIZE = 166693;

  private static Logger log = LoggerFactory.getLogger(Regression.class);

  public static void main(String[] args) throws IOException, InterruptedException {
    int seed = 123;
    int iterations = 10;
    int featureNum = 6;

    RecordReader trainSetReader = new CSVRecordReader();
    trainSetReader.initialize(new FileSplit(new File("data/train-feature-6.csv")));

    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().miniBatch(false)
        .weightInit(WeightInit.XAVIER)
        .seed(seed)  // Seed to lock in weight initialization for tuning.
        .iterations(iterations)  // # training iterations predict/classify & backprop.
        .updater(Updater.SGD).dropOut(0.5)
        .learningRate(1e-3)  // Optimization step size.
        .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT)  // Backprop method (calculate the gradients).
        .list(2)  // # NN layers (does not count input layer).
        .layer(0, new RBM.Builder(RBM.HiddenUnit.RECTIFIED, RBM.VisibleUnit.GAUSSIAN)
            .nIn(featureNum * 1)  // # input nodes.
            .nOut(5)  // # fully connected hidden layer nodes. Add list if multiple layers.
            .weightInit(WeightInit.XAVIER)  // Weight initialization method.
            .k(1)  // # contrastive divergence iterations.
            .activation("relu")  // Activation function type.
            .lossFunction(LossFunctions.LossFunction.RMSE_XENT)  // Loss function type.
            .updater(Updater.ADAGRAD)
            .dropOut(0.5)
            .build()
        )  // NN layer type.
        .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.RMSE_XENT)
            .nIn(5)  // # input nodes.
            .nOut(1)  // # output nodes.
            .activation("identity")
            .weightInit(WeightInit.XAVIER)
            .build()
        )  // NN layer type.
        .build();

    DataSetIterator trainSetIter = new RecordReaderDataSetIterator(trainSetReader, null, TRAINING_SIZE, featureNum, 1, true);
    DataSet trainSet = trainSetIter.next();

    SplitTestAndTrain testAndTrain = trainSet.splitTestAndTrain(0.9);
    MultiLayerNetwork network = new MultiLayerNetwork(conf);

    network.init();
    int listenerFreq = iterations / 5;
    network.setListeners(Arrays.asList((IterationListener) new ScoreIterationListener(listenerFreq)));

    trainSet.normalizeZeroMeanZeroUnitVariance();
    trainSet.shuffle();

    network.fit(testAndTrain.getTrain());

    DataSet test = testAndTrain.getTest();
    INDArray output = network.output(test.getFeatureMatrix());
    log.info("RMSE: " + rmse(test.getLabels(), output));

    RecordReader testSetReader = new CSVRecordReader();
    testSetReader.initialize(new FileSplit(new File("data/test-feature-6.csv")));
    DataSetIterator testSetIter = new RecordReaderDataSetIterator(testSetReader, TESTING_SIZE);
    DataSet testSet = testSetIter.next();
    INDArray testOutput = network.output(testSet.getFeatureMatrix());

    output("data/predict-6.txt", testOutput);
  }

  private static double rmse(INDArray array1, INDArray array2) {
    int size = array1.shape()[0];
    double diffSum = 0;
    for (int i = 0; i < size; i++) {
      double diff = array1.getDouble(i) - array2.getDouble(i);
      diffSum += diff * diff;
    }
    return Math.sqrt(diffSum / size);
  }

  private static void output(String outputFilePath, INDArray predicts) {
    try (
        FileWriter writer = new FileWriter(outputFilePath)
    ) {
      // Print header.
      writer.write("\"relevance\"\n");
      int size = predicts.shape()[0];
      for (int i = 0; i < size; ++i) {
        double score = predicts.getDouble(i);
        if (score < 1) {
          score = 1;
        } else if (score > 3) {
          score = 3;
        }
        writer.write(score + "\n");
      }
    } catch (IOException e) {
      System.err.println("Write result file failed");
      e.printStackTrace();
    }
  }
}
