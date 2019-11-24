package edu.cwru.android.ui;
/**
	 * This class is used to represent a node in rendered layout, which is then used in a SparseNumberedGraph. ItemInfo
	 * or View object may be modified in re-render process.
	 */

import android.view.View;

public class UIElement {
	public int left, top, right, bottom;// coordinator
	public int paddingLeft, paddingRight, paddingTop, paddingBottom; // paddings
																		// to
																		// compute
																		// the
																		// internal
																		// area
	public int id, inputType;
	public String text;// include both TEXT and HINT: (TEXT; HINT) if both are
						// not empty
	public int visibility, height, width;// attributes of the view object. may
											// be modified in re-render process
	public String className;// class of the object
	public View view;// View instance, better than className to determine the
						// type of the object if we concern more
						// than TextView
	
	public static int NODENBR = 0;
	public int nodeid = 0;
	
	public UIElement(){
		NODENBR++;
		this.nodeid = NODENBR;
	}

	@Override
	public String toString() {
		try {
			return String.format("[%08x] at (%d,%d)-(%d,%d) %s:%s", id, left, top, right, bottom, className, text);
		} catch (Exception e) {
			return String.format("[%d] at (%d,%d)-(%d,%d) %s:%s", id, left, top, right, bottom, className, text);
		}
	}

	/**
	 * Clone a simplified copy of current {@link UIElement}. Only
	 * coordinates/padding and id/text are cloned.
	 */
	@Override
	public UIElement clone() {
		UIElement ret = new UIElement();
		ret.className = className;
		ret.left = left;
		ret.top = top;
		ret.right = right;
		ret.bottom = bottom;
		ret.paddingLeft = paddingLeft;
		ret.paddingBottom = paddingBottom;
		ret.paddingRight = paddingRight;
		ret.paddingTop = paddingTop;
		ret.id = id;
		ret.inputType = inputType;
		ret.text = text;
		ret.nodeid = nodeid;
		return ret;
	}
}