package com.wfh.drawio.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

/**
 * 架构图中的"节点" (一个 Spring Bean 类)
 * <p>
 * Token 节省策略：@JsonInclude(NON_NULL) 确保未赋值字段不出现在 JSON 中。
 * LLM 可读性策略：字段名保持完整语义，layer/role 双字段明确架构层次。
 * @author fenghuanwang
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchNode {

    /** 全限定类名，作为唯一 ID，例如 "com.example.UserController" */
    private String id;

    /** 简单类名，作为图中显示名称，例如 "UserController" */
    private String name;

    /**
     * 架构层级 (用于 LLM 按层分区布局)
     * 枚举值: API | BIZ | DATA | INFRA
     */
    private String layer;

    /**
     * 技术角色 (用于 LLM 选择节点样式/颜色)
     * 枚举值: CONTROLLER | SERVICE | REPOSITORY | MAPPER | ENTITY | CONFIG | COMPONENT
     */
    private String role;

    /**
     * 所属包名 (用于 LLM 子图分组，同包的类画在一起)
     * 例如 "com.example.user"
     */
    private String packageName;

    /**
     * Javadoc 注释纯文本 (帮助 LLM 理解类的业务职责)
     * 为空时不序列化，节省 Token
     */
    private String description;

    /**
     * API 路由列表，仅 Controller 类有值 (为 null 时不序列化)
     * 例如: ["[GET] /api/users", "[POST] /api/users"]
     */
    private List<String> apiRoutes;

    /**
     * 数据库表名，仅 Entity/Mapper 类有值 (为 null 时不序列化)
     * 例如: "t_user"
     */
    private String tableName;
}
