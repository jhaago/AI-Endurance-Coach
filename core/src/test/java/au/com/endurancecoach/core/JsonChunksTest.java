package au.com.endurancecoach.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class JsonChunksTest {
 @Test public void hugeUltraRecordSplitsBelowCursorWindowLimitAndReassembles() {
  String source="x".repeat(65535)+"\uD83C\uDFC3"+"y".repeat(4*1024*1024);
  java.util.List<String> parts=JsonChunks.split(source);assertTrue(parts.size()>60);assertEquals(source,String.join("",parts));
  for(String part:parts){assertTrue(part.length()<=65536);assertFalse(Character.isHighSurrogate(part.charAt(part.length()-1)));}
 }
}
