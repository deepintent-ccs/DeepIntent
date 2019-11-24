package edu.cwru.android.ui;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class WidgetIDIconMappingMain {

	public static void main(String[] args) {

		if (args.length != 1) {
			System.out.println("Please give a directory for the apks to be analyzed");
			return;
		}

		class ValueComparator implements Comparator<String> {
			Map<String, Integer> base;

			public ValueComparator(Map<String, Integer> base) {
				this.base = base;
			}

			// Note: this comparator imposes orderings that are inconsistent
			// with
			// equals.
			public int compare(String a, String b) {
				if (base.get(a) >= base.get(b)) {
					return -1;
				} else {
					return 1;
				} // returning 0 would merge keys
			}
		}

		String folder = args[0];
		File dir = new File(folder);
		Map<String, Integer> stats = new HashMap<>();
		Map<String, Integer> results = new TreeMap<String, Integer>(new ValueComparator(stats));
		Queue<File> queue = new LinkedList<>();
		File[] files = dir.listFiles();
		Arrays.asList(files).forEach(f -> queue.offer(f));
		HashSet<String> failedfiles = new HashSet<>();
		int count = 0;
		String[] tags = new String[] {};

		while (!queue.isEmpty()) {
			File apkfile = queue.poll();
			if (apkfile.isDirectory()) {
				Arrays.asList(apkfile.listFiles()).forEach(f -> queue.offer(f));
				continue;
			}
			if (!apkfile.getName().endsWith("apk")) {
				continue;
			}

			try {
				PrintWriter out = new PrintWriter("output/" + apkfile.getName().substring(0, apkfile.getName().length()-4)+".json");

				String filename = apkfile.getAbsolutePath();
				System.out.println("Analyzing " + filename);

				try {
					APKResourceResolver resolver = new APKResourceResolver(filename);
					ImageResourceTagDetailHandler handler = new ImageResourceTagDetailHandler(out);
					for (String tag : tags) {
						handler.addTagsToInspect(tag);
					}
					resolver.setTagHandler(handler);
					resolver.extractARSCAndLayoutFiles();

					System.out.println("Done analyzing " + filename);
					count++;
				} catch (Exception e) {
					failedfiles.add(filename);
				}

				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("Successfully process " + count + " files");

	}

}
