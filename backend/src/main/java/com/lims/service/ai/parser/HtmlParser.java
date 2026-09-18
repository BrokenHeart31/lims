package com.lims.service.ai.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * HTML 解析器（feature A，T03）—— 唯一使用 jsoup 的地方。
 *
 * <p>为什么用 jsoup 而不用正则/手写：HTML 容错解析是脏活（未闭合标签、实体、脚本样式混排），
 * 手写必然出错；jsoup 是**本项目唯一新增 Maven 依赖**（已获批准并登记 DECISIONS，
 * 实测离线本地仓可解析、无运行期传递依赖）。</p>
 *
 * <p>抽取策略：移除 {@code script/style/noscript/head}（避免把 JS/CSS 灌进检索索引），
 * 取 {@code body} 的**可见文本**；标题优先取 {@code <title>}。</p>
 */
@Component
public class HtmlParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "html".equals(ext) || "htm".equals(ext);
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        // 让 jsoup 自行处理 meta charset；无声明时其默认按 UTF-8，可用 setOutputSettings 已在内部处理
        String html = DocumentParser.decode(bytes);
        Document doc = Jsoup.parse(html);
        String title = doc.title();
        // 去噪声：脚本/样式/模板/头部导航
        doc.select("script,style,noscript,head,nav,footer").remove();
        Element body = doc.body();
        String text = body != null ? body.wholeText() : doc.wholeText();
        // 压缩连续空白与空行
        text = text.replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll("(?m)^\\s+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        String stdNo = DocumentParser.extractStdNo(fileName, head);
        String stdTitle = (title != null && !title.isBlank()) ? title.trim() : TxtParser.firstTitle(text);
        return new ParsedDocument(stdNo, stdTitle, text);
    }
}
