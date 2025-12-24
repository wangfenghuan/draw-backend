package com.wfh.drawio.ai.tools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Title: DrawioXmlProcessor
 * @Author wangfenghuan
 * @Date 2025/12/20 20:53
 * @description: Utility class for processing Draw.io XML structure
 */
public class DrawioXmlProcessor {

    private static final Pattern MXCELL_PATTERN = Pattern.compile("<mxCell[^>]*>.*?</mxCell>", Pattern.DOTALL);
    private static final Pattern ID_PATTERN = Pattern.compile("id=\"([^\"]+)\"");


    public static String wrapWithModel(String bodyXml) {
        // 生成一个随机 ID 给 diagram 标签
        String uuid = UUID.randomUUID().toString();

        // 标准头部：包含 mxfile, diagram, mxGraphModel 和默认的 root 节点 (id=0, id=1)
        // 注意：AI 提示词中明确要求不生成 id="0" 和 id="1"，所以这里必须补上
        String header = """
            <mxfile host="Electron" modified="2024-01-01T00:00:00.000Z" agent="5.0" etag="xi" version="21.0.0" type="device">
              <diagram id="%s" name="Page-1">
                <mxGraphModel dx="1422" dy="794" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
                  <root>
                    <mxCell id="0"/>
                    <mxCell id="1" parent="0"/>
            """.formatted(uuid);

        // 标准尾部
        String footer = """
                  </root>
                </mxGraphModel>
              </diagram>
            </mxfile>
            """;

        return header + "\n" + bodyXml + "\n" + footer;
    }

    /**
     * Validate and parse XML content
     */
    public static ValidationResult validateAndParseXml(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 使用 UTF-8 避免中文乱码
            Document document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            return ValidationResult.valid(document);
        } catch (Exception e) {
            return ValidationResult.invalid("XML parse error: " + e.getMessage());
        }
    }

    /**
     * Extract mxCell elements from XML content using Regex (for quick analysis)
     */
    public static List<CellElement> extractMxCells(String xml) {
        List<CellElement> cells = new ArrayList<>();
        Matcher matcher = MXCELL_PATTERN.matcher(xml);

        while (matcher.find()) {
            String cellXml = matcher.group();
            Matcher idMatcher = ID_PATTERN.matcher(cellXml);

            if (idMatcher.find()) {
                String id = idMatcher.group(1);
                // Skip root cells
                if (!"0".equals(id) && !"1".equals(id)) {
                    cells.add(new CellElement(id, cellXml));
                }
            }
        }

        return cells;
    }

    /**
     * Extract only the mxCell elements (excluding root cells) from a complete drawio XML
     * and return them as a concatenated string. This is used for saving to database
     * in the same format as CreateDiagramTool.
     */
    public static String extractMxCellsOnly(String xml) {
        List<CellElement> cells = extractMxCells(xml);
        if (cells.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CellElement cell : cells) {
            sb.append(cell.xml).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Apply operations to diagram XML using DOM parser
     */
    public static OperationResult applyOperations(String xmlContent, List<DiagramSchemas.EditOperation> operations) {
        List<String> errors = new ArrayList<>();
        List<String> appliedOperations = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            // Find root element (Draw.io structure usually implies <root> inside <mxGraphModel>)
            // Note: This logic assumes xmlContent is a full valid XML or specifically <root>...
            // If the input is standard .drawio XML, we might need to search recursively or getElementsByTagName
            Node root = doc.getElementsByTagName("root").item(0);
            if (root == null) {
                // Fallback: If no <root> tag, maybe the top element itself is the container
                // But standard drawio has <root>. Let's try to be robust.
                return OperationResult.failure("Could not find <root> element in XML");
            }

            // Build cell map for quick lookup
            Map<String, Element> cellMap = new HashMap<>();
            NodeList cellNodes = ((Element) root).getElementsByTagName("mxCell");

            // Note: getElementsByTagName retrieves all descendants.
            // If we only want direct children of root, checking parentNode might be needed,
            // but standard drawio usually keeps mxCells flat under root.
            for (int i = 0; i < cellNodes.getLength(); i++) {
                Element cell = (Element) cellNodes.item(i);
                String id = cell.getAttribute("id");
                if (!id.isEmpty()) {
                    cellMap.put(id, cell);
                }
            }

            // Apply each operation
            for (DiagramSchemas.EditOperation op : operations) {
                try {
                    switch (op.getType()) {
                        case "update":
                            applyUpdate(doc, root, cellMap, op);
                            appliedOperations.add("Updated cell " + op.getCellId());
                            break;
                        case "add":
                            applyAdd(doc, root, cellMap, op);
                            appliedOperations.add("Added cell " + op.getCellId());
                            break;
                        case "delete":
                            applyDelete(root, cellMap, op);
                            appliedOperations.add("Deleted cell " + op.getCellId());
                            break;
                        default:
                            errors.add("Unknown operation type: " + op.getType());
                    }
                } catch (Exception e) {
                    errors.add(op.getType() + " operation on cell " + op.getCellId() + " failed: " + e.getMessage());
                }
            }

            String resultXml = convertDocumentToString(doc);
            return OperationResult.success(resultXml, appliedOperations, errors);

        } catch (Exception e) {
            return OperationResult.failure("Failed to process operations: " + e.getMessage());
        }
    }

    private static void applyUpdate(Document doc, Node root, Map<String, Element> cellMap, DiagramSchemas.EditOperation op) throws Exception {
        Element existingCell = cellMap.get(op.getCellId());
        if (existingCell == null) {
            throw new Exception("Cell with id=\"" + op.getCellId() + "\" not found");
        }

        Element newCell = parseCellFromXml(doc, op.getNewXml(), op.getCellId());

        // We need to replace the node in the DOM
        existingCell.getParentNode().replaceChild(newCell, existingCell);

        // Update map
        cellMap.put(op.getCellId(), newCell);
    }

    private static void applyAdd(Document doc, Node root, Map<String, Element> cellMap, DiagramSchemas.EditOperation op) throws Exception {
        if (cellMap.containsKey(op.getCellId())) {
            throw new Exception("Cell with id=\"" + op.getCellId() + "\" already exists");
        }

        Element newCell = parseCellFromXml(doc, op.getNewXml(), op.getCellId());
        root.appendChild(newCell);
        cellMap.put(op.getCellId(), newCell);
    }

    private static void applyDelete(Node root, Map<String, Element> cellMap, DiagramSchemas.EditOperation op) throws Exception {
        Element existingCell = cellMap.get(op.getCellId());
        if (existingCell == null) {
            throw new Exception("Cell with id=\"" + op.getCellId() + "\" not found");
        }

        // Remove from DOM
        existingCell.getParentNode().removeChild(existingCell);
        // Remove from Map
        cellMap.remove(op.getCellId());
    }

    private static Element parseCellFromXml(Document doc, String cellXml, String expectedId) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Wrap in a temporary root to make it valid XML for parsing
        String wrappedXml = "<wrapper>" + cellXml + "</wrapper>";
        Document tempDoc = builder.parse(new ByteArrayInputStream(wrappedXml.getBytes(StandardCharsets.UTF_8)));

        NodeList nodes = tempDoc.getElementsByTagName("mxCell");
        if (nodes.getLength() == 0) {
            throw new Exception("new_xml must contain an mxCell element");
        }

        Element cell = (Element) nodes.item(0);

        String cellId = cell.getAttribute("id");
        if (!cellId.equals(expectedId)) {
            throw new Exception("ID mismatch: cell_id is \"" + expectedId + "\" but new_xml has id=\"" + cellId + "\"");
        }

        // Import the node into the main document
        return (Element) doc.importNode(cell, true);
    }

    private static String convertDocumentToString(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        // Omit XML declaration to keep it clean if inserting into other systems, or keep it true based on requirement
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    // ================= Helper Classes =================

    public static class ValidationResult {
        public final boolean valid;
        public final String error;
        public final Document document;

        private ValidationResult(boolean valid, String error, Document document) {
            this.valid = valid;
            this.error = error;
            this.document = document;
        }

        public static ValidationResult valid(Document document) {
            return new ValidationResult(true, null, document);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error, null);
        }
    }

    public static class CellElement {
        public final String id;
        public final String xml;

        public CellElement(String id, String xml) {
            this.id = id;
            this.xml = xml;
        }
    }

    public static class OperationResult {
        public final boolean success;
        public final String resultXml;
        public final List<String> appliedOperations;
        public final List<String> errors;

        private OperationResult(boolean success, String resultXml, List<String> appliedOperations, List<String> errors) {
            this.success = success;
            this.resultXml = resultXml;
            this.appliedOperations = appliedOperations;
            this.errors = errors;
        }

        public static OperationResult success(String resultXml, List<String> appliedOperations, List<String> errors) {
            return new OperationResult(true, resultXml, appliedOperations, errors);
        }

        public static OperationResult failure(String error) {
            return new OperationResult(false, null, new ArrayList<>(), Collections.singletonList(error));
        }
    }
}