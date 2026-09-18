package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务全流程引导（feature 增量 ai_flow_assistant，T02，设计 §2.6 / §4.4）。
 *
 * <p><b>事实层 100% 由代码确定性装配</b>（{@code FlowGuideAssembler} × {@code BusinessFlowMap}），
 * <b>业务域完全不调模型</b>。同一「当前状态 + 角色」重复询问，结构化事实字段 100% 一致（G3）。</p>
 *
 * <p>权限收敛（TE）：{@code hasPermission=false} 的步骤**不提供跳转**，改由前端灰显 +
 * 提示「需 X 权限，联系 R100/R2 开通」，**不诱导越权**。</p>
 */
@Data
public class FlowGuideVO {

    /** 样品编号（可空：空则返回总览，不标当前步） */
    private String sampleNo;

    /** 当前状态 code（可空） */
    private Integer currentStatus;

    /** 当前状态中文名 */
    private String currentStatusLabel;

    /** 当前步在步骤序列中的 1-based 序号（0=未标当前步/总览） */
    private int stageIndex;

    /** 下一步（= 当前状态对应的动作事实）；总览时为 null */
    private Step nextStep;

    /** 全部步骤（S10→S90，含权限可见性） */
    private List<Step> steps = new ArrayList<>();

    /**
     * 单个流程步骤（事实字段，模型不得生成/改写）。
     */
    @Data
    public static class Step {

        /** 该步骤所属状态 code（动作在此状态下发起） */
        private Integer status;

        /** 状态中文名 */
        private String stageLabel;

        /** 下一步动作（主语=下一步做什么） */
        private String nextAction;

        /** 入口路由（前端 router.push） */
        private String entryPath;

        /** 所需权限标识 */
        private String requiredPermission;

        /** 当前用户是否具备该权限（false → 前端灰显、不给跳转） */
        private boolean hasPermission;

        /** 字段/按钮级指引 */
        private String fieldHint;

        /** 是否当前步 */
        private boolean isCurrent;

        /** 负责人角色（R1/R2/R3/R100，供文案「找谁开权限」） */
        private String actorRole;
    }
}
