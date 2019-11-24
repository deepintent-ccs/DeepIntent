package presto.android.gui.clients.energy;

/**
 * Created by zero on 10/22/15.
 */
public class Pair<E, T> {
  private E m_E;
  private T m_T;

  public Pair(E e, T t){
    this.m_E = e;
    this.m_T = t;
  }

  public E getL(){
    return m_E;
  }

  public T getR(){
    return m_T;
  }

  @Override
  public int hashCode(){
    return m_E.hashCode() ^ m_T.hashCode();
  }

  @Override
  public boolean equals(Object o1){
    if (! (o1 instanceof Pair))
      return false;
    Pair a = (Pair) o1;
    if (this.m_E.equals(a.m_E) && this.m_T.equals(a.m_T))
      return true;
    else
      return false;
  }

  @Override
  public String toString(){
    return "L: " + m_E.toString() + "\tR: " + m_T.toString();
  }
}
