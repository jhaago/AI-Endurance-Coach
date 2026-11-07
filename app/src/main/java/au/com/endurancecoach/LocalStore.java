package au.com.endurancecoach;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import au.com.endurancecoach.core.JsonChunks;
import au.com.endurancecoach.core.HealthRecords;

final class LocalStore extends SQLiteOpenHelper {
 LocalStore(Context c){super(c.getApplicationContext(),"coach.db",null,1);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE documents (key TEXT NOT NULL, part INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(key,part))");db.execSQL("CREATE TABLE outbox (id TEXT PRIMARY KEY, kind TEXT NOT NULL, json TEXT NOT NULL)");}
 @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unsupported database migration");}
 synchronized JSONObject get(String key) throws Exception {try(Cursor c=getReadableDatabase().rawQuery("SELECT json FROM documents WHERE key=? ORDER BY part",new String[]{key})){StringBuilder value=new StringBuilder();while(c.moveToNext())value.append(c.getString(0));return value.length()>0?new JSONObject(value.toString()):new JSONObject();}}
 synchronized void put(String key,JSONObject json){SQLiteDatabase db=getWritableDatabase();boolean owns=!db.inTransaction();if(owns)db.beginTransaction();try{db.delete("documents","key=?",new String[]{key});int part=0;for(String chunk:JsonChunks.split(json.toString())){ContentValues values=new ContentValues();values.put("key",key);values.put("part",part++);values.put("json",chunk);db.insertOrThrow("documents",null,values);}if(owns)db.setTransactionSuccessful();}finally{if(owns)db.endTransaction();}}
 synchronized void queue(String id,String kind,JSONObject json){ContentValues v=new ContentValues();v.put("id",id);v.put("kind",kind);v.put("json",json.toString());if(getWritableDatabase().insertWithOnConflict("outbox",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new SQLiteException("Could not persist feedback");}
 synchronized JSONArray pending(String kind) throws Exception {JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT json FROM outbox WHERE kind=? ORDER BY rowid",new String[]{kind})){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
 synchronized void ack(String id){getWritableDatabase().delete("outbox","id=?",new String[]{id});}
 synchronized void acceptSnapshot(JSONObject snapshot,JSONArray feedback,JSONArray sentMatches) throws Exception {
  acceptSnapshot(snapshot,feedback,sentMatches,new JSONArray());
 }
 synchronized JSONArray healthRecords() throws Exception {return HealthRecords.current(CoachRepository.array(get("snapshot"),"health"),CoachRepository.array(get("health-local"),"records"),pending("health"));}
 synchronized void saveHealth(JSONArray changes) throws Exception {
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
   JSONArray current=HealthRecords.current(healthRecords(),changes);put("health-local",new JSONObject().put("records",current));
   for(int i=0;i<changes.length();i++){JSONObject change=changes.getJSONObject(i);queue("health:"+change.getString("change_id"),"health",change);}
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 synchronized void acceptSnapshot(JSONObject snapshot,JSONArray feedback,JSONArray sentMatches,JSONArray healthAck) throws Exception {
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
   JSONArray current=HealthRecords.current(healthRecords(),CoachRepository.array(snapshot,"health"));
   put("snapshot",snapshot);put("health-local",new JSONObject().put("records",current));
   for(int i=0;i<feedback.length();i++)ack(feedback.getString(i));
   for(int i=0;i<healthAck.length();i++)ack("health:"+healthAck.getString(i));
   for(int i=0;i<sentMatches.length();i++){JSONObject m=sentMatches.getJSONObject(i);db.delete("outbox","id=? AND json=?",new String[]{"match:"+m.getString("activity_id"),m.toString()});}
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
}
