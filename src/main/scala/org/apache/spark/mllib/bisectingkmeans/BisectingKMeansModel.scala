/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.mllib.bisectingkmeans

import breeze.linalg.{Vector => BV, norm => breezeNorm}

import org.apache.spark.Logging
import org.apache.spark.api.java.JavaRDD
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.rdd.RDD

/**
 * This class is used for the model of the bisecting kmeans
 *
 * @param node a cluster as a tree node
 */
class BisectingKMeansModel(val node: BisectingClusterNode) extends Serializable with Logging {

  def getClusters: Array[BisectingClusterNode] = this.node.getLeavesNodes

  def getCenters: Array[Vector] = this.getClusters.map(_.center)

  /**
   * Predicts the closest cluster by one point
   */
  def predict(vector: Vector): Int = {
    // TODO Supports distance metrics other Euclidean distance metric
    val metric = (bv1: BV[Double], bv2: BV[Double]) => breezeNorm(bv1 - bv2, 2.0)

    val centers = this.getCenters.map(_.toBreeze)
    BisectingKMeans.findClosestCenter(metric)(centers)(vector.toBreeze)
  }

  /**
   * Predicts the closest cluster by RDD of the points
   */
  def predict(data: RDD[Vector]): RDD[Int] = {
    val sc = data.sparkContext

    // TODO Supports distance metrics other Euclidean distance metric
    val metric = (bv1: BV[Double], bv2: BV[Double]) => breezeNorm(bv1 - bv2, 2.0)
    sc.broadcast(metric)
    val centers = this.getCenters.map(_.toBreeze)
    sc.broadcast(centers)

    data.map{point =>
      BisectingKMeans.findClosestCenter(metric)(centers)(point.toBreeze)
    }
  }

  /**
   * Predicts the closest cluster by RDD of the points for Java
   */
  def predict(points: JavaRDD[Vector]): JavaRDD[java.lang.Integer] =
    predict(points.rdd).toJavaRDD().asInstanceOf[JavaRDD[java.lang.Integer]]

  /**
   * Computes Within Set Sum of Squared Error(WSSSE)
   */
  def WSSSE(data: RDD[Vector]): Double = {
    val bvCenters = this.getCenters.map(_.toBreeze)
    data.context.broadcast(bvCenters)
    val distances = data.map {point =>
      val bvPoint = point.toBreeze
      val metric = (bv1: BV[Double], bv2: BV[Double]) => breezeNorm(bv1 - bv2, 2.0)
      val idx = BisectingKMeans.findClosestCenter(metric)(bvCenters)(bvPoint)
      val closestCenter = bvCenters(idx)
      val distance = metric(bvPoint, closestCenter)
      distance
    }
    distances.sum()
  }

  def WSSSE(data: JavaRDD[Vector]): Double = this.WSSSE(data.rdd)

  def toAdjacencyList: Array[(Int, Int, Double)] = this.node.toAdjacencyList

  /** Since Java doesn't support tuple, we must support the data structure for java and py4j. */
  def toJavaAdjacencyList: java.util.ArrayList[java.util.ArrayList[java.lang.Double]] = {
    val javaList = new java.util.ArrayList[java.util.ArrayList[java.lang.Double]]()
    this.node.toAdjacencyList.foreach { x =>
      val edge = new java.util.ArrayList[java.lang.Double]()
      edge.add(x._1.toDouble)
      edge.add(x._2.toDouble)
      edge.add(x._3.toDouble)
      javaList.add(edge)
    }
    javaList
  }

  def toLinkageMatrix: Array[(Int, Int, Double, Int)] = this.node.toLinkageMatrix

  /** Since Java doesn't support tuple, we must support the data structure for java and py4j. */
  def toJavaLinkageMatrix: java.util.ArrayList[java.util.ArrayList[java.lang.Double]] = {
    val javaList = new java.util.ArrayList[java.util.ArrayList[java.lang.Double]]()
    this.node.toLinkageMatrix.foreach {x =>
      val row = new java.util.ArrayList[java.lang.Double]()
      row.add(x._1.toDouble)
      row.add(x._2.toDouble)
      row.add(x._3.toDouble)
      row.add(x._4.toDouble)
      javaList.add(row)
    }
    javaList
  }
}

