package org.example.travel_agent.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档主状态机（落库字段 status）。
 * <p>0=INIT 初始化，1=PARSED 已解析，2=CHUNKING 切块中，3=CHUNKED 已切块，
 * 4=EMBEDDING 向量化中，5=VECTORIZED 已向量化，6=FAILED 失败</p>
 */
@Getter
@AllArgsConstructor
public enum DocumentStatusEnum  {

    INIT(0, "初始化"),
    PARSED(1, "已解析"),
    CHUNKING(2, "切块中"),
    CHUNKED(3, "已切块"),
    EMBEDDING(4, "向量化中"),
    VECTORIZED(5, "已向量化"),
    FAILED(6, "失败");

    @EnumValue
    private final Integer value;
    private final String desc;
}
