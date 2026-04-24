package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Result <T> {

    private String msg;

    private T data;

    private int code;

    public static <T> Result <T> success(){
        return new Result<>("操作成功",null,1);
    }

    public static <T> Result <T> success(String msg){
        return new Result<>(msg,null,1);
    }

    public static <T> Result <T> success(T data){
        return new Result<>("操作成功",data,1);
    }

    public static <T> Result <T> success(String msg,T data){
        return new Result<>(msg,data,1);
    }

    public static <T> Result <T> fail(){
        return new Result<>("操作失败",null,0);
    }

    public static <T> Result <T> fail(String msg){
        return new Result<>(msg,null,0);
    }

    public static <T> Result <T> fail(T data){
        return new Result<>("操作失败",data,0);
    }

    public static <T> Result <T> fail(String msg,T data){
        return new Result<>(msg,data,0);
    }
}
