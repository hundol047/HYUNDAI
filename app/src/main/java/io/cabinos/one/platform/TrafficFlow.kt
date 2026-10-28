package io.cabinos.one.platform

import io.cabinos.core.NavPoint
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrafficFlow(val points:List<NavPoint>,val speed:Double,val free:Double,val confidence:Double,val closed:Boolean,val fetchedAt:Long) {
    fun current(now:Long)=now-fetchedAt in 0..90_000
    val level get()=when {closed->"통제";confidence<.7->"정보 불확실";speed/free<=.5->"정체";speed/free<=.8->"서행";else->"원활"}
    val color get()=when(level){"정체","통제"->0xFFE53935.toInt();"서행"->0xFFFFC107.toInt();"원활"->0xFF43A047.toInt();else->0xFF888888.toInt()}
    companion object {
        fun parse(raw:String,now:Long):TrafficFlow {
            val o=JSONObject(raw);val a=o.getJSONArray("points")
            require(a.length() in 2..2000)
            val p=(0 until a.length()).map {i->val v=a.getJSONArray(i);NavPoint(v.getDouble(0),v.getDouble(1)).also {require(it.latitude.isFinite()&&it.longitude.isFinite()&&it.latitude in -90.0..90.0&&it.longitude in -180.0..180.0)}}
            return TrafficFlow(p,o.getDouble("currentSpeed"),o.getDouble("freeFlowSpeed"),o.getDouble("confidence"),o.getBoolean("closed"),o.getLong("fetchedAtMs")).also {
                require(it.speed.isFinite()&&it.speed>=0&&it.free.isFinite()&&it.free>0&&it.confidence in 0.0..1.0&&it.current(now))
            }
        }
    }
}
internal class TrafficClient {
    var endpoint="";private set
    private var token=""
    @Volatile private var connection:HttpURLConnection?=null
    fun cancel(){connection?.disconnect()}
    fun configure(url:String,key:String) {
        if(url.isNotBlank()){val u=java.net.URI(url);require(u.scheme=="https"&&u.host!=null&&u.userInfo==null&&u.query==null&&u.fragment==null);require(key.length in 24..256&&key.all {it.code in 33..126})}
        cancel()
        endpoint=url.trimEnd('/');token=key
    }
    suspend fun fetch(p:NavPoint,now:()->Long):TrafficFlow {
        val base=endpoint;val access=token
        return withContext(Dispatchers.IO) {
        val c=URL("$base/flow?point=${p.latitude},${p.longitude}").openConnection() as HttpURLConnection
        connection=c
        try {
            c.instanceFollowRedirects=false;c.connectTimeout=6000;c.readTimeout=12000;c.setRequestProperty("Authorization","Bearer $access")
            require(c.responseCode==200)
            val b=c.inputStream.use {input->
                val output=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                while(true){val n=input.read(chunk);if(n<0)break;require(output.size()+n<=500000);output.write(chunk,0,n)}
                output.toByteArray()
            }
            require(b.size<=500000)
            TrafficFlow.parse(String(b,Charsets.UTF_8),now())
        }finally {c.disconnect();if(connection===c)connection=null}
        }
    }
}
