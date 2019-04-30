package bigdata.mtf.gmall.realtime.utils

import java.io.InputStreamReader
import java.util.Properties

object PropertiesUtil {



  def main(args: Array[String]): Unit = {
    val properties: Properties = PropertiesUtil.load("configure.properties")

    println(properties.getProperty("kafka.broker.list"))
  }

  def load(propertieName:String="configure.properties"): Properties ={

    val prop=new Properties();
    prop.load(new InputStreamReader(Thread.currentThread().getContextClassLoader.getResourceAsStream(propertieName) , "UTF-8"))
    prop
  }




}






























