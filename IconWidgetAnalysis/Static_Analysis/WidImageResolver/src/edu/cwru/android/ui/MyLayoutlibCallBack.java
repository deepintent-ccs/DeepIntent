package edu.cwru.android.ui;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.ide.common.rendering.api.AdapterBinding;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceType;
import com.android.util.Pair;

public class MyLayoutlibCallBack extends LayoutlibCallback {

	private final HashMap<String, Class<?>> mLoadedClasses = new HashMap<String, Class<?>>();
	private Map<ResourceType, Map<String, Integer>> mIdMap = new HashMap<ResourceType, Map<String, Integer>>();
	private Map<Integer, Pair<ResourceType, String>> mReverseIdMap = new HashMap<Integer, Pair<ResourceType, String>>();
	private APKResourceResolver resolver;
	private int dynamicIdSeed = 0x7fff0000;
	private Set<String> classes;
	private static org.apache.logging.log4j.Logger log = LogManager.getFormatterLogger(MyLayoutlibCallBack.class);
	private Map<ResourceType, Map<String, ResourceValue>> res;

	public MyLayoutlibCallBack(APKResourceResolver resolver, Set<String> classes,
			Map<ResourceType, Map<String, ResourceValue>> map) {
		// TODO Auto-generated constructor stub
		this.resolver = resolver;
		this.classes = classes;
		this.res = map;
	}

	@Override
	public ActionBarCallback getActionBarCallback() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AdapterBinding getAdapterBinding(ResourceReference arg0, Object arg1, Object arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getAdapterItemValue(ResourceReference arg0, Object arg1, ResourceReference arg2, int arg3, int arg4,
			int arg5, int arg6, ResourceReference arg7, ViewAttribute arg8, Object arg9) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getNamespace() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ILayoutPullParser getParser(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ILayoutPullParser getParser(ResourceValue arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getResourceId(ResourceType type, String name) {
		Map<String, Integer> typeMap = mIdMap.get(type);
		if (typeMap == null) {
			typeMap = new HashMap<String, Integer>();
			mIdMap.put(type, typeMap);
		}

		Integer value = typeMap.get(name);
		if (value == null) {
			if (type != null)
				value = resolver.resolveIdForName(type.getName(), name);// else
																		// if
																		// (name.equals("textAppearance"))
																		// return
																		// android.R.attr.textAppearance;
			if (value == null) {
				// value = typeMap.size() + 1;
				value = Integer.valueOf(++dynamicIdSeed);
			}
			typeMap.put(name, value);
			mReverseIdMap.put(value, Pair.of(type, name));
		}

		return value;
	}

	/**
	 * Instantiate a class object, using a specific constructor and parameters.
	 *
	 * @param clazz
	 *            the class to instantiate
	 * @param constructorSignature
	 *            the signature of the constructor to use
	 * @param constructorParameters
	 *            the parameters to use in the constructor.
	 * @return A new class object, created using a specific constructor and
	 *         parameters.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Object instantiateClass(Class<?> clazz, Class[] constructorSignature, Object[] constructorParameters)
			throws Exception {
		Constructor<?> constructor = null;

		try {
			constructor = clazz.getConstructor(constructorSignature);
			// System.out.println (constructor.getName() + "+ " +
			// constructor.getClass());
		} catch (NoSuchMethodException e) {
			// Custom views can either implement a 3-parameter, 2-parameter or a
			// 1-parameter. Let's synthetically build and try all the
			// alternatives.
			// That's kind of like switching to the other box.
			//
			// The 3-parameter constructor takes the following arguments:
			// ...(Context context, AttributeSet attrs, int defStyle)

			int n = constructorSignature.length;
			if (n == 0) {
				// There is no parameter-less constructor. Nobody should ask for
				// one.
				throw e;
			}

			for (int i = 3; i >= 1; i--) {
				if (i == n) {
					// Let's skip the one we know already fails
					continue;
				}
				Class[] sig = new Class[i];
				Object[] params = new Object[i];

				int k = i;
				if (n < k) {
					k = n;
				}
				System.arraycopy(constructorSignature, 0, sig, 0, k);
				System.arraycopy(constructorParameters, 0, params, 0, k);

				for (k++; k <= i; k++) {
					if (k == 2) {
						// Parameter 2 is the AttributeSet
						sig[k - 1] = clazz.getClassLoader().loadClass("android.util.AttributeSet");
						params[k - 1] = null;

					} else if (k == 3) {
						// Parameter 3 is the int defstyle
						sig[k - 1] = int.class;
						params[k - 1] = 0;
					}
				}

				constructorSignature = sig;
				constructorParameters = params;

				try {
					// Try again...
					constructor = clazz.getConstructor(constructorSignature);
					if (constructor != null) {
						// Found a suitable constructor, now let's use it.
						break;
					}
				} catch (NoSuchMethodException e1) {
					// pass
				}
			}

			// If all the alternatives failed, throw the initial exception.
			if (constructor == null) {
				throw e;
			}
		}

		constructor.setAccessible(true);
		return constructor.newInstance(constructorParameters);
	}

	@Override
	public Object loadView(String name, Class[] constructorSignature, Object[] constructorArgs) throws Exception {
		Class<?> clazz = mLoadedClasses.get(name);
		if (clazz != null) {
			return instantiateClass(clazz, constructorSignature, constructorArgs);
		}

		// in Paypal, custom view class
		// com.android.internal.widget.ActionBarView$HomeView is included in
		// layoutlib.jar.
		// It can be instantiated directly. Its super class canot be found in
		// the class hierarchy.
		if (name.startsWith("com.android.internal.")) {
			try {
				clazz = Class.forName(name);
				if (clazz != null) {
					Object view = instantiateClass(clazz, constructorSignature, constructorArgs);
					mLoadedClasses.put(name, clazz);
					// postInstantiation(view,
					// (AttributeSet)constructorArgs[1]);
					return view;
				}
			} catch (ClassNotFoundException cnfe) {
				// skip
			}
		}

		// load the class.

		
//
		try {
			
			clazz = Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			ClassLoader currentThreadClassLoader
			 = Thread.currentThread().getContextClassLoader();

			// Add the conf dir to the classpath
			// Chain the current thread classloader
			URLClassLoader urlClassLoader
			 = new URLClassLoader(new URL[]{new File("lib/EarSpy.jar").toURL()},
			                      currentThreadClassLoader);

			// Replace the thread classloader - assumes
			// you have permissions to do so
			Thread.currentThread().setContextClassLoader(urlClassLoader);
			clazz = urlClassLoader.loadClass(name);
		}
		if (clazz != null) {

			Object view = null;
			while (view == null) {
				try {
					if (clazz.getName().equals("android.view.ViewGroup")) {
						clazz = android.widget.LinearLayout.class;
					}
					view = instantiateClass(clazz, constructorSignature, constructorArgs);
					mLoadedClasses.put(name, clazz);
					return view;
				} catch (Exception e) {
					clazz = clazz.getSuperclass();
					if (clazz == null) {
						break;
					}
				}
			}
		}
		
		
		
		
//		ClassLoader loader = URLClassLoader.newInstance(new URL[] { new File("classes-dex2jar.jar").toURL() },
//				getClass().getClassLoader());
		try {
			clazz = Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		// if above step fails, just create a MockView as in ADT plugin
		try {
			// clazzmap.put(clazz.getName(),);
			clazz = com.android.layoutlib.bridge.MockView.class;
			Object view = instantiateClass(clazz, constructorSignature, constructorArgs);
			Method m = view.getClass().getMethod("setText", new Class<?>[] { CharSequence.class });
			String label = name;
			if (name.equals(SdkConstants.VIEW_FRAGMENT)) {
				label = "<fragment> is just simply mocked.";
			} else {
				label = String.format("Tag other than <fragment> is met. See details to handle it: <%s>", name);
			}
			m.invoke(view, label);
			return view;
		} catch (Exception e) {
			// convert 2nd and 3rd arg to Object to avoid invoke warning(String,
			// String, Object);
			log.warn("loadView fails at the second step: %s - '%s'", (Object) e.getMessage(), (Object) name);
			throw new Exception("Failed to loadView: " + name, e);// return null
																	// will also
																	// emit an
																	// exception
																	// later.
		}

	}

	private String getPrimordialSuperclassOf(String name) {
		Class<?> clazz = null;
		try {
			clazz = Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		if (clazz == null) {
			return "";
		}
		Class<?> parent = clazz;
		String classname = parent.getName();
		while (!classes.contains(classname)) {
			parent = parent.getSuperclass();
			if (parent == null) {
				return "";
			}
			classname = parent.getName();
		}

		return parent.getName();
	}

	@Override
	public Pair<ResourceType, String> resolveResourceId(int id) {
		// TODO Auto-generated method stub
		return mReverseIdMap.get(id);
	}

	@Override
	public String resolveResourceId(int[] arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean supports(int arg0) {
		// TODO Auto-generated method stub
		return false;
	}

}
