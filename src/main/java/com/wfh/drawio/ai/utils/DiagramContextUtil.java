package com.wfh.drawio.ai.utils;

/**
 * @Title: DiagramContextUtil
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.utils
 * @Date 2025/12/21 08:57
 * @description: 已废弃 - diagramId 现通过函数式参数传递，不再依赖 ThreadLocal
 * @deprecated 使用构造函数传递 diagramId 和 sink 到 Tool 类，无需 ThreadLocal 上下文传播
 */
@Deprecated
public class DiagramContextUtil {

    // 此类已废弃，保留仅用于向后兼容
    // 实际使用参见 DrawClient.doChatStream() 中 Tool 的构造函数传递

}