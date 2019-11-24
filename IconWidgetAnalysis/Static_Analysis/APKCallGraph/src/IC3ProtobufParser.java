

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.TextFormat;

import edu.psu.cse.siis.ic3.Ic3Data;
import edu.psu.cse.siis.ic3.Ic3Data.Application.Component;
import edu.psu.cse.siis.ic3.Ic3Data.Application.Component.ComponentKind;
import edu.psu.cse.siis.ic3.Ic3Data.Application.Component.ExitPoint;
import edu.psu.cse.siis.ic3.Ic3Data.Application.Component.ExitPoint.Intent;
import edu.psu.cse.siis.ic3.Ic3Data.Application.Component.ExitPoint.Uri;
import edu.psu.cse.siis.ic3.Ic3Data.Attribute;
import edu.psu.cse.siis.ic3.Ic3Data.AttributeKind;

public class IC3ProtobufParser {
	Ic3Data.Application.Builder ic3Builder;
	HashMap<String, ArrayList<String>> iccs = new HashMap<>();

	public HashMap<String, ArrayList<String>> parseFromFile(String filename,
			HashMap<String, HashSet<String>> m2providers, HashMap<String, HashSet<String>> m2intents)
			throws FileNotFoundException, IOException {
		ic3Builder = Ic3Data.Application.newBuilder();
		TextFormat.merge(new FileReader(new File(filename)), ic3Builder);

		System.out.println(ic3Builder.toString());

		Map<FieldDescriptor, Object> fields = ic3Builder.getAllFields();

		List<Component> componentsList = ic3Builder.getComponentsList();
		for (Component component : componentsList) {
			// System.out.println("========= " + component.getName() + "
			// =========");
			List<ExitPoint> exitpoints = component.getExitPointsList();
			for (ExitPoint exitPoint : exitpoints) {
				String fromMethod = exitPoint.getInstruction().getMethod();
				String toClass = "";
				List<Intent> intentsList = exitPoint.getIntentsList();
				HashSet<String> actions = new HashSet<String>();
				for (Intent intent : intentsList) {
					List<Attribute> attributesList = intent.getAttributesList();
					for (Attribute attribute : attributesList) {
						if (attribute.getKind() == AttributeKind.CLASS) {
							toClass = attribute.getValue(0).replace("/", ".");
						}
						if (attribute.getKind() == AttributeKind.ACTION) {
							actions.add(attribute.getValue(0));
						}

					}

				}
				
				if (actions.size() > 0) {
					if (!m2intents.containsKey(fromMethod)) {
						m2intents.put(fromMethod, new HashSet<>());
					}
					m2intents.get(fromMethod).addAll(actions);
				}

				HashSet<String> uristrings = new HashSet<String>();
				if (exitPoint.getKind() == ComponentKind.PROVIDER) {
					List<Uri> urisList = exitPoint.getUrisList();
					for (Uri uri : urisList) {
						for (Attribute attribute : uri.getAttributesList()) {
							if (attribute.getKind() == AttributeKind.URI) {
								uristrings.add(attribute.getValue(0));
							}
						}
					}
				}
				
				if (uristrings.size() > 0) {
					if (!m2providers.containsKey(fromMethod)) {
						m2providers.put(fromMethod, new HashSet<>());
					}
					m2providers.get(fromMethod).addAll(uristrings);
				}

				System.out.println("CLASS: " + fromMethod + " -> " + toClass + " [" + exitPoint.getKind() + "]");
				System.out.println("ACTIONS: " + fromMethod + " -> " + actions + " [" + exitPoint.getKind() + "]");
				System.out.println("PROVIDERS: " + fromMethod + " -> " + uristrings + " [" + exitPoint.getKind() + "]");
				if (!iccs.containsKey(fromMethod)) {
					iccs.put(fromMethod, new ArrayList<String>());
				}
				iccs.get(fromMethod).add(toClass);
			}
			// System.out.println("==========================================");
			// System.out.println();
		}

		System.out.println("read successfully");
		return iccs;
	}

}
