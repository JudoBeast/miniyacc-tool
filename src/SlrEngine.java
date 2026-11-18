import java.util.*;

/** Grammar analysis and SLR(1) construction: FIRST/FOLLOW, LR(0) item sets, ACTION/GOTO tables, shift-reduce parsing. */
public class SlrEngine {
    public static final String EPS = "ε", END = "$";

    public static class Prod {
        public final String lhs; public final List<String> rhs;
        Prod(String lhs, List<String> rhs) { this.lhs = lhs; this.rhs = rhs; }
        public String toString() { return lhs + " → " + (rhs.isEmpty() ? EPS : String.join(" ", rhs)); }
    }
    public static class Item implements Comparable<Item> {
        public final int p, d;
        Item(int p, int d) { this.p = p; this.d = d; }
        public int compareTo(Item o) { return p != o.p ? Integer.compare(p, o.p) : Integer.compare(d, o.d); }
        public boolean equals(Object o) { return o instanceof Item && ((Item) o).p == p && ((Item) o).d == d; }
        public int hashCode() { return p * 1000 + d; }
    }
    public static class Step { public int n; public String stack, input, action; }

    public final List<Prod> prods = new ArrayList<>();           // prods.get(0) is the augmented production
    public final LinkedHashSet<String> nonTerms = new LinkedHashSet<>(), terms = new LinkedHashSet<>();
    public final List<String> symOrder = new ArrayList<>();
    public String start, aug;
    public final Map<String, Set<String>> first = new LinkedHashMap<>(), follow = new LinkedHashMap<>();
    public final List<TreeSet<Item>> states = new ArrayList<>();
    public final List<Map<String, Integer>> trans = new ArrayList<>();
    public final List<Map<String, String>> action = new ArrayList<>();
    public final List<Map<String, Integer>> gotoT = new ArrayList<>();
    public final List<String> conflicts = new ArrayList<>();

    public SlrEngine(String text, String startSymbol) {
        List<Prod> user = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int k = line.indexOf("->"), len = 2;
            if (k < 0) { k = line.indexOf('→'); len = 1; }
            if (k < 0) throw new IllegalArgumentException("Line " + (i + 1) + ": missing '->'");
            String lhs = line.substring(0, k).trim();
            if (lhs.isEmpty() || lhs.contains(" ")) throw new IllegalArgumentException("Line " + (i + 1) + ": left side must be one symbol");
            for (String alt : line.substring(k + len).split("\\|")) {
                List<String> rhs = new ArrayList<>();
                for (String t : alt.trim().split("\\s+")) if (!t.isEmpty() && !t.equals("eps") && !t.equals(EPS)) rhs.add(t);
                user.add(new Prod(lhs, rhs)); nonTerms.add(lhs);
            }
        }
        if (user.isEmpty()) throw new IllegalArgumentException("Enter at least one production");
        start = (startSymbol == null || startSymbol.trim().isEmpty()) ? user.get(0).lhs : startSymbol.trim();
        if (!nonTerms.contains(start)) throw new IllegalArgumentException("Start symbol '" + start + "' is not a non-terminal");
        aug = start + "'";
        prods.add(new Prod(aug, new ArrayList<>(List.of(start)))); prods.addAll(user);
        for (Prod p : prods) {
            addSym(p.lhs);
            for (String s : p.rhs) { addSym(s); if (!nonTerms.contains(s) && !s.equals(aug)) terms.add(s); }
        }
        computeFirstFollow(); buildStates(); buildTables();
    }
    private void addSym(String s) { if (!symOrder.contains(s)) symOrder.add(s); }
    boolean isNT(String s) { return nonTerms.contains(s) || s.equals(aug); }

    private Set<String> firstSeq(List<String> seq, int from) {
        Set<String> r = new LinkedHashSet<>();
        for (int i = from; i < seq.size(); i++) {
            String s = seq.get(i);
            if (!isNT(s)) { r.add(s); return r; }
            for (String t : first.get(s)) if (!t.equals(EPS)) r.add(t);
            if (!first.get(s).contains(EPS)) return r;
        }
        r.add(EPS); return r;
    }
    private void computeFirstFollow() {
        for (String n : nonTerms) { first.put(n, new LinkedHashSet<>()); follow.put(n, new LinkedHashSet<>()); }
        first.put(aug, new LinkedHashSet<>());
        boolean ch = true;
        while (ch) { ch = false;
            for (Prod p : prods) if (first.get(p.lhs).addAll(firstSeq(p.rhs, 0))) ch = true; }
        follow.get(start).add(END);
        ch = true;
        while (ch) { ch = false;
            for (int q = 1; q < prods.size(); q++) { Prod p = prods.get(q);
                for (int i = 0; i < p.rhs.size(); i++) {
                    String b = p.rhs.get(i); if (!nonTerms.contains(b)) continue;
                    Set<String> f = firstSeq(p.rhs, i + 1);
                    for (String t : f) if (!t.equals(EPS) && follow.get(b).add(t)) ch = true;
                    if (f.contains(EPS) && follow.get(b).addAll(follow.get(p.lhs))) ch = true;
                } } }
    }

    private TreeSet<Item> closure(Collection<Item> kernel) {
        TreeSet<Item> set = new TreeSet<>(kernel); Deque<Item> work = new ArrayDeque<>(kernel);
        while (!work.isEmpty()) { Item it = work.poll(); Prod p = prods.get(it.p);
            if (it.d < p.rhs.size() && isNT(p.rhs.get(it.d)))
                for (int q = 0; q < prods.size(); q++)
                    if (prods.get(q).lhs.equals(p.rhs.get(it.d))) { Item n = new Item(q, 0); if (set.add(n)) work.add(n); } }
        return set;
    }
    private TreeSet<Item> go(Set<Item> s, String x) {
        List<Item> k = new ArrayList<>();
        for (Item it : s) { Prod p = prods.get(it.p); if (it.d < p.rhs.size() && p.rhs.get(it.d).equals(x)) k.add(new Item(it.p, it.d + 1)); }
        return k.isEmpty() ? null : closure(k);
    }
    private void buildStates() {
        Map<Set<Item>, Integer> index = new HashMap<>();
        TreeSet<Item> i0 = closure(List.of(new Item(0, 0))); states.add(i0); index.put(i0, 0);
        for (int i = 0; i < states.size(); i++) {
            Map<String, Integer> t = new LinkedHashMap<>();
            for (String x : symOrder) {
                if (x.equals(aug)) continue;
                TreeSet<Item> g = go(states.get(i), x); if (g == null) continue;
                Integer j = index.get(g);
                if (j == null) { j = states.size(); states.add(g); index.put(g, j); }
                t.put(x, j);
            }
            trans.add(t);
        }
    }
    private void put(int s, String t, String v) {
        String old = action.get(s).get(t);
        if (old == null) { action.get(s).put(t, v); return; }
        if (old.equals(v)) return;
        conflicts.add("State " + s + " on '" + t + "': " + old + " / " + v);
        if (v.startsWith("s") && !old.equals("acc")) action.get(s).put(t, v);   // shift wins over reduce
    }
    private void buildTables() {
        for (int i = 0; i < states.size(); i++) { action.add(new LinkedHashMap<>()); gotoT.add(new LinkedHashMap<>()); }
        for (int i = 0; i < states.size(); i++)
            for (Item it : states.get(i)) {
                Prod p = prods.get(it.p);
                if (it.d < p.rhs.size()) {
                    String x = p.rhs.get(it.d);
                    if (!isNT(x)) put(i, x, "s" + trans.get(i).get(x));
                } else if (it.p == 0) put(i, END, "acc");
                else for (String t : follow.get(p.lhs)) put(i, t, "r" + it.p);
            }
        for (int i = 0; i < states.size(); i++)
            for (Map.Entry<String, Integer> e : trans.get(i).entrySet()) if (isNT(e.getKey())) gotoT.get(i).put(e.getKey(), e.getValue());
    }
    public List<String> columns() { List<String> c = new ArrayList<>(terms); c.add(END); return c; }
    public List<String> gotoColumns() { return new ArrayList<>(nonTerms); }
    public String itemText(Item it) {
        Prod p = prods.get(it.p); StringBuilder sb = new StringBuilder(p.lhs + " →");
        for (int i = 0; i <= p.rhs.size(); i++) { if (i == it.d) sb.append(" ."); if (i < p.rhs.size()) sb.append(' ').append(p.rhs.get(i)); }
        if (p.rhs.isEmpty() && it.d == 0) sb.append(" ").append(EPS);
        return sb.toString().replace("→ .", "→ .");
    }

    /** Runs the shift-reduce parser and returns the trace; accepted is stored in the last step's action text. */
    public List<Step> parse(String input, boolean[] accepted) {
        List<String> toks = new ArrayList<>();
        for (String t : input.trim().split("\\s+")) if (!t.isEmpty() && !t.equals(END)) toks.add(t);
        toks.add(END);
        List<Step> out = new ArrayList<>(); Deque<String> stack = new ArrayDeque<>(); stack.push("0");
        int pos = 0; accepted[0] = false;
        for (int n = 1; n < 5000; n++) {
            int s = Integer.parseInt(stack.peek()); String a = toks.get(pos);
            Step st = new Step(); st.n = n; st.stack = stackText(stack);
            StringBuilder in = new StringBuilder(); for (int i = pos; i < toks.size(); i++) in.append(i > pos ? " " : "").append(toks.get(i)); st.input = in.toString();
            String act = action.get(s).get(a); out.add(st);
            if (act == null) { st.action = "error"; return out; }
            if (act.equals("acc")) { st.action = "accept"; accepted[0] = true; return out; }
            if (act.charAt(0) == 's') { st.action = "shift " + act.substring(1); stack.push(a); stack.push(act.substring(1)); pos++; }
            else {
                int q = Integer.parseInt(act.substring(1)); Prod p = prods.get(q);
                st.action = "reduce " + p;
                for (int i = 0; i < p.rhs.size() * 2; i++) stack.pop();
                int top = Integer.parseInt(stack.peek()); Integer g = gotoT.get(top).get(p.lhs);
                if (g == null) { st.action = "error"; return out; }
                stack.push(p.lhs); stack.push(String.valueOf(g));
            }
        }
        return out;
    }
    private String stackText(Deque<String> st) {
        List<String> l = new ArrayList<>(st); Collections.reverse(l); return String.join(" ", l);
    }
}
