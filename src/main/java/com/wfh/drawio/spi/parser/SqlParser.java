package com.wfh.drawio.spi.parser;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLForeignKeyConstraint;
import com.alibaba.druid.sql.ast.statement.SQLTableElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Title: SqlParser
 * @Author wangfenghuan
 * @Package com.wfh.drawio.spi.parser
 * @description: SQL文件解析器 (Based on Alibaba Druid)
 */
@Slf4j
public class SqlParser implements LanguageParser {

    @Override
    public String getName() {
        return "SQL-DDL (Druid)";
    }

    @Override
    public boolean canParse(String projectDir) {
        File file = new File(projectDir);
        if (!file.exists()) return false;
        
        // If it's a file, check extension
        if (file.isFile()) {
            return file.getName().toLowerCase().endsWith(".sql");
        }
        
        // If it's a directory:
        // 1. Avoid claiming it if it's a Java project (has pom.xml or build.gradle)
        if (new File(file, "pom.xml").exists() || new File(file, "build.gradle").exists()) {
            return false;
        }

        // 2. Check if it contains any .sql files
        try (Stream<Path> paths = Files.walk(Paths.get(projectDir))) {
            return paths.anyMatch(p -> p.toString().toLowerCase().endsWith(".sql"));
        } catch (IOException e) {
            log.error("Error checking for SQL files", e);
            return false;
        }
    }

    @Override
    public ProjectAnalysisResult parse(String projectDir) {
        log.info("Starting SQL analysis with Druid: {}", projectDir);
        
        ProjectAnalysisResult result = new ProjectAnalysisResult();
        List<ArchNode> nodes = new ArrayList<>();
        List<ArchRelationship> relationships = new ArrayList<>();
        
        try {
            List<Path> sqlFiles = findSqlFiles(projectDir);
            log.info("Found {} SQL files", sqlFiles.size());
            
            for (Path sqlFile : sqlFiles) {
                String content = Files.readString(sqlFile);
                parseSqlContent(content, nodes, relationships);
            }
            
        } catch (IOException e) {
            log.error("Error parsing SQL files", e);
            throw new RuntimeException("Failed to parse SQL files: " + e.getMessage());
        }
        
        result.setNodes(nodes);
        result.setRelationships(relationships);
        return result;
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

    private void parseSqlContent(String content, List<ArchNode> nodes, List<ArchRelationship> relationships) {
        try {
            // Attempt to parse with MySQL dialect first, as it's common
            // Druid handles many standard SQL features even if dialect is slightly off
            List<SQLStatement> statements = SQLUtils.parseStatements(content, JdbcConstants.MYSQL);
            
            for (SQLStatement stmt : statements) {
                if (stmt instanceof SQLCreateTableStatement) {
                    processCreateTable((SQLCreateTableStatement) stmt, nodes, relationships);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SQL content with Druid: {}", e.getMessage());
            // Fallback or just log error? For now, we log and continue.
        }
    }
    
    private void processCreateTable(SQLCreateTableStatement createTable, List<ArchNode> nodes, List<ArchRelationship> relationships) {
        String tableName = createTable.getTableName().replace("`", "");
        
        ArchNode node = new ArchNode();
        node.setId(tableName);
        node.setName(tableName);
        node.setType("TABLE");
        node.setStereotype("Database Table");
        
        List<String> fields = new ArrayList<>();
        
        for (SQLTableElement element : createTable.getTableElementList()) {
            if (element instanceof SQLColumnDefinition) {
                SQLColumnDefinition column = (SQLColumnDefinition) element;
                String colName = column.getName().getSimpleName().replace("`", "");
                String colType = column.getDataType().getName();
                
                String fieldStr = colName + ": " + colType;
                if (column.isPrimaryKey()) {
                    fieldStr += " (PK)";
                }
                fields.add(fieldStr);


            } else if (element instanceof SQLForeignKeyConstraint) {
                SQLForeignKeyConstraint fk = (SQLForeignKeyConstraint) element;
                String targetTable = fk.getReferencedTableName().getSimpleName().replace("`", "");
                
                // Construct relationship
                ArchRelationship rel = new ArchRelationship();
                rel.setSourceId(tableName);
                rel.setTargetId(targetTable);
                rel.setType("FOREIGN_KEY");
                
                String colName = fk.getReferencingColumns().stream()
                        .map(c -> c.getSimpleName().replace("`", ""))
                        .collect(Collectors.joining(","));
                        
                rel.setLabel("FK: " + colName);
                relationships.add(rel);
            }
        }
        
        node.setFields(fields);
        nodes.add(node);
    }
}
