package org.example.travel_agent.constant;

import lombok.Getter;

@Getter
public enum ChuckStrategyEnum {

    FIXED_TOKEN(0,"固定token策略");

    private int code;

    private String description;

    ChuckStrategyEnum(int code,String description){
        this.code=code;
        this.description=description;
    }

}
