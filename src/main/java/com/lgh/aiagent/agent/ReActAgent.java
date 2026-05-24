package com.lgh.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReActAgent 模式的代理抽象类
 */

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent{

    /**
     * 处理当前状态，并决定下一步行动
     *
     * @return 是否需要执行，true 表示需要执行，false 表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 动作结果
     */
    public abstract String act();

    /**
     * 步骤执行
     *
     * @return 步骤结果
     */
    @Override
    public String step() {
        try {
            // 先思考 - 决定是否需要行动
            boolean shouldAct = think();
            if (!shouldAct) {
                log.info("Thinking complete - no action needed");
                return "Thinking complete - no action needed";
            }
            // 执行行动
            String actionResult = act();
            if (StrUtil.isBlank(actionResult)) {
                return "Action executed but returned no result";
            }
            log.info("Action completed: {}", actionResult);
            return actionResult;
        } catch (Exception e) {
            log.error("Error during step: {}", e.getMessage());
            return "Error during step: " + e.getMessage();
        }
    }

}
