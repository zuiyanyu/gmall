package bigdata.mtf.gmall.realtime

import java.text.SimpleDateFormat
import java.util
import java.util.Date

import bigdata.mtf.common.constants.GmallConstants
import bigdata.mtf.gmall.realtime.bean.StartUpLog
import bigdata.mtf.gmall.realtime.utils.{MyKafkaUtil, RedisUtil}
import com.alibaba.fastjson.JSON
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis

object DauApp {
  def main(args: Array[String]): Unit = {
    val sparkConf: SparkConf = new SparkConf().setAppName("dau_app").setMaster("local[*]")
    val ssc = new StreamingContext(new SparkContext(sparkConf),Seconds(5))

    val startupDSteam: InputDStream[ConsumerRecord[String, String]] =MyKafkaUtil.getKafkaStream(GmallConstants.KAFKA_TOPIC_STARTUP,ssc)
    print("开始处理数据...")

   /* startupDSteam.foreachRDD(rdd=>{
      println(rdd.map(record=>record.value).collect().mkString("\n"))
    })*/
    //TODO 日访问日志去重:
    //把当日已经访问的启动日志保存起来() .mid是设备id
    // 以当日已经访问用户清单为依据，过滤再次访问的请求。

    //TODO 将当日已经访问过的用户保存起来，redis
    //转换log数据的格式 。每行数据映射到一个样例类中 .补齐日期格式
    val startupLogDStream: DStream[StartUpLog] = startupDSteam.map { record =>
      val jsonLog: String = record.value
    //  println("1===="+jsonLog)

      val startUpLog: StartUpLog = JSON.parseObject(jsonLog, classOf[StartUpLog])
      val ts: Long = startUpLog.ts
      val datetimeString: String = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ts))
      val datetimeArray: Array[String] = datetimeString.split(" ")
      startUpLog.logDate = datetimeArray(0)
      startUpLog.logHour = datetimeArray(1).split(":")(0)
      startUpLog.logHourMinute = datetimeArray(1)
      startUpLog
    }

      //TODO 过滤
    //对数据进行过滤:已经记录到redis中的日活跃用户，当日再次活跃，就不再进行记录。
    //虽然redis的set也能去重，但是过滤数据，减少后续数据传输量和处理量，提高性能。
    val filteredStream: DStream[StartUpLog] = startupLogDStream.transform { rdd =>

      println("过滤前的量：count= " + rdd.count())
      //每个周期都会连接一次redis，然后查询redis中的最新数据，然后广播变量。
      val jedis: Jedis = RedisUtil.getJedisClient
      val curDate: String = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
      val key = "dau:" + curDate
      val dauSet: util.Set[String] = jedis.smembers(key) //得到当日日活用户清单

      println("redis中已经记录的日活量：" + dauSet.size())
      //进行广播变量
      val dauBC: Broadcast[util.Set[String]] = ssc.sparkContext.broadcast(dauSet)
      jedis.close();
      //开始进行过滤
      val filteredRDD: RDD[StartUpLog] = rdd.filter(startUpLog => {
        //判断此当日活跃设备，是否已经记录在redis中了。如果已经记录了，那就过滤掉
        !dauBC.value.contains(startUpLog.mid)
      })
      println("过滤后的量：count= " + filteredRDD.count())
      filteredRDD
    }

    //TODO 第二次过滤后进行去重  :利用groupByKey分组去重
    // 第一次用户触发很多日活日志，这些日活日志的设备id（mid）都一样，我们只留一条。
    val startuplogGroupByMid: DStream[(String, Iterable[StartUpLog])] =
                                           filteredStream.map{startuplog=>(startuplog.mid,startuplog)}.groupByKey()
    val startuplogDistinct: DStream[StartUpLog] = startuplogGroupByMid.flatMap {
          case (mid, startuplogIter) => {
            val startupOnelogIter: Iterable[StartUpLog] = startuplogIter.take(1)
            startupOnelogIter
          }
        }

    //TODO 第一次保存到redis中时候去重
    startuplogDistinct.foreachRDD{rdd=>{
          rdd.foreachPartition{
            startUpLogIter=>{
              val jedis: Jedis = RedisUtil.getJedisClient
             // println("2.-----连接redis保存日活跃记录")
              for (startUpLog <- startUpLogIter) {
                //设计保存的key 类型 set  key: dau:data(2019-xx-xx)  value:mid  (用户设备id，代表用户)
                //sadd key value

                val key: String =  "dau:"+ startUpLog.logDate  //用户日活跃标志dau + 当日活跃日期
                val value: String = startUpLog.mid  //设备id。用户当日活跃 按照活跃设备计算。每天只取其一次启动日志
                //key相同，value。即每天可能有多个用户活跃。
                jedis.sadd(key,value)
                //数据保存到es
                println(startUpLog.mid)
              }
              jedis.close()
    }}}}

//没数据产生，就不进行循环读取？

    //TODO 将已经去重的日志，发送到到ES进行展示
    ssc.start()
    ssc.awaitTermination()
  }
}
































