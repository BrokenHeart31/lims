package com.lims;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LIMS 后端启动类
 *
 * <p>食品质量检验测试中心 实验室信息管理系统。
 * 业务主线七阶段与样品状态机 S10→S90 见 AGENTS.md 第 7 章。</p>
 */
@SpringBootApplication
@MapperScan("com.lims.mapper")
public class LimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LimsApplication.class, args);
    }
}
