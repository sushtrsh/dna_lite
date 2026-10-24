package com.dna.tools;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Model + parser untuk format menu XML (kompatibel dengan format kr-script yang dipakai menu lama). */
final class Menu {
    static final int GROUP = 0, ACTION = 1, PAGE = 2, TEXT = 3;

    static final class Opt {
        String value = "", label = "";
        boolean selected;
    }

    static final class Param {
        String name = "", title = "", label = "", desc = "", value = "", valueSh = "";
        String type = "", optionsSh = "", separator = "\n", placeholder = "";
        boolean multiple, required;
        int min = 0, max = 100;
        List<Opt> options = new ArrayList<>();
    }

    static final class Node {
        final int type;
        String title = "", desc = "", set = "", lockSh = "", config = "", configSh = "", link = "", handler = "";
        boolean fileOption;
        List<Param> params = new ArrayList<>();
        List<Node> children = new ArrayList<>();

        Node(int type) { this.type = type; }
    }

    private static String attr(XmlPullParser p, String n) {
        String v = p.getAttributeValue(null, n);
        return v == null ? "" : v;
    }

    private static int toInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    static List<Node> parse(String xml) throws Exception {
        xml = xml.replaceAll("<\\?xml[^>]*\\?>", "");
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader("<root>" + xml + "</root>"));

        Node top = new Node(GROUP);
        Deque<Node> st = new ArrayDeque<>();
        st.push(top);
        Param param = null;
        Opt opt = null;
        String leaf = null;
        boolean brk = false;
        StringBuilder buf = new StringBuilder();

        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                switch (n) {
                    case "group": {
                        Node g = new Node(GROUP);
                        g.title = attr(p, "title");
                        st.peek().children.add(g);
                        st.push(g);
                        break;
                    }
                    case "action": {
                        Node a = new Node(ACTION);
                        a.title = attr(p, "title");
                        st.peek().children.add(a);
                        st.push(a);
                        break;
                    }
                    case "page": {
                        Node g = new Node(PAGE);
                        g.title = attr(p, "title");
                        g.desc = attr(p, "desc");
                        g.config = attr(p, "config").replace("file:///android_asset/", "");
                        g.configSh = attr(p, "config-sh");
                        g.link = attr(p, "link");
                        st.peek().children.add(g);
                        st.push(g);
                        break;
                    }
                    case "text": {
                        Node t = new Node(TEXT);
                        st.peek().children.add(t);
                        st.push(t);
                        break;
                    }
                    case "param": {
                        param = new Param();
                        param.name = attr(p, "name");
                        param.title = attr(p, "title");
                        param.label = attr(p, "label");
                        param.desc = attr(p, "desc");
                        param.value = attr(p, "value");
                        param.valueSh = attr(p, "value-sh");
                        param.type = attr(p, "type");
                        param.optionsSh = attr(p, "options-sh");
                        param.placeholder = attr(p, "placeholder");
                        param.multiple = "true".equals(attr(p, "multiple"));
                        param.required = "true".equals(attr(p, "required"));
                        param.min = toInt(attr(p, "min"), 0);
                        param.max = toInt(attr(p, "max"), 100);
                        if (p.getAttributeValue(null, "separator") != null) param.separator = attr(p, "separator");
                        if (st.peek().type == ACTION) st.peek().params.add(param);
                        break;
                    }
                    case "option": {
                        if (param != null) {
                            opt = new Opt();
                            opt.value = attr(p, "value");
                            opt.selected = "true".equals(attr(p, "selected"));
                            param.options.add(opt);
                            leaf = "option";
                            buf.setLength(0);
                        } else if ("file".equals(attr(p, "type"))) {
                            st.peek().fileOption = true;
                        }
                        break;
                    }
                    case "slice":
                        brk = "true".equals(attr(p, "break"));
                        leaf = n;
                        buf.setLength(0);
                        break;
                    case "title": case "desc": case "summary": case "set": case "handler": case "lock":
                        leaf = n;
                        buf.setLength(0);
                        break;
                    default:
                        break;
                }
            } else if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
                if (leaf != null) buf.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                Node cur = st.peek();
                String text = buf.toString().trim();
                switch (n) {
                    case "title": if (leaf != null) cur.title = text; break;
                    case "desc": case "summary": if (leaf != null && cur.desc.isEmpty()) cur.desc = text; break;
                    case "set": if (leaf != null) cur.set = text; break;
                    case "handler": if (leaf != null) cur.handler = text; break;
                    case "lock": if (leaf != null) cur.lockSh = text; break;
                    case "option":
                        if (opt != null) {
                            opt.label = text;
                            if (opt.value.isEmpty()) opt.value = text;
                            opt = null;
                        }
                        break;
                    case "slice":
                        if (cur.type == TEXT) cur.title += text + (brk ? "\n" : " ");
                        break;
                    case "param": param = null; break;
                    case "group": case "action": case "page": case "text":
                        if (st.size() > 1) st.pop();
                        break;
                    default: break;
                }
                if (isLeaf(n)) leaf = null;
            }
        }
        return top.children;
    }

    private static boolean isLeaf(String n) {
        return n.equals("title") || n.equals("desc") || n.equals("summary") || n.equals("set")
                || n.equals("handler") || n.equals("lock") || n.equals("slice") || n.equals("option");
    }

    /** Ratakan group bertingkat jadi daftar baris: header group + item. */
    static void flatten(List<Node> in, List<Node> out) {
        for (Node n : in) {
            if (n.type == GROUP) {
                if (!n.title.isEmpty()) out.add(n);
                flatten(n.children, out);
            } else {
                out.add(n);
            }
        }
    }
}
