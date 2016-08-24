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
package com.intel.hibench.streambench.gearpump

import com.intel.hibench.streambench.common.metrics.MetricsUtil
import com.intel.hibench.streambench.common.{StreamBenchConfig, Platform, ConfigLoader}
import com.intel.hibench.streambench.gearpump.application._
import com.intel.hibench.streambench.gearpump.source.KafkaSourceProvider
import com.intel.hibench.streambench.gearpump.util.GearpumpConfig
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.cluster.client.ClientContext

object RunBench {
  def main(args: Array[String]) {
    this.run(args)
  }

  def run(args: Array[String]) {
    val context = ClientContext()
    implicit val system = context.system
    implicit val sourceProvider = new KafkaSourceProvider()
    val confLoader = new ConfigLoader(args(0))
    val gearConf = getConfig(confLoader)

    val benchmark = gearConf.benchName match {
      case "wordcount" => new WordCount(gearConf)
      case _ => new IdentityApp(gearConf)
    }

    val benchConf = UserConfig.empty
        .withValue(GearpumpConfig.BENCH_CONFIG, gearConf)
    context.submit(benchmark.application(benchConf))
    context.close()
  }

  private def getConfig(conf: ConfigLoader): GearpumpConfig = {
    val benchName = conf.getProperty(StreamBenchConfig.TESTCASE)
    val topic = conf.getProperty(StreamBenchConfig.KAFKA_TOPIC)
    val zkHost = conf.getProperty(StreamBenchConfig.ZK_HOST)
    val consumerGroup = conf.getProperty(StreamBenchConfig.CONSUMER_GROUP)
    val parallelism = conf.getProperty(StreamBenchConfig.GEARPUMP_PARALLELISM).toInt
    val brokerList = conf.getProperty(StreamBenchConfig.KAFKA_BROKER_LIST)
    val prob = conf.getProperty(StreamBenchConfig.SAMPLE_PROBABILITY).toDouble
    val reporterTopic = getReporterTopic(conf)
    val reporterTopicPartitions = conf.getProperty(StreamBenchConfig.KAFKA_TOPIC_PARTITIONS).toInt
    MetricsUtil.createTopic(zkHost, reporterTopic, reporterTopicPartitions)

    val windowDuration = conf.getProperty(StreamBenchConfig.FixWINDOW_DURATION).toLong
    val windowStep = conf.getProperty(StreamBenchConfig.FixWINDOW_SLIDESTEP).toLong

    GearpumpConfig(benchName, zkHost, brokerList, consumerGroup, topic,
      parallelism, prob, reporterTopic, windowDuration, windowStep)
  }

  private def getReporterTopic(conf: ConfigLoader): String = {
    val topic = conf.getProperty(StreamBenchConfig.KAFKA_TOPIC)
    val producerNum: Int = conf.getProperty(StreamBenchConfig.DATAGEN_PRODUCER_NUMBER).toInt
    val recordPerInterval = conf.getProperty(StreamBenchConfig.DATAGEN_RECORDS_PRE_INTERVAL).toLong
    val intervalSpan: Int = conf.getProperty(StreamBenchConfig.DATAGEN_INTERVAL_SPAN).toInt
    MetricsUtil.getTopic(Platform.GEARPUMP, topic, producerNum, recordPerInterval, intervalSpan)
  }
}
