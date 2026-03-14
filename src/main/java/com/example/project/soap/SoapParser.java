package com.example.project.soap;

import com.example.project.model.AttendanceSoapData;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPMessage;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

public class SoapParser {

    public static List<AttendanceSoapData> parse(SOAPMessage response) throws Exception {

        List<AttendanceSoapData> results = new ArrayList<>();
        SOAPBody body = response.getSOAPBody();

        // SAFE: Extract data (never returns null)
        String raw = extractStrDataList(body);
        if (raw == null) raw = "";
        raw = raw.trim();

        // CASE 1: Inside XML (CDATA or structured)
        String rawLower = raw.toLowerCase();
        if (rawLower.contains("<row") || rawLower.contains("<log") || rawLower.contains("<attlog")) {
            return parseXMLString(raw);
        }

        // CASE 2: Text-based list
        if (rawLower.contains("strdatalist") || raw.contains("\n")) {
            return parseTextFormat(raw);
        }

        // CASE 3: Normal SOAP XML elements
        Iterator<?> it = body.getChildElements();
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj instanceof SOAPElement el) {
                traverse(el, results);
            }
        }

        return results;
    }

    /**
     * NEVER returns null.
     */
    private static String extractStrDataList(SOAPBody body) {

        try {
            Iterator<?> it = body.getChildElements();

            while (it.hasNext()) {
                Object obj = it.next();

                if (obj instanceof SOAPElement element) {

                    Iterator<?> childIt = element.getChildElements();

                    while (childIt.hasNext()) {
                        Object child = childIt.next();

                        if (child instanceof SOAPElement se &&
                                "strDataList".equals(se.getLocalName())) {

                            String value = se.getValue();

                            // 🌟 Important: Avoid returning NULL
                            return value == null ? "" : value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 🌟 Fallback: return empty string, never null
        return "";
    }

    private static List<AttendanceSoapData> parseTextFormat(String raw) {

        raw = raw.replace("strDataList>", "strDataList>\n");

        List<AttendanceSoapData> list = new ArrayList<>();
        String[] lines = raw.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.contains("Logs Count") ||
                    line.contains("GetTransactionsLogResult") ||
                    line.contains("strDataList")) continue;

            String[] parts = line.split("\\s+");
            if (parts.length < 4) continue;

            try {
                String userId = parts[0];
                String date = parts[1];
                String time = parts[2];
                String direction = parts[3];

                AttendanceSoapData d = new AttendanceSoapData();
                d.setUserId(userId);
                d.setTimeStamp(date + " " + time);
                d.setDirection(direction);

                list.add(d);
            } catch (Exception ignored) {}
        }

        return list;
    }

    private static List<AttendanceSoapData> parseXMLString(String xml) throws Exception {

        List<AttendanceSoapData> list = new ArrayList<>();

        org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));

        NodeList nodes = doc.getElementsByTagName("*");

        for (int i = 0; i < nodes.getLength(); i++) {

            Node node = nodes.item(i);
            String nodeName = node.getNodeName();
            if (nodeName.contains(":")) nodeName = nodeName.substring(nodeName.indexOf(':') + 1);

            String name = nodeName.toLowerCase();
            if (!(name.equals("row") || name.equals("log") || name.equals("attlog"))) continue;

            AttendanceSoapData d = new AttendanceSoapData();
            NodeList children = node.getChildNodes();

            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);

                if (child.getNodeType() != Node.ELEMENT_NODE) continue;

                String tag = child.getNodeName();
                if (tag.contains(":")) tag = tag.substring(tag.indexOf(':') + 1);
                tag = tag.toLowerCase();

                String val = child.getTextContent().trim();

                if (tag.contains("userid") || tag.contains("pin")) d.setUserId(val);
                if (tag.contains("name")) d.setUserName(val);
                if (tag.contains("time") || tag.contains("verify") || tag.contains("timestamp"))
                    d.setTimeStamp(val);
                if (tag.contains("status") || tag.contains("direction")) d.setDirection(val);
            }

            list.add(d);
        }

        return list;
    }

    private static void traverse(SOAPElement el, List<AttendanceSoapData> list) {

        String name = el.getLocalName();

        if (name == null) {
            name = el.getNodeName();
            if (name != null && name.contains(":"))
                name = name.substring(name.indexOf(':') + 1);
        }
        if (name == null) return;

        if (name.equalsIgnoreCase("row") ||
                name.equalsIgnoreCase("log") ||
                name.equalsIgnoreCase("attlog")) {

            AttendanceSoapData d = new AttendanceSoapData();
            NodeList children = el.getChildNodes();

            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;

                String tag = n.getNodeName();
                if (tag.contains(":")) tag = tag.substring(tag.indexOf(':') + 1);
                tag = tag.toLowerCase();

                String val = n.getTextContent().trim();

                if (tag.contains("userid") || tag.contains("pin")) d.setUserId(val);
                if (tag.contains("name")) d.setUserName(val);
                if (tag.contains("time") || tag.contains("verify") || tag.contains("timestamp"))
                    d.setTimeStamp(val);
                if (tag.contains("status") || tag.contains("direction")) d.setDirection(val);
            }

            list.add(d);
        }

        // Recursively search deeper
        Iterator<?> it = el.getChildElements();
        while (it.hasNext()) {
            Object child = it.next();
            if (child instanceof SOAPElement ce) {
                traverse(ce, list);
            }
        }
    }
}
