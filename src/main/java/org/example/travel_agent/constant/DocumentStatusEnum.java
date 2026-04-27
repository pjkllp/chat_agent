package org.example.travel_agent.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.core.enums.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档状态枚举（落库为整数编码）。
 */
@Getter
@AllArgsConstructor
public enum DocumentStatusEnum implements IEnum<Integer> {
    INIT(0, "初始化"),
    CHUNKING(1, "切块中"),
    CHUNKED(2, "已切块"),
    EMBEDDING(3, "向量化中"),
    VECTORIZED(4, "已向量化"),
    FAILED(5, "失败");

    @EnumValue
    private final Integer value;
    private final String desc;

    @Override
    public Integer getValue() {
        return value;
    }
}
