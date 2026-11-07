package au.com.endurancecoach.core;
import com.garmin.fit.*;
import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class FitRecordsTest {
 @Test public void decodesOriginalZipHeartRatePaceElevationAndCadence() throws Exception {
  Path temp=Files.createTempFile("fixture",".fit");
  FileEncoder encoder=new FileEncoder(temp.toFile(),Fit.ProtocolVersion.V2_0);
  RecordMesg record=new RecordMesg();record.setTimestamp(new DateTime(1000000000L));record.setHeartRate((short)145);record.setSpeed(2.5f);record.setAltitude(50f);record.setCadence((short)80);encoder.write(record);encoder.close();
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("activity.fit"));zip.write(Files.readAllBytes(temp));zip.closeEntry();}Files.delete(temp);
  JSONObject sample=FitRecords.decodeZip(bytes.toByteArray(),true).getJSONObject(0);
  assertEquals(145,sample.getInt("hr"));assertEquals(400,sample.getDouble("pace_s_km"),.1);assertEquals(50,sample.getDouble("elevation_m"),.1);assertEquals(160,sample.getDouble("cadence_spm"),.1);
 }
 @Test public void corruptOriginalNeverLooksLikeValidEmptyRun() {assertThrows(IOException.class,()->FitRecords.decodeZip(new byte[]{1,2,3},true));}
}
