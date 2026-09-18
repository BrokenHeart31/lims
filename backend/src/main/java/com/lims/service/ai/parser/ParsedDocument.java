package com.lims.service.ai.parser;

/**
 * 解析产物（feature A，T03）。
 *
 * @param stdNo    从文件名/正文识别出的标准号；识别不到为 null（落库时以占位补齐）
 * @param stdTitle 标准名称（尽量从正文首段提取；可为 null）
 * @param text     抽取出的纯文本正文（供 ChunkSplitter 切块）
 */
public record ParsedDocument(String stdNo, String stdTitle, String text) {
}
