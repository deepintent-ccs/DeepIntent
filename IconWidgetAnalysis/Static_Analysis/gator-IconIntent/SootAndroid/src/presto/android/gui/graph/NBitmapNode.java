package presto.android.gui.graph;

import soot.Scene;
import soot.SootClass;

public class NBitmapNode extends NObjectNode {

    @Override
    public SootClass getClassType() {
        return Scene.v().getSootClass("android.graphics.Bitmap");
    }

    @Override
    public String toString() {
        return "Bitmap[" +  "]" + id;
    }
}
