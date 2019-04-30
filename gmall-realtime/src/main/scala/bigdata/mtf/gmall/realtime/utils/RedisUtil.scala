package bigdata.mtf.gmall.realtime.utils

import java.util.Properties

import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

object RedisUtil {
  private var password:String = null ;
  var jedisPool:JedisPool =null ;
  {
      //开辟一个连接池
      val config: Properties = PropertiesUtil.load()
      val host: String = config.getProperty("redis.host")
      val port: String = config.getProperty("redis.port")
      password = config.getProperty("redis.auth")
      println("host="+host+"  port="+port+" password= "+password)
      val jedisPoolConfig = new JedisPoolConfig
      jedisPoolConfig.setMaxTotal(100) //最大连接数
      jedisPoolConfig.setMaxIdle(20) //最大空闲
      jedisPoolConfig.setMinIdle(20)//最小空闲
      jedisPoolConfig.setBlockWhenExhausted(true)//忙碌的时候是否等待
      jedisPoolConfig.setMaxWaitMillis(500)//忙碌的时候等待的时长 毫秒
      jedisPoolConfig.setTestOnBorrow(true)//每次获得连接前进行测试
      jedisPool = new JedisPool(jedisPoolConfig,host,port.toInt,2000,password)

   /* if(jedisPool==null){
      println("redis连接池准备失败！")
    }else{
      println("redis连接池已经准备成功！")
    }
    println("jedisPool="+jedisPool)*/
  }
  def getJedisClient:Jedis = {
    //获取一个连接
    return jedisPool.getResource
  }

  def main(args: Array[String]): Unit = {
    val client: Jedis = RedisUtil.getJedisClient
    println( client.ping())
    client.close()
  }

}
