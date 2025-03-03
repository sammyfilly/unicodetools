package org.unicode.jsp;

import com.ibm.icu.impl.Relation;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UProperty.NameChoice;
import com.ibm.icu.text.UnicodeSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class Typology {
    // static UnicodeMap<String> reasons = new UnicodeMap<String>();
    public static Map<String, UnicodeSet> label_to_uset = new TreeMap<String, UnicodeSet>();

    static {
        label_to_uset.put("S", new UnicodeSet("[:S:]").freeze());
        label_to_uset.put("L", new UnicodeSet("[:L:]").freeze());
        label_to_uset.put("M", new UnicodeSet("[:M:]").freeze());
        label_to_uset.put("N", new UnicodeSet("[:N:]").freeze());
        label_to_uset.put("C", new UnicodeSet("[:C:]").freeze());
        label_to_uset.put("Z", new UnicodeSet("[:Z:]").freeze());
        label_to_uset.put("P", new UnicodeSet("[:P:]").freeze());
    }

    public static Map<String, UnicodeSet> full_path_to_uset = new TreeMap<String, UnicodeSet>();
    public static Map<String, UnicodeSet> path_to_uset = new TreeMap<String, UnicodeSet>();
    // static Map<List<String>,UnicodeSet> path_to_uset = new TreeMap<List<String>,UnicodeSet>();
    public static Relation<String, String> labelToPaths =
            new Relation(new TreeMap(), TreeSet.class);
    public static Map<String, Map<String, UnicodeSet>> label_parent_uset = new TreeMap();
    // public static Relation<String, String> pathToList = new Relation(new TreeMap(),
    // TreeSet.class);

    static class MyReader extends FileUtilities.SemiFileReader {
        // 0000  Cc  [Control] [X] [X] [X] <control>
        public static final Pattern SPLIT = Pattern.compile("\\s*\t\\s*");
        public static final Pattern NON_ALPHANUM = Pattern.compile("[^0-9A-Za-z]+");

        protected String[] splitLine(String line) {
            return SPLIT.split(line);
        }

        StringBuilder temp_path = new StringBuilder();

        @Override
        protected boolean handleLine(int startRaw, int endRaw, String[] items) {
            temp_path.setLength(0);
            temp_path.append('/');
            for (int i = 2; i < 6; ++i) {
                String item = items[i];
                if (item.equals("[X]")) continue;

                if (!item.startsWith("[") || !item.endsWith("]")) {
                    throw new IllegalArgumentException(i + "\t" + item);
                }
                item = item.substring(1, item.length() - 1);
                if (item.length() == 0) continue;
                item = NON_ALPHANUM.matcher(item).replaceAll("_");
                temp_path.append('/').append(item);
            }
            String fullPath = temp_path.toString();

            // store
            {
                fullPath = fullPath.intern();
                UnicodeSet uset = full_path_to_uset.get(fullPath);
                if (uset == null) {
                    full_path_to_uset.put(fullPath, uset = new UnicodeSet());
                }
                uset.addAll(startRaw, endRaw);
            }

            String[] labels = fullPath.split("/");
            String path = "";
            for (String item : labels) {
                UnicodeSet uset = label_to_uset.get(item);
                if (uset == null) {
                    label_to_uset.put(item, uset = new UnicodeSet());
                }
                uset.add(startRaw, endRaw);

                // labelToPath.put(item, path);

                path = (path + "/" + item).intern();

                uset = path_to_uset.get(path);
                if (uset == null) {
                    path_to_uset.put(path, uset = new UnicodeSet());
                }
                uset.addAll(startRaw, endRaw);
            }
            return true;
        }

        Map<List<String>, List<String>> listCache = new HashMap<List<String>, List<String>>();
        Map<Set<String>, Set<String>> setCache = new HashMap<Set<String>, Set<String>>();

        private <T> T intern(Map<T, T> cache, T list) {
            T old = cache.get(list);
            if (old != null) return old;
            cache.put(list, list);
            return list;
        }
    }

    static {
        new MyReader().process(Typology.class, "Categories.txt"); // "09421-u52m09xxxx.txt"

        // fix the paths
        Map<String, UnicodeSet> temp = new TreeMap<String, UnicodeSet>();
        for (int i = 0; i < UCharacter.CHAR_CATEGORY_COUNT; ++i) {
            UnicodeSet same = new UnicodeSet().applyIntPropertyValue(UProperty.GENERAL_CATEGORY, i);
            String gcName =
                    UCharacter.getPropertyValueName(
                            UProperty.GENERAL_CATEGORY, i, NameChoice.SHORT);
            // System.out.println("\n" + gcName);
            String prefix = gcName.substring(0, 1);

            for (String path : path_to_uset.keySet()) {
                UnicodeSet uset = path_to_uset.get(path);
                if (!same.containsSome(uset)) {
                    continue;
                }
                String path2 = prefix + path;
                temp.put(path2, new UnicodeSet(uset).retainAll(same));
                String[] labels = path2.split("/");
                String parent = "";
                for (int j = 0; j < labels.length; ++j) {
                    labelToPaths.put(labels[j], path2);
                    if (j == 0) {
                        continue;
                    }
                    Map<String, UnicodeSet> map = label_parent_uset.get(labels[j]);
                    if (map == null) {
                        label_parent_uset.put(labels[j], map = new TreeMap<String, UnicodeSet>());
                    }
                    UnicodeSet uset2 = map.get(parent);
                    if (uset2 == null) {
                        map.put(parent, uset2 = new UnicodeSet());
                    }
                    uset2.addAll(uset);
                    parent += labels[j] + "/";
                }
            }
        }
        //        Set<String> labelUsetKeys = label_to_uset.keySet();
        //        Set<String> labelToPathKeys = labelToPath.keySet();
        //        if (!labelUsetKeys.equals(labelToPathKeys)) {
        //            TreeSet<String> uset_path = new TreeSet<String>(labelUsetKeys);
        //            uset_path.removeAll(labelToPathKeys);
        //            System.out.println("\nuset - path labels\t" + uset_path);
        //            TreeSet<String> path_uset = new TreeSet<String>(labelToPathKeys);
        //            path_uset.removeAll(labelUsetKeys);
        //            System.out.println("\npath -uset labels\t" + path_uset);
        //        }
        label_to_uset = freezeMapping(label_to_uset);
        path_to_uset = freezeMapping(temp);
        labelToPaths.freeze();
        // invert
    }

    private static Map<String, UnicodeSet> freezeMapping(Map<String, UnicodeSet> map) {
        for (String key : map.keySet()) {
            UnicodeSet uset = map.get(key);
            uset.freeze();
        }
        return Collections.unmodifiableMap(map);
    }

    public static UnicodeSet getSet(String label) {
        return label_to_uset.get(label);
    }

    public static Set<String> getLabels() {
        return label_to_uset.keySet();
    }
}
