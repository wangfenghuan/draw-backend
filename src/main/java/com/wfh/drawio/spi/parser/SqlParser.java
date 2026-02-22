package com.wfh.drawio.spi.parser;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.util.JdbcConstants;
import com.wfh.drawio.core.model.ArchNode;
import com.wfh.drawio.core.model.ArchRelationship;
import com.wfh.drawio.core.model.ProjectAnalysisResult;
import com.wfh.drawio.spi.LanguageParser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Title: SqlParser
 * @Author wangfenghuan
 * @description: 优化版 SQL 解析器 (已修复 Lambda 变量 Final 问题)
 */
@Slf4j
public class SqlParser implements LanguageParser {

    private static final Set<String> IGNORED_COLUMNS = Set.of(
            "create_time", "update_time", "create_at", "update_at",
            "created_time", "updated_time", "created_at", "updated_at",
            "is_delete", "is_deleted", "version", "revision"
    );

    @Override
    public String getName() {
        return "SQL-DDL-Enhanced";
    }

    @Override
    public boolean canParse(String projectDir) {
        File file = new File(projectDir);
        if (!file.exists()) {
            return false;
        }
        if (file.isFile()) {
            return file.getName().toLowerCase().endsWith(".sql");
        }
        try (Stream<Path> paths = Files.walk(Paths.get(projectDir), 3)) {
            return paths.anyMatch(p -> p.toString().toLowerCase().endsWith(".sql"));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public ProjectAnalysisResult parse(String projectDir) {
        log.info("Starting SQL analysis: {}", projectDir);
        ProjectAnalysisResult result = new ProjectAnalysisResult();
        List<ArchNode> nodes = new ArrayList<>();
        List<ArchRelationship> relationships = new ArrayList<>();
        Set<String> tableNames = new HashSet<>();

        try {
            List<Path> sqlFiles = findSqlFiles(projectDir);
            for (Path sqlFile : sqlFiles) {
                String content = Files.readString(sqlFile);
                parseSqlContent(content, nodes, relationships, tableNames);
            }
            inferLogicalRelationships(nodes, relationships, tableNames);
        } catch (Exception e) {
            log.error("Error parsing SQL files", e);
        }

        result.setNodes(nodes);
        result.setRelationships(relationships);
        return result;
    }

    private void parseSqlContent(String content, List<ArchNode> nodes, List<ArchRelationship> relationships, Set<String> tableNames) {
        List<SQLStatement> statements = parseWithFallback(content);
        for (SQLStatement stmt : statements) {
            if (stmt instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement createTable = (SQLCreateTableStatement) stmt;
                ArchNode node = processCreateTable(createTable, relationships);
                if (node != null) {
                    nodes.add(node);
                    tableNames.add(node.getId());
                }
            }
        }
    }

    private List<SQLStatement> parseWithFallback(String content) {
        try {
            return SQLUtils.parseStatements(content, JdbcConstants.MYSQL);
        } catch (Exception e1) {
            try {
                return SQLUtils.parseStatements(content, JdbcConstants.POSTGRESQL);
            } catch (Exception e2) {
                try {
                    return SQLUtils.parseStatements(content, JdbcConstants.ORACLE);
                } catch (Exception e3) {
                    return Collections.emptyList();
                }
            }
        }
    }

    private ArchNode processCreateTable(SQLCreateTableStatement createTable, List<ArchRelationship> relationships) {
        String tableName = cleanName(createTable.getTableName());
        ArchNode node = new ArchNode();
        node.setId(tableName);
        node.setName(tableName);
        node.setLayer("DATA");       // SQL 表属于 DATA 层
        node.setRole("ENTITY");      // 数据库表对应 ENTITY 角色
        node.setTableName(tableName); // 表名字段，供 LLM 生成 ER 图匹配

        // Javadoc 注释：表级别注释
        StringBuilder desc = new StringBuilder();
        if (createTable.getComment() != null) {
            desc.append(cleanComment(createTable.getComment().toString()));
        }

        // 字段列表：拼入 description，一行一个，节省 Token。格式：「name: type [注释]」
        List<String> fieldLines = new ArrayList<>();
        for (SQLTableElement element : createTable.getTableElementList()) {
            if (element instanceof SQLColumnDefinition) {
                SQLColumnDefinition column = (SQLColumnDefinition) element;
                String colName = cleanName(column.getName().getSimpleName());

                if (IGNORED_COLUMNS.contains(colName.toLowerCase())) continue;

                String colType = column.getDataType().getName();
                StringBuilder fieldStr = new StringBuilder(colName).append(":").append(colType);

                if (column.isPrimaryKey() ||
                        (createTable.findColumn(colName) != null && isPrimaryKeyInConstraints(createTable, colName))) {
                    fieldStr.append("(PK)");
                }

                if (column.getComment() != null) {
                    String comment = cleanComment(column.getComment().toString());
                    if (!comment.isEmpty()) fieldStr.append("/").append(comment);
                }
                fieldLines.add(fieldStr.toString());

            } else if (element instanceof SQLForeignKeyConstraint) {
                SQLForeignKeyConstraint fk = (SQLForeignKeyConstraint) element;
                String targetTable = cleanName(fk.getReferencedTableName().getSimpleName());
                ArchRelationship rel = new ArchRelationship();
                rel.setSourceId(tableName);
                rel.setTargetId(targetTable);
                rel.setType("FOREIGN_KEY");
                // label 字段已删除，外键信息记录在 description 中
                relationships.add(rel);
            }
        }

        if (!fieldLines.isEmpty()) {
            if (desc.length() > 0) desc.append("\n");
            // 字段列表拼成紧凑字符串，用分号隔开，供 LLM 生成表格
            desc.append("fields: ").append(String.join(", ", fieldLines));
        }

        if (desc.length() > 0) {
            node.setDescription(desc.toString());
        }

        return node;
    }

    /**
     * 修复点：inferLogicalRelationships 方法
     * 引入 finalTarget 变量，解决 Lambda 表达式报错
     */
    private void inferLogicalRelationships(List<ArchNode> nodes, List<ArchRelationship> relationships, Set<String> allTables) {
        for (ArchNode node : nodes) {
            // 从 description 中提取字段列表（格式是 "fields: col1:type1, col2:type2, ..."）
            String desc = node.getDescription();
            if (desc == null || !desc.contains("fields:")) continue;

            String fieldsSection = desc.substring(desc.indexOf("fields:") + 7).trim();
            String[] fieldEntries = fieldsSection.split(",");

            for (String fieldStr : fieldEntries) {
                String colName = fieldStr.split(":")[0].trim();

                if (colName.toLowerCase().endsWith("_id")) {
                    String potentialTableName = colName.substring(0, colName.length() - 3);

                    String tempTarget = matchTable(potentialTableName, allTables);
                    if (tempTarget == null) {
                        tempTarget = matchTable(potentialTableName + "s", allTables);
                    }

                    String finalTarget = tempTarget;
                    if (finalTarget != null && !finalTarget.equals(node.getId())) {
                        boolean exists = relationships.stream().anyMatch(r ->
                                r.getSourceId().equals(node.getId()) && r.getTargetId().equals(finalTarget)
                        );
                        if (!exists) {
                            ArchRelationship rel = new ArchRelationship();
                            rel.setSourceId(node.getId());
                            rel.setTargetId(finalTarget);
                            rel.setType("LOGICAL_KEY");
                            // label 字段已删除
                            relationships.add(rel);
                        }
                    }
                }
            }
        }
    }

    private String matchTable(String guess, Set<String> tables) {
        for (String table : tables) {
            if (table.equalsIgnoreCase(guess)) return table;
        }
        return null;
    }

    private boolean isPrimaryKeyInConstraints(SQLCreateTableStatement table, String colName) {
        if (table.getTableElementList() == null) return false;
        for (SQLTableElement element : table.getTableElementList()) {
            if (element instanceof SQLPrimaryKey) {
                SQLPrimaryKey pk = (SQLPrimaryKey) element;
                return pk.getColumns().stream().anyMatch(c -> cleanName(c.getExpr().toString()).equals(colName));
            }
        }
        return false;
    }

    private String cleanName(String name) {
        if (name == null) return "";
        return name.replace("`", "").replace("\"", "").replace("'", "").trim();
    }

    private String cleanComment(String comment) {
        if (comment == null) return "";
        return comment.replace("'", "").trim();
    }

    private List<Path> findSqlFiles(String projectPath) throws IOException {
        Path startPath = Paths.get(projectPath);
        if (Files.isRegularFile(startPath) && startPath.toString().toLowerCase().endsWith(".sql")) {
            return List.of(startPath);
        }
        try (Stream<Path> paths = Files.walk(startPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".sql"))
                    .collect(Collectors.toList());
        }
    }
}