package au.com.endurancecoach.core;
import java.util.*;
/** A row remains below 256 KiB UTF-8, well below Android's CursorWindow per-row limit. */
public final class JsonChunks {
 private JsonChunks(){}
 public static List<String> split(String value){List<String> result=new ArrayList<>();for(int from=0;from<value.length();){int end=Math.min(from+65536,value.length());if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1)))end--;result.add(value.substring(from,end));from=end;}if(result.isEmpty())result.add("");return result;}
}
