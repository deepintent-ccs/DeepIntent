package edu.cwru.android.ui;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RResParser {

	class Res {
		public String type;
		public String subclass;
		public String name;
		public List<String> value = new ArrayList<String>();

		@Override
		public String toString() {
			return "Res [type=" + type + ", subclass=" + subclass + ", name=" + name + ", value=" + value + "]";
		}

	}

	public Map<String, Map<String, Integer>> parseNameToId(String rfile) throws Exception {
		Map<String, Map<String, Integer>> result = new HashMap<>();
		List<String> lines = Files.readAllLines(Paths.get(rfile));
		for (String line : lines) {
			String[] split = line.split("\\s+");
			Res r = new Res();
			r.type = split[0];
			r.subclass = split[1];
			r.name = split[2];
			if (!r.type.contains("[]")) {
				r.value.add(split[3]);
			} else {
				for (int i = 3; i < split.length; i++) {
					if (split[i].contains("{") || split[i].contains("}")) {
						continue;
					}
					r.value.add(split[i].replace(",", ""));
				}
			}

			if (!result.containsKey(r.subclass)) {
				result.put(r.subclass, new HashMap<>());
			}
			Map<String, Integer> subclassMap = result.get(r.subclass);
			if (r.value.size() > 0) {
				subclassMap.put(r.name, Integer.decode(r.value.get(0)));
			}
			// System.out.println("r: " + r);

		}

		return result;
	}

	@Test
	public void testRTest() throws Exception {
		List<String> lines = Files.readAllLines(Paths.get("appres/R.txt"));
		HashMap<String, List<Res>> map = new HashMap<>();
		for (String line : lines) {
			String[] split = line.split("\\s+");
			Res r = new Res();
			r.type = split[0];
			r.subclass = split[1];
			r.name = split[2];
			if (!r.type.contains("[]")) {
				r.value.add(split[3]);
			} else {
				for (int i = 3; i < split.length; i++) {
					if (split[i].contains("{") || split[i].contains("}")) {
						continue;
					}
					r.value.add(split[i].replace(",", ""));
				}
			}

			if (!map.containsKey(r.subclass)) {
				map.put(r.subclass, new ArrayList<>());
			}
			map.get(r.subclass).add(r);
			System.out.println("r: " + r);
		}

		PrintWriter out = new PrintWriter("appres/R.java");

		out.println("public final class R {");

		for (String subclass : map.keySet()) {
			out.format("public static final class %s {", subclass);
			out.println();

			for (Res r : map.get(subclass)) {
				if (!r.type.contains("[]")) {
					out.format("public static final %s %s=%s;", r.type, r.name, r.value.get(0));
					out.println();
				} else {
					String[] array = r.value.toArray(new String[r.value.size()]);
					out.format("public static final %s %s={ %s };", r.type, r.name, String.join(",", array));
					out.println();
				}
			}

			out.println("}");

		}

		out.println("}");

		out.close();
	}
}
