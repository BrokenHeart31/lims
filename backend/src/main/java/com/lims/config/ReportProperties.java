package com.lims.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 检验报告版式配置（application.yml 的 {@code lims.report.*}，T-702）。
 *
 * <p>报告封面/页脚的机构抬头、资质证书编号、联系方式、实验环境与「注意事项」条款属于
 * <b>机构标识信息</b>，会随资质换证而变更，且不应硬编码在代码里（换证即重新发版）。
 * 故抽为配置项：{@code application.yml} 为生产口径，未配置时回退到本类默认值。</p>
 *
 * <p><b>默认值取自业务说明书报告样例（与 application.yml 的 lims.report.* 严格一致）</b>，
 * 非编造占位值；生产部署仍应由使用单位核对。</p>
 *
 * <p>用 {@code @Component + @ConfigurationProperties} 自注册 Bean，避免改动
 * {@code SecurityConfig} 等公共配置类（T-702 施工约定：不触碰公共文件）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lims.report")
public class ReportProperties {

    /** 机构名称（封面抬头与页脚） */
    private String orgName = "南通市食品质量检验测试中心";

    /** 检验检测机构资质认定（CMA）证书编号（封面资质行第 1 行） */
    private String cmaNo = "181004090030";

    /** 农产品质量安全检测机构考核合格（CATL）证书编号（仅 CMA-CATL 报告显示，为第 2 行） */
    private String catlNo = "[2020]农质检核（苏）字第0021号";

    /** 机构地址 */
    private String address = "南通市通州区江海大道18号";

    /** 联系电话 */
    private String phone = "0513-83548999";

    /** 邮政编码 */
    private String postcode = "226011";

    /** 传真 */
    private String fax = "0513-83548888";

    /** 实验环境条件（报告第 1 页固定栏） */
    private String environment = "温度：20℃-25℃  湿度：40%-60%";

    /** 报告注意事项（封面固定条款，逐条渲染并自动编号） */
    private List<String> notes = new ArrayList<>(List.of(
            "报告无“检验报告专用章”或检验单位公章无效。",
            "复制报告未重新加盖“检验报告专用章”和检验单位公章无效。",
            "报告无制表、审核、批准人签字无效。",
            "报告涂改无效。",
            "对检验报告若有异议，应于收到报告之日起五日内向检验单位提出，逾期不予受理。",
            "委托检验仅对来样负责。",
            "未经本中心同意，该检验报告不得用于商业性宣传。"
    ));
}
