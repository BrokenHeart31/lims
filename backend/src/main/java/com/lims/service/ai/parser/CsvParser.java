package com.lims.service.ai.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 解析器（feature A，T03）。
 *
 * <p>把表格转成「每行一条 `列: 值` 的可读文本」，让 ngram 检索能命中单元格内容
 * （直接保留逗号分隔会丢掉「列名 → 值」的语义）。支持双引号包裹的字段（含转义 {@code ""}）。</p>
 */
@Component
public class CsvParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "csv".equals(ext) || "tsv".equals(ext);
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        String raw = DocumentParser.decode(bytes);
        char delim = fileName != null && fileName.toLowerCase().endsWith(".tsv") ? '\t' : ',';
        List<List<String>> rows = parseCsv(raw, delim);
        StringBuilder sb = new StringBuilder();
        List<String> header = rows.isEmpty() ? List.of() : rows.get(0);
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (i == 0) {
                sb.append(String.join(" | ", row)).append('\n');
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (int c = 0; c < row.size(); c++) {
                String col = c < header.size() ? header.get(c) : ("列" + (c + 1));
                String val = row.get(c).trim();
                if (!val.isEmpty()) {
                    parts.add(col + ": " + val);
                }
            }
            sb.append(String.join("; ", parts)).append('\n');
        }
        String text = sb.toString().trim();
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        return new ParsedDocument(DocumentParser.extractStdNo(fileName, head), null, text);
    }

    /** 极简 CSV 解析（支持引号包裹与 {@code ""} 转义；不支持跨行字段）。 */
    static List<List<String>> parseCsv(String text, char delim) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        for (String line : text.split("\\r?\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            cur.append('"');
                            i++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        cur.append(ch);
                    }
                } else if (ch == '"') {
                    quoted = true;
                } else if (ch == delim) {
                    cells.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
            cells.add(cur.toString());
            rows.add(cells);
        }
        return rows;
    }
}
