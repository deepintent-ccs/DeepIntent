package presto.android.gui.clients.energy;

/**
 * Created by zero on 11/22/15.
 */
public class StatUtil {
  private static StatUtil instance;
  public long maxTotalMem;
  public long maxUsedMem;
  private Runtime m_runtime;
  private StatUtil(){
    m_runtime = Runtime.getRuntime();
    maxTotalMem = 0;
    maxUsedMem = 0;
  }
  public static StatUtil v(){
    if (instance == null){
      instance = new StatUtil();
    }
    return instance;
  }

  public long getTotalMem(){
    long curTotalMem = m_runtime.totalMemory();
    if (curTotalMem > maxTotalMem)
      maxTotalMem = curTotalMem;
    return curTotalMem;
  }

  public long getUsedMem(){
    long usedMem = getTotalMem() - getFreeMem();
    if (usedMem > maxUsedMem)
      maxUsedMem = usedMem;
    return usedMem;
  }

  public long getMaxMem(){
    return m_runtime.maxMemory();
  }

  public long getFreeMem(){
    return m_runtime.freeMemory();
  }

}
