package au.com.endurancecoach.core;
import org.json.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public final class GpxRoutes {
 private GpxRoutes(){}
 public static JSONObject parse(String xml)throws Exception{
  if(xml==null||xml.length()>5000000||xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IllegalArgumentException("GPX is too large or contains unsupported XML");
  JSONArray points=new JSONArray();StringBuilder name=new StringBuilder();
  try{
   SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(true);org.xml.sax.XMLReader reader=f.newSAXParser().getXMLReader();reader.setFeature("http://xml.org/sax/features/external-general-entities",false);reader.setFeature("http://xml.org/sax/features/external-parameter-entities",false);reader.setEntityResolver((a,b)->{throw new SAXException("External entities are not allowed");});
   reader.setContentHandler(new DefaultHandler(){JSONArray point;String text="",element="";boolean boundary=true;int routeGroups=0;
    @Override public void startElement(String uri,String local,String q,Attributes a)throws SAXException{
     element=local.isEmpty()?q:local;text="";
     try{if(element.equals("trk")||element.equals("rte")){if(++routeGroups>1)throw new IllegalArgumentException("Import one track or route at a time");}
      if(element.equals("trkseg"))boundary=true;
      if(element.equals("trkpt")||element.equals("rtept")){point=new JSONArray().put(Double.parseDouble(a.getValue("lat"))).put(Double.parseDouble(a.getValue("lon")));RoutePlan.point(point);if(boundary&&points.length()>0){JSONArray last=points.getJSONArray(points.length()-1);if(RoutePlan.distance(last.getDouble(0),last.getDouble(1),point.getDouble(0),point.getDouble(1))>20)throw new IllegalArgumentException("Disconnected GPX segments; import a continuous track");}boundary=false;}
     }catch(Exception e){throw new SAXException(e.getMessage());}
    }
    @Override public void characters(char[] c,int start,int count){text+=new String(c,start,count);if(text.length()>10000)text=text.substring(0,10000);}
    @Override public void endElement(String uri,String local,String q)throws SAXException{
     String e=local.isEmpty()?q:local;try{if(e.equals("ele")&&point!=null)point.put(Double.parseDouble(text.trim()));if(e.equals("name")&&name.length()==0)name.append(text.trim());if((e.equals("trkpt")||e.equals("rtept"))&&point!=null){RoutePlan.point(point);points.put(point);point=null;if(points.length()>20000)throw new IllegalArgumentException("GPX exceeds 20000 track points");}}catch(Exception error){throw new SAXException(error.getMessage());}text="";
    }
   });reader.parse(new InputSource(new StringReader(xml)));
  }catch(Exception e){throw new IllegalArgumentException("Cannot import GPX: "+e.getMessage());}
  String label=name.length()==0?"Imported GPX":name.substring(0,Math.min(120,name.length()));return RoutePlan.create(label,"imported",points,new JSONArray(),"GPX import · access and surface unverified","");
 }
}
