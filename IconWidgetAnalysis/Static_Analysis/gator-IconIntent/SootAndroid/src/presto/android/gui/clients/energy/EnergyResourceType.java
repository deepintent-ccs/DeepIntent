package presto.android.gui.clients.energy;

import java.util.*;

import presto.android.Hierarchy;
import presto.android.Logger;



import com.google.common.collect.*;

import presto.android.gui.wtg.util.WTGUtil;
import soot.Body;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;

public class EnergyResourceType {
  public enum resType{
    LOCATION, LOCATION_FINE, LOCATION_COARSE, LOCATION_GOOGLE, LOCATION_GOOGLE_GPS,
    LOCATION_GOOGLE_COMPASS, SENSOR_CAMERA, SENSOR_AUDIO, SENSOR_MEDIA, VIBRATION, SENSOR,
    LOCK_WIFI, LOCK_WAKE, LOCK, NULL
  }

//
  public enum locationType{
  LOCATION_GOOGLE_GPS, LOCATION_GOOGLE_COMPASS, NULL
}
  
  public EnergyResourceType(){

  }

  private static EnergyResourceType instance = null;

  public static EnergyResourceType v(){
    if (instance == null){
      instance = new EnergyResourceType();
    }
    return instance;
  }

  /**
   * Use the methodName to determine what resource it is.
   * @param tgtMethod method
   * @return Enum type of the resource
   */
  public resType getLocationType(SootMethod tgtMethod){
    String subSig = tgtMethod.getSubSignature();

    if(subSig.equals("boolean enableCompass()")){
      return resType.LOCATION_GOOGLE_COMPASS;
    } else if (subSig.equals("void disableCompass()")){
      return resType.LOCATION_GOOGLE_COMPASS;
    } else if (subSig.equals("boolean enableMyLocation()")){
      return resType.LOCATION_GOOGLE_GPS;
    } else if (subSig.equals("void disableMyLocation()")){
      return resType.LOCATION_GOOGLE_GPS;
    }
    return resType.NULL;
  }

  public resType classMethodTypeToResTyoe(SootClass clz, SootMethod mtd){
    resType ret0 = classTypeToResType(clz);
    if (ret0 == resType.LOCATION_GOOGLE){
      return getLocationType(mtd);
    }else{
      return ret0;
    }
  }


  /**
   * Use the className to determine the resource type.
   * @param clz Declaring class
   * @return Enum type of the resource
   */
  public resType classTypeToResType(SootClass clz){
    String className = clz.getName();
   //Logger.verb("EnergyResourceType", "TESTTING: " + className);
    if (className == "android.net.wifi.WifiManager.WifiLock"){
      return resType.LOCK_WIFI;
    }else if (className == "android.os.Vibrator"){
      return resType.VIBRATION;
    }else if (className == "android.media.AudioManager"){
      return resType.SENSOR_AUDIO;
    }else if (className == "android.media.MediaPlayer"){
      return resType.SENSOR_MEDIA;
    }else if (className == "android.hardware.SensorManager"){
      return resType.SENSOR;
    }else if (className == "android.location.LocationManager"){
      return resType.LOCATION;
    }else if (className == "android.hardware.Camera"){
      return resType.SENSOR_CAMERA;
    }else if (className == "com.google.android.gms.location.LocationClient"){
      return resType.LOCATION_GOOGLE;
    }else if (className == "com.google.android.maps.MyLocationOverlay"){
      return resType.LOCATION_GOOGLE;
    }else if (className == "android.media.MediaRecorder"){
      return resType.SENSOR_MEDIA;
    }else if (className == "android.os.PowerManager.WakeLock"){
      return resType.LOCK_WAKE;
    }
    
    return resType.NULL;
  }
}
