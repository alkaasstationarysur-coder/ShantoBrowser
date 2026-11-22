package bd.shanto.browser

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object BookmarkBackup {
    private const val limit=4*1024*1024
    fun encode(marks:Map<String,String>):ByteArray {
        require(marks.size<=10000)
        val rows=JSONArray();marks.toSortedMap().forEach { (url,name)->rows.put(JSONObject().put("url",url).put("name",name)) }
        return JSONObject().put("format","shanto-bookmarks").put("version",1).put("bookmarks",rows).toString(2).toByteArray(Charsets.UTF_8).also {require(it.size<=limit)}
    }
    fun decode(input:InputStream):Map<String,String> {
        val buf=ByteArray(8192);val out=ByteArrayOutputStream();while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=limit);out.write(buf,0,n)}
        val obj=JSONObject(out.toString("UTF-8"));require(obj.getString("format")=="shanto-bookmarks" && obj.getInt("version")==1)
        val rows=obj.getJSONArray("bookmarks");require(rows.length()<=10000);val result=linkedMapOf<String,String>()
        for(i in 0 until rows.length()){val r=rows.getJSONObject(i);val url=r.getString("url");require(Rules.host(url)!=null);val name=r.optString("name","").trim().take(100);result.putIfAbsent(url,name)}
        return result
    }
}
