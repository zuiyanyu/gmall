package bigdata.mtf.gmall.logger.control;



import bigdata.mtf.common.constants.GmallConstants;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;


@RestController  //<==>@Controller  +  @ResponseBody
public class LoggerControl {
    private static final  org.slf4j.Logger logger = LoggerFactory.getLogger(LoggerControl.class) ;

    @Autowired
    KafkaTemplate kafka ;

    /**
     * 生产日志
     * @return 返回一条日志
     */
   // @RequestMapping(value = "log",method = RequestMethod.POST)
    @PostMapping
    public String doLog(@RequestParam("log") String log){

        JSONObject jsonObject = JSON.parseObject(log);
        jsonObject.put("ts",System.currentTimeMillis());
        log = jsonObject.toJSONString();
        //System.out.println("log="+log);
        //落盘成为日志文件。
        logger.info(log);

        //发送到kafka
        if("startup".equals(jsonObject.getString("type"))){
            //启动日志发送到  启动主题上
            kafka.send(GmallConstants.KAFKA_TOPIC_STARTUP,log);
        }else{
            //事件日志发送到 事件主题上 KAFKA_TOPIC_EVENT
            kafka.send(GmallConstants.KAFKA_TOPIC_EVENT,log);
        }


       return  "success";
    }
}
