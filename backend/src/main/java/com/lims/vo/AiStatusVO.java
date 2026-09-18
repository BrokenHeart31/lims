package com.lims.vo;

import lombok.Data;

/**
 * AI 服务状态（健康检查，feature A，T03 / api-spec A1）。
 *
 * <p><b>fail-soft 契约</b>：离线时本接口仍是 {@code HTTP 200 + code=0}，
 * 只是 {@code online=false}——「状态查询」本身不是错误，前端据此显示角标与启动指引，
 * **绝不弹全局错误**。</p>
 */
@Data
public class AiStatusVO {

    /** 服务是否在线（/api/tags 可达） */
    private boolean online;

    /** 服务地址（回环口） */
    private String baseUrl;

    /** 期望模型名 */
    private String model;

    /** 模型是否已就绪（tags 中含该模型） */
    private boolean modelPresent;

    /** 健康检查往返延迟（毫秒）；离线为 -1 */
    private long latencyMs;

    /** 提示文案（在线：「AI 服务正常」；离线：启动指引） */
    private String hint;

    /** 离线时展示的启动脚本路径 */
    private String startScript;

    /** 标准索引是否就绪（gb_document 已入库 ≥1 篇）——C-11 三就绪自检之一 */
    private boolean kbReady;

    /** 已入库标准文档数（只读，走 GbDocumentMapper） */
    private long kbDocCount;
}
