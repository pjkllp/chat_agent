package org.example.travel_agent.common;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.example.travel_agent.dto.UserInfo;

public class UserContext {

    private static final TransmittableThreadLocal<UserInfo> threadLocal=new TransmittableThreadLocal<>();

    public static UserInfo get(){
        return threadLocal.get();
    }

    public static void set(UserInfo userInfo){
        threadLocal.set(userInfo);
    }

    public static void remove(){
        threadLocal.remove();
    }

}
